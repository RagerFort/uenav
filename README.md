# UENav

Modules: `uenav-core` (graph, routing, directions), `uenav-server` (Spring Boot REST), `uenav-mobile` (Gluon phone app for
visitors and admins), `uenav-client` (JavaFX PC app, admins only). Full guide: `docs/PROJECT_CONTEXT.md`.

## Versions (edit to match what you actually run)
JDK 17+, Maven 3.9+, Spring Boot 3.3.5, JavaFX 21.0.5. The server has MariaDB 11.4 (MySQL compatible) built in.

## Git
    git init -b main
    git add .
    git commit -m "Initial multi-module skeleton"
    git remote add origin <your-repo-url>
    git push -u origin main

## Database
Built into the server (docs/decisions.md D24): it starts on 127.0.0.1:3307 and loads the campus map the first time.
Query it with MySQL Workbench or `mysql -h 127.0.0.1 -P 3307 -u uenav_app -p uenav` (password: `UENAV_DB_PASSWORD` in
`~/.uenav/server.properties`), or `java -jar uenav-server/target/uenav-server-*.jar sql` (UENav SQL.exe when packaged).
Your own MySQL instead: set `UENAV_DB_URL` and run `mysql -u root -p < sql/00_create_database.sql` (edit the password).

## Run
In VS Code: Terminal > Run Task... > pick a `UENav:` task. Or by hand:

    mvn clean verify
    mvn -pl uenav-server spring-boot:run
    curl http://localhost:8080/api/health           # {"status":"UP"}
    curl http://localhost:8080/api/health/db        # {"status":"UP","database":"uenav"}
    mvn -pl uenav-core -am install -DskipTests      # once, and after changing uenav-core
    mvn -pl uenav-client javafx:run                 # PC admin app (admins only)
    mvn -pl uenav-mobile javafx:run                 # phone app in a phone-sized window (works offline)
