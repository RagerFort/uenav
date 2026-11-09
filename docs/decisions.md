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

## D20: Settings tab instead of Admin; theme follows the phone (implemented 2026-11-09, owner asked)

The phone's bottom bar is Explore / Map / Settings. Admin screens moved under Settings > Staff sign in (visitors no
longer see Admin). Settings has the theme (Same as phone, Light, Dark), Avoid stairs and the map version. "Same as
phone" uses JavaFX's platform colour scheme when the device reports one (JavaFX 22+); with JavaFX 21 on Android it
is not reported, so the app says so and uses Light.
