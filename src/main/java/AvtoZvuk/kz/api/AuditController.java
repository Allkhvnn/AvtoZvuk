package AvtoZvuk.kz.api;

import AvtoZvuk.kz.accounting.AuditService;
import AvtoZvuk.kz.api.dto.audit.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService audit;

    @GetMapping
    List<AuditLogResponse> history(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(defaultValue = "0") int offset) {
        return audit.history(limit, offset);
    }
}
