package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.FinanceService;
import AvtoZvuk.kz.api.dto.common.ReversalRequest;
import AvtoZvuk.kz.api.dto.finance.ExpenseCategoryRequest;
import AvtoZvuk.kz.api.dto.finance.ExpenseRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceService finance;

    @GetMapping("/expense-categories") List<Map<String, Object>> expenseCategories() { return finance.expenseCategories(); }
    @PostMapping("/expense-categories") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> expenseCategory(@Valid @RequestBody ExpenseCategoryRequest command) { return Map.of("id", finance.expenseCategory(command)); }
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> expense(@Valid @RequestBody ExpenseRequest command) { return Map.of("id", finance.expense(command)); }
    @PostMapping("/expenses/{id}/reversal") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> reverseExpense(@PathVariable long id, @Valid @RequestBody ReversalRequest command) { return Map.of("id", finance.reverseExpense(id, command)); }
    @GetMapping("/expenses") List<Map<String, Object>> expenses(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return finance.expenses(limit, offset); }
    @GetMapping("/expenses/totals") Map<String, Object> expenseTotals() { return finance.expenseTotals(); }
}
