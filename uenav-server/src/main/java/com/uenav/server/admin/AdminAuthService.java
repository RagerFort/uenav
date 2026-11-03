package com.uenav.server.admin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Admin login (D8). Passwords are checked here on the server only. After a good login the admin
 * gets a random token to send as "Authorization: Bearer ...". Tokens live in memory, so they are
 * forgotten when the server restarts.
 */
@Service
public class AdminAuthService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final int MIN_PASSWORD_LENGTH = 8;

    /** Too many wrong passwords for this username. */
    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException() {
            super("Too many failed logins. Try again later.");
        }
    }

    private record TokenEntry(Admin.Session session, Instant expiresAt) { }

    private record Failures(int count, Instant firstAt) { }

    private final AdminAccountRepository repo;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final String dummyHash;

    @Autowired
    public AdminAuthService(AdminAccountRepository repo, PasswordHasher hasher) {
        this(repo, hasher, Clock.systemUTC());
    }

    AdminAuthService(AdminAccountRepository repo, PasswordHasher hasher, Clock clock) {
        this.repo = repo;
        this.hasher = hasher;
        this.clock = clock;
        this.dummyHash = hasher.hash("not-a-real-password");
    }

    /**
     * @return a token and session, or empty when the username or password is wrong
     * @throws TooManyAttemptsException after 5 wrong passwords for the same username
     */
    public synchronized Optional<Admin.LoginResult> login(String username, String password) {
        String name = username == null ? "" : username.trim();
        String key = name.toLowerCase(Locale.ROOT);
        if (isLocked(key)) {
            throw new TooManyAttemptsException();
        }
        String pw = password == null ? "" : password;
        Optional<Admin.Account> account = name.isEmpty() ? Optional.empty() : repo.findByUsername(name);
        boolean ok;
        if (account.isPresent()) {
            ok = hasher.matches(pw, account.get().passwordHash());
        } else {
            hasher.matches(pw, dummyHash); // same work as a real check, so timing does not reveal usernames
            ok = false;
        }
        if (!ok) {
            recordFailure(key);
            return Optional.empty();
        }
        failures.remove(key);
        purgeExpiredTokens();
        Admin.Account a = account.get();
        Admin.Session session = new Admin.Session(a.id(), a.username(), a.fullName(), a.role());
        String token = newToken();
        tokens.put(token, new TokenEntry(session, clock.instant().plus(TOKEN_LIFETIME)));
        return Optional.of(new Admin.LoginResult(token, session, TOKEN_LIFETIME.toSeconds()));
    }

    /** @return who the token belongs to, or empty if it is unknown or expired */
    public Optional<Admin.Session> session(String token) {
        if (token == null) {
            return Optional.empty();
        }
        TokenEntry e = tokens.get(token);
        if (e == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(e.expiresAt())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(e.session());
    }

    public void logout(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /**
     * Creates the first admin, but only when no admin exists yet.
     * @return true if an account was created
     */
    public boolean ensureInitialAdmin(String username, String password, String fullName) {
        if (repo.count() > 0) {
            return false;
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Admin username must not be empty");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Admin password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        repo.insert(username.trim(), hasher.hash(password), fullName, "ADMIN");
        return true;
    }

    private boolean isLocked(String key) {
        Failures f = failures.get(key);
        if (f == null) {
            return false;
        }
        if (!clock.instant().isBefore(f.firstAt().plus(LOCKOUT))) {
            failures.remove(key);
            return false;
        }
        return f.count() >= MAX_FAILURES;
    }

    private void recordFailure(String key) {
        Instant now = clock.instant();
        Failures f = failures.get(key);
        if (f == null || !now.isBefore(f.firstAt().plus(LOCKOUT))) {
            failures.put(key, new Failures(1, now));
        } else {
            failures.put(key, new Failures(f.count() + 1, f.firstAt()));
        }
    }

    private void purgeExpiredTokens() {
        Instant now = clock.instant();
        tokens.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiresAt()));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
