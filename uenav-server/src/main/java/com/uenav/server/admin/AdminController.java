package com.uenav.server.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record LoginRequest(String username, String password) { }

    private final AdminAuthService auth;

    public AdminController(AdminAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password())
                .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                        "token", r.token(),
                        "fullName", r.session().fullName(),
                        "role", r.session().role(),
                        "expiresInSeconds", r.expiresInSeconds())))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(Map.<String, Object>of("error", "Invalid username or password")));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        auth.logout(AdminAuthInterceptor.bearerToken(request));
        return Map.of("status", "logged out");
    }

    /** A protected address: only works with a valid token. */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(AdminAuthInterceptor.SESSION) Admin.Session session) {
        return Map.of("username", session.username(), "fullName", session.fullName(), "role", session.role());
    }

    @ExceptionHandler(AdminAuthService.TooManyAttemptsException.class)
    public ResponseEntity<Map<String, String>> tooMany(AdminAuthService.TooManyAttemptsException e) {
        return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
    }
}
