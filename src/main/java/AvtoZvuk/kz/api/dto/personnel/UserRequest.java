package AvtoZvuk.kz.api.dto.personnel;
import jakarta.validation.constraints.*;
public record UserRequest(@NotNull @Positive Long employeeId,
                          @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}") String username,
                          @NotBlank @Size(min = 12, max = 72) String password,
                          @NotNull Role role) {
    public enum Role { OWNER, SELLER }
    @Override public String toString() { return "UserRequest[username=" + username + ", password=REDACTED]"; }
}
