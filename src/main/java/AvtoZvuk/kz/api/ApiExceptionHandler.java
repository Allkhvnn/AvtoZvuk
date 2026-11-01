package AvtoZvuk.kz.api;

import AvtoZvuk.kz.common.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException ex) { return ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage()); }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ProblemDetail validation(Exception ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Проверьте поля запроса");
        if (ex instanceof ConstraintViolationException errors)
            problem.setProperty("errors", errors.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList());
        if (ex instanceof MethodArgumentNotValidException errors)
            problem.setProperty("errors", errors.getBindingResult().getFieldErrors().stream()
                    .map(v -> v.getField() + ": " + v.getDefaultMessage()).toList());
        return problem;
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ProblemDetail conflict(DataAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Операция конфликтует с текущими данными. Обновите данные; при повторе используйте прежний ключ запроса");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException ex) {
        log.error("Database operation failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить операцию учета");
    }
}
