package AvtoZvuk.kz.api.dto.catalog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record SupplierRequest(@NotBlank @Size(max = 200) String name, @Size(max = 40) String phone) {}
