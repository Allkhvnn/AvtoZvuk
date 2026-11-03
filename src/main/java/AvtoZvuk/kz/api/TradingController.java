package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.*;
import AvtoZvuk.kz.api.dto.trading.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradingController {
    private final PurchaseService purchases;
    private final SaleService sales;
    private final ReturnService returns;
    private final StockService stock;
    private final DocumentQueries documents;

    @PostMapping("/stock/opening") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> opening(@Valid @RequestBody OpeningStockRequest command) { return Map.of("id", stock.opening(command)); }
    @PostMapping("/purchases") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> purchase(@Valid @RequestBody PurchaseRequest command) { return Map.of("id", purchases.receive(command)); }
    @GetMapping("/purchases") List<PurchaseSummaryResponse> purchases(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return documents.purchases(limit, offset); }
    @GetMapping("/purchases/{id}") PurchaseDocumentResponse purchase(@PathVariable long id) { return documents.purchase(id); }
    @PostMapping("/sales") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> sale(@Valid @RequestBody SaleRequest command) { return Map.of("id", sales.sell(command)); }
    @GetMapping("/sales") List<SaleSummaryResponse> sales(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return documents.sales(limit, offset); }
    @GetMapping("/sales/{id}") SaleDocumentResponse sale(@PathVariable long id) { return documents.sale(id); }
    @PostMapping("/returns") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> returned(@Valid @RequestBody SaleReturnRequest command) { return Map.of("id", returns.returnSale(command)); }
    @GetMapping("/returns/{id}") ReturnDocumentResponse returned(@PathVariable long id) { return documents.returned(id); }
}
