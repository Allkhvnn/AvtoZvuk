package AvtoZvuk.kz.api.dto.inventory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record InventoryRequest(@NotNull UUID requestKey,@NotBlank @Size(max=2000) String reason,
                               @NotEmpty @Size(max=500) List<@NotNull @Valid InventoryItemRequest> items) {}
