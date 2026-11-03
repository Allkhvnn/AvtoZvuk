package AvtoZvuk.kz.api.dto.personnel;
import java.math.BigDecimal;
public record EmployeeResponse(long id, String fullName, String phone, BigDecimal bonusRate, boolean active) {}
