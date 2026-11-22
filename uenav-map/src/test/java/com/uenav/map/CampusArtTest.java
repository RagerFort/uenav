package com.uenav.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CampusArtTest {

    @Test
    void theCampusShapesLoad() {
        CampusArt art = CampusArt.load("/maps/campus-3d.json");
        assertNotNull(art, "campus-3d.json is missing or unreadable");
        // the buildings (Engineering is 3 blocks) plus the security office and smaller blocks
        assertTrue(art.buildingCount() >= 14, "buildings in the 3D map: " + art.buildingCount());
        double[] b = art.bounds();
        assertTrue(b[0] >= 0 && b[2] <= 1560 && b[1] >= 0 && b[3] <= 924, "shapes off the map frame");
    }

    @Test
    void mapLabelsAreShort() {
        assertEquals("Engineering", MapView.shortName("College of Engineering Building"));
        assertEquals("Tan Yan Kee", MapView.shortName("Tan Yan Kee Academic Building"));
    }
}
