package com.uenav.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminAuthServiceTest {

    private static class FakeRepo implements AdminAccountRepository {
        final List<Admin.Account> rows = new ArrayList<>();

        public Optional<Admin.Account> findByUsername(String username) {
            return rows.stream().filter(a -> a.username().equalsIgnoreCase(username)).findFirst();
        }

        public int count() {
            return rows.size();
        }

        public void insert(String username, String passwordHash, String fullName, String role) {
            rows.add(new Admin.Account(rows.size() + 1, username, passwordHash, fullName, role));
        }
    }

    private static class FakeHasher implements PasswordHasher {
        public String hash(String raw) {
            return "hashed:" + raw;
        }

        public boolean matches(String raw, String hash) {
            return hash.equals("hashed:" + raw);
        }
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-03T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final MutableClock clock = new MutableClock();
    private final AdminAuthService service = new AdminAuthService(repo, new FakeHasher(), clock);

    private void createAdmin() {
        service.ensureInitialAdmin("admin", "correct-horse", "Campus administrator");
    }

    @Test
    void correctPasswordGivesTokenAndSession() {
        createAdmin();
        Admin.LoginResult r = service.login("admin", "correct-horse").orElseThrow();
        assertFalse(r.token().isBlank());
        assertEquals("admin", r.session().username());
        assertEquals("ADMIN", r.session().role());
        assertEquals(28800L, r.expiresInSeconds());
        assertTrue(service.session(r.token()).isPresent());
    }

    @Test
    void wrongPasswordAndUnknownUserAreRejected() {
        createAdmin();
        assertTrue(service.login("admin", "wrong").isEmpty());
        assertTrue(service.login("nobody", "correct-horse").isEmpty());
        assertTrue(service.login(null, null).isEmpty());
    }

    @Test
    void tokenExpiresAfterEightHours() {
        createAdmin();
        String token = service.login("admin", "correct-horse").orElseThrow().token();
        clock.advance(Duration.ofHours(7));
        assertTrue(service.session(token).isPresent());
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertTrue(service.session(token).isEmpty());
    }

    @Test
    void logoutEndsTheSession() {
        createAdmin();
        String token = service.login("admin", "correct-horse").orElseThrow().token();
        service.logout(token);
        assertTrue(service.session(token).isEmpty());
        assertTrue(service.session("made-up-token").isEmpty());
        assertTrue(service.session(null).isEmpty());
    }

    @Test
    void fiveWrongPasswordsLockTheUsernameForFifteenMinutes() {
        createAdmin();
        for (int i = 0; i < 5; i++) {
            assertTrue(service.login("admin", "wrong").isEmpty());
        }
        assertThrows(AdminAuthService.TooManyAttemptsException.class,
                () -> service.login("admin", "correct-horse"));
        clock.advance(Duration.ofMinutes(16));
        assertTrue(service.login("admin", "correct-horse").isPresent());
    }

    @Test
    void goodLoginResetsTheFailureCount() {
        createAdmin();
        for (int i = 0; i < 4; i++) {
            service.login("admin", "wrong");
        }
        assertTrue(service.login("admin", "correct-horse").isPresent());
        for (int i = 0; i < 4; i++) {
            service.login("admin", "wrong");
        }
        assertTrue(service.login("admin", "correct-horse").isPresent());
    }

    @Test
    void firstAdminIsCreatedOnlyWhenNoneExists() {
        assertTrue(service.ensureInitialAdmin("admin", "correct-horse", "Campus administrator"));
        assertFalse(service.ensureInitialAdmin("other", "another-password", "Someone"));
        assertEquals(1, repo.count());
    }

    @Test
    void shortPasswordAndEmptyUsernameAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.ensureInitialAdmin("admin", "short", "x"));
        assertThrows(IllegalArgumentException.class, () -> service.ensureInitialAdmin(" ", "correct-horse", "x"));
        assertEquals(0, repo.count());
    }

    @Test
    void passwordIsStoredOnlyAsHash() {
        createAdmin();
        assertFalse(repo.rows.get(0).passwordHash().equals("correct-horse"));
    }
}
