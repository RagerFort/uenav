package com.uenav.server.db;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Starts the built-in database before the server connects to it (D24), and closes it when the server closes.
 * It runs once the settings (environment variables, ~/.uenav/server.properties) and logging are ready, and then
 * points spring.datasource at the database. With UENAV_DB_URL set it does nothing: the server uses that MySQL.
 */
public final class EmbeddedDatabaseStarter
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDatabaseStarter.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        if (DbSettings.usesOwnServer(env::getProperty)) {
            LOG.info("UENAV_DB_URL is set: using that MySQL server, not the built-in database");
            return;
        }
        EmbeddedMariaDb db;
        try {
            DbSettings settings = DbSettings.from(env::getProperty, DbSettings.defaultHome()).withPassword();
            db = EmbeddedMariaDb.start(settings);
        } catch (Exception e) {
            // the server still starts (health check, error messages), as when MySQL was down before
            LOG.error("The built-in database could not start: {}", e.getMessage(), e);
            return;
        }
        DbSettings s = db.settings();
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", s.jdbcUrl(EmbeddedMariaDb.DATABASE));
        props.put("spring.datasource.username", s.user());
        props.put("spring.datasource.password", s.password());
        env.getPropertySources().addFirst(new MapPropertySource("uenavBuiltInDatabase", props));
        // Closed with the server, also one left running before: by the server itself on Ctrl+C or a normal exit
        // (once it has closed its own connections), and by a helper when the window is closed (Windows ends Java
        // then before it can do anything).
        db.takeOver();
        db.closeWhenThisProgramEnds();
        SpringApplication.getShutdownHandlers().add(db::stop);
    }

    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1; // after logging is set up
    }
}
