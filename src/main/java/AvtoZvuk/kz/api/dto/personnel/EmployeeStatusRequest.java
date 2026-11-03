package AvtoZvuk.kz.api.dto.personnel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record EmployeeStatusRequest(boolean active, @NotBlank @Size(max = 2000) String reason) {}
