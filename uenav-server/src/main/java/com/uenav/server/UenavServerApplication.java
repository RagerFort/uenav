package com.uenav.server;

import com.uenav.server.db.EmbeddedDatabaseStarter;
import com.uenav.server.db.SqlConsole;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UenavServerApplication {

    /**
     * Starts the server with its built-in database (D24). With the argument "sql" it opens the UENav SQL window
     * instead (the mysql command line on that database).
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "sql".equalsIgnoreCase(args[0])) {
            SqlConsole.main(new String[0]);
            return;
        }
        SpringApplication app = new SpringApplication(UenavServerApplication.class);
        app.addListeners(new EmbeddedDatabaseStarter());
        app.run(args);
    }
}
