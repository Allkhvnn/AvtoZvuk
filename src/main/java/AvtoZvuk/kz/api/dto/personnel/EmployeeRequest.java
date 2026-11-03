package AvtoZvuk.kz.api.dto.personnel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record EmployeeRequest(@NotBlank @Size(max = 200) String fullName, @Size(max = 40) String phone,
                              @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal bonusRate) {}
