package AvtoZvuk.kz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Commands {
    private Commands() {}

    public record Named(@NotBlank @Size(max = 150) String name) {}
    public record Supplier(@NotBlank @Size(max = 200) String name, @Size(max = 40) String phone) {}
    public record Employee(@NotBlank @Size(max = 200) String fullName, @Size(max = 40) String phone,
                           @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal bonusRate) {}
    public record User(@NotNull @Positive Long employeeId,
                       @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}") String username,
                       @NotBlank @Size(min = 12, max = 72) String password,
                       @NotNull Role role) {
        @Override public String toString() { return "User[username=" + username + ", password=REDACTED]"; }
    }
    public enum Role { OWNER, SELLER }
    public enum PaymentMethod { CASH, KASPI_PAY }
    public enum FinanceMethod { CASH, KASPI_PAY, BANK_TRANSFER }
    public enum StaffPaymentType { BONUS, SALARY }
    public record Product(@NotBlank @Size(max = 250) String name, @NotBlank @Size(max = 100) String sku,
                          @Size(min = 1, max = 100) String barcode, @Positive Long categoryId,
                          @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal sellingPrice,
                          @Min(0) int lowStockThreshold) {}
    public record PurchaseItem(@Positive long productId, @Min(1) @Max(1000000) int quantity,
                               @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost) {}
    public record Purchase(@NotNull UUID requestKey, @Positive long supplierId,
                           @NotEmpty @Size(max = 100) List<@NotNull @Valid PurchaseItem> items) {}
    public record SaleItem(@Positive long productId, @Min(1) @Max(1000000) int quantity,
                           @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
                           @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal discountAmount) {}
    public record Payment(@NotNull PaymentMethod method,
                          @NotNull @DecimalMin(value = "0", inclusive = false)
                          @Digits(integer = 17, fraction = 2) BigDecimal amount) {}
    public record Sale(@NotNull UUID requestKey, @Positive Long sellerId,
                       @NotEmpty @Size(max = 100) List<@NotNull @Valid SaleItem> items,
                       @NotNull @Size(max = 2) List<@NotNull @Valid Payment> payments) {}
    public record ReturnItem(@Positive long saleItemId, @Min(1) @Max(1000000) int quantity, boolean restock) {}
    public record SaleReturn(@NotNull UUID requestKey, @Positive long saleId,
                             @NotBlank @Size(max = 2000) String reason,
                             @NotEmpty @Size(max = 100) List<@NotNull @Valid ReturnItem> items,
                             @NotNull @Size(max = 2) List<@NotNull @Valid Payment> payments) {}
    public record OpeningStock(@NotNull UUID requestKey, @Positive long productId, @Min(1) @Max(1000000) int quantity,
                               @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost,
                               @NotBlank @Size(max = 2000) String reason) {}
    public record Expense(@NotNull UUID requestKey, @Positive long categoryId,
                          @NotNull @DecimalMin(value = "0", inclusive = false)
                          @Digits(integer = 17, fraction = 2) BigDecimal amount,
                          @NotNull FinanceMethod method, @NotBlank @Size(max = 2000) String description,
                          @PastOrPresent Instant incurredAt) {}
    public record Reversal(@NotNull UUID requestKey, @NotBlank @Size(max = 2000) String reason) {}
    public record BonusAdjustment(@NotNull UUID requestKey, @Positive long employeeId,
                                  @NotNull @DecimalMin("-99999999999999999.99")
                                  @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 2000) String reason) {}
    public record StaffPayment(@NotNull UUID requestKey, @Positive long employeeId,
                               @NotNull StaffPaymentType type,
                               @NotNull @DecimalMin(value = "0", inclusive = false)
                               @Digits(integer = 17, fraction = 2) BigDecimal amount,
                               @NotNull FinanceMethod method, @NotBlank @Size(max = 2000) String reason,
                               @PastOrPresent Instant paidAt) {}
    public record EmployeeStatus(boolean active, @NotBlank @Size(max = 2000) String reason) {}
    public record UserStatus(boolean active, @NotBlank @Size(max = 2000) String reason) {}
}
