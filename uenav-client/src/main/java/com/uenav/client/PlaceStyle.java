package com.uenav.client;

import java.util.Locale;

/** How a kind of place looks in lists: a readable type name, an icon and a colour tone (same as the phone app). */
record PlaceStyle(String label, String icon, String tone) {

    /** Tones match CSS classes nv-tone-red, nv-tone-orange, nv-tone-green, nv-tone-blue, nv-tone-grey. */
    static PlaceStyle of(String nodeType) {
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
            default -> new PlaceStyle(t.isEmpty() ? "Place" : capitalise(t), "pin", "grey");
        };
    }

    private static String capitalise(String upper) {
        String s = upper.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
