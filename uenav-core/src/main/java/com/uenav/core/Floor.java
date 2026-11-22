package com.uenav.core;

/** One row of the Floor table, as sent in the snapshot. planImagePath may be null. */
public record Floor(int id, int buildingId, int floorNumber, String floorName, String planImagePath) {

    /** Short label for floor tabs, such as "4F"; a mezzanine is named after the floor below it ("5M"). */
    public String shortLabel() {
        if (floorName != null && floorName.toLowerCase(java.util.Locale.ROOT).contains("mezzanine")) {
            return (floorNumber - 1) + "M";
        }
        return floorNumber + "F";
    }
}
