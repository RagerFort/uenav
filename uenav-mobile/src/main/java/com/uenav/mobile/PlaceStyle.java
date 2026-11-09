package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Node;
import java.util.Locale;

/** How a kind of place looks in lists: a readable type name, an icon and a colour tone. */
public record PlaceStyle(String label, String icon, String tone) {

    /** Tones match CSS classes nv-tone-red, nv-tone-orange, nv-tone-green, nv-tone-blue, nv-tone-grey. */
    public static PlaceStyle of(String nodeType) {
        String t = nodeType == null ? "" : nodeType.toUpperCase(Locale.ROOT);
        return switch (t) {
            case "BUILDING", "BUILDING_ENTRANCE" -> new PlaceStyle("Building", "building", "red");
            case "OFFICE" -> new PlaceStyle("Office", "office", "orange");
            case "ROOM", "CLASSROOM", "LAB" -> new PlaceStyle(t.equals("LAB") ? "Lab" : "Room", "room", "red");
            case "GATE" -> new PlaceStyle("Gate", "gate", "blue");
            case "EXIT" -> new PlaceStyle("Exit", "run", "blue");
            case "RESTROOM" -> new PlaceStyle("Restroom", "restroom", "blue");
            case "CANTEEN", "FOOD" -> new PlaceStyle("Canteen", "food", "orange");
            case "CLINIC" -> new PlaceStyle("Clinic", "clinic", "red");
            case "FACILITY", "GYM", "LIBRARY" -> new PlaceStyle(capitalise(t), "facility", "green");
            case "STAIRS" -> new PlaceStyle("Stairs", "stairs", "green");
            case "ELEVATOR" -> new PlaceStyle("Elevator", "elevator", "green");
            case "JUNCTION" -> new PlaceStyle("Junction", "dot", "grey");
            default -> new PlaceStyle(t.isEmpty() ? "Place" : capitalise(t), "pin", "grey");
        };
    }

    /** Second line under a place name, such as "Room · Engineering Building · 4th floor". */
    public static String subtitle(Node n, CampusData data) {
        StringBuilder sb = new StringBuilder(of(n.nodeType()).label());
        data.building(n.buildingId()).ifPresent(b -> {
            if (!"BUILDING_ENTRANCE".equals(n.nodeType()) && !b.name().equalsIgnoreCase(n.name())) {
                sb.append(" · ").append(b.name());
            }
        });
        data.floor(n.floorId()).ifPresent(f -> sb.append(" · ").append(f.floorName()));
        return sb.toString();
    }

    private static String capitalise(String upper) {
        String s = upper.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
