# Adding floors and rooms after the apps are built

Everything about the map is **data in MySQL**, edited in the PC admin app (UENav Admin.exe). Nothing needs to be
rebuilt when floors or rooms are added: phones download the new map by themselves (they check when the app opens
and every 30 minutes). Only the 3D building shapes and the phone's built-in fallback copy live inside the APK.

## The Engineering building
Floors 1 to 4 of the College of Engineering building come from `sql/tools/engineering-building.ps1`, which writes
`sql/04_engineering_building.sql` from the posted floor plans. To move rooms or add missing ones there, edit the
lists at the top of the script, run it, then load the SQL file (`mysql -u root -p uenav < sql/04_engineering_building.sql`).
It can be run again: it keeps the usage log and the ids it already uses. There is no elevator in this building.

## Order
1. **Floors** page: add the missing floors of a building (for example Engineering 2F and 3F: floor number 2, name "2nd floor").
2. **Places** page, for every floor:
   - the rooms (type `ROOM` or `OFFICE`), with that building and floor;
   - a `STAIRS` place and an `ELEVATOR` place where they are on that floor;
   - `JUNCTION` places for hallway corners (not shown in search, only used for routes).
3. **Walkways** page:
   - `WALK` walkways along each hallway (room to hallway junction, junction to junction, junction to stairs/elevator);
   - one `STAIRS` walkway between the stairs places of two floors next to each other (not accessible);
   - one `ELEVATOR` walkway between the elevator places of two floors (accessible).
   Each floor must be connected to the building entrance through these, or routes to it are "unavailable".
4. Open the phone app: Explore shows the new rooms; the Directions screen gets floor tabs (1F, 2F, ...).
5. **Map health** page (Tools): fix anything it lists (places nothing connects to, floors without stairs, places
   with no step-free route, distances that do not fit the map, duplicate names, offices without hours).

## Place types for the home screen buttons
The phone's big buttons and "Nearest ..." buttons find places by type:

| Button | Types (or a word in the name) |
|---|---|
| Offices | `OFFICE` |
| Classrooms | `ROOM`, `CLASSROOM`, `LAB` |
| Restrooms / Nearest restroom | `RESTROOM` (or "restroom", "comfort room", "CR", "toilet" in the name) |
| Food / Nearest food | `CANTEEN` (or "canteen", "cafeteria", "food" in the name) |
| Exits / Emergency exit | `GATE`, `EXIT` |
| Clinic | `CLINIC` |
| Facilities | `FACILITY`, `LIBRARY`, `CLINIC`, `CANTEEN` |

The map has no canteen or clinic yet: add the real ones (type `CANTEEN`, `CLINIC`) with a walkway to the nearest
junction. The two EN restrooms are SAMPLE places.

## Office hours and "What it handles"
On the Places form, fill in **Office hours** (e.g. `Mon-Fri 8:00 AM - 5:00 PM`) and **What it handles**. The phone
shows them on the office's info card. The seeded text is SAMPLE text: confirm it with each office.

## "You are here" QR signs
**QR codes** page (Tools): pick places (gates, building entrances, stairs, elevators, hallway junctions), press
**Save signs**, print the PNG files at 100% and post them where those places are. Each code holds only the place's id
(`UENAV:12`), so scanning works offline. Do not delete and re-add a place that has a sign: it would get a new id and
the printed sign would stop working (edit it instead).

## X and Y of indoor places
X/Y are in the same frame as the 3D campus map (X to the right, Y downwards, units of the campus map picture).
Put rooms inside their building's area so the 3D floor drawing lines up:

| Building id | Building | X from - to | Y from - to |
|---|---|---|---|
| 1 | College of Engineering | 1250 - 1490 | 118 - 602 |
| 2 | Administration | 388 - 562 | 384 - 571 |
| 3 | Tan Yan Kee | 640 - 1085 | 546 - 640 |
| 4 | Dr. Lucio C. Tan | 885 - 1206 | 120 - 190 |
| 5 | Old Academic | 615 - 832 | 140 - 190 |
| 6 | Old Fine Arts | 428 - 520 | 166 - 270 |
| 7 | CFAD | 522 - 652 | 505 - 642 |
| 8 | HRM Mock Hotel | 1105 - 1230 | 580 - 650 |
| 9 | Gym | 205 - 365 | 322 - 526 |

Use the same X/Y for the stairs (and the elevator) on every floor, since they are above each other.
Distances (metres) are typed per walkway: measure them, or use about 0.27 m per X/Y unit as an estimate.

## When to rebuild the APK
Not needed for new floors, rooms, walkways, office hours or QR signs. Rebuild only to change the 3D building shapes
(`uenav-mobile/src/main/resources/maps/campus-3d.json`), the built-in server address, or to refresh the phone's
built-in map copy (copy `GET /api/snapshot` into `campus-snapshot.json`) so brand-new phones have it even offline.
