package AvtoZvuk.kz;

import AvtoZvuk.kz.accounting.*;
import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.catalog.CatalogService;
import AvtoZvuk.kz.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KzApplicationTests {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("BOOTSTRAP_OWNER_USERNAME", () -> "test-bootstrap");
        registry.add("BOOTSTRAP_OWNER_PASSWORD", () -> "test-bootstrap-password");
    }

    @Autowired Database db;
    @Autowired CatalogService catalog;
    @Autowired PurchaseService purchases;
    @Autowired SaleService sales;
    @Autowired ReturnService returns;
    @Autowired StockService stock;
    @Autowired DocumentQueries documents;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    long ownerEmployee, sellerEmployee, supplier, product;
    String ownerName, sellerName;

    @BeforeEach
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        ownerName = "owner-" + suffix;
        sellerName = "seller-" + suffix;
        ownerEmployee = db.id("INSERT INTO employees(full_name) VALUES ('Test owner') RETURNING id");
        sellerEmployee = db.id("INSERT INTO employees(full_name) VALUES ('Test seller') RETURNING id");
        String hash = passwords.encode("test-password-123");
        db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, 'OWNER') RETURNING id", ownerEmployee, ownerName, hash);
        db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, 'SELLER') RETURNING id", sellerEmployee, sellerName, hash);
        login(ownerName);
        supplier = catalog.supplier(new Commands.Supplier("Supplier " + suffix, null));
        product = newProduct();
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void fifoSaleSplitPaymentAndPartialReturn() {
        receive(product, 1, "100"); receive(product, 2, "120");
        long sale = sales.sell(new Commands.Sale(UUID.randomUUID(), sellerEmployee,
                List.of(new Commands.SaleItem(product, 2, n("200"), n("20"))),
                List.of(payment("100"), new Commands.Payment(Commands.PaymentMethod.KASPI_PAY, n("280")))));
        var view = documents.sale(sale);
        assertThat(view.total()).isEqualByComparingTo("380");
        assertThat(view.bonus()).isEqualByComparingTo("19");
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.amount("SELECT sum(-m.quantity * l.unit_cost) FROM stock_movements m JOIN stock_lots l ON l.id = m.lot_id WHERE m.sale_id = ?", sale)).isEqualByComparingTo("220");
        var command = returnCommand(sale, line(sale), "190", true);
        long returned = returns.returnSale(command);
        assertThat(returns.returnSale(command)).isEqualTo(returned);
        assertThat(documents.returned(returned).bonus()).isEqualByComparingTo("-9.50");
        assertThat(quantity(product)).isEqualTo(2);
        assertThat(db.amount("SELECT stock_cost FROM product_stock WHERE product_id = ?", product)).isEqualByComparingTo("220");
        assertThat(db.id("SELECT count(*) FROM audit_log WHERE entity_type = 'Sale' AND entity_id = ?", sale)).isEqualTo(1);
    }

    @Test
    void insufficientStockRollsBackEarlierLinesAndRetrySucceeds() {
        receive(product, 1, "10");
        long empty = newProduct();
        UUID key = UUID.randomUUID();
        var command = new Commands.Sale(key, null,
                List.of(new Commands.SaleItem(product, 1, n("20"), n("0")), new Commands.SaleItem(empty, 1, n("20"), n("0"))), List.of(payment("40")));
        assertThatThrownBy(() -> sales.sell(command)).isInstanceOf(BusinessException.class).hasMessageContaining("Недостаточно товара");
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.id("SELECT count(*) FROM sales WHERE request_key = ?", key)).isZero();
        receive(empty, 1, "10");
        assertThat(documents.sale(sales.sell(command)).total()).isEqualByComparingTo("40");
        assertThat(quantity(product)).isZero();
    }

    @Test
    void invalidPaymentsAndDiscountRollBackEverything() {
        receive(product, 1, "10");
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> sales.sell(saleCommand(key, product, 1, "20", "19")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Сумма оплат");
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.id("SELECT count(*) FROM sales WHERE request_key = ?", key)).isZero();
        assertThatThrownBy(() -> sales.sell(new Commands.Sale(key, null,
                List.of(new Commands.SaleItem(product, 1, n("20"), n("21"))), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Скидка");
    }

    @Test
    void partialReturnRoundingRefundsExactTotalAndBonus() {
        receive(product, 3, "0.10");
        long sale = sales.sell(new Commands.Sale(UUID.randomUUID(), null,
                List.of(new Commands.SaleItem(product, 3, n("1"), n("2"))), List.of(payment("1"))));
        long line = line(sale);
        for (String amount : List.of("0.33", "0.34", "0.33")) returns.returnSale(returnCommand(sale, line, amount, true));
        assertThat(db.amount("SELECT sum(amount) FROM bonus_entries WHERE sale_id = ? OR return_id IN (SELECT id FROM sale_returns WHERE sale_id = ?)", sale, sale)).isEqualByComparingTo("0");
        assertThat(quantity(product)).isEqualTo(3);
        assertThatThrownBy(() -> returns.returnSale(returnCommand(sale, line, "0.33", true)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Количество возврата");
    }

    @Test
    void defectiveReturnConsumesOriginalUnitBeforeRestock() {
        receive(product, 1, "100"); receive(product, 1, "120");
        long sale = sales.sell(saleCommand(UUID.randomUUID(), product, 2, "200", "400"));
        long line = line(sale);
        returns.returnSale(returnCommand(sale, line, "200", false));
        returns.returnSale(returnCommand(sale, line, "200", true));
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.amount("SELECT stock_cost FROM product_stock WHERE product_id = ?", product)).isEqualByComparingTo("120");
    }

    @Test
    void idempotencyRejectsChangedPayloadAndDoesNotDuplicateDocuments() {
        var purchase = new Commands.Purchase(UUID.randomUUID(), supplier, List.of(new Commands.PurchaseItem(product, 1, n("100"))));
        long receipt = purchases.receive(purchase);
        assertThat(purchases.receive(purchase)).isEqualTo(receipt);
        var command = saleCommand(UUID.randomUUID(), product, 1, "200", "200");
        long sale = sales.sell(command);
        assertThat(sales.sell(command)).isEqualTo(sale);
        assertThatThrownBy(() -> sales.sell(saleCommand(command.requestKey(), product, 1, "199", "199")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ключ запроса");
        var opening = new Commands.OpeningStock(UUID.randomUUID(), product, 2, n("90"), "Initial count");
        long adjustment = stock.opening(opening);
        assertThat(stock.opening(opening)).isEqualTo(adjustment);
        assertThat(quantity(product)).isEqualTo(2);
    }

    @Test
    void concurrentSellersCannotOversellLastUnit() throws Exception {
        receive(product, 1, "100");
        var outcomes = concurrently(() -> sales.sell(saleCommand(UUID.randomUUID(), product, 1, "200", "200")),
                () -> sales.sell(saleCommand(UUID.randomUUID(), product, 1, "200", "200")));
        assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        assertThat(quantity(product)).isZero();
    }

    @Test
    void concurrentReplayCreatesOneSale() throws Exception {
        receive(product, 2, "100");
        var command = saleCommand(UUID.randomUUID(), product, 1, "200", "200");
        var outcomes = concurrently(() -> sales.sell(command), () -> sales.sell(command));
        assertThat(outcomes).allMatch(Long.class::isInstance);
        assertThat(outcomes.getFirst()).isEqualTo(outcomes.getLast());
        assertThat(quantity(product)).isEqualTo(1);
    }

    @Test
    void sellerPermissionsAndDisabledAccountAreCheckedInServices() {
        login(sellerName);
        assertThatThrownBy(() -> catalog.supplier(new Commands.Supplier("Forbidden", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sales.sell(new Commands.Sale(UUID.randomUUID(), ownerEmployee,
                List.of(new Commands.SaleItem(product, 1, n("20"), n("0"))), List.of(payment("20")))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("прав");
        login(ownerName); receive(product, 2, "10");
        long ownerSale = sales.sell(saleCommand(UUID.randomUUID(), product, 1, "20", "20"));
        login(sellerName);
        assertThatThrownBy(() -> documents.sale(ownerSale)).isInstanceOf(BusinessException.class);
        long ownSale = sales.sell(saleCommand(UUID.randomUUID(), product, 1, "20", "20"));
        assertThat(documents.sale(ownSale).id()).isEqualTo(ownSale);
        db.update("UPDATE employees SET active = false WHERE id = ?", sellerEmployee);
        assertThatThrownBy(() -> documents.sale(ownSale)).isInstanceOf(BusinessException.class);
    }

    @Test
    void httpRequiresAuthenticationCsrfValidationAndOwnerRole() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        var csrfResponse = mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) csrfResponse.getRequest().getSession(false);
        String token = com.jayway.jsonpath.JsonPath.read(csrfResponse.getResponse().getContentAsString(), "$.token");
        var loggedIn = mvc.perform(post("/api/login").session(session).header("X-CSRF-TOKEN", token)
                .param("username", ownerName).param("password", "test-password-123"))
                .andExpect(status().isNoContent()).andReturn();
        session = (org.springframework.mock.web.MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ownerName));
        mvc.perform(post("/api/categories").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"name\":\"Old token\"}"))
                .andExpect(status().isForbidden());
        var refreshed = mvc.perform(get("/api/csrf").session(session)).andReturn();
        token = com.jayway.jsonpath.JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/categories").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"name\":\"HTTP category\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/login").with(csrf()).param("username", ownerName).param("password", "wrong-password")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").with(user(ownerName).roles("OWNER")).contentType("application/json").content("{\"name\":\"Category\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").with(user(ownerName).roles("OWNER")).with(csrf()).contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/categories").with(user(sellerName).roles("SELLER")).with(csrf()).contentType("application/json").content("{\"name\":\"Category\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products").with(user(sellerName).roles("SELLER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unitCost"))));
    }

    private long newProduct() {
        return catalog.saveProduct(null, new Commands.Product("Speaker", "SKU-" + UUID.randomUUID(), null, null, n("200"), 1)).getId();
    }
    private long receive(long productId, int quantity, String cost) {
        return purchases.receive(new Commands.Purchase(UUID.randomUUID(), supplier, List.of(new Commands.PurchaseItem(productId, quantity, n(cost)))));
    }
    private long quantity(long productId) { return db.id("SELECT quantity FROM product_stock WHERE product_id = ?", productId); }
    private long line(long sale) { return ((Number) documents.sale(sale).items().getFirst().get("id")).longValue(); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static Commands.Payment payment(String amount) { return new Commands.Payment(Commands.PaymentMethod.CASH, n(amount)); }
    private static Commands.Sale saleCommand(UUID key, long productId, int quantity, String price, String paid) {
        return new Commands.Sale(key, null, List.of(new Commands.SaleItem(productId, quantity, n(price), n("0"))), List.of(payment(paid)));
    }
    private static Commands.SaleReturn returnCommand(long sale, long line, String amount, boolean restock) {
        return new Commands.SaleReturn(UUID.randomUUID(), sale, "Test return", List.of(new Commands.ReturnItem(line, 1, restock)), List.of(payment(amount)));
    }
    private static void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "unused", List.of()));
    }
    private List<Object> concurrently(Supplier<Long> first, Supplier<Long> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Object>>();
            for (var action : List.of(first, second)) tasks.add(executor.submit(() -> {
                login(ownerName); ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return action.get();
                } catch (BusinessException ex) { return ex; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(tasks.getFirst().get(20, TimeUnit.SECONDS), tasks.getLast().get(20, TimeUnit.SECONDS));
        }
    }
}
