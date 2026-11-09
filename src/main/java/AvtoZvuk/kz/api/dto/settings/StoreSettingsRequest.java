package AvtoZvuk.kz.api.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreSettingsRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 40) String phone,
        @Size(max = 500) String address) {}
