package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.PayrollService;
import AvtoZvuk.kz.catalog.CatalogService;
import AvtoZvuk.kz.catalog.PersonnelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PersonnelController {
    private final CatalogService catalog;
    private final PersonnelService personnel;
    private final PayrollService payroll;

    @GetMapping("/employees") List<Map<String, Object>> employees() { return catalog.employees(); }
    @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> employee(@Valid @RequestBody Commands.Employee command) { return Map.of("id", catalog.employee(command)); }
    @PutMapping("/employees/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void employee(@PathVariable long id, @Valid @RequestBody Commands.Employee command) { personnel.updateEmployee(id, command); }
    @PutMapping("/employees/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    void employeeStatus(@PathVariable long id, @Valid @RequestBody Commands.EmployeeStatus command) { personnel.employeeStatus(id, command); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> user(@Valid @RequestBody Commands.User command) { return Map.of("id", catalog.user(command)); }
    @GetMapping("/users") List<Map<String, Object>> users() { return personnel.users(); }
    @PutMapping("/users/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    void userStatus(@PathVariable long id, @Valid @RequestBody Commands.UserStatus command) { personnel.userStatus(id, command); }
    @PostMapping("/bonuses/adjustments") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> bonusAdjustment(@Valid @RequestBody Commands.BonusAdjustment command) { return Map.of("id", payroll.adjustBonus(command)); }
    @GetMapping("/bonuses/balances") List<Map<String, Object>> bonusBalances() { return payroll.balances(); }
    @GetMapping("/employees/{id}/bonuses") List<Map<String, Object>> bonusEntries(@PathVariable long id, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return payroll.bonusEntries(id, limit, offset); }
    @PostMapping("/staff-payments") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> staffPayment(@Valid @RequestBody Commands.StaffPayment command) { return Map.of("id", payroll.pay(command)); }
    @PostMapping("/staff-payments/{id}/reversal") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> reverseStaffPayment(@PathVariable long id, @Valid @RequestBody Commands.Reversal command) { return Map.of("id", payroll.reversePayment(id, command)); }
    @GetMapping("/employees/{id}/staff-payments") List<Map<String, Object>> staffPayments(@PathVariable long id, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return payroll.payments(id, limit, offset); }
}
