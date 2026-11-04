package com.uenav.server.usagelog;

import java.util.List;

/** Minimal CSV writer. Text that starts like a spreadsheet formula is neutralised. */
final class Csv {

    private Csv() { }

    static String field(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s; // stops Excel running user-supplied text as a formula
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String line(List<?> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(field(values.get(i)));
        }
        return sb.append("\r\n").toString();
    }
}
