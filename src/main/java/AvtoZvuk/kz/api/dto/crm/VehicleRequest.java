package AvtoZvuk.kz.api.dto.crm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 100) String make,
        @Size(max = 100) String model,
        @Min(1900) @Max(2100) Integer productionYear,
        @Size(max = 30) String plateNumber,
        @Size(max = 500) String notes) {}
