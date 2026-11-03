package AvtoZvuk.kz.api.dto.inventory;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record WriteOffRequest(@NotNull UUID requestKey,@Positive long productId,@Min(1) @Max(1000000) int quantity,
                              @NotBlank @Size(max=2000) String reason) {}
