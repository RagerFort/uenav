package com.uenav.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Destination search with autocomplete. Accepts room codes ("EN 420", "en420"), plain names ("registrar"),
 * building names ("engineering 4th"), and everyday words people type ("cr", "toilet", "pay", "food").
 * Every word typed must match something; better matches (the name starts with what was typed) come first.
 */
public final class PlaceSearch {

    /** Words people type, and the words in place names or categories they should also find. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("cr", List.of("restroom", "comfort", "toilet")),
            Map.entry("comfort", List.of("restroom", "cr")),
            Map.entry("toilet", List.of("restroom", "cr", "comfort")),
            Map.entry("bathroom", List.of("restroom", "cr", "comfort", "toilet")),
            Map.entry("washroom", List.of("restroom", "cr", "comfort", "toilet")),
            Map.entry("restroom", List.of("cr", "comfort", "toilet")),
            Map.entry("food", List.of("canteen", "cafeteria")),
            Map.entry("eat", List.of("canteen", "cafeteria", "food")),
            Map.entry("cafeteria", List.of("canteen", "food")),
            Map.entry("canteen", List.of("cafeteria", "food")),
            Map.entry("nurse", List.of("clinic", "infirmary")),
            Map.entry("medical", List.of("clinic", "infirmary")),
            Map.entry("infirmary", List.of("clinic")),
            Map.entry("books", List.of("library")),
            Map.entry("pay", List.of("cashier", "comptroller")),
            Map.entry("payment", List.of("cashier", "comptroller")),
            Map.entry("tuition", List.of("cashier", "comptroller")),
            Map.entry("enroll", List.of("registrar", "admissions")),
            Map.entry("enrollment", List.of("registrar", "admissions")),
            Map.entry("records", List.of("registrar")),
            Map.entry("transcript", List.of("registrar")),
            Map.entry("tor", List.of("registrar")),
            Map.entry("counseling", List.of("guidance")),
            Map.entry("counselling", List.of("guidance")),
            Map.entry("exit", List.of("gate")),
            Map.entry("entrance", List.of("gate")),
            Map.entry("guard", List.of("security")),
            Map.entry("room", List.of("classroom")),
            Map.entry("lab", List.of("laboratory")));

    private PlaceSearch() { }

    /** Places matching the query, best first. An empty query gives every place, A to Z. */
    public static List<Node> search(CampusData data, String query) {
        String q = normalize(query);
        List<Node> places = new ArrayList<>(data.places());
        if (q.isEmpty()) {
            places.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return places;
        }
        record Hit(Node node, int score) { }
        List<Hit> hits = new ArrayList<>();
        for (Node n : places) {
            int s = score(n, data, q);
            if (s > 0) {
                hits.add(new Hit(n, s));
            }
        }
        hits.sort(Comparator.comparingInt((Hit h) -> -h.score())
                .thenComparingInt(h -> h.node().name().length())
                .thenComparing(h -> h.node().name(), String.CASE_INSENSITIVE_ORDER));
        List<Node> out = new ArrayList<>();
        hits.forEach(h -> out.add(h.node()));
        return out;
    }

    /** 0 = no match; higher is better. */
    static int score(Node n, CampusData data, String q) {
        String name = normalize(n.name());
        String compactName = name.replace(" ", "");
        String compactQ = q.replace(" ", "");
        if (name.equals(q) || compactName.equals(compactQ)) {
            return 1000;
        }
        if (name.startsWith(q) || compactName.startsWith(compactQ)) {
            return 800;
        }
        String[] words = q.split(" ");
        String[] nameWords = name.split(" ");
        if (allMatch(words, nameWords)) {
            return 600;
        }
        if (compactQ.length() >= 3 && compactName.contains(compactQ)) {
            return 500; // room codes typed without the space: "en420"
        }
        String context = normalize(contextOf(n, data));
        String[] all = (name + " " + context).split(" ");
        if (allMatch(words, all)) {
            return 300;
        }
        if (allMatchWithSynonyms(words, all)) {
            return 200;
        }
        return 0;
    }

    /** Building, floor, type and category words, so "engineering", "4th floor" and "restroom" find a place. */
    private static String contextOf(Node n, CampusData data) {
        StringBuilder sb = new StringBuilder(n.nodeType() == null ? "" : n.nodeType().replace('_', ' '));
        data.building(n.buildingId()).ifPresent(b -> sb.append(' ').append(b.name()));
        data.floor(n.floorId()).ifPresent(f -> sb.append(' ').append(f.floorName()).append(' ').append(f.shortLabel()));
        for (PlaceCategory c : PlaceCategory.values()) {
            if (c.matches(n)) {
                sb.append(' ').append(c.label());
            }
        }
        return sb.toString();
    }

    private static boolean allMatch(String[] words, String[] hay) {
        for (String w : words) {
            if (!anyStartsWith(hay, w)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allMatchWithSynonyms(String[] words, String[] hay) {
        for (String w : words) {
            boolean found = anyStartsWith(hay, w);
            for (String alt : SYNONYMS.getOrDefault(w, List.of())) {
                found |= anyStartsWith(hay, alt);
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyStartsWith(String[] hay, String word) {
        for (String h : hay) {
            if (h.startsWith(word)) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, apostrophes dropped ("Registrar's" -> "registrars"), other symbols turned into spaces. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace("'", "").replace("’", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
