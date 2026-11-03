package AvtoZvuk.kz.api;

import AvtoZvuk.kz.catalog.CatalogService;
import AvtoZvuk.kz.catalog.Product;
import AvtoZvuk.kz.api.dto.catalog.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalog;

    @GetMapping("/products")
    List<ProductResponse> products(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return catalog.products(limit, offset); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    Product product(@Valid @RequestBody Commands.Product command) { return catalog.saveProduct(null, command); }
    @PutMapping("/products/{id}")
    Product product(@PathVariable long id, @Valid @RequestBody Commands.Product command) { return catalog.saveProduct(id, command); }
    @PostMapping("/products/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) { catalog.deactivateProduct(id); }
    @GetMapping("/categories")
    List<CategoryResponse> categories() { return catalog.categories(); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> category(@Valid @RequestBody Commands.Named command) { return Map.of("id", catalog.category(command)); }
    @GetMapping("/suppliers")
    List<SupplierResponse> suppliers() { return catalog.suppliers(); }
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> supplier(@Valid @RequestBody Commands.Supplier command) { return Map.of("id", catalog.supplier(command)); }
}
