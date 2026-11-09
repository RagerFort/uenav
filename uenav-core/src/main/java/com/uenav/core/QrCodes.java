package com.uenav.core;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text inside the "You are here" QR codes posted at entrances, stairwells and hallway corners. A code holds only
 * a node id, such as "UENAV:12", so scanning one sets the starting point with no internet. The same text is printed
 * under the QR code so it can be typed in when a camera is not available.
 */
public final class QrCodes {

    public static final String PREFIX = "UENAV:";

    private static final Pattern CODE = Pattern.compile(
            "^(?:UENAV\\s*[:\\-]?\\s*|UENAV://NODE/|HTTPS?://\\S*[?&]NODE=)?(\\d{1,9})$");

    private QrCodes() { }

    /** The text to put in the QR code for a node, e.g. "UENAV:12". */
    public static String text(int nodeId) {
        if (nodeId <= 0) {
            throw new IllegalArgumentException("Node ids are positive");
        }
        return PREFIX + nodeId;
    }

    /**
     * The node id in a scanned or typed code. Accepts "UENAV:12", "uenav 12", "uenav://node/12", a link ending in
     * "?node=12", or just "12". Spaces around the text are ignored.
     */
    public static OptionalInt parse(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String t = text.trim().toUpperCase(Locale.ROOT);
        Matcher m = CODE.matcher(t);
        if (!m.matches()) {
            return OptionalInt.empty();
        }
        int id = Integer.parseInt(m.group(1));
        return id > 0 ? OptionalInt.of(id) : OptionalInt.empty();
    }
}
