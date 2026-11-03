# UENav: Project Context (for AI assistants and new team members)

Read this file first. It is the single source of truth for what the project is, what is decided,
what is done, and what to do next. **Update the "Status" and "Next tasks" sections after every task.**
Last updated: 2026-11-03 (after the PC admin screens were written).

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
- **Admin (PC first):** logs in, adds/edits/deletes places and walkways, sees most-searched places and
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
                                 MiniJson (reader+writer), SnapshotReader, CampusData, Version
  src/test/...                   PathFinderTest (10), SnapshotReaderTest (5), VersionTest
uenav-server/                    Spring Boot: UenavServerApplication, HealthController
  .../snapshot/                  GET /api/snapshot and /api/snapshot/version (reads MySQL)
  .../admin/                     BCrypt login, 8-hour token, lockout, /api/admin/login|logout|me, interceptor
  .../mapadmin/                  admin CRUD for buildings, floors, nodes, edges under /api/admin/**
uenav-client/                    desktop JavaFX admin app: UenavApp, LoginView, AdminShell, DashboardView,
                                 PlacesView, AdminApi (+ AdminModels, ApiException), Async, Theme, uenav.css
uenav-mobile/                    Gluon app: MobileApp (loads bundled campus-snapshot.json), RouteFormatter; `android` profile
sql/00_create_database.sql       creates database `uenav` and user `uenav_app`
sql/01_schema.sql                6 tables
sql/02_seed_sample.sql           SAMPLE data only (made-up campus)
.github/workflows/android.yml    builds the APK on GitHub (Ubuntu) and uploads it as artifact "Package"
docs/decisions.md                decisions D1 to D10
docs/PROJECT_CONTEXT.md          this file
```
The old `uenav-client/.../ApiClient.java` (health-check window) is obsolete and should be deleted.
Routing rules in `PathFinder` (assumptions, change if the team decides otherwise): edges are walkable both ways;
`ANY` = all edges; `STAIRS` = no elevator edges; `ELEVATOR` = no stairs edges and only `is_accessible` edges.
Walking time shown to users assumes 80 m per minute (`RouteFormatter`). No stairs/elevator time model yet.

## 8. Status
Evidence levels: **Confirmed** = shown by owner's output or CI log. **Reported** = owner said so, no output seen.
**Ran here** = AI ran it in its sandbox with stand-ins (no Maven, MySQL, Spring or JavaFX available there).
**Not run** = written but never executed.

| Item | Status |
|------|--------|
| Git repo, pushed to GitHub (`main`) | Confirmed |
| Spring Boot server starts and connects to MySQL | Confirmed from log |
| `uenav-core` builds and tests pass in GitHub CI ("Build and test core" step passed) | Confirmed from CI log |
| Gluon hello sample ran on the owner's phone | Reported |
| UENav `uenav-mobile` APK builds on GitHub | Reported ("it now works"). An earlier build failed because of a `resourcesList` setting; it was removed |
| Offline routing on the phone in airplane mode | **Not confirmed** |
| `sql/01_schema.sql`, `02_seed_sample.sql` run on owner's MySQL | Reported ("it all works") |
| `GET /api/snapshot` and `/version` | Reported working |
| Phone app loads bundled `campus-snapshot.json` | Reported built; on-device behaviour not confirmed |
| Admin login (BCrypt, token, lockout, `/api/admin/me`) | Reported working after a missing-file fix |
| Admin map-editing endpoints (`mapadmin`), 11 unit tests | Tests **Ran here** with stand-ins; not reported by owner |
| `AdminApi` (client) + `AdminApiTest` (8 tests, local fake server) | **Ran here** |
| PC admin screens: login, shell/sidebar, dashboard, Places page | **Not run**: JavaFX code was never compiled (no JavaFX in AI sandbox) |
| Usage log: sync endpoint, stats, reports, CSV export | Not written |
| Admin pages for walkways, buildings and floors | Not written (needed before real data can be entered through the app) |
| Phone: update snapshot from server, local log queue, Explore list, map image overlay, Helpful/Wrong route | Not written |
| Real campus data and Photoshop map images | Not started |

## 9. Gotchas already hit (do not repeat)
1. Android apps with Gluon **cannot be built on Windows**. Build on GitHub Actions (Ubuntu). Windows only downloads and installs the APK.
2. Gluon's build starts a nested Maven run, so the parent pom must be installed: the workflow uses `mvn -B -pl uenav-core -am install` before `gluonfx:build`.
3. In PowerShell set the DB password with `$env:UENAV_DB_PASSWORD="..."` (not `export`).
4. `uenav-mobile` uses a plain JavaFX `Application` with no Gluon Attach services. If the phone shows a blank screen, add `display`, `lifecycle`, `storage` (and `util`) as in Gluon's `hello-gluon-ci` sample (Attach 4.0.25).
5. `uenav-core` uses Java `record`s. If a future native-image build complains, rewrite them as plain classes.
6. Git shows "LF will be replaced by CRLF" warnings on Windows. They are harmless.
7. The sample/test campus data is invented. Do not present its distances as real.

8. Do **not** add a `resourcesList` entry like `.*\\.json$` to the gluonfx plugin config: it produced an invalid resource-config file and broke the native build.
9. **Give the owner every file** a step needs (use the file list, not just the interesting ones). A missing `JdbcAdminAccountRepository` caused a Spring "no bean" start-up error.
10. When adding code, **search the repo first** (`grep`) for an existing method or file. Duplicates of `MiniJson` writer and `SnapshotReader.isSearchable` were almost added twice.
11. Spring/JavaFX code cannot be compiled in the AI sandbox: say so, and expect a first-run fix.

## 10. Known inconsistencies in the proposal deck (still to fix)
- Slide text says 7 tables; there are 6 (D2). Class diagram must have no Room class.
- ERD Node lacks `name`, `x`, `y`; Usage Log lacks feedback type, status and `client_log_id`.
- Sequence diagram has the app calling the server for routes and the database directly; routing is now local and the log is queued then synced.
- Software slide still lists SQLite and JDBC from the client; has a stray "User/Admin" text box.
- Flowchart and sequence diagram lack the stairs/elevator choice. Figma lacks: Explore/search list, place add/edit form, route-reports list, walkway editing, CSV export, error states.
- Concept, "how a user navigates", "admin panel" and "boundaries by design" slides are one-line stubs.
- Figma admin dashboard is shown on a phone; D7 says PC first.

## 11. Next tasks (in dependency order)
1. **Owner:** compile and run the PC admin app (steps in the last reply), report errors. Confirm airplane-mode routing on the phone.
2. **Admin pages for buildings and floors, then walkways** (extend `AdminApi` with edges, building and floor create/update/delete; same table-and-dialog pattern as `PlacesView`). Needed before real campus data can be entered.
3. **Usage log:** `POST` sync endpoint using `client_log_id`, stats endpoints (most searched, recent activity, wrong-route reports), CSV export; make the dashboard cards real; "Route reports" page.
4. **Phone:** update the snapshot from the server when online; local log queue and sync; Explore/search list; map image with route overlay; Helpful / Wrong route buttons.
5. **HTTPS** for the server before anyone uses it over school Wi-Fi (tokens and passwords are plain HTTP today).
6. **Testing:** API tests, device tests, route correctness against real campus measurements.
7. **Docs:** fix the deck items in section 10.

**In parallel, owner action:** collect real campus data (each place: name, building, floor, distance in meters to neighbouring nodes) and prepare the Photoshop map images.

## 12. How to run
```
mvn clean verify
mvn -pl uenav-core test
$env:UENAV_DB_PASSWORD="your_password"
$env:UENAV_ADMIN_USER="admin"; $env:UENAV_ADMIN_PASSWORD="your-password"   # first start only (creates the first admin)
mvn -pl uenav-server spring-boot:run          # then open http://localhost:8080/api/health and /api/snapshot
mvn -pl uenav-core -am install                # needed before the next two lines
mvn -pl uenav-client javafx:run               # PC admin app; start the server first (see below)
mvn -pl uenav-mobile javafx:run               # offline routing test on desktop
```
Database: `mysql -u root -p < sql/00_create_database.sql`, then `01_schema.sql` and `02_seed_sample.sql` into database `uenav`.
Android: push to GitHub `main` (or run the "Android" workflow by hand), download artifact **Package**, install the `.apk` on the phone.
