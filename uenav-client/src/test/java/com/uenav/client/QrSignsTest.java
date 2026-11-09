package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.uenav.core.QrCodes;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class QrSignsTest {

    /** Draws the modules at 8 pixels each and reads them back with ZXing's own reader. */
    private static String decode(boolean[][] m) throws Exception {
        int scale = 8;
        int quiet = 4 * scale;
        int size = m.length * scale + 2 * quiet;
        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) {
                if (m[y][x]) {
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            pixels[(quiet + y * scale + dy) * size + quiet + x * scale + dx] = 0xFF000000;
                        }
                    }
                }
            }
        }
        LuminanceSource source = new RGBLuminanceSource(size, size, pixels);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }

    @Test
    void qrCodeHoldsTheNodeCode() throws Exception {
        boolean[][] m = QrSigns.matrix(QrCodes.text(42));
        assertEquals(m.length, m[0].length);
        assertTrue(m.length >= 21, "a QR code is at least 21 modules wide");
        String text = decode(m);
        assertEquals("UENAV:42", text);
        assertEquals(OptionalInt.of(42), QrCodes.parse(text));
    }

    @Test
    void fileNamesAreSafeAndReadable() {
        assertEquals("uenav-qr-22-registrars-office.png", QrSigns.fileName(22, "Registrar's Office"));
        assertEquals("uenav-qr-42-en-420-4f.png", QrSigns.fileName(42, "EN 420 (4F)"));
        assertEquals("uenav-qr-7.png", QrSigns.fileName(7, "***"));
        assertTrue(QrSigns.fileName(1, "x".repeat(100)).length() < 60);
    }
}
