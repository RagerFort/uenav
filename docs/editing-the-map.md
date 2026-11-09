# Adding floors and rooms after the apps are built

Everything about the map is **data in MySQL**, edited in the PC admin app (UENav Admin.exe). Nothing needs to be
rebuilt when floors or rooms are added: phones download the new map by themselves (they check when the app opens
and every 30 minutes). Only the 3D building shapes and the phone's built-in fallback copy live inside the APK.

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
Not needed for new floors, rooms or walkways. Rebuild only to change the 3D building shapes
(`uenav-mobile/src/main/resources/maps/campus-3d.json`), the built-in server address, or to refresh the phone's
built-in map copy (copy `GET /api/snapshot` into `campus-snapshot.json`) so brand-new phones have it even offline.
