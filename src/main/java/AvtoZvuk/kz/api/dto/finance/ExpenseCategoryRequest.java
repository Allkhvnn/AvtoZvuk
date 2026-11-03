package AvtoZvuk.kz.api.dto.finance;
import jakarta.validation.constraints.*;
public record ExpenseCategoryRequest(@NotBlank @Size(max=150) String name) {}
