package com.uenav.client;

/** Plain-English lines for usage log rows. */
final class UsageText {

    private UsageText() { }

    /** e.g. "Route: Hills Gate \u2192 Registrar's Office" or "Feedback: wrong route \u00B7 Gym". */
    static String describe(AdminModels.UsageEvent e) {
        if ("FEEDBACK".equals(e.eventType())) {
            return "Feedback: " + feedbackWords(e.feedbackType()) + " \u00B7 " + e.toName();
        }
        String text = "Route: " + e.fromName() + " \u2192 " + e.toName();
        if ("STAIRS".equals(e.routePref())) {
            text += " (stairs)";
        } else if ("ELEVATOR".equals(e.routePref())) {
            text += " (elevator)";
        }
        return text;
    }

    static String feedbackWords(String feedbackType) {
        if ("WRONG_ROUTE".equals(feedbackType)) {
            return "wrong route";
        }
        if ("HELPFUL".equals(feedbackType)) {
            return "helpful";
        }
        return feedbackType == null ? "" : feedbackType.toLowerCase().replace('_', ' ');
    }
}
