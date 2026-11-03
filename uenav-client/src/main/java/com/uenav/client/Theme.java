package com.uenav.client;

import java.net.URL;

final class Theme {

    private Theme() { }

    /** Address of the stylesheet, or null if it is missing (the app still works, just unstyled). */
    static String css() {
        URL url = Theme.class.getResource("/uenav.css");
        return url == null ? null : url.toExternalForm();
    }
}
