package com.uenav.mobile;

import com.uenav.core.RoutePreference;

/** Where the visitor screens record searches and feedback. The phone keeps them in a UsageQueue. */
public interface UsageLog {

    void route(int fromNodeId, int toNodeId, RoutePreference pref);

    /** @param feedbackType HELPFUL or WRONG_ROUTE */
    void feedback(int fromNodeId, int toNodeId, RoutePreference pref, String feedbackType);

    /** A log that writes entries into a queue and then asks for them to be sent. */
    static UsageLog of(UsageQueue queue, Runnable afterAdd) {
        return new UsageLog() {
            @Override
            public void route(int from, int to, RoutePreference pref) {
                queue.addRoute(from, to, pref);
                afterAdd.run();
            }

            @Override
            public void feedback(int from, int to, RoutePreference pref, String type) {
                queue.addFeedback(from, to, pref, type);
                afterAdd.run();
            }
        };
    }
}
