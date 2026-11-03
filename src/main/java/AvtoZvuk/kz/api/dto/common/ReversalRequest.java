package AvtoZvuk.kz.api.dto.common;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ReversalRequest(@NotNull UUID requestKey,@NotBlank @Size(max=2000) String reason) {}
