package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceSearchTest {

    private static final String JSON = """
        {"version":"s",
         "buildings":[{"id":1,"name":"College of Engineering Building","category":"Building","description":null,
                       "mapX":null,"mapY":null,"imagePath":null}],
         "floors":[{"id":1,"buildingId":1,"floorNumber":1,"floorName":"1st floor","planImagePath":null},
                   {"id":2,"buildingId":1,"floorNumber":4,"floorName":"4th floor","planImagePath":null}],
         "nodes":[
           {"id":1,"buildingId":null,"floorId":null,"name":"Registrar's Office","nodeType":"OFFICE","x":0,"y":0},
           {"id":2,"buildingId":null,"floorId":null,"name":"Cashier's Office","nodeType":"OFFICE","x":0,"y":0},
           {"id":3,"buildingId":1,"floorId":2,"name":"EN 420 (4F)","nodeType":"ROOM","x":0,"y":0},
           {"id":4,"buildingId":1,"floorId":2,"name":"EN 421 (4F)","nodeType":"ROOM","x":0,"y":0},
           {"id":5,"buildingId":1,"floorId":1,"name":"EN restroom (1F)","nodeType":"RESTROOM","x":0,"y":0},
           {"id":6,"buildingId":null,"floorId":null,"name":"Main Canteen","nodeType":"CANTEEN","x":0,"y":0},
           {"id":7,"buildingId":null,"floorId":null,"name":"Samson Road Gate","nodeType":"GATE","x":0,"y":0},
           {"id":8,"buildingId":null,"floorId":null,"name":"Hall junction","nodeType":"JUNCTION","x":0,"y":0},
           {"id":9,"buildingId":null,"floorId":null,"name":"Registration Desk","nodeType":"OFFICE","x":0,"y":0}],
         "edges":[]}
        """;

    private static final CampusData DATA = SnapshotReader.read(JSON);

    private static List<String> names(String q) {
        return PlaceSearch.search(DATA, q).stream().map(Node::name).toList();
    }

    @Test
    void plainWordsFindOffices() {
        assertEquals("Registrar's Office", names("registrar").get(0));
        assertEquals("Registrar's Office", names("Registrars").get(0));
        assertEquals(List.of("Cashier's Office"), names("cashier"));
    }

    @Test
    void roomCodesWorkWithOrWithoutTheSpace() {
        assertEquals("EN 420 (4F)", names("EN 420").get(0));
        assertEquals("EN 420 (4F)", names("en420").get(0));
        assertEquals(List.of("EN 420 (4F)", "EN 421 (4F)"), names("en 42"));
    }

    @Test
    void buildingAndFloorWordsNarrowTheList() {
        List<String> found = names("engineering 4th");
        assertEquals(List.of("EN 420 (4F)", "EN 421 (4F)"), found);
    }

    @Test
    void everydayWordsFindTheRightPlaces() {
        assertEquals(List.of("EN restroom (1F)"), names("cr"));
        assertEquals(List.of("EN restroom (1F)"), names("toilet"));
        assertEquals(List.of("Main Canteen"), names("food"));
        assertEquals(List.of("Cashier's Office"), names("tuition"));
        assertEquals(List.of("Samson Road Gate"), names("exit"));
    }

    @Test
    void betterMatchesComeFirst() {
        // name words first (shorter names before longer), then places that only match by type
        assertEquals(List.of("Cashier's Office", "Registrar's Office", "Registration Desk"), names("office"));
    }

    @Test
    void waypointsAreNeverOffered() {
        assertFalse(names("junction").contains("Hall junction"));
        assertTrue(names("nothing like this").isEmpty());
    }

    @Test
    void emptyQueryListsEverythingAToZAndSuggestLimits() {
        List<String> all = names("  ");
        assertEquals(8, all.size());
        assertEquals("Cashier's Office", all.get(0));
        assertEquals(2, PlaceSearch.suggest(DATA, "en", 2).size());
    }
}
