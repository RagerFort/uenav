package com.uenav.mobile;

import java.io.IOException;
import java.util.List;

/** Sends saved entries to the server. */
public interface SyncClient {

    /** What the server did with a batch. Rejected entries were invalid and will never be accepted. */
    record Result(int accepted, int duplicates, int rejected) { }

    /** @throws IOException if the server could not be reached or did not accept the request */
    Result send(String baseUrl, List<UsageEntry> entries) throws IOException;
}
