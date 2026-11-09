package AvtoZvuk.kz.api.dto.crm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerStatusRequest(boolean active, @NotBlank @Size(max = 2000) String reason) {}
