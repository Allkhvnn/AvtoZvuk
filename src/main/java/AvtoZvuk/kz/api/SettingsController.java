package AvtoZvuk.kz.api;

import AvtoZvuk.kz.api.dto.settings.PasswordChangeRequest;
import AvtoZvuk.kz.api.dto.settings.StoreSettingsRequest;
import AvtoZvuk.kz.api.dto.settings.StoreSettingsResponse;
import AvtoZvuk.kz.catalog.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {
    private final SettingsService settings;

    @GetMapping("/store")
    StoreSettingsResponse store() { return settings.store(); }

    @PutMapping("/store")
    StoreSettingsResponse store(@Valid @RequestBody StoreSettingsRequest command) {
        return settings.updateStore(command);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(@Valid @RequestBody PasswordChangeRequest command) {
        settings.changePassword(command);
    }
}
