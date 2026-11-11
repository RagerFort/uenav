package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recent and favourite destinations, kept on the phone (places.json). Only place ids are stored; ids that are no
 * longer on the map are simply skipped by the screens.
 */
public final class PlacesMemory {

    static final int MAX_RECENT = 6;

    private final TextStore store;
    private final List<Integer> recent = new ArrayList<>();
    private final Set<Integer> favourites = new LinkedHashSet<>();

    public PlacesMemory(TextStore store) {
        this.store = store;
        load();
    }

    /** Most recent first. */
    public synchronized List<Integer> recent() {
        return List.copyOf(recent);
    }

    /** In the order they were starred. */
    public synchronized List<Integer> favourites() {
        return List.copyOf(favourites);
    }

    public synchronized boolean isFavourite(int id) {
        return favourites.contains(id);
    }

    /** Remembers a destination the visitor asked for directions to. */
    public synchronized void visited(int id) {
        recent.remove(Integer.valueOf(id));
        recent.add(0, id);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    /** Stars or un-stars a place. @return true if it is now a favourite */
    public synchronized boolean toggleFavourite(int id) {
        boolean now;
        if (favourites.remove(id)) {
            now = false;
        } else {
            favourites.add(id);
            now = true;
        }
        save();
        return now;
    }

    private void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("recent", new ArrayList<>(recent));
        root.put("favourites", new ArrayList<>(favourites));
        try {
            store.save(MiniJson.write(root));
        } catch (RuntimeException diskProblem) {
            // still remembered until the app closes
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        String text;
        try {
            text = store.load();
        } catch (RuntimeException unreadable) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Object root = MiniJson.parse(text);
            if (!(root instanceof Map)) {
                return;
            }
            Map<String, Object> m = (Map<String, Object>) root;
            if (m.get("recent") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Double d && recent.size() < MAX_RECENT && !recent.contains(d.intValue())) {
                        recent.add(d.intValue());
                    }
                }
            }
            if (m.get("favourites") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Double d) {
                        favourites.add(d.intValue());
                    }
                }
            }
        } catch (IllegalArgumentException damagedFile) {
            recent.clear();
            favourites.clear();
        }
    }
}
