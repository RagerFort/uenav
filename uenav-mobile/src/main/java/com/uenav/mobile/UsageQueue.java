package com.uenav.mobile;

import com.uenav.core.MiniJson;
import com.uenav.core.RoutePreference;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The searches and feedback saved on the phone until they can be sent. Everything is kept in a file, so
 * nothing is lost when the app closes or the phone has no internet. Keeps at most 1000 entries; the oldest
 * are dropped first if the phone stays offline for a very long time.
 */
public final class UsageQueue {

    static final int MAX_ENTRIES = 1000;

    private final TextStore store;
    private final Clock clock;
    private final Supplier<String> ids;
    private final List<UsageEntry> entries = new ArrayList<>();
    private boolean saveFailed;

    public UsageQueue(TextStore store) {
        this(store, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    UsageQueue(TextStore store, Clock clock, Supplier<String> ids) {
        this.store = store;
        this.clock = clock;
        this.ids = ids;
        load();
    }

    public synchronized UsageEntry addRoute(int fromNodeId, int toNodeId, RoutePreference pref) {
        return add("ROUTE", fromNodeId, toNodeId, pref, null);
    }

    /** @param feedbackType HELPFUL or WRONG_ROUTE */
    public synchronized UsageEntry addFeedback(int fromNodeId, int toNodeId, RoutePreference pref, String feedbackType) {
        if (!"HELPFUL".equals(feedbackType) && !"WRONG_ROUTE".equals(feedbackType)) {
            throw new IllegalArgumentException("feedbackType must be HELPFUL or WRONG_ROUTE");
        }
        return add("FEEDBACK", fromNodeId, toNodeId, pref, feedbackType);
    }

    private UsageEntry add(String type, int from, int to, RoutePreference pref, String feedback) {
        UsageEntry e = new UsageEntry(ids.get(), clock.instant().truncatedTo(ChronoUnit.SECONDS).toString(),
                type, from, to, pref.name(), feedback);
        entries.add(e);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        persist();
        return e;
    }

    /** The oldest entries first, at most max of them. */
    public synchronized List<UsageEntry> pending(int max) {
        return new ArrayList<>(entries.subList(0, Math.min(max, entries.size())));
    }

    /** Forgets entries that have been sent. */
    public synchronized void remove(Collection<UsageEntry> sent) {
        Set<String> gone = new HashSet<>();
        for (UsageEntry e : sent) {
            gone.add(e.clientLogId());
        }
        entries.removeIf(e -> gone.contains(e.clientLogId()));
        persist();
    }

    public synchronized int size() {
        return entries.size();
    }

    /** True if the last attempt to save to the phone's storage failed (entries are still kept in memory). */
    public synchronized boolean saveFailed() {
        return saveFailed;
    }

    private void persist() {
        List<Object> list = new ArrayList<>();
        for (UsageEntry e : entries) {
            list.add(e.toMap());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", list);
        try {
            store.save(MiniJson.write(root));
            saveFailed = false;
        } catch (RuntimeException e) {
            saveFailed = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        String text = store.load();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Object root = MiniJson.parse(text);
            if (!(root instanceof Map)) {
                return;
            }
            Object list = ((Map<String, Object>) root).get("entries");
            if (!(list instanceof List)) {
                return;
            }
            for (Object o : (List<Object>) list) {
                try {
                    if (o instanceof Map) {
                        entries.add(UsageEntry.fromMap((Map<String, Object>) o));
                    }
                } catch (IllegalArgumentException skipBadEntry) {
                    // one damaged entry must not lose the others
                }
            }
        } catch (IllegalArgumentException damagedFile) {
            entries.clear();
        }
    }
}
