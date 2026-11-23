package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditHistoryTest {

    @Test
    void undoesTheLastChangeFirst() throws ApiException {
        EditHistory h = new EditHistory();
        List<String> done = new ArrayList<>();
        h.push("move of A", 1, x -> done.add("A"));
        h.push("move of B", 2, x -> done.add("B"));
        assertEquals("move of B", h.peek().label());
        h.pop().step().undo(h);
        h.pop().step().undo(h);
        assertEquals(List.of("B", "A"), done);
        assertTrue(h.isEmpty());
        assertNull(h.pop());
    }

    @Test
    void keepsOnlyTheLatestChanges() {
        EditHistory h = new EditHistory();
        for (int i = 0; i < EditHistory.LIMIT + 10; i++) {
            h.push("change " + i, null, x -> { });
        }
        int n = 0;
        while (h.pop() != null) {
            n++;
        }
        assertEquals(EditHistory.LIMIT, n);
    }

    @Test
    void placesThatCameBackKeepTheirNewIds() {
        EditHistory h = new EditHistory();
        assertEquals(7, h.node(7));
        h.nodeRecreated(7, 40);
        h.nodeRecreated(40, 55); // deleted and brought back twice
        assertEquals(55, h.node(7));
        h.edgeRecreated(3, 90);
        assertEquals(90, h.edge(3));
        AdminModels.EdgeForm f = h.ends(new AdminModels.EdgeForm(7, 9, 12.5, "WALK", true));
        assertEquals(new AdminModels.EdgeForm(55, 9, 12.5, "WALK", true), f);
    }
}
