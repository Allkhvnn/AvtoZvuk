package AvtoZvuk.kz.api;

import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final CurrentActor actors;

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName(), "parameterName", token.getParameterName());
    }

    @GetMapping("/me")
    CurrentActor.Actor me() { return actors.get(); }

    @GetMapping("/session")
    SessionStatus session(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken)
            return new SessionStatus(false, null);
        return new SessionStatus(true, actors.get());
    }

    record SessionStatus(boolean authenticated, CurrentActor.Actor actor) {}
}
