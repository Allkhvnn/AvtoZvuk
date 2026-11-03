package AvtoZvuk.kz;

import AvtoZvuk.kz.accounting.*;
import AvtoZvuk.kz.api.dto.common.ReversalRequest;
import AvtoZvuk.kz.api.dto.finance.*;
import AvtoZvuk.kz.api.dto.catalog.ProductRequest;
import AvtoZvuk.kz.api.dto.catalog.SupplierRequest;
import AvtoZvuk.kz.api.dto.personnel.EmployeeRequest;
import AvtoZvuk.kz.api.dto.personnel.EmployeeStatusRequest;
import AvtoZvuk.kz.api.dto.personnel.UserStatusRequest;
import AvtoZvuk.kz.api.dto.personnel.BonusAdjustmentRequest;
import AvtoZvuk.kz.api.dto.personnel.StaffPaymentRequest;
import AvtoZvuk.kz.api.dto.inventory.*;
import AvtoZvuk.kz.api.dto.trading.*;
import AvtoZvuk.kz.catalog.CatalogService;
import AvtoZvuk.kz.catalog.PersonnelService;
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
import java.time.LocalDate;
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
    @Autowired FinanceService finance;
    @Autowired PayrollService payroll;
    @Autowired PersonnelService personnel;
    @Autowired ReportService reports;
    @Autowired InventoryService inventory;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    long ownerEmployee, sellerEmployee, ownerUser, sellerUser, supplier, product;
    String ownerName, sellerName;

    @BeforeEach
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        ownerName = "owner-" + suffix;
        sellerName = "seller-" + suffix;
        ownerEmployee = db.id("INSERT INTO employees(full_name) VALUES ('Test owner') RETURNING id");
        sellerEmployee = db.id("INSERT INTO employees(full_name) VALUES ('Test seller') RETURNING id");
        String hash = passwords.encode("test-password-123");
        ownerUser = db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, 'OWNER') RETURNING id", ownerEmployee, ownerName, hash);
        sellerUser = db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, 'SELLER') RETURNING id", sellerEmployee, sellerName, hash);
        login(ownerName);
        supplier = catalog.supplier(new SupplierRequest("Supplier " + suffix, null));
        product = newProduct();
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void fifoSaleSplitPaymentAndPartialReturn() {
        receive(product, 1, "100"); receive(product, 2, "120");
        long sale = sales.sell(new SaleRequest(UUID.randomUUID(), sellerEmployee,
                List.of(new SaleItemRequest(product, 2, n("200"), n("20"))),
                List.of(payment("100"), new PaymentRequest(PaymentRequest.Method.KASPI_PAY, n("280")))));
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
        var command = new SaleRequest(key, null,
                List.of(new SaleItemRequest(product, 1, n("20"), n("0")), new SaleItemRequest(empty, 1, n("20"), n("0"))), List.of(payment("40")));
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
        assertThatThrownBy(() -> sales.sell(new SaleRequest(key, null,
                List.of(new SaleItemRequest(product, 1, n("20"), n("21"))), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Скидка");
    }

    @Test
    void partialReturnRoundingRefundsExactTotalAndBonus() {
        receive(product, 3, "0.10");
        long sale = sales.sell(new SaleRequest(UUID.randomUUID(), null,
                List.of(new SaleItemRequest(product, 3, n("1"), n("2"))), List.of(payment("1"))));
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
        var purchase = new PurchaseRequest(UUID.randomUUID(), supplier, List.of(new PurchaseItemRequest(product, 1, n("100"))));
        long receipt = purchases.receive(purchase);
        assertThat(purchases.receive(purchase)).isEqualTo(receipt);
        assertThat(documents.purchases(50, 0)).anyMatch(row -> row.id() == receipt);
        var command = saleCommand(UUID.randomUUID(), product, 1, "200", "200");
        long sale = sales.sell(command);
        assertThat(sales.sell(command)).isEqualTo(sale);
        assertThatThrownBy(() -> sales.sell(saleCommand(command.requestKey(), product, 1, "199", "199")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ключ запроса");
        var opening = new OpeningStockRequest(UUID.randomUUID(), product, 2, n("90"), "Initial count");
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
        assertThatThrownBy(() -> catalog.supplier(new SupplierRequest("Forbidden", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sales.sell(new SaleRequest(UUID.randomUUID(), ownerEmployee,
                List.of(new SaleItemRequest(product, 1, n("20"), n("0"))), List.of(payment("20")))))
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

    @Test
    void expenseIsIdempotentAndReversalPreservesHistory() {
        long category = finance.expenseCategory(new ExpenseCategoryRequest("Rent " + UUID.randomUUID()));
        var command = new ExpenseRequest(UUID.randomUUID(), category, n("150000"),
                FinanceMethod.BANK_TRANSFER, "Monthly rent", null);
        long expense = finance.expense(command);
        assertThat(finance.expense(command)).isEqualTo(expense);
        assertThat(finance.expenseTotals().get("total")).isEqualTo(n("150000.00"));
        var reversal = new ReversalRequest(UUID.randomUUID(), "Entered twice");
        long reversed = finance.reverseExpense(expense, reversal);
        assertThat(finance.reverseExpense(expense, reversal)).isEqualTo(reversed);
        assertThat(finance.expenseTotals().get("total")).isEqualTo(n("0.00"));
        assertThat(finance.expenses(10, 0)).filteredOn(row -> Objects.equals(row.get("id"), expense))
                .singleElement().satisfies(row -> assertThat(row.get("reversed")).isEqualTo(true));
        assertThatThrownBy(() -> finance.reverseExpense(expense,
                new ReversalRequest(UUID.randomUUID(), "Again")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("уже отменен");
    }

    @Test
    void bonusAdjustmentsPaymentsSalaryAndReversalAreSeparate() {
        var adjustment = new BonusAdjustmentRequest(UUID.randomUUID(), sellerEmployee, n("100"), "Opening bonus balance");
        long adjustmentId = payroll.adjustBonus(adjustment);
        assertThat(payroll.adjustBonus(adjustment)).isEqualTo(adjustmentId);
        var bonusPayment = new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                StaffPaymentRequest.Type.BONUS, n("60"), FinanceMethod.CASH, "Bonus payout", null);
        long payment = payroll.pay(bonusPayment);
        assertThat(payroll.pay(bonusPayment)).isEqualTo(payment);
        assertThat(balance(sellerEmployee)).isEqualByComparingTo("40");
        assertThatThrownBy(() -> payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                StaffPaymentRequest.Type.BONUS, n("40.01"), FinanceMethod.CASH, "Too much", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("превышает");
        long salary = payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                StaffPaymentRequest.Type.SALARY, n("200000"), FinanceMethod.BANK_TRANSFER, "Salary", null));
        assertThat(balance(sellerEmployee)).isEqualByComparingTo("40");
        var reversal = new ReversalRequest(UUID.randomUUID(), "Wrong cash box");
        long reverse = payroll.reversePayment(payment, reversal);
        assertThat(payroll.reversePayment(payment, reversal)).isEqualTo(reverse);
        assertThat(balance(sellerEmployee)).isEqualByComparingTo("100");
        assertThat(payroll.payments(sellerEmployee, 10, 0)).extracting(row -> row.get("id"))
                .contains(payment, salary, reverse);
    }

    @Test
    void concurrentBonusPaymentsCannotExceedBalance() throws Exception {
        payroll.adjustBonus(new BonusAdjustmentRequest(UUID.randomUUID(), sellerEmployee, n("100"), "Competition test"));
        var outcomes = concurrently(
                () -> payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                        StaffPaymentRequest.Type.BONUS, n("70"), FinanceMethod.CASH, "First", null)),
                () -> payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                        StaffPaymentRequest.Type.BONUS, n("70"), FinanceMethod.CASH, "Second", null)));
        assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        assertThat(balance(sellerEmployee)).isEqualByComparingTo("30");
    }

    @Test
    void personnelStatusDisablesAccountsAndRequiresExplicitReactivation() {
        personnel.updateEmployee(sellerEmployee, new EmployeeRequest("Updated seller", "+77000000000", n("0.075")));
        assertThat(catalog.employees()).filteredOn(row -> row.id() == sellerEmployee)
                .singleElement().satisfies(row -> assertThat(row.bonusRate()).isEqualTo(n("0.0750")));
        personnel.employeeStatus(sellerEmployee, new EmployeeStatusRequest(false, "Employee left"));
        assertThat(db.one("SELECT active FROM app_users WHERE id = ?", sellerUser).get("active")).isEqualTo(false);
        login(sellerName);
        assertThatThrownBy(() -> documents.sales(10, 0)).isInstanceOf(BusinessException.class);
        login(ownerName);
        assertThatThrownBy(() -> personnel.userStatus(sellerUser, new UserStatusRequest(true, "Return")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Сначала активируйте");
        personnel.employeeStatus(sellerEmployee, new EmployeeStatusRequest(true, "Rehired"));
        personnel.userStatus(sellerUser, new UserStatusRequest(true, "Access restored"));
        login(sellerName);
        assertThat(documents.sales(10, 0)).isEmpty();
        login(ownerName);
        assertThatThrownBy(() -> personnel.userStatus(ownerUser, new UserStatusRequest(false, "Self")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("собственную");
    }

    @Test
    void reportsReconcileRevenueCostExpensesPaymentsProductsAndEmployees() {
        LocalDate today = LocalDate.now(ReportService.STORE_ZONE);
        var before = reports.summary(today, today);
        receive(product, 2, "100");
        long sale = sales.sell(new SaleRequest(UUID.randomUUID(), sellerEmployee,
                List.of(new SaleItemRequest(product, 2, n("200"), n("20"))), List.of(payment("380"))));
        returns.returnSale(returnCommand(sale, line(sale), "190", true));
        long expenseCategory = finance.expenseCategory(new ExpenseCategoryRequest("Report expense " + UUID.randomUUID()));
        finance.expense(new ExpenseRequest(UUID.randomUUID(), expenseCategory, n("50"),
                FinanceMethod.CASH, "Report expense", null));
        payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                StaffPaymentRequest.Type.BONUS, n("5"), FinanceMethod.CASH, "Bonus report", null));
        payroll.pay(new StaffPaymentRequest(UUID.randomUUID(), sellerEmployee,
                StaffPaymentRequest.Type.SALARY, n("30"), FinanceMethod.CASH, "Salary report", null));

        var after = reports.summary(today, today);
        assertDelta(before.salesRevenue(), after.salesRevenue(), "380");
        assertDelta(before.returns(), after.returns(), "190");
        assertDelta(before.netRevenue(), after.netRevenue(), "190");
        assertDelta(before.discounts(), after.discounts(), "20");
        assertDelta(before.saleCost(), after.saleCost(), "200");
        assertDelta(before.restoredCost(), after.restoredCost(), "100");
        assertDelta(before.netCost(), after.netCost(), "100");
        assertDelta(before.grossProfit(), after.grossProfit(), "90");
        assertDelta(before.expenses(), after.expenses(), "50");
        assertDelta(before.grossProfitAfterExpenses(), after.grossProfitAfterExpenses(), "40");
        assertDelta(before.bonusAccrued(), after.bonusAccrued(), "9.50");
        assertDelta(before.bonusPaid(), after.bonusPaid(), "5");
        assertDelta(before.salaryPaid(), after.salaryPaid(), "30");

        assertThat(reports.products(today, today)).filteredOn(row -> Objects.equals(row.get("productId"), product))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("netQuantity")).isEqualTo(1L);
                    assertThat(row.get("netRevenue")).isEqualTo(n("190.00"));
                    assertThat(row.get("netCost")).isEqualTo(n("100.00"));
                    assertThat(row.get("grossProfit")).isEqualTo(n("90.00"));
                });
        assertThat(reports.employees(today, today)).filteredOn(row -> Objects.equals(row.get("employeeId"), sellerEmployee))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("salesRevenue")).isEqualTo(n("380.00"));
                    assertThat(row.get("returns")).isEqualTo(n("190.00"));
                    assertThat(row.get("bonusAccrued")).isEqualTo(n("9.50"));
                    assertThat(row.get("bonusPaid")).isEqualTo(n("5.00"));
                });
        assertThat(reports.lowStock()).extracting(row -> row.get("productId")).contains(product);
        assertThat(reports.paymentMethods(today, today)).filteredOn(row -> "CASH".equals(row.get("method")))
                .singleElement().satisfies(row -> assertThat((BigDecimal) row.get("net")).isGreaterThanOrEqualTo(n("190")));
    }

    @Test
    void reportPeriodAndRoleAreValidated() {
        LocalDate today = LocalDate.now(ReportService.STORE_ZONE);
        assertThatThrownBy(() -> reports.summary(today, today.minusDays(1)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("раньше");
        assertThatThrownBy(() -> reports.summary(today.minusDays(400), today))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Период");
        login(sellerName);
        assertThatThrownBy(() -> reports.summary(today, today)).isInstanceOf(BusinessException.class);
    }

    @Test
    void inventoryShortageExcessWriteOffAndHistoryKeepExactCost() {
        receive(product, 3, "100");
        var shortage = new InventoryRequest(UUID.randomUUID(), "Counted shortage",
                List.of(new InventoryItemRequest(product, 2, null)));
        long shortageId = inventory.inventory(shortage);
        assertThat(inventory.inventory(shortage)).isEqualTo(shortageId);
        assertThat(quantity(product)).isEqualTo(2);
        assertThat(db.amount("SELECT stock_cost FROM product_stock WHERE product_id = ?", product))
                .isEqualByComparingTo("200");
        var shortageDocument = inventory.adjustment(shortageId);
        assertThat(shortageDocument.operationType()).isEqualTo("INVENTORY");
        assertThat(shortageDocument.items()).singleElement().satisfies(row -> {
            assertThat(row.systemQuantity()).isEqualTo(3L);
            assertThat(row.actualQuantity()).isEqualTo(2L);
            assertThat(row.difference()).isEqualTo(-1L);
        });

        long excessId = inventory.inventory(new InventoryRequest(UUID.randomUUID(), "Found sealed units",
                List.of(new InventoryItemRequest(product, 4, n("120")))));
        assertThat(quantity(product)).isEqualTo(4);
        assertThat(db.amount("SELECT stock_cost FROM product_stock WHERE product_id = ?", product))
                .isEqualByComparingTo("440");
        long writeOff = inventory.writeOff(new WriteOffRequest(UUID.randomUUID(), product, 3, "Damaged by water"));
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.amount("SELECT stock_cost FROM product_stock WHERE product_id = ?", product))
                .isEqualByComparingTo("120");
        assertThat(inventory.adjustment(writeOff).operationType()).isEqualTo("WRITE_OFF");
        assertThat(inventory.inventories(10, 0)).extracting(row -> row.id()).contains(shortageId, excessId);
        var movements = inventory.movements(product, 20, 0);
        assertThat(movements.getFirst().balance()).isEqualTo(1L);
        assertThat(movements).extracting(row -> row.adjustmentType())
                .contains("INVENTORY", "WRITE_OFF");
    }

    @Test
    void inventoryValidationRollsBackAndConcurrentWriteOffCannotOverspend() throws Exception {
        receive(product, 1, "100");
        UUID invalidKey = UUID.randomUUID();
        assertThatThrownBy(() -> inventory.inventory(new InventoryRequest(invalidKey, "Missing cost",
                List.of(new InventoryItemRequest(product, 2, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("себестоимость");
        assertThat(quantity(product)).isEqualTo(1);
        assertThat(db.id("SELECT count(*) FROM stock_adjustments WHERE request_key = ?", invalidKey)).isZero();
        var outcomes = concurrently(
                () -> inventory.writeOff(new WriteOffRequest(UUID.randomUUID(), product, 1, "First damage")),
                () -> inventory.writeOff(new WriteOffRequest(UUID.randomUUID(), product, 1, "Second damage")));
        assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        assertThat(quantity(product)).isZero();
    }

    private long newProduct() {
        return catalog.saveProduct(null, new ProductRequest("Speaker", "SKU-" + UUID.randomUUID(), null, null, n("200"), 1)).getId();
    }
    private long receive(long productId, int quantity, String cost) {
        return purchases.receive(new PurchaseRequest(UUID.randomUUID(), supplier, List.of(new PurchaseItemRequest(productId, quantity, n(cost)))));
    }
    private long quantity(long productId) { return db.id("SELECT quantity FROM product_stock WHERE product_id = ?", productId); }
    private BigDecimal balance(long employeeId) { return db.amount("SELECT balance FROM employee_bonus_balances WHERE employee_id = ?", employeeId); }
    private static void assertDelta(BigDecimal before, BigDecimal after, String expected) {
        assertThat(after.subtract(before)).isEqualByComparingTo(expected);
    }
    private long line(long sale) { return documents.sale(sale).items().getFirst().id(); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static PaymentRequest payment(String amount) { return new PaymentRequest(PaymentRequest.Method.CASH, n(amount)); }
    private static SaleRequest saleCommand(UUID key, long productId, int quantity, String price, String paid) {
        return new SaleRequest(key, null, List.of(new SaleItemRequest(productId, quantity, n(price), n("0"))), List.of(payment(paid)));
    }
    private static SaleReturnRequest returnCommand(long sale, long line, String amount, boolean restock) {
        return new SaleReturnRequest(UUID.randomUUID(), sale, "Test return", List.of(new ReturnItemRequest(line, 1, restock)), List.of(payment(amount)));
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
