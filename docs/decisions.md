# UENav Decisions

Status: D1 to D9 answered by the project owner.

| ID | Question | Decision |
|----|----------|----------|
| D1 | How do the app and database connect? | Spring Boot REST API sits in front of MySQL. The "SQLite / JDBC in client" wording in the proposal slides is dropped. |
| D2 | Add a Room table? | **No.** Rooms and places are rows in the **Node** table, told apart by `Node_Type`. The ERD stays as drawn. |
| D3 | Node positions and names? | Node gets `X`, `Y` and a `Name`, so routes can be drawn and steps can say "Turn left". |
| D4 | Feedback storage? | Each "Helpful" / "Wrong route" is its own Usage Log record with a feedback type (`Helpful`, `Wrong_Route`), a status (`New`, `Reviewed`) and the route it was about. |
| D5 | Where does Dijkstra run? | **Inside the Android app** (and desktop client), using the `uenav-core` module, so navigation works **offline** inside the school. The server is not needed to find a route. |
| D6 | How are maps shown? | Map pictures made in Photoshop (the emergency map can be used as the base), with the route line drawn on top by the app using node X/Y positions. |
| D7 | Where does the admin work? | On the PC first. The phone admin screen comes later. |
| D8 | Admin login? | Passwords stored hashed with BCrypt. Server gives a token after login. Password checks happen on the server only. |

## What these decisions change

- **Table count:** the proposal text says 7 tables. With D2 it is **6**: Admin Account, Building, Floor, Node, Edge, Usage Log. Fix this on the ERD slide and in the Class Diagram (no Room class).
- **Offline (D5):** the phone needs its own copy of the campus data (nodes, edges, buildings, floors, map images). It cannot read MySQL. See D9.
- **Logs while offline:** searches and feedback are saved on the phone first and sent to the server later, when there is a connection.
- **Sequence diagram:** route search no longer calls the server. Update it so `computeRoute` runs inside the app and the log is saved locally and synced later.
- **Server role:** admin login, place/walkway editing, reports, stats, CSV export, and handing out the data snapshot.

## D9: getting campus data onto the phone (approved)

The server exports one data file (JSON) with the whole graph: nodes, edges, buildings and floors. The app ships with a built-in copy and replaces it when it connects and finds a newer version. Map pictures are packed inside the app.

## D10: JSON handling (implemented, owner has not explicitly confirmed)

The phone and desktop apps read and write JSON with `MiniJson` in `uenav-core`, a small hand-written reader and writer with no outside library and no reflection. This avoids native-image (Android) problems with JSON libraries. The server keeps using Spring's built-in Jackson. If the team prefers a library later, only `MiniJson` and its callers change.

## D11: the PC app is for admins only (owner confirmed 2026-11-04)

The proposal lists Windows 10/11 as the **admin app** and Android for users and admins. So the PC app has only the
admin login and admin pages: no Explore, no Map, no "Continue as visitor". The Figma desktop frame shows Explore and
Map links in the sidebar; those were left out to match the proposal. Visitors use the phone app.
(An earlier version of the code put the phone's visitor screens on the PC; that was removed.)

## D12: a basic phone Admin tab (implemented 2026-11-04, owner has not confirmed)

D7 says admin on the PC first, phone later. The Figma phone frames include Login and an admin Dashboard, so the phone
now has an Admin tab: sign in, dashboard numbers, most searched, recent activity, add/edit/delete place, route reports.
Walkways, buildings, floors and CSV export stay on the PC. It uses the same server API and token as the PC (D8).

## D13: the phone map is drawn in basic 3D shapes (implemented 2026-11-04, owner asked for it)

D6 said Photoshop map pictures with the route drawn on top. The owner asked for a 3D map in basic shapes instead, like
the Figma 3D frames. The app now draws the campus from `maps/campus-3d.json`: flat shapes (roads, ground, field) and
raised blocks (buildings, trees), traced from the official campus map. Floors are drawn as their building's outline.
A floor plan picture can still be added per floor. The database and ERD do not change (shapes are app files, like
the map pictures in D9).

## D14: office info on places (implemented 2026-11-09, owner has not confirmed)

The `node` table gets two optional columns: `description` (what the office handles, up to 500 characters) and
`open_hours` (up to 100 characters). The phone shows them on the destination's info card; the PC admin app edits
them on the Places form. Still 6 tables (D2); the ERD's Node needs these two attributes added. Older databases are
upgraded with `sql/03_place_details.sql`. When a place is edited, a field that is **not sent** (older apps, the phone
Admin tab) keeps the saved text, and an **empty** field clears it, so older apps cannot wipe office info.
The descriptions and hours in the seed data are SAMPLE text to be confirmed with each office.

## D15: more place types and home-screen categories (implemented 2026-11-09, owner has not confirmed)

New node types: RESTROOM, CANTEEN, CLINIC, LIBRARY, EXIT (no database change; `node_type` is free text).
`PlaceCategory` in uenav-core groups places for the kiosk-style home screen (Offices, Classrooms, Restrooms, Food,
Exits, Facilities) by type, or by a word in the name for older rows. Two SAMPLE restrooms were added to the
Engineering building (1F, 4F) so the "Nearest restroom" button can be shown; there is no canteen or clinic on the map
until the team adds the real ones.

## D16: "You are here" QR codes (implemented 2026-11-09, owner has not confirmed)

A QR code holds only `UENAV:<node id>` (`QrCodes` in uenav-core), so scanning sets the starting point with no
internet. The same code is printed under the QR for typing it in. The PC admin app prints the signs (QR codes page,
ZXing library, PC only). The phone scans with Gluon Attach `barcode-scan` (added to `attachList`); on a PC, or if
the service is missing, the visitor types the code. Node ids must stay the same after signs are printed: deleting
and re-adding a place gives it a new id and its old signs stop working.

## D17: dark mode (implemented 2026-11-09, owner asked for it)

Both apps have a light/dark switch (phone: Explore header and Admin dashboard; PC: top bar). The light stylesheets
now use colour variables only; `uenav-nav-dark.css` / `uenav-dark.css` change the variables. The phone map draws
its own darker colours. The choice is remembered (phone `settings.json`, PC `admin.properties`).

## D18: route modes (implemented 2026-11-09, owner has not confirmed)

"Avoid stairs" (phone setting) uses the existing ELEVATOR preference: no stairs edges and only accessible walkways.
The emergency exit route uses the STAIRS preference (no elevators) to the nearest GATE or EXIT. "Nearest ..." buttons
run one Dijkstra search that stops at the first place of the category it reaches (`PathFinder.findNearest`).
Usage log `route_pref` values are unchanged (ANY / STAIRS / ELEVATOR).

## D19: the Engineering building from its floor plans, stairs only (implemented 2026-11-09, owner gave the plans)

The SAMPLE indoor rows were replaced with floors 1 to 4 of the College of Engineering building, read from the
emergency-exit floor plans posted on each floor: rooms, labs, offices, the library (3F), comfort rooms at both ends
and four stairwells (two per end). The owner confirmed there is **no elevator**, so no ELEVATOR walkways exist and
"Avoid stairs" cannot reach the upper floors (the app says so and offers "Allow stairs"). The data is generated by
`sql/tools/engineering-building.ps1` into `sql/04_engineering_building.sql`. Assumptions to check on site: the
plans' left wing is the north end and the plans' top is east; building length 104.6 m (owner's map measurement);
room positions follow the plans' order and spacing; the doors are the north exit, the south-west main door and a
south-east exit.

## D28: Lucio Tan floors, office hours, Beach Volleyball Court (implemented 2026-11-22, owner gave the plans)

- **Dr. Lucio C. Tan Building** (owner: the plans with grid lines 1-12 are this building): ground floor to the 5th
  floor mezzanine from the architectural plans (scale 1:200), generated by `sql/tools/lucio-tan.ps1` into
  `sql/07_lucio_tan.sql`: basic education offices, canteen and kindergarten rooms (GF), classrooms 1-31, computer,
  speech and science laboratories, the guidance office (2F), the high school library and a hall with a stage on 5F
  (owner), and the library mezzanine and light and sound room above it. Three elevators GF-5F (anyone may use them),
  the main stairs and two fire-exit stairs; the mezzanine is reached by stairs only. Assumption: grid line 1 is the
  west end and the main stairs' side faces the campus; the campus door (node 29) moved to the lobby.
- **Engineering**: the owner's 2014 plans are older than the 2026 emergency plans the map uses, so the 2026 names
  stay; only gaps are filled: "Open Lab Room" is Computer Laboratory Room I, the 3F "Office" is The Dawn Office, and
  the 2014 plan's Alternative Learning System Center and Engineering Student Council Office are added (marked "please
  confirm": the 2026 plan does not show them).
- **Office hours** (`sql/08_office_hours.sql`, UE's announcement): every office Mon-Fri 8:00 AM-12:00 noon and
  1:00-5:00 PM; the Admissions Office also on Saturdays during enrollment. Hours an admin typed are kept.
- The court is the **Beach Volleyball Court** again (it was renamed Open Court on 2026-11-15). The site development
  plan's playground is not used (owner).
- Mezzanine floors get tabs like "5M" (`Floor.shortLabel`). Loaded into the owner's database 2026-11-22 (backup
  `~/UENav/backup/uenav-before-lucio-tan-20261122-160556.sql`); phone snapshot `0bc5c15023658e43`.

## D27: Map editor in the PC admin app (implemented 2026-11-22, owner asked)

UENav Admin has a **Map editor** page (first under ADMINISTRATION): the map the phones show, drawn by the same code,
with every walkway, place id and walkway length (the graph view), level by level (campus, or one building floor).
Select and move: click a place or walkway to edit it on the right (name, type, description, office hours; length,
type, step-free), drag a place to move it (its walkways on that level get new lengths in proportion, stairs,
elevators and bridges keep theirs). Add place: click where it goes, name it, and it is joined to the nearest point.
Connect: click two places for a walkway, its length measured on the map at that level's scale (the middle value of
metres per map unit over the level's walkways). Stairs and elevators have "Link to the floor above / below" (nearest
one of the same kind on that floor; 15 m for a flight, 6 m by elevator). Deleting a place deletes its walkways first.
Every change goes to the server at once through the existing admin API; phones get it with their next map update.
Desktop only: the phone's admin screens have no editor.

To share the drawing, `MapView`, `CampusArt` and `maps/campus-3d.json` moved from uenav-mobile to a new module,
**uenav-map** (package `com.uenav.map`, JavaFX only), which the phone app and the PC app both use. `MapView` gained
what editing needs: `toMap`/`toView`, `nodeAt`, `edgeAt`, `levelNodes`, `updateData` (new data, same view) and
`setDecorations` (drawing on its top layer). The admin app's screen tour drives the editor with mouse events and
prints the requests it sends (drag, connect, add).

## D26: the phone app finds the server even where the network's DNS blocks it (implemented 2026-11-19, owner asked)

The owner's phone could not reach `https://encounter-applied-goofiness.ngrok-free.dev`: its mobile network's DNS
answers "does not exist" for all of `ngrok-free.dev` and `ngrok-free.app` (seen from the PC on the phone's hotspot,
DNS 10.247.120.74), while Cloudflare and Google DNS resolve it and the tunnel works. Other students on that carrier
would have the same problem, so the app works around it instead of asking everyone to change Private DNS.

`Http.call` (every server call: map updates, usage log, admin screens) first connects as before. Only when the
phone's lookup fails (UnknownHostException) does `SecureDns` ask Cloudflare (`https://1.1.1.1/dns-query`) and then
Google (`https://8.8.8.8/resolve`) over HTTPS by IP address, and the request goes straight to the address found: a
TCP connection to it, TLS with the real name (SNI) and the normal certificate check for that name
(endpoint identification "HTTPS"), then a small HTTP/1.1 request (`Http.direct`, `Http.readReply`: sized, chunked or
read to the end). The address is remembered for 5 minutes and forgotten when it stops answering. Tried here behind
the blocking DNS: map version, full map (222 KB) and an admin login POST all worked, and a certificate for another
name was refused. With no internet at all the call fails as before (searching and directions work offline).

## D25: UENav Server puts itself online through ngrok (implemented 2026-11-19, owner asked)

With `UENAV_NGROK_URL=https://<name>.ngrok-free.dev` in `~/.uenav/server.properties`, opening UENav Server also
starts `ngrok http <port> --url=<that address>` once the server answers, so phones on any network reach it at the
address built into the app (`uenav-server.json`); the owner's is `https://encounter-applied-goofiness.ngrok-free.dev`.
The server watches ngrok's log and says "Online at ..." or ngrok's error; if ngrok already has the address online
(VS Code task, by hand) it uses that one. ngrok stops with the server, also when the window is closed (the same
hidden helper as the database, `AfterExit`). ngrok must be installed and logged in once (`ngrok config
add-authtoken`); `UENAV_NGROK_PATH` names ngrok.exe if it is not on the PATH. Tried here: the address served the map
7 s after opening the server. This PC's own DNS (10.247.120.74) does not resolve ngrok-free.dev names (public DNS
does), so testing it from this PC needs `curl --resolve`; phones on mobile data are not affected.

## D24: the database is built into UENav Server (implemented 2026-11-19, owner asked)

UENav Server needed a MySQL server that was installed and running first; on the owner's PC MySQL80 is set to start
by hand, so the server "did not work immediately". Now `UENav Server.exe` starts its own database: MariaDB 11.4
(MySQL compatible) through MariaDB4j, unpacked to `~/.uenav/mariadb-11.4.5`, data in `~/.uenav/database`, listening
only on this computer at **127.0.0.1:3307** (3307 so it never clashes with a MySQL on 3306). The first start creates
database `uenav`, user `uenav_app` (password `UENAV_DB_PASSWORD` from `~/.uenav/server.properties`, made up and saved
if missing; root gets the same password, anonymous accounts are removed) and loads the campus map from `sql/` (01,
02, 04, 05, 06, copied into the server jar). Later starts take about a second.

Querying: while the server runs, MySQL Workbench or the `mysql` command connect to 127.0.0.1:3307 as `uenav_app`, and
**UENav SQL.exe** (beside UENav Server.exe; it runs `UENav Server.exe sql`) opens the mysql command line already
logged in to `uenav` (it starts the database itself if the server is closed). Closing: on Ctrl+C or exit the server
sends SHUTDOWN after closing its own connections; closing the console window ends Java before it can do anything,
so a hidden helper (PowerShell `Wait-Process`, then the bundled mysql `-e SHUTDOWN`) closes the database once the
server process has ended, also after a crash or `taskkill /F`. A database left running is found and taken over by the
next start. Setting `UENAV_DB_URL` turns all of this off and uses that MySQL server, as before.

The owner's MySQL80 data (1 admin, 878 places, 971 walkways, 5 usage rows) was copied in on 2026-11-19; backup
`~/UENav/backup/uenav-mysql80-before-built-in-db-20261119-123225.sql`. MySQL80 is no longer needed.

## D23: directions play the route step by step (implemented 2026-11-15, owner asked)

When directions open for a new route (Get directions, swap, stairs/elevator choice, nearest place, emergency), the
route plays on the map after a short wait: a walker (white disc, red arrow turning smoothly) walks it, the walked
part fills in red with arrows flowing along it while the rest stays grey, and the matching step lights up in the
list (scrolled into view). It slows down and pauses at each step with a ring; at stairs, elevators, doors and the
bridge a tag says where it goes ("Elevator up to 4F", "Bridge to Engineering 3F") and the map fades to that floor.
At the destination it fades out and the full route is red again. Arrows along the route also show the way when
nothing plays. Touching the map, a step, a floor tab or + / - stops it; Play / Stop beside STEPS replays it;
Settings > Animate directions turns the automatic play off.

For speed on a phone the view does not follow the walker: each floor is fitted to its part of the route, so a frame
only redraws a see-through layer the size of the view (`MapView.drawPreview`) and the map is drawn once per floor,
while faded. `RoutePlayer` holds the timing (walk speed 320 px/s, 0.6-2.2 s between steps); gate to EN 420 plays in
about 15 s.

## D22: Tan Yan Kee floors from the posted plans (implemented 2026-11-15, owner gave the photos)

The owner photographed the evacuation / emergency-exit plan on every floor (2F to 10F, the 6F library and the 6F
"WORKSHOP" plan) and the building directory. `sql/tools/tan-yan-kee.ps1` turns them into `sql/06_tan_yan_kee.sql`:
ground floor to 10th floor, 184 rooms, offices and comfort rooms, four stairwells (1 by the central lobby, 2 at the
east end, 3 at the west lobby, 4 at the wing tip) and two elevator banks (central, 3 cars; west, at the wing).
Owner's answers: the "CFAD building" is TYK's angled west wing (rooms TYK x00-x05), so building 7 was removed and
its door (node 34) belongs to TYK; ten floors; anyone may use the elevators; WORKSHOP is the 6th floor. On 5F both
wings are roof decks, so stairs 2 and 4 only pass through. One floor of stairs counts 15 m (climbing time) and one
floor by elevator 6 m, so routes take the elevator for long climbs. The main door (node 33) is under the 2F canopy
on the field side. The ground floor (owner) is a canteen in the middle of the building, entered from the grass field
or through the CFAD wing; the floors above are reached by the stairs and elevators, and it has no offices. A bridge
on TYK 4F leads to EN 3F (owner), traced on the photo from TYK's east end over HRM to EN's south wing (48 m), so
EN 3F is reachable by the TYK elevators (step-free). Directions show it as "Cross the bridge to ..." and the map tag
names the other building. Not confirmed: TYK 706, the 9F and 10F room numbers (read from small print), and the 6F
comfort rooms.

## D21: campus layout checked by a student (implemented 2026-11-15, owner asked)

The outdoor map was traced from the campus map picture. It is now placed from a satellite photo (Esri World
Imagery) and OpenStreetMap building outlines, turned into the app's existing map frame with a four-corner fit, so
node X/Y and the Engineering floor data keep working. The owner, a UE Caloocan student, named the buildings and
confirmed the paths:
- Only two gates: University Hills Gate (the arch at the Samson Road / U.E. Tech Road corner, "263 UE Tech" on
  Street View) and Samson Road Gate (the pedestrian arch next to STI). Students mainly use the Samson Road Gate.
- From the Samson Road Gate an opening between the CFAD Building and the Administration Building leads to both of
  them and onto the grass field; students cross the field to Lucio Tan and to the Engineering building.
- The square on the east side of the field is an open court (not beach volleyball); the round driveway by the gym
  is parking; the small building by the Samson Road Gate is the Security Office; the field is not a football field.
- The CFAD Building is the red-roof building west of Tan Yan Kee (UE's pages say CFAD classes are inside TYK).
Distances are measured on the photo. The Engineering building keeps its earlier shape (about 10 m off at the south
end) so its floor plans still match. Not confirmed yet: where the Engineering "left side entrance" is, and the
playground and gazebo positions.

## D20: Settings tab instead of Admin; theme follows the phone (implemented 2026-11-09, owner asked)

The phone's bottom bar is Explore / Map / Settings. Admin screens moved under Settings > Staff sign in (visitors no
longer see Admin). Settings has the theme (Same as phone, Light, Dark), Avoid stairs and the map version. "Same as
phone" uses JavaFX's platform colour scheme when the device reports one (JavaFX 22+); with JavaFX 21 on Android it
is not reported, so the app says so and uses Light.
