# UENav: Project Context (for AI assistants and new team members)

Read this file first. It is the single source of truth for what the project is, what is decided,
what is done, and what to do next. **Update the "Status" and "Next tasks" sections after every task.**
Last updated: 2026-11-04 (campus map picture and traced UE Caloocan walkways added; phone map gestures fixed).

## 1. What this is
UENav is a campus navigation app for the University of the East (UE) Caloocan campus.
It is a student Software Design project (class 3CPE-1B). The campus is treated as a road network:
places are nodes, walkways are edges, and Dijkstra's algorithm finds the shortest route,
including indoor routes across floors by stairs or elevator.

Team roles (from the proposal deck): Alde = UI/UX designer and project manager,
Rance = data administrator, Gungon = routing and quality.

Two kinds of users:
- **Visitor/student (phone):** pick From and To, see the route on a map with step-by-step directions,
  choose stairs or elevator, press Helpful or Wrong route. Must work **offline** inside the school.
- **Admin (PC app, admin only; also a basic Admin tab on the phone):** logs in, adds/edits/deletes places and walkways, sees most-searched places and
  recent activity, reviews wrong-route reports, exports the usage log as CSV.

## 2. Owner and how to work with them
- The owner is a student on **Windows**, using **VS Code** and the **PowerShell** terminal. They have an Android phone and USB cable.
- They asked for **plain English, step-by-step instructions** with exact commands. Avoid jargon; explain each step in one line.
- Give **one task at a time** with: files needed, expected result, and how to know it is done.
- When something fails, ask for the last ~30 lines of the error and the step number.

## 3. Rules for any AI working on this project
1. **Separate confirmed facts from assumptions.** Never claim something works unless there is source code, a test result, or the owner's report showing it. Say plainly when code was not run.
2. **Do not redesign** the project or recreate the existing diagrams. The decisions in section 5 are final unless the owner changes them.
3. Keep `uenav-core` **pure Java**: no Spring, no JavaFX, no JDBC, no reflection tricks. It must run on desktop, server and Android (Gluon native-image).
4. The phone app must **never talk to MySQL** and must **not need the server to find a route**.
5. Never commit passwords or secrets. Database password comes from the `UENAV_DB_PASSWORD` environment variable.
6. Java release level is **17**. Tests go in `src/test`, never `src/main` (this mistake already happened once).
7. Do not generate the whole app at once. Follow the task order in section 8.
8. If a change affects a decision, a diagram or the ERD, say so and record it in `docs/decisions.md`.

## 4. Architecture
```
Android phone / desktop app (JavaFX)         Admin PC (JavaFX, later)
   uenav-core (Dijkstra, runs here)              |
   local copy of campus data (JSON snapshot)     |  HTTP + token
   local usage/feedback queue                    v
            |  sync when online          uenav-server (Spring Boot REST)
            +--------------------------> MySQL 8 (uenav database)
```
- **Phone:** loads a bundled campus data file, routes locally (D5, D9). When online it can fetch a newer
  data file and upload queued usage logs.
- **Server:** admin login, place/walkway editing, reports, stats, CSV export, serves the data snapshot, receives synced logs.
- **Maps:** images made in Photoshop (the emergency map can be the base). The app draws the route line on top using node X/Y (D6).

## 5. Decisions (full text in `docs/decisions.md`)
| ID | Decision |
|----|----------|
| D1 | Client -> Spring Boot REST -> MySQL. The proposal's "SQLite / JDBC in client" is dropped. |
| D2 | **No Room table.** Rooms and places are rows in `node`, told apart by `node_type`. **6 tables total** (the deck's "7 tables" is wrong). |
| D3 | Node has `name`, `x`, `y`. |
| D4 | Feedback is a Usage Log row with `feedback_type` (HELPFUL / WRONG_ROUTE) and `status` (NEW / REVIEWED). The reported route is `from_node_id` + `to_node_id` + `route_pref` on that row. |
| D5 | Dijkstra runs **inside the app** (offline). |
| D6 | Photoshop map images with the route drawn on top. |
| D7 | Admin on PC first; phone admin later. |
| D8 | Admin passwords BCrypt-hashed; server issues a token after login; password checks only on the server. |
| D9 | Server exports one JSON snapshot of the graph. The app ships with a built-in copy and replaces it when online and a newer one exists. Map images are packed in the app. |

D10 (JSON): implemented as a hand-written `MiniJson` in `uenav-core` (no library, no reflection); the server uses Spring's Jackson. Owner has not explicitly confirmed; see `docs/decisions.md`.

## 6. Technology and versions
- Java 17 release level (owner's PC runs JDK 21; CI uses Temurin 17 plus Gluon's GraalVM 22.1.0.1-Final java17).
- Maven multi-module. Spring Boot 3.3.5. JavaFX 21.0.5. gluonfx-maven-plugin 1.0.28. MySQL 8.0.16+.
- Android build tools in CI: NDK r28c, Ubuntu 22.04 runner.
- No Maven wrapper in this repo; use `mvn`.

## 7. Repository layout
```
pom.xml                          parent; modules: core, server, client, mobile
uenav-core/                      Node, Edge, EdgeType, RoutePreference, Graph, Route, PathFinder (Dijkstra),
                                 Building, Floor, CampusData, Directions (route -> steps: Start walking / Turn left /
                                 Take the elevator to 4th floor / Arrive), MiniJson (reader+writer), SnapshotReader, Version
  src/test/...                   PathFinderTest, SnapshotReaderTest, DirectionsTest, VersionTest
uenav-server/                    Spring Boot: UenavServerApplication, HealthController
  .../snapshot/                  GET /api/snapshot and /api/snapshot/version (reads MySQL)
  .../admin/                     BCrypt login, 8-hour token, lockout, /api/admin/login|logout|me, interceptor
  .../mapadmin/                  admin CRUD for buildings, floors, nodes, edges under /api/admin/**
  .../usagelog/                  POST /api/usage (open, phone sync, idempotent by client_log_id);
                                 admin: /api/admin/usage/summary|most-searched|recent|export.csv, /api/admin/reports, PUT reports/{id}/status
uenav-mobile/                    Gluon phone app (visitors and admins):
                                 MobileApp (bottom bar Explore / Map / Admin), Navigator (Figma screens Explore, Plan a route,
                                 Directions with map + steps + stairs/elevator choice + Helpful/Wrong route, Route unavailable,
                                 Campus map), MapView (draws a level and the route; uses /maps/*.png if present), Icons, Ui,
                                 PlaceStyle, uenav-nav.css; CampusStore (bundled snapshot, downloads a newer one, D9);
                                 AdminScreens + MobileAdminApi (phone Admin tab: sign in, dashboard, add/edit/delete place,
                                 route reports, server address); usage log: UsageEntry, UsageQueue, Settings, SyncService,
                                 HttpSyncClient, Http, FileTextStore/TextStore, AppStorage; `android` profile
uenav-client/                    PC admin app, **admins only** (proposal: Windows = admin app). JavaFX: UenavApp, LoginView,
                                 AdminShell (Figma sidebar), DashboardView (+ Places card), PlacesView, BuildingsView,
                                 FloorsView, WalkwaysView (ManagePage), ReportsView, Icons, PlaceStyle, TimeAgo, UsageText,
                                 AdminApi (+ AdminModels, ApiException), Async, Theme, uenav.css. Does not depend on uenav-mobile.
.vscode/tasks.json               VS Code tasks: build, start server, open PC admin app, open phone app on PC
sql/00_create_database.sql       creates database `uenav` and user `uenav_app`
sql/01_schema.sql                6 tables
sql/02_seed_sample.sql           SAMPLE data only (made-up campus)
.github/workflows/android.yml    builds the APK on GitHub (Ubuntu) and uploads it as artifact "Package"
docs/decisions.md                decisions D1 to D10
docs/PROJECT_CONTEXT.md          this file
```
Routing rules in `PathFinder` (assumptions, change if the team decides otherwise): edges are walkable both ways;
`ANY` = all edges; `STAIRS` = no elevator edges; `ELEVATOR` = no stairs edges and only `is_accessible` edges.
Walking time shown to users assumes 80 m per minute (`Directions`). No stairs/elevator time model yet.
Turns ("Turn left/right") are worked out from node X/Y, treated as map-picture pixels: X to the right, **Y downwards**.
Map pictures (D6): the campus picture is `uenav-mobile/src/main/resources/maps/campus.jpg` (the official UE Caloocan
campus map, legend cut off, 1560x924). Node X/Y are pixels of that picture (X right, Y down). Floor plans go in
`maps/<floor.plan_image_path>`; without one the app draws a simple plan of the floor.
Map gestures (like Google Maps): drag = move, pinch / mouse wheel = zoom around the fingers, double-tap = zoom in,
+/- buttons. Touch scroll events and flick "inertia" are ignored for zoom, and screen resizes keep the zoom.

## 8. Status
Evidence levels: **Confirmed** = shown by owner's output or CI log. **Reported** = owner said so, no output seen.
**Ran here** = AI ran it in its sandbox with stand-ins (no Maven, MySQL, Spring or JavaFX available there).
**Not run** = written but never executed.

| Item | Status |
|------|--------|
| Git repo, pushed to GitHub (`main`) | Confirmed |
| Spring Boot server starts and connects to MySQL | Confirmed from log; also **Ran here** 2026-11-04 with MySQL 8.0 in Docker |
| `mvn clean verify` (all 4 modules, 120 tests, JavaFX code compiled) | **Ran here** 2026-11-04 (Maven and JavaFX were available this time) |
| Gluon hello sample ran on the owner's phone | Reported |
| UENav `uenav-mobile` APK builds on GitHub | Reported for the older screen. **Not built** with the new screens yet |
| Offline routing on the phone in airplane mode | **Not confirmed** |
| `sql/01_schema.sql`, `02_seed_sample.sql` | Reported by owner; **Ran here** too |
| `GET /api/snapshot` and `/version` | Reported working; **Ran here** |
| Admin login, map editing, usage log endpoints | Reported (login); all **Ran here** end to end through the apps |
| Phone screens from Figma (Explore, Plan a route, Directions, Route unavailable, Campus map, bottom bar) | **Ran here** as a desktop window (390x820) under a virtual screen, screenshots checked against Figma; never on a phone |
| Phone: search + Wrong route saved offline, then sent when the server address was set | **Ran here** end to end: rows appeared in MySQL `usage_log` |
| Phone: newer map downloaded from server (D9) | **Ran here**: a place added in the Admin tab appeared in Explore after restart |
| Phone Admin tab (sign in, dashboard, add/edit/delete place, route reports, server address) | **Ran here** against the real server |
| PC admin app: Figma sidebar (admin pages only), dashboard with Places card | **Ran here**, screenshots checked against the Figma desktop frame |
| PC admin pages Places, Buildings, Floors, Walkways, Reports, CSV export | Compiled and the app opened them; add/edit dialogs not clicked through here |
| Phone on Android: clear-text `http://` to the server | **Not tested** (see task 2) |
| UE Caloocan outdoor map: campus picture, 9 buildings, 2 gates, offices, facilities, 46 walkways | **Ran here** (tests, MySQL, phone screenshots). Positions traced from the campus map; distances are **estimates** (0.269 m per picture pixel, from the 4.86 ha campus area). Not checked on site |
| Indoor data (Engineering 1F/4F, EN 420) | Still SAMPLE rows; no floor plans yet |
| Phone map: no zooming while dragging (touch scroll / flick events ignored), pinch and double-tap zoom | **Ran here** with simulated touch events; not tried on a phone |

## 9. Gotchas already hit (do not repeat)
1. Android apps with Gluon **cannot be built on Windows**. Build on GitHub Actions (Ubuntu). Windows only downloads and installs the APK.
2. Gluon's build starts a nested Maven run, so the parent pom must be installed: the workflow uses `mvn -B -pl uenav-core -am install` before `gluonfx:build`.
3. In PowerShell set the DB password with `$env:UENAV_DB_PASSWORD="..."` (not `export`).
4. `uenav-mobile` uses a plain JavaFX `Application` with no Gluon Attach services. If the phone shows a blank screen, add `display`, `lifecycle`, `storage` (and `util`) as in Gluon's `hello-gluon-ci` sample (Attach 4.0.25).
5. `uenav-core` uses Java `record`s. If a future native-image build complains, rewrite them as plain classes.
6. Git shows "LF will be replaced by CRLF" warnings on Windows. They are harmless.
7. The outdoor campus data is traced from the campus map picture; its distances are estimates (picture pixels x 0.269 m), not measurements. The indoor Engineering rooms are invented sample rows. Say so when presenting.

8. Do **not** add a `resourcesList` entry like `.*\\.json$` to the gluonfx plugin config: it produced an invalid resource-config file and broke the native build.
9. **Give the owner every file** a step needs (use the file list, not just the interesting ones). A missing `JdbcAdminAccountRepository` caused a Spring "no bean" start-up error.
10. When adding code, **search the repo first** (`grep`) for an existing method or file. Duplicates of `MiniJson` writer and `SnapshotReader.isSearchable` were almost added twice.
11. Spring/JavaFX code cannot be compiled in the AI sandbox: say so, and expect a first-run fix.
12. This project does not use `spring-boot-starter-parent`, so Java does **not** keep parameter names. Always name them: `@PathVariable("id")`, `@RequestParam("x")`. Unnamed ones return HTTP 500 for PUT/DELETE `/api/admin/*/{id}`.
13. Node ids in the phone's built-in `campus-snapshot.json` must match the database, or sent usage entries are rejected.
    After changing the map in the admin app, copy `GET /api/snapshot` into the phone app before building the APK.
    Usage log rows block deleting the places they mention (database foreign keys). While testing with sample data, clear the log with `DELETE FROM usage_log;` in MySQL before removing sample places.

14. Tables must fit the window with no sideways scrolling: use `TableView.CONSTRAINED_RESIZE_POLICY` and pin the button column's min and max width (owner could not reach the Mark reviewed button before this).
15. Phone sending uses plain `HttpURLConnection`, not `java.net.http`, on purpose (safer in the Android native build). The phone keeps unsent entries and never deletes one unless the server answered 200 for its batch.
16. `AppStorage` uses `com.gluonhq.attach.storage.StorageService` (Attach 4.0.25) and the `storage` entry in `attachList`. Written from memory of the API; if it fails to compile or the queue is not saved on the phone, look here first.
17. The PC app is **admin only** (proposal slide "Software requirements": Windows 10/11 = admin app). Do not add
    Explore/Map or a visitor mode to it; the Figma desktop frame's Explore/Map links were left out on purpose (D11).
18. Plain `HttpURLConnection` with `setFixedLengthStreamingMode` loses the server's error text on HTTP 401
    (wrong password showed a generic message). `Http.java` does not use streaming mode for that reason.
19. The phone opens on **Explore** (visitors need no login); the Figma Login screen is the **Admin** tab, with
    "Continue as visitor". The PC app opens on the admin login, with no visitor option.

## 10. Known inconsistencies in the proposal deck (still to fix)
- Slide text says 7 tables; there are 6 (D2). Class diagram must have no Room class.
- ERD Node lacks `name`, `x`, `y`; Usage Log lacks feedback type, status and `client_log_id`.
- Sequence diagram has the app calling the server for routes and the database directly; routing is now local and the log is queued then synced.
- Software slide still lists SQLite and JDBC from the client; has a stray "User/Admin" text box.
- Flowchart and sequence diagram lack the stairs/elevator choice. Figma lacks: Explore/search list, place add/edit form, route-reports list, walkway editing, CSV export, error states.
- Concept, "how a user navigates", "admin panel" and "boundaries by design" slides are one-line stubs.
- Figma admin dashboard is shown on a phone; D7 says PC first.

## 11. Next tasks (in dependency order)
1. **Owner:** pull, open the folder in VS Code, run the tasks "UENav: start server" and "UENav: open PC admin app",
   then "UENav: open phone app on the PC". Report anything that looks wrong next to the Figma.
2. **Owner:** push to `main` so GitHub builds the APK; install it and test: Explore, directions in airplane mode, then the
   Admin tab with the PC's address (e.g. `http://192.168.1.10:8080`). If sign-in or sending fails only on the phone,
   it is probably Android blocking plain `http://`: add a custom Android manifest that allows clear text
   (GluonFX `src/android/AndroidManifest.xml`) or serve the API over HTTPS. Offline routing works either way.
3. **Data:** walk the campus and check the traced places and walkway distances; fix them in the PC admin app.
   Add real indoor rooms and floors (order: Buildings, Floors, Places, Walkways). Then copy `GET /api/snapshot` into
   `uenav-mobile/src/main/resources/campus-snapshot.json` so the phone ships with the same data.
4. **Floor plans (D6):** add floor plan pictures to `maps/` and set each floor's plan image path.
5. **HTTPS** and a request limit on the open `/api/usage` address before school use (anyone can send log entries).
6. **Testing:** device tests, route correctness against real campus measurements.
7. **Docs:** fix the deck items in section 10 (see decisions D11, D12).

## 12. How to run
In VS Code: **Terminal > Run Task...** and pick a `UENav:` task (`.vscode/tasks.json`). The same commands by hand:
```
mvn clean verify                              # builds and tests everything
mvn -pl uenav-core -am install -DskipTests    # needed before the run lines below
$env:UENAV_DB_PASSWORD="your_password"
$env:UENAV_ADMIN_USER="admin"; $env:UENAV_ADMIN_PASSWORD="your-password"   # first start only (creates the first admin)
mvn -pl uenav-server spring-boot:run          # then open http://localhost:8080/api/health and /api/snapshot
mvn -pl uenav-client javafx:run               # PC admin app; start the server first
mvn -pl uenav-mobile javafx:run               # phone app in a phone-sized window; works without the server
```
Database: edit the password in `sql/00_create_database.sql`, run it with `mysql -u root -p`, then `01_schema.sql` and
`02_seed_sample.sql` into database `uenav`. `02_seed_sample.sql` can be run again: it first **deletes the usage log and
all map rows** (admin accounts are kept), then loads the UE Caloocan map.
Phone app server address: Admin tab, "Server & sync" card. Without it the phone still finds routes offline.
Android: push to GitHub `main` (or run the "Android" workflow by hand), download artifact **Package**, install the `.apk` on the phone.
