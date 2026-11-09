package com.uenav.mobile;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Asks the device whether it is in dark mode, for the "Same as phone" theme. JavaFX reports this from version 22
 * (Platform.getPreferences().getColorScheme()); it is read by reflection so the app still runs on JavaFX 21.
 * Empty means the device did not say, and the app then uses the light look.
 */
final class SystemTheme {

    private SystemTheme() { }

    /** true = dark, false = light, empty = this device or JavaFX version does not report it. */
    static Optional<Boolean> phoneIsDark() {
        try {
            Method prefs = javafx.application.Platform.class.getMethod("getPreferences");
            Object p = prefs.invoke(null);
            if (p instanceof java.util.Map<?, ?> m && m.isEmpty()) {
                return Optional.empty(); // the platform reports nothing (its LIGHT would only be a default)
            }
            Object scheme = p.getClass().getMethod("getColorScheme").invoke(p);
            return scheme == null ? Optional.empty() : Optional.of("DARK".equals(scheme.toString()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notAvailable) {
            return Optional.empty();
        }
    }

    /** Whether the app should look dark with the chosen theme. */
    static boolean dark(Settings.Theme theme) {
        return switch (theme) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> phoneIsDark().orElse(false);
        };
    }
}
