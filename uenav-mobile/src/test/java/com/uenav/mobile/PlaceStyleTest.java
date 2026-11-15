package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.uenav.core.CampusData;
import com.uenav.core.Route;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceStyleTest {

    @Test
    void namesAndTones() {
        assertEquals(new PlaceStyle("Office", "office", "orange"), PlaceStyle.of("OFFICE"));
        assertEquals("Building", PlaceStyle.of("BUILDING_ENTRANCE").label());
        assertEquals("Gate", PlaceStyle.of("GATE").label());
        assertEquals("Snack bar", PlaceStyle.of("SNACK_BAR").label());
    }

    @Test
    void subtitleNamesBuildingAndFloor() throws IOException {
        CampusData d = CampusStore.bundled();
        assertEquals("Room · College of Engineering Building · 4th floor",
                PlaceStyle.subtitle(d.graph().node(42).orElseThrow(), d));
        assertEquals("Office \u00B7 Administration Building", PlaceStyle.subtitle(d.graph().node(22).orElseThrow(), d));
    }

    @Test
    void listRowsUseTheShortSubtitle() throws IOException {
        CampusData d = CampusStore.bundled();
        assertEquals("Room \u00B7 Engineering \u00B7 4F", PlaceStyle.shortSubtitle(d.graph().node(42).orElseThrow(), d));
        assertEquals("Office \u00B7 Administration", PlaceStyle.shortSubtitle(d.graph().node(22).orElseThrow(), d));
    }

    @Test
    void mapLevelsFollowTheRoute() throws IOException {
        CampusData d = CampusStore.bundled();
        Route r = com.uenav.core.PathFinder.findRoute(d.graph(), 1, 42, com.uenav.core.RoutePreference.ANY)
                .orElseThrow();
        // campus, TYK ground floor (5), up the elevator to TYK 4F (8), over the bridge to EN 3F (4), stairs to EN 4F (2);
        // the TYK floors the elevator only passes get no tab
        assertEquals(Arrays.asList(null, 5, 8, 4, 2), MapView.levelsOf(r, d));
        Route outdoor = com.uenav.core.PathFinder.findRoute(d.graph(), 1, 22, com.uenav.core.RoutePreference.ANY)
                .orElseThrow();
        assertEquals(Arrays.asList((Integer) null), MapView.levelsOf(outdoor, d));
    }

    @Test
    void shortMapLabels() {
        assertEquals("Engineering", MapView.shortName("College of Engineering Building"));
        assertEquals("EN 420", MapView.shortName("EN 420 (4F)"));
        assertEquals("Registrar's", MapView.shortName("Registrar's Office"));
        assertEquals("Tan Yan Kee", MapView.shortName("Tan Yan Kee Academic Building"));
        assertEquals("Old Academic", MapView.shortName("Old Academic Building"));
        assertEquals(List.of(), List.of());
    }
}
