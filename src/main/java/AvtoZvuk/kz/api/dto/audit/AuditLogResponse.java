package AvtoZvuk.kz.api.dto.audit;

import java.time.OffsetDateTime;

public record AuditLogResponse(long id, String action, String entityType, long entityId,
                               String reason, String username, OffsetDateTime createdAt) {}
