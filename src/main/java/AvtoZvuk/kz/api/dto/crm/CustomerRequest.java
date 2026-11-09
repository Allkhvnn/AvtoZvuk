package AvtoZvuk.kz.api.dto.crm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CustomerRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 40) String phone,
        @Size(max = 2000) String notes,
        @NotNull @Size(max = 20) List<@Valid VehicleRequest> vehicles) {}
