package com.uenav.core;

/** One row of the Floor table, as sent in the snapshot. planImagePath may be null. */
public record Floor(int id, int buildingId, int floorNumber, String floorName, String planImagePath) {

    /** Short label for floor tabs, such as "4F". */
    public String shortLabel() {
        return floorNumber + "F";
    }
}
