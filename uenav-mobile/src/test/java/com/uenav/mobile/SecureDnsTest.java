package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecureDnsTest {

    @Test
    void addressesComeFromTheAnswers() {
        String json = "{\"Status\":0,\"Answer\":[{\"name\":\"x.ngrok-free.dev\",\"type\":5,\"data\":\"edge.ngrok\"},"
                + "{\"name\":\"edge.ngrok\",\"type\":1,\"TTL\":60,\"data\":\"18.136.148.247\"},"
                + "{\"name\":\"edge.ngrok\",\"type\":1,\"TTL\":60,\"data\":\"52.220.121.212\"}]}";
        assertEquals(List.of("18.136.148.247", "52.220.121.212"), SecureDns.addresses(json));
        assertEquals(List.of(), SecureDns.addresses("{\"Status\":3}")); // does not exist
        assertEquals(List.of(), SecureDns.addresses("<html>"));
    }

    @Test
    void repliesWithALengthAreRead() throws IOException {
        Http.Reply r = read("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 15\r\n\r\n"
                + "{\"status\":\"UP\"}");
        assertEquals(200, r.status());
        assertEquals("{\"status\":\"UP\"}", r.text());
    }

    @Test
    void chunkedRepliesAreJoined() throws IOException {
        Http.Reply r = read("HTTP/1.1 401 \r\nTransfer-Encoding: chunked\r\n\r\n"
                + "7\r\n{\"error\r\n6;ext=1\r\n\":\"no\"\r\n1\r\n}\r\n0\r\n\r\n");
        assertEquals(401, r.status());
        assertEquals("{\"error\":\"no\"}", r.text());
    }

    @Test
    void interimRepliesAreSkippedAndCutRepliesRefused() throws IOException {
        Http.Reply r = read("HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\nConnection: close\r\n\r\nhello");
        assertEquals(200, r.status());
        assertEquals("hello", r.text());
        assertThrows(IOException.class, () -> read("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nshort"));
        assertThrows(IOException.class, () -> read("<html>not http</html>\r\n"));
    }

    private static Http.Reply read(String raw) throws IOException {
        return Http.readReply(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
