package AvtoZvuk.kz.security;

import AvtoZvuk.kz.common.Database;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(Database db) {
        return username -> {
            var row = db.optional("""
                    SELECT u.username, u.password_hash, u.role FROM app_users u
                    LEFT JOIN employees e ON e.id = u.employee_id
                    WHERE lower(u.username) = lower(?) AND u.active AND (u.employee_id IS NULL OR e.active)
                    """, username).orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
            return User.withUsername((String) row.get("username")).password((String) row.get("password_hash"))
                    .roles((String) row.get("role")).build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/csrf", "/api/login", "/api/session", "/api/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginProcessingUrl("/api/login")
                        .successHandler((req, res, auth) -> res.setStatus(204))
                        .failureHandler((req, res, error) -> res.setStatus(401)).permitAll())
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((req, res, error) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
