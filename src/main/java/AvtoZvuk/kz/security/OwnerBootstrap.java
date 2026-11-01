package AvtoZvuk.kz.security;

import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OwnerBootstrap implements ApplicationRunner {
    private final Environment environment;
    private final Database db;
    private final PasswordEncoder passwords;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = environment.getProperty("BOOTSTRAP_OWNER_USERNAME");
        String password = environment.getProperty("BOOTSTRAP_OWNER_PASSWORD");
        if (username == null && password == null) return;
        if (username == null || !username.matches("[a-zA-Z0-9._-]{3,100}") || password == null
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Задайте BOOTSTRAP_OWNER_USERNAME и BOOTSTRAP_OWNER_PASSWORD (12–72 байта)");
        db.rows("SELECT pg_advisory_xact_lock(4829157001)");
        if (db.id("SELECT count(*) FROM app_users") != 0) return;
        long employee = db.id("INSERT INTO employees(full_name) VALUES ('Владелец') RETURNING id");
        long user = db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, 'OWNER') RETURNING id",
                employee, username, passwords.encode(password));
        db.update("INSERT INTO audit_log(actor_user_id, action, entity_type, entity_id) VALUES (?, 'OWNER_BOOTSTRAP', 'User', ?)", user, user);
    }
}
