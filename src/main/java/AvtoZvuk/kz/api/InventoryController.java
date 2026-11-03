package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.InventoryService;
import AvtoZvuk.kz.api.dto.inventory.InventoryDetailResponse;
import AvtoZvuk.kz.api.dto.inventory.InventoryRequest;
import AvtoZvuk.kz.api.dto.inventory.InventorySummaryResponse;
import AvtoZvuk.kz.api.dto.inventory.StockMovementResponse;
import AvtoZvuk.kz.api.dto.inventory.WriteOffRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventory;

    @PostMapping("/inventories") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> inventory(@Valid @RequestBody InventoryRequest command) { return Map.of("id", inventory.inventory(command)); }
    @GetMapping("/inventories") List<InventorySummaryResponse> inventories(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return inventory.inventories(limit, offset); }
    @GetMapping("/inventories/{id}") InventoryDetailResponse inventory(@PathVariable long id) { return inventory.adjustment(id); }
    @PostMapping("/stock/write-offs") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> writeOff(@Valid @RequestBody WriteOffRequest command) { return Map.of("id", inventory.writeOff(command)); }
    @GetMapping("/products/{id}/stock-movements") List<StockMovementResponse> movements(@PathVariable long id, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return inventory.movements(id, limit, offset); }
}
