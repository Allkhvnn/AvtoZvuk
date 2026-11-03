package AvtoZvuk.kz.api.dto.catalog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CategoryRequest(@NotBlank @Size(max = 150) String name) {}
