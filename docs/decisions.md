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
