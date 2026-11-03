package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record SaleReturnRequest(@NotNull UUID requestKey,@Positive long saleId,@NotBlank @Size(max=2000) String reason,
                                @NotEmpty @Size(max=100) List<@NotNull @Valid ReturnItemRequest> items,
                                @NotNull @Size(max=2) List<@NotNull @Valid PaymentRequest> payments) {}
