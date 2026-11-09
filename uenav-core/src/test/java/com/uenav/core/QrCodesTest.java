package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class QrCodesTest {

    @Test
    void textHoldsOnlyTheNodeId() {
        assertEquals("UENAV:12", QrCodes.text(12));
        assertThrows(IllegalArgumentException.class, () -> QrCodes.text(0));
    }

    @Test
    void parsesTheFormsPeopleMayScanOrType() {
        assertEquals(OptionalInt.of(12), QrCodes.parse("UENAV:12"));
        assertEquals(OptionalInt.of(12), QrCodes.parse("  uenav:12 "));
        assertEquals(OptionalInt.of(12), QrCodes.parse("uenav 12"));
        assertEquals(OptionalInt.of(12), QrCodes.parse("UENAV-12"));
        assertEquals(OptionalInt.of(7), QrCodes.parse("uenav://node/7"));
        assertEquals(OptionalInt.of(40), QrCodes.parse("https://example.org/where?node=40"));
        assertEquals(OptionalInt.of(5), QrCodes.parse("5"));
    }

    @Test
    void rejectsAnythingElse() {
        assertEquals(OptionalInt.empty(), QrCodes.parse(null));
        assertEquals(OptionalInt.empty(), QrCodes.parse(""));
        assertEquals(OptionalInt.empty(), QrCodes.parse("UENAV:"));
        assertEquals(OptionalInt.empty(), QrCodes.parse("UENAV:0"));
        assertEquals(OptionalInt.empty(), QrCodes.parse("hello 12"));
        assertEquals(OptionalInt.empty(), QrCodes.parse("UENAV:12abc"));
    }
}
