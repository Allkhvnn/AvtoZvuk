package AvtoZvuk.kz.api.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {
    @Override public String toString() {
        return "PasswordChangeRequest[currentPassword=REDACTED, newPassword=REDACTED]";
    }
}
