package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uenav.core.CampusData;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Checks on the UE Caloocan map built into the app (traced from the campus map picture). */
class CampusMapTest {

    private static final int HILLS_GATE = 1;
    private static final int SAMSON_GATE = 2;

    @Test
    void everyPlaceCanBeReachedFromBothGates() throws IOException {
        CampusData d = CampusStore.bundled();
        for (Node place : d.places()) {
            for (int gate : new int[] {HILLS_GATE, SAMSON_GATE}) {
                if (place.id() == gate) {
                    continue;
                }
                assertTrue(PathFinder.findRoute(d.graph(), gate, place.id(), RoutePreference.ANY).isPresent(),
                        "no route from gate " + gate + " to " + place.name());
            }
        }
    }

    @Test
    void placesAndShapesShareTheMapFrame() throws IOException {
        CampusData d = CampusStore.bundled();
        for (Node n : d.graph().nodes()) {
            assertTrue(n.x() >= 0 && n.x() <= 1560 && n.y() >= 0 && n.y() <= 924, n.name() + " is off the picture");
        }
        CampusArt art = CampusArt.load("/maps/campus-3d.json");
        assertTrue(art != null, "campus-3d.json is missing or unreadable");
        assertTrue(art.buildingCount() >= 15, "buildings in the 3D map: " + art.buildingCount());
    }

    @Test
    void distancesLookLikeTheRealCampus() throws IOException {
        // The campus is about 300 m long, so gate to gate across it should be a few hundred metres, not kilometres.
        CampusData d = CampusStore.bundled();
        Route r = PathFinder.findRoute(d.graph(), HILLS_GATE, 35, RoutePreference.ANY).orElseThrow();
        assertTrue(r.totalDistanceM() > 200 && r.totalDistanceM() < 500, "Hills Gate to Engineering: " + r.totalDistanceM());
    }
}
