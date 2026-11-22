package com.uenav.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapViewTest {

    @Test
    void distanceToAWalkwayOnScreen() {
        // a walkway from (0, 0) to (100, 0): beside it, past its end, and on it
        assertEquals(5, MapView.distanceToSegment(50, 5, 0, 0, 100, 0), 1e-9);
        assertEquals(5, MapView.distanceToSegment(103, 4, 0, 0, 100, 0), 1e-9);
        assertEquals(0, MapView.distanceToSegment(30, 0, 0, 0, 100, 0), 1e-9);
        assertEquals(5, MapView.distanceToSegment(3, 4, 0, 0, 0, 0), 1e-9); // a walkway of no length
    }
}
