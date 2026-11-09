package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlacesMemoryTest {

    private final MemoryStore store = new MemoryStore();

    @Test
    void recentIsMostRecentFirstWithoutRepeatsAndLimited() {
        PlacesMemory m = new PlacesMemory(store);
        for (int id = 1; id <= 8; id++) {
            m.visited(id);
        }
        m.visited(5);
        assertEquals(List.of(5, 8, 7, 6, 4, 3), m.recent());
        assertEquals(List.of(5, 8, 7, 6, 4, 3), new PlacesMemory(store).recent());
    }

    @Test
    void favouritesToggleAndAreRemembered() {
        PlacesMemory m = new PlacesMemory(store);
        assertTrue(m.toggleFavourite(22));
        assertTrue(m.toggleFavourite(20));
        assertFalse(m.toggleFavourite(22));
        assertEquals(List.of(20), new PlacesMemory(store).favourites());
        assertTrue(new PlacesMemory(store).isFavourite(20));
    }

    @Test
    void damagedOrUnsavableFilesAreHarmless() {
        store.text = "{\"recent\":\"oops\",\"favourites\":[1,\"x\",2]}";
        PlacesMemory m = new PlacesMemory(store);
        assertEquals(List.of(), m.recent());
        assertEquals(List.of(1, 2), m.favourites());
        store.text = "][";
        assertEquals(List.of(), new PlacesMemory(store).favourites());
        store.failSaves = true;
        PlacesMemory n = new PlacesMemory(store);
        n.visited(3);
        assertEquals(List.of(3), n.recent());
    }
}
