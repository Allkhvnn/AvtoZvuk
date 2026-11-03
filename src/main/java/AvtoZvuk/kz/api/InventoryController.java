package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.InventoryService;
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
    Map<String, Long> inventory(@Valid @RequestBody Commands.Inventory command) { return Map.of("id", inventory.inventory(command)); }
    @GetMapping("/inventories") List<Map<String, Object>> inventories(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return inventory.inventories(limit, offset); }
    @GetMapping("/inventories/{id}") InventoryService.Adjustment inventory(@PathVariable long id) { return inventory.adjustment(id); }
    @PostMapping("/stock/write-offs") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> writeOff(@Valid @RequestBody Commands.WriteOff command) { return Map.of("id", inventory.writeOff(command)); }
    @GetMapping("/products/{id}/stock-movements") List<Map<String, Object>> movements(@PathVariable long id, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return inventory.movements(id, limit, offset); }
}
