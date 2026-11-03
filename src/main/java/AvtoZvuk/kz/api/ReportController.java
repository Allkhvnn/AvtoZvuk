package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reports;

    @GetMapping("/summary") ReportService.Summary summary(@RequestParam LocalDate from, @RequestParam LocalDate to) { return reports.summary(from, to); }
    @GetMapping("/payment-methods") List<Map<String, Object>> paymentMethods(@RequestParam LocalDate from, @RequestParam LocalDate to) { return reports.paymentMethods(from, to); }
    @GetMapping("/employees") List<Map<String, Object>> employees(@RequestParam LocalDate from, @RequestParam LocalDate to) { return reports.employees(from, to); }
    @GetMapping("/products") List<Map<String, Object>> products(@RequestParam LocalDate from, @RequestParam LocalDate to) { return reports.products(from, to); }
    @GetMapping("/low-stock") List<Map<String, Object>> lowStock() { return reports.lowStock(); }
}
