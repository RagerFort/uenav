package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DirectionsTest {

    /** The sample snapshot bundled with the phone app (made-up campus). */
    private static final String JSON = """
        {"version":"v1",
         "buildings":[{"id":1,"name":"Engineering Building","category":"Building","description":null,
                       "mapX":null,"mapY":null,"imagePath":null}],
         "floors":[{"id":1,"buildingId":1,"floorNumber":1,"floorName":"1st floor","planImagePath":null},
                   {"id":2,"buildingId":1,"floorNumber":4,"floorName":"4th floor","planImagePath":null}],
         "nodes":[
           {"id":1,"buildingId":null,"floorId":null,"name":"Gate","nodeType":"GATE","x":0,"y":0},
           {"id":2,"buildingId":null,"floorId":null,"name":"Junction","nodeType":"JUNCTION","x":120,"y":0},
           {"id":3,"buildingId":null,"floorId":null,"name":"Registrar","nodeType":"OFFICE","x":120,"y":-60},
           {"id":4,"buildingId":1,"floorId":1,"name":"EN entrance","nodeType":"BUILDING_ENTRANCE","x":250,"y":0},
           {"id":5,"buildingId":1,"floorId":1,"name":"EN stairs 1F","nodeType":"STAIRS","x":260,"y":20},
           {"id":6,"buildingId":1,"floorId":2,"name":"EN stairs 4F","nodeType":"STAIRS","x":260,"y":20},
           {"id":7,"buildingId":1,"floorId":2,"name":"EN 420","nodeType":"ROOM","x":300,"y":20},
           {"id":8,"buildingId":null,"floorId":null,"name":"Gym","nodeType":"FACILITY","x":120,"y":60}],
         "edges":[
           {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":120,"edgeType":"WALK","accessible":true},
           {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":60,"edgeType":"WALK","accessible":true},
           {"id":3,"fromNodeId":2,"toNodeId":4,"distanceM":130,"edgeType":"WALK","accessible":true},
           {"id":4,"fromNodeId":4,"toNodeId":5,"distanceM":20,"edgeType":"WALK","accessible":true},
           {"id":5,"fromNodeId":5,"toNodeId":6,"distanceM":25,"edgeType":"STAIRS","accessible":false},
           {"id":6,"fromNodeId":6,"toNodeId":7,"distanceM":40,"edgeType":"WALK","accessible":true},
           {"id":7,"fromNodeId":2,"toNodeId":8,"distanceM":60,"edgeType":"WALK","accessible":true}]}
        """;

    private static final CampusData DATA = SnapshotReader.read(JSON);

    private static Route route(int from, int to) {
        return PathFinder.findRoute(DATA.graph(), from, to, RoutePreference.ANY).orElseThrow();
    }

    private static List<String> titles(Route r) {
        return Directions.steps(r, DATA).stream().map(Directions.Step::title).toList();
    }

    @Test
    void leftTurnWhenYGoesUpOnTheMap() {
        // east, then north (y smaller) = left
        assertEquals(List.of("Start walking", "Turn left", "Arrive at Registrar"), titles(route(1, 3)));
    }

    @Test
    void rightTurnWhenYGoesDownOnTheMap() {
        assertEquals(List.of("Start walking", "Turn right", "Arrive at Gym"), titles(route(1, 8)));
    }

    @Test
    void straightEdgesAreMergedIntoOneStep() {
        Route r = route(1, 4);
        List<Directions.Step> steps = Directions.steps(r, DATA);
        assertEquals(2, steps.size());
        assertEquals("Walk ~250 m", steps.get(0).detail());
    }

    @Test
    void stairsStepNamesTheFloorAndCountsFloors() {
        Directions.Summary s = Directions.summarize(route(1, 7), DATA);
        Directions.Step stairs = s.steps().stream()
                .filter(st -> st.kind() == Directions.StepKind.STAIRS).findFirst().orElseThrow();
        assertEquals("Take the stairs to 4th floor", stairs.title());
        assertTrue(stairs.detail().startsWith("Up 3 floors"));
        assertEquals(3, s.flights());
        assertTrue(s.usesStairs());
        assertFalse(s.usesElevator());
        assertEquals("Outdoor + indoor", s.areaLabel());
        assertEquals("Engineering Building · 4th floor", s.steps().get(s.steps().size() - 1).detail());
    }

    @Test
    void stepsKnowWhereTheyHappen() {
        List<Directions.Step> steps = Directions.steps(route(1, 7), DATA);
        assertEquals(1, steps.get(0).at().id());
        Directions.Step stairs = steps.stream()
                .filter(st -> st.kind() == Directions.StepKind.STAIRS).findFirst().orElseThrow();
        assertEquals(5, stairs.at().id()); // the stairs on the floor where the climb starts
        assertEquals(7, steps.get(steps.size() - 1).at().id());
    }

    @Test
    void turnsNameALandmark() {
        String json = """
            {"version":"l","nodes":[
              {"id":1,"buildingId":null,"floorId":null,"name":"Gate","nodeType":"GATE","x":0,"y":0},
              {"id":2,"buildingId":null,"floorId":null,"name":"Gazebo","nodeType":"FACILITY","x":50,"y":0},
              {"id":3,"buildingId":null,"floorId":null,"name":"j","nodeType":"JUNCTION","x":100,"y":0},
              {"id":4,"buildingId":null,"floorId":null,"name":"Clinic","nodeType":"FACILITY","x":100,"y":100},
              {"id":5,"buildingId":null,"floorId":null,"name":"Library","nodeType":"FACILITY","x":0,"y":100}],
             "edges":[
              {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":50,"edgeType":"WALK","accessible":true},
              {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":50,"edgeType":"WALK","accessible":true},
              {"id":3,"fromNodeId":3,"toNodeId":4,"distanceM":100,"edgeType":"WALK","accessible":true},
              {"id":4,"fromNodeId":4,"toNodeId":5,"distanceM":100,"edgeType":"WALK","accessible":true}]}
            """;
        CampusData d = SnapshotReader.read(json);
        Route r = PathFinder.findRoute(d.graph(), 1, 5, RoutePreference.ANY).orElseThrow();
        assertEquals(List.of("Start walking", "Turn right after Gazebo", "Turn right at Clinic", "Arrive at Library"),
                Directions.steps(r, d).stream().map(Directions.Step::title).toList());
    }

    @Test
    void flightsInARowAreOneStep() {
        CampusData d = SnapshotReader.read("""
            {"version":"f",
             "buildings":[{"id":1,"name":"EN","category":"Building","description":null,"mapX":null,"mapY":null,
                           "imagePath":null}],
             "floors":[{"id":1,"buildingId":1,"floorNumber":1,"floorName":"1st floor","planImagePath":null},
                       {"id":2,"buildingId":1,"floorNumber":2,"floorName":"2nd floor","planImagePath":null},
                       {"id":3,"buildingId":1,"floorNumber":3,"floorName":"3rd floor","planImagePath":null}],
             "nodes":[
               {"id":1,"buildingId":1,"floorId":1,"name":"Lobby","nodeType":"BUILDING_ENTRANCE","x":0,"y":0},
               {"id":2,"buildingId":1,"floorId":1,"name":"s1","nodeType":"STAIRS","x":0,"y":50},
               {"id":3,"buildingId":1,"floorId":2,"name":"s2","nodeType":"STAIRS","x":0,"y":50},
               {"id":4,"buildingId":1,"floorId":3,"name":"s3","nodeType":"STAIRS","x":0,"y":50},
               {"id":5,"buildingId":1,"floorId":3,"name":"EN 310","nodeType":"ROOM","x":0,"y":100}],
             "edges":[
               {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":50,"edgeType":"WALK","accessible":true},
               {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":9,"edgeType":"STAIRS","accessible":false},
               {"id":3,"fromNodeId":3,"toNodeId":4,"distanceM":9,"edgeType":"STAIRS","accessible":false},
               {"id":4,"fromNodeId":4,"toNodeId":5,"distanceM":50,"edgeType":"WALK","accessible":true}]}
            """);
        Route r = PathFinder.findRoute(d.graph(), 1, 5, RoutePreference.ANY).orElseThrow();
        List<Directions.Step> steps = Directions.steps(r, d);
        assertEquals(List.of("Start walking", "Take the stairs to 3rd floor", "Exit and continue walking",
                "Arrive at EN 310"), steps.stream().map(Directions.Step::title).toList());
        assertTrue(steps.get(1).detail().startsWith("Up 2 floors"), steps.get(1).detail());
    }

    @Test
    void minutesAreAtLeastOne() {
        assertEquals(1, Directions.minutes(5));
        assertEquals(4, Directions.minutes(310));
    }

    @Test
    void readsBuildingsAndFloors() {
        assertEquals("Engineering Building", DATA.building(1).orElseThrow().name());
        assertEquals(List.of("1F", "4F"), DATA.floorsOf(1).stream().map(Floor::shortLabel).toList());
        assertEquals(8, DATA.graph().nodeCount());
        assertEquals(7, DATA.graph().edges().size());
    }

    @Test
    void shortKinksAreNotStepsOfTheirOwn() {
        // 0,0 -> 100,0 -> 105,8 (a 9 m kink) -> 205,8 -> 205,60 : one left/right turn, not three
        String json = """
            {"version":"k","nodes":[
              {"id":1,"buildingId":null,"floorId":null,"name":"A","nodeType":"GATE","x":0,"y":0},
              {"id":2,"buildingId":null,"floorId":null,"name":"j1","nodeType":"JUNCTION","x":100,"y":0},
              {"id":3,"buildingId":null,"floorId":null,"name":"j2","nodeType":"JUNCTION","x":105,"y":8},
              {"id":4,"buildingId":null,"floorId":null,"name":"j3","nodeType":"JUNCTION","x":205,"y":8},
              {"id":5,"buildingId":null,"floorId":null,"name":"B","nodeType":"OFFICE","x":205,"y":60},
              {"id":6,"buildingId":null,"floorId":null,"name":"C","nodeType":"OFFICE","x":212,"y":60}],
             "edges":[
              {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":100,"edgeType":"WALK","accessible":true},
              {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":9,"edgeType":"WALK","accessible":true},
              {"id":3,"fromNodeId":3,"toNodeId":4,"distanceM":100,"edgeType":"WALK","accessible":true},
              {"id":4,"fromNodeId":4,"toNodeId":5,"distanceM":52,"edgeType":"WALK","accessible":true},
              {"id":5,"fromNodeId":5,"toNodeId":6,"distanceM":7,"edgeType":"WALK","accessible":true}]}
            """;
        CampusData d = SnapshotReader.read(json);
        Route r = PathFinder.findRoute(d.graph(), 1, 6, RoutePreference.ANY).orElseThrow();
        List<Directions.Step> steps = Directions.steps(r, d);
        assertEquals(List.of("Start walking", "Turn right", "Arrive at C"),
                steps.stream().map(Directions.Step::title).toList());
        assertTrue(steps.get(1).detail().startsWith("Walk ~59 m"));
    }
}
