package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.*;
import AvtoZvuk.kz.catalog.CatalogService;
import AvtoZvuk.kz.catalog.Product;
import AvtoZvuk.kz.security.CurrentActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StoreController {
    private final CatalogService catalog;
    private final PurchaseService purchases;
    private final SaleService sales;
    private final ReturnService returns;
    private final StockService stock;
    private final DocumentQueries documents;
    private final CurrentActor actors;

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName(), "parameterName", token.getParameterName());
    }
    @GetMapping("/me") CurrentActor.Actor me() { return actors.get(); }
    @GetMapping("/products") List<Map<String, Object>> products(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return catalog.products(limit, offset); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    Product product(@Valid @RequestBody Commands.Product command) { return catalog.saveProduct(null, command); }
    @PutMapping("/products/{id}") Product product(@PathVariable long id, @Valid @RequestBody Commands.Product command) { return catalog.saveProduct(id, command); }
    @PostMapping("/products/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) { catalog.deactivateProduct(id); }
    @GetMapping("/categories") List<Map<String, Object>> categories() { return catalog.categories(); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> category(@Valid @RequestBody Commands.Named command) { return Map.of("id", catalog.category(command)); }
    @GetMapping("/suppliers") List<Map<String, Object>> suppliers() { return catalog.suppliers(); }
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> supplier(@Valid @RequestBody Commands.Supplier command) { return Map.of("id", catalog.supplier(command)); }
    @GetMapping("/employees") List<Map<String, Object>> employees() { return catalog.employees(); }
    @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> employee(@Valid @RequestBody Commands.Employee command) { return Map.of("id", catalog.employee(command)); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> user(@Valid @RequestBody Commands.User command) { return Map.of("id", catalog.user(command)); }
    @PostMapping("/stock/opening") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> opening(@Valid @RequestBody Commands.OpeningStock command) { return Map.of("id", stock.opening(command)); }
    @PostMapping("/purchases") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> purchase(@Valid @RequestBody Commands.Purchase command) { return Map.of("id", purchases.receive(command)); }
    @GetMapping("/purchases/{id}") DocumentQueries.Document purchase(@PathVariable long id) { return documents.purchase(id); }
    @PostMapping("/sales") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> sale(@Valid @RequestBody Commands.Sale command) { return Map.of("id", sales.sell(command)); }
    @GetMapping("/sales") List<Map<String, Object>> sales(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return documents.sales(limit, offset); }
    @GetMapping("/sales/{id}") DocumentQueries.Document sale(@PathVariable long id) { return documents.sale(id); }
    @PostMapping("/returns") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> returned(@Valid @RequestBody Commands.SaleReturn command) { return Map.of("id", returns.returnSale(command)); }
    @GetMapping("/returns/{id}") DocumentQueries.Document returned(@PathVariable long id) { return documents.returned(id); }
}
