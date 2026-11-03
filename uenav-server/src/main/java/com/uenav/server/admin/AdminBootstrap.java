package com.uenav.server.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** On start, creates the first admin from UENAV_ADMIN_USER / UENAV_ADMIN_PASSWORD if there is none. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminAuthService auth;
    private final String username;
    private final String password;
    private final String fullName;

    public AdminBootstrap(AdminAuthService auth,
                          @Value("${uenav.admin.username:}") String username,
                          @Value("${uenav.admin.password:}") String password,
                          @Value("${uenav.admin.full-name:Campus administrator}") String fullName) {
        this.auth = auth;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        try {
            if (auth.ensureInitialAdmin(username, password, fullName)) {
                log.info("Created first admin account '{}'", username);
            }
        } catch (Exception e) {
            // The server must still start when MySQL is down or the password is too short.
            log.warn("Could not create the first admin account: {}", e.getMessage());
        }
    }
}
