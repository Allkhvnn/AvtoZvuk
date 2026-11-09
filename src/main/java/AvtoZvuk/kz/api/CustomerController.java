package AvtoZvuk.kz.api;

import AvtoZvuk.kz.api.dto.crm.*;
import AvtoZvuk.kz.catalog.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customers;

    @GetMapping List<CustomerSummaryResponse> clients() { return customers.customers(); }
    @GetMapping("/{id}") CustomerDetailResponse client(@PathVariable long id) { return customers.customer(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> create(@Valid @RequestBody CustomerRequest command) { return Map.of("id", customers.create(command)); }
    @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable long id, @Valid @RequestBody CustomerRequest command) { customers.update(id, command); }
    @PutMapping("/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    void status(@PathVariable long id, @Valid @RequestBody CustomerStatusRequest command) { customers.status(id, command); }
}
