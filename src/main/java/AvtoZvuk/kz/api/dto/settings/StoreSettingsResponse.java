package AvtoZvuk.kz.api.dto.settings;

import java.time.OffsetDateTime;

public record StoreSettingsResponse(
        String name,
        String phone,
        String address,
        OffsetDateTime updatedAt) {}
