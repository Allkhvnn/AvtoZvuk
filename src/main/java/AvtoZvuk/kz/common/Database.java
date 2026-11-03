package AvtoZvuk.kz.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class Database {
    private final JdbcClient jdbc;

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.sql(sql).params(args).query().listOfRows();
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.sql(sql).params(args).query(mapper).list();
    }

    public Optional<Map<String, Object>> optional(String sql, Object... args) {
        var result = rows(sql, args);
        if (result.size() > 1) throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1, result.size());
        return result.stream().findFirst();
    }

    public Map<String, Object> one(String sql, Object... args) {
        return optional(sql, args).orElseThrow(() -> BusinessException.missing("Запись не найдена"));
    }

    public long id(String sql, Object... args) {
        return jdbc.sql(sql).params(args).query(Long.class).single();
    }

    public BigDecimal amount(String sql, Object... args) {
        return jdbc.sql(sql).params(args).query(BigDecimal.class).single();
    }

    public int update(String sql, Object... args) {
        return jdbc.sql(sql).params(args).update();
    }

    public static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    public static BigDecimal money(Map<String, Object> row, String key) { return (BigDecimal) row.get(key); }
}
