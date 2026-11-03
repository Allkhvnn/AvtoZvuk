package AvtoZvuk.kz.api.dto.personnel;
import java.time.OffsetDateTime;
public record UserResponse(long id, Long employeeId, String employeeName, String username, String role,
                           boolean active, Boolean employeeActive, OffsetDateTime createdAt) {}
