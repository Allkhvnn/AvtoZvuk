package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record SaleRequest(@NotNull UUID requestKey,@Positive Long sellerId,
                          @NotEmpty @Size(max=100) List<@NotNull @Valid SaleItemRequest> items,
                          @NotNull @Size(max=2) List<@NotNull @Valid PaymentRequest> payments) {}
