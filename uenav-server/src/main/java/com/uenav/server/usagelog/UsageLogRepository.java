package com.uenav.server.usagelog;

import java.util.List;

public interface UsageLogRepository {

    boolean nodeExists(int nodeId);

    /** @return false if a row with the same clientLogId is already stored */
    boolean insert(Usage.Row row);

    Usage.Summary summary();

    List<Usage.TopPlace> mostSearched(int limit);

    List<Usage.Event> recent(int limit);

    /** Wrong-route reports, newest first. */
    List<Usage.Event> reports();

    /** Every row, oldest first, for the CSV export. */
    List<Usage.Event> all();

    /** @return false if no wrong-route report has this id */
    boolean setReportStatus(int logId, String status);
}
