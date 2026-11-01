package AvtoZvuk.kz.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class Rules {
    private final Validator validator;

    public void validate(Object value) {
        var errors = validator.validate(value);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public static String fingerprint(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(command.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static void equalMoney(BigDecimal expected, BigDecimal actual, String message) {
        if (expected.compareTo(actual) != 0) throw BusinessException.invalid(message);
    }
}
