package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record PurchaseRequest(@NotNull UUID requestKey,@Positive long supplierId,
                              @NotEmpty @Size(max=100) List<@NotNull @Valid PurchaseItemRequest> items) {}
