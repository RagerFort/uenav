package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.uenav.core.CampusData;
import com.uenav.core.Directions;
import com.uenav.core.EdgeType;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePlayerTest {

    @Test
    void eachStepStartsWhereItHappensOnTheRoute() throws IOException {
        CampusData d = CampusStore.bundled();
        Route r = PathFinder.findRoute(d.graph(), 1, 42, RoutePreference.ANY).orElseThrow();
        List<Directions.Step> steps = Directions.steps(r, d);
        int[] at = RoutePlayer.stepIndexes(r.nodes(), steps);
        assertEquals(steps.size(), at.length);
        assertEquals(0, at[0]);
        assertEquals(r.nodes().size() - 1, at[at.length - 1]);
        for (int i = 0; i + 1 < at.length; i++) {
            assertEquals(steps.get(i).at().id(), r.nodes().get(at[i]).id());
            assertEquals(true, at[i] <= at[i + 1], "steps in walking order");
        }
    }

    @Test
    void transferTagsSayWhereTheWalkGoes() throws IOException {
        CampusData d = CampusStore.bundled();
        Route r = PathFinder.findRoute(d.graph(), 1, 42, RoutePreference.ANY).orElseThrow();
        List<Node> n = r.nodes();
        // campus, TYK ground floor (5), elevator to TYK 4F (8), bridge to EN 3F (4), stairs to EN 4F (2)
        Node gf = first(n, 5);
        Node tyk4 = first(n, 8);
        Node en3 = first(n, 4);
        Node en4 = first(n, 2);
        assertEquals("Into Tan Yan Kee", RoutePlayer.transferText(d, n.get(0), gf, EdgeType.WALK, null, 5));
        assertEquals("Elevator up to 4F", RoutePlayer.transferText(d, gf, tyk4, EdgeType.ELEVATOR, 5, 8));
        assertEquals("Bridge to Engineering 3F", RoutePlayer.transferText(d, tyk4, en3, EdgeType.WALK, 8, 4));
        assertEquals("Stairs up to 4F", RoutePlayer.transferText(d, en3, en4, EdgeType.STAIRS, 4, 2));
        assertEquals("Stairs down to 3F", RoutePlayer.transferText(d, en4, en3, EdgeType.STAIRS, 2, 4));
        assertEquals("Out to campus", RoutePlayer.transferText(d, gf, n.get(0), EdgeType.WALK, 5, null));
    }

    @Test
    void easingStartsAndEndsGently() {
        assertEquals(0, RoutePlayer.easeInOut(0), 1e-9);
        assertEquals(0.5, RoutePlayer.easeInOut(0.5), 1e-9);
        assertEquals(1, RoutePlayer.easeInOut(1), 1e-9);
        assertEquals(1, RoutePlayer.easeOutBack(1), 1e-9);
        assertArrayEquals(new double[] {0, 1}, new double[] {RoutePlayer.easeOut(0), RoutePlayer.easeOut(1)}, 1e-9);
    }

    private static Node first(List<Node> nodes, int floorId) {
        return nodes.stream().filter(x -> x.floorId() != null && x.floorId() == floorId).findFirst().orElseThrow();
    }
}
