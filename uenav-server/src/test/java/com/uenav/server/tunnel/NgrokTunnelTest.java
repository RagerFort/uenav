package com.uenav.server.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NgrokTunnelTest {

    @Test
    void addressesAreTidied() {
        assertEquals("https://encounter-applied-goofiness.ngrok-free.dev",
                NgrokTunnel.tidyUrl(" https://encounter-applied-goofiness.ngrok-free.dev/ "));
        assertEquals("https://uenav.ngrok-free.app", NgrokTunnel.tidyUrl("uenav.ngrok-free.app"));
        assertNull(NgrokTunnel.tidyUrl("  "));
        assertNull(NgrokTunnel.tidyUrl(null));
    }

    @Test
    void ngrokLogLinesAreRead() {
        Map<String, String> f = NgrokTunnel.fields("t=2026-11-19T12:55:08+0800 lvl=info msg=\"started tunnel\" "
                + "obj=tunnels name=command_line addr=http://localhost:8080 url=https://x.ngrok-free.dev");
        assertEquals("started tunnel", f.get("msg"));
        assertEquals("https://x.ngrok-free.dev", f.get("url"));
        assertEquals("info", f.get("lvl"));
        assertEquals("authentication failed: \"bad token\"",
                NgrokTunnel.fields("lvl=eror msg=\"session closing\" err=\"authentication failed: \\\"bad token\\\"\"")
                        .get("err"));
    }
}
