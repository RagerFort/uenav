# UENav

Modules: `uenav-core` (graph, routing, directions), `uenav-server` (Spring Boot REST), `uenav-mobile` (Gluon phone app and the
shared visitor screens), `uenav-client` (JavaFX PC app: admin pages plus Explore/Map). Full guide: `docs/PROJECT_CONTEXT.md`.

## Versions (edit to match what you actually run)
JDK 17+, Maven 3.9+, Spring Boot 3.3.5, JavaFX 21.0.5, MySQL 8.

## Git
    git init -b main
    git add .
    git commit -m "Initial multi-module skeleton"
    git remote add origin <your-repo-url>
    git push -u origin main

## MySQL
    mysql -u root -p < sql/00_create_database.sql    # edit the password first

## Run
    mvn clean verify
    export UENAV_DB_PASSWORD=your_password          # Windows: set UENAV_DB_PASSWORD=...
    mvn -pl uenav-server spring-boot:run
    curl http://localhost:8080/api/health           # {"status":"UP"}
    curl http://localhost:8080/api/health/db        # {"status":"UP","database":"uenav"}
    mvn install -DskipTests                         # once, so the apps can find each other
    mvn -pl uenav-client javafx:run                 # PC app: admin sign-in or "Continue as visitor"
    mvn -pl uenav-mobile javafx:run                 # phone app in a phone-sized window (works offline)
