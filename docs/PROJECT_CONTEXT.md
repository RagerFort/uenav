# UENav: Project Context (for AI assistants and new team members)

Read this file first. It is the single source of truth for what the project is, what is decided,
what is done, and what to do next. **Update the "Status" and "Next tasks" sections after every task.**
Last updated: 2026-11-09 (kiosk-style phone home, QR "You are here", nearest/emergency routes, office info, recent and
favorite places, graph view, avoid stairs, dark mode on phone and PC, map health and QR sign pages in the admin app).

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

| ID | Decision (2026-11-09, full text in `docs/decisions.md`) |
|----|----------|
| D14 | Node gets optional `description` and `open_hours` (office info card). Not sent = keep, empty = clear. Upgrade: `sql/03_place_details.sql`. |
| D15 | New place types RESTROOM, CANTEEN, CLINIC, LIBRARY, EXIT; `PlaceCategory` groups places for the home screen buttons. |
| D16 | QR signs hold only `UENAV:<node id>`. PC admin prints them (ZXing); phone scans with Attach `barcode-scan` or the code is typed. |
| D17 | Dark mode on phone and PC: stylesheets use colour variables; a dark stylesheet changes them. |
| D18 | Avoid stairs = ELEVATOR preference; emergency exit = STAIRS preference to the nearest GATE/EXIT; "nearest" = one Dijkstra run. |

## 6. Technology and versions
- Java 17 release level (owner's PC runs JDK 21; CI uses Temurin 17 plus Gluon's GraalVM 22.1.0.1-Final java17).
- Maven multi-module. Spring Boot 3.3.5. JavaFX 21.0.5. gluonfx-maven-plugin 1.0.28. MySQL 8.0.16+.
- Android build tools in CI: NDK r28c, Ubuntu 22.04 runner.
- No Maven wrapper in this repo; use `mvn`.

## 7. Repository layout
```
pom.xml                          parent; modules: core, server, client, mobile
uenav-core/                      Node (+ description, openHours), Edge, EdgeType, RoutePreference, Graph, Route,
                                 PathFinder (Dijkstra: findRoute, trace = route + places checked, findNearest,
                                 distancesFrom, reachable), Building, Floor, CampusData, Directions (route -> steps with
                                 landmarks: "Turn left at Registrar's Office", "Turn right after Gazebo"; each step knows
                                 its node), PlaceSearch (ranked search: room codes "en420", synonyms "cr", "pay"),
                                 PlaceCategory (home buttons), QrCodes (UENAV:<id>), GraphCheck (map health),
                                 MiniJson (reader+writer), SnapshotReader, Version
  src/test/...                   PathFinderTest, NearestAndTraceTest, SnapshotReaderTest, DirectionsTest, PlaceSearchTest,
                                 QrCodesTest, GraphCheckTest, VersionTest
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
                                 HttpSyncClient, Http, FileTextStore/TextStore, AppStorage; `android` profile.
                                 2026-11-09: Explore is a kiosk home (search, You are here card, category tiles, Nearest
                                 buttons, Emergency exit, Favorites, Recent, Avoid stairs switch, light/dark button);
                                 category lists sorted by walking distance; QrScanner (Attach barcode-scan, typed code
                                 on PC); "Tap where you are" map; Directions: office info card, favorite star, tappable
                                 steps that switch the floor and zoom in, graph view button (nodes, walkway lengths,
                                 places Dijkstra checked). PlacesMemory (places.json: recent + favorites).
                                 uenav-nav-dark.css; MapView/CampusArt draw dark colours.
                                 src/test/.../ScreenTour.java: dev tool that saves PNGs of every screen (not a test)
uenav-client/                    PC admin app, **admins only** (proposal: Windows = admin app). JavaFX: UenavApp, LoginView,
                                 AdminShell (Figma sidebar), DashboardView (+ Places card), PlacesView, BuildingsView,
                                 FloorsView, WalkwaysView (ManagePage), ReportsView, Icons, PlaceStyle, TimeAgo, UsageText,
                                 AdminApi (+ AdminModels, ApiException), Async, Theme, uenav.css. Does not depend on uenav-mobile.
                                 2026-11-09: Theme switches light/dark (top bar button, uenav-dark.css, remembered in
                                 admin.properties); MapHealthView + dashboard Map health card (GraphCheck on
                                 GET /api/snapshot); QrCodesView + QrSigns (printable "You are here" PNG signs, ZXing);
                                 Places form: Office hours, What it handles, new types.
                                 src/test/.../ScreenTour.java: dev tool that saves PNGs of the admin pages (not a test)
.vscode/tasks.json               VS Code tasks: build, start server, put server online (ngrok), make Windows .exe files, open apps
packaging/package-apps.ps1|.sh   jpackage: target/package/dist/UENav Admin (.exe) and UENav Server (.exe), Java built in
.github/workflows/windows.yml    builds the two Windows apps on GitHub, artifact "UENav-Windows"
docs/editing-the-map.md          how to add floors, rooms, stairs/elevators and walkways later (no rebuild needed)
sql/00_create_database.sql       creates database `uenav` and user `uenav_app`
sql/01_schema.sql                6 tables
sql/02_seed_sample.sql           UE Caloocan outdoor map (traced) + SAMPLE indoor rows, office info, EN restrooms
sql/03_place_details.sql         upgrade for older databases: node.description/open_hours, sample office info and
                                 EN restrooms (safe to run again; keeps data and the usage log)
.github/workflows/android.yml    builds the APK on GitHub (Ubuntu) and uploads it as artifact "Package"
docs/decisions.md                decisions D1 to D10
docs/PROJECT_CONTEXT.md          this file
```
Routing rules in `PathFinder` (assumptions, change if the team decides otherwise): edges are walkable both ways;
`ANY` = all edges; `STAIRS` = no elevator edges; `ELEVATOR` = no stairs edges and only `is_accessible` edges.
The phone's "Avoid stairs" switch uses `ELEVATOR`; the emergency exit route uses `STAIRS` (D18).
Walking time shown to users assumes 80 m per minute (`Directions`). No stairs/elevator time model yet.
Turns ("Turn left/right") are worked out from node X/Y, treated as map-picture pixels: X to the right, **Y downwards**.
Map (D6, changed to drawn 3D): the campus is drawn in basic 3D shapes from
`uenav-mobile/src/main/resources/maps/campus-3d.json` (roads, campus ground, field, buildings as raised blocks with
shaded walls, trees), traced from the official UE Caloocan campus map. Node X/Y and the shapes use the same frame:
pixels of that campus map with the legend cut off (X right, Y down). A floor is drawn as its building's outline
(a thin slab) with stairs/elevators as green blocks; a floor plan picture `maps/<floor.plan_image_path>` is used
instead if one is added. To move a building, edit its `points` in `campus-3d.json`; heights are in the same units.
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
| Phone: built-in server address, background retry (search made with server off arrived ~30 s after it started) | **Ran here** with `http://localhost:8080` as the built-in address; ngrok not tried here (blocked) |
| Packaged apps (jpackage app-image): UENav Server reads ~/.uenav/server.properties; UENav Admin login has a server box | **Ran here** as Linux app-images (server connected to MySQL and created the admin from the file; admin app opened). Windows .exe build (workflow and .ps1) **not run** |
| Phone: newer map downloaded from server (D9) | **Ran here**: a place added in the Admin tab appeared in Explore after restart |
| Phone Admin tab (sign in, dashboard, add/edit/delete place, route reports, server address) | **Ran here** against the real server |
| PC admin app: Figma sidebar (admin pages only), dashboard with Places card | **Ran here**, screenshots checked against the Figma desktop frame |
| PC admin pages Places, Buildings, Floors, Walkways, Reports, CSV export | Compiled and the app opened them; add/edit dialogs not clicked through here |
| Phone on Android: clear-text `http://` to the server | **Not tested** (see task 2) |
| UE Caloocan outdoor map in 3D shapes (16 blocks, field, roads, trees), 9 buildings, 2 gates, offices, facilities, 46 walkways | **Ran here** (tests, MySQL, phone screenshots). Positions traced from the campus map; distances are **estimates** (0.269 m per picture pixel, from the 4.86 ha campus area). Not checked on site |
| Indoor data (Engineering 1F/4F, EN 420) | Still SAMPLE rows; no floor plans yet |
| Phone map: no zooming while dragging (touch scroll / flick events ignored), pinch and double-tap zoom | **Ran here** with simulated touch events; not tried on a phone |
| `mvn clean verify` on the owner's Windows PC (160 tests: core 46, server 33, admin 27, phone 54) | **Ran here** 2026-11-09 |
| Owner's MySQL upgraded with `03_place_details.sql` (backup first in `~/UENav/backup`); new server build on port 8081 served office info and the EN restrooms; phone's `campus-snapshot.json` copied from it (version `9944eeeaf8d7cf92`) | **Ran here** 2026-11-09 |
| Phone: kiosk home, categories (nearest first), You are here (typed QR code, tap on map), nearest restroom/exit, emergency route, avoid stairs, office info card, favorites/recent, tappable steps, graph view, dark mode | **Ran here** as off-screen 390x820 screenshots (ScreenTour), light and dark; never on a phone |
| Phone: camera QR scanning (Attach barcode-scan) | APK **built on GitHub** 2026-11-09 (run 42577220171, Google code scanner inside); **not run** on a phone yet |
| PC admin: dark mode, Map health page and dashboard card, QR codes page, Places form office fields | **Ran here** as off-screen screenshots against a stand-in server; Save signs and the Places dialog not clicked through |
| QR code content | **Ran here**: a generated sign's QR decodes back to `UENAV:42` (ZXing reader in QrSignsTest) |

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
20. **No hard-coded colours** in CSS or Java styles any more: use the colour variables (`-nv-surface`, `-surface`,
    ...) so dark mode keeps working. Canvas drawing (MapView, CampusArt) picks dark colours itself.
21. Attach 4.0.25 `BarcodeScanService` has `asyncScan(...)` and `resultProperty()` (not "resultsProperty").
    On Android it uses Google's code scanner (ML Kit, through Google Play services): the APK asks for no camera
    permission, and the scanner may download itself the first time it is used, which needs internet. "Scan QR sign"
    therefore opens the type-the-code screen behind the camera, so a failed or cancelled scan falls back to typing.
22. Printed QR signs depend on node ids. Edit a place instead of deleting and re-adding it.
23. Editing a place from an older app sends no office info: the server keeps the saved text (D14). The PC form sends
    an empty text to clear it.

## 10. Known inconsistencies in the proposal deck (still to fix)
- Slide text says 7 tables; there are 6 (D2). Class diagram must have no Room class.
- ERD Node lacks `name`, `x`, `y`; Usage Log lacks feedback type, status and `client_log_id`.
- Sequence diagram has the app calling the server for routes and the database directly; routing is now local and the log is queued then synced.
- Software slide still lists SQLite and JDBC from the client; has a stray "User/Admin" text box.
- Flowchart and sequence diagram lack the stairs/elevator choice. Figma lacks: Explore/search list, place add/edit form, route-reports list, walkway editing, CSV export, error states.
- Concept, "how a user navigates", "admin panel" and "boundaries by design" slides are one-line stubs.
- Figma admin dashboard is shown on a phone; D7 says PC first.

## 11. Next tasks (in dependency order)
0. **2026-11-09 work:** push the branch so GitHub builds the APK and the Windows .exe files; on the phone check the
   camera QR scan (print one sign from the QR codes page), dark mode, and airplane mode. Confirm the SAMPLE office
   hours with each office; add the real canteen, clinic, library and restrooms (types CANTEEN, CLINIC, LIBRARY,
   RESTROOM); print and post QR signs at entrances, stairwells and hallway corners.
1. **Owner:** pull, open the folder in VS Code, run the tasks "UENav: start server" and "UENav: open PC admin app",
   then "UENav: open phone app on the PC". Report anything that looks wrong next to the Figma.
2. **Owner:** set up ngrok (section 12), put its HTTPS address in `uenav-server.json`, push to `main` so GitHub builds
   the APK; install it and test: Explore, directions in airplane mode, then turn data on and check the dashboard
   numbers move without typing an address. Plain `http://` addresses will likely be blocked by Android (clear text);
   the ngrok HTTPS address avoids that.
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
### Windows programs (.exe)
VS Code task "UENav: make Windows .exe files" (or `.\packaging\package-apps.ps1`) makes, in `target\package\dist`:
`UENav Admin\UENav Admin.exe` and `UENav Server\UENav Server.exe` (Java built in; MySQL must still be installed).
GitHub also builds them on every push to `main` (Actions > Windows > artifact **UENav-Windows**).
The server .exe needs no environment variables: create `C:\Users\<you>\.uenav\server.properties` with
`UENAV_DB_PASSWORD=...` (and `UENAV_ADMIN_USER` / `UENAV_ADMIN_PASSWORD` for the very first start).
The admin .exe remembers the server address typed on its login screen (`~/.uenav/admin.properties`).
Adding floors and rooms later needs no rebuild: see `docs/editing-the-map.md`.

Database: edit the password in `sql/00_create_database.sql`, run it with `mysql -u root -p`, then `01_schema.sql` and
`02_seed_sample.sql` into database `uenav`. A database made before 2026-11-09 needs `03_place_details.sql` once
(`mysql -u root -p uenav < sql/03_place_details.sql`) before the new server is started. `02_seed_sample.sql` can be run again: it first **deletes the usage log and
all map rows** (admin accounts are kept), then loads the UE Caloocan map.
Phone app server address: built into the app in `uenav-mobile/src/main/resources/uenav-server.json`
(`{"serverUrl": "https://..."}`), so phones connect by themselves. An address typed in the Admin tab ("Server & sync")
replaces it on that phone; clearing the box goes back to the built-in one. Without any address the phone still finds
routes offline. While the app is open it retries sending every minute and checks for a newer map every 30 minutes.

### Reaching the server from any Wi-Fi or mobile data (ngrok)
A PC address like `http://192.168.1.10:8080` works only for phones on the **same** Wi-Fi, and Android blocks plain
`http://` by default. A free ngrok address is HTTPS and works from any network:
1. Sign up at ngrok.com, install ngrok for Windows, run `ngrok config add-authtoken <your token>` once.
2. In the ngrok dashboard, open **Domains** and claim the free static domain (e.g. `uenav-xyz.ngrok-free.app`).
3. Start the server, then the VS Code task "UENav: put server online (ngrok)" (or `ngrok http 8080 --url=https://uenav-xyz.ngrok-free.app`;
   older ngrok versions use `--domain=` instead of `--url=`). Check `https://uenav-xyz.ngrok-free.app/api/health` in a browser.
4. Put that address in `uenav-server.json`, commit, push, install the new APK. Every phone then connects by itself.
The PC with the server and ngrok must stay on. For the school pilot, move the server and MySQL to a school or cloud
server with its own HTTPS address and change `uenav-server.json` to it. Anyone on the internet can reach the server
then: admin login is protected (BCrypt, lockout), but `/api/usage` is open (see task 5).
Android: push to GitHub `main` (or run the "Android" workflow by hand), download artifact **Package**, install the `.apk` on the phone.
