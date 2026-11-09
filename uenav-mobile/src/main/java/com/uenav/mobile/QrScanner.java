package com.uenav.mobile;

import com.gluonhq.attach.barcodescan.BarcodeScanService;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;

/**
 * Scans a "You are here" QR sign with the phone camera (Gluon Attach barcode-scan). On a PC, or a phone where the
 * service is missing, scan() returns false and the screen asks for the code printed under the QR instead.
 */
final class QrScanner {

    private QrScanner() { }

    /** True if the camera scanner can be opened on this device. */
    static boolean available() {
        try {
            return BarcodeScanService.create().isPresent();
        } catch (Throwable notAvailable) {
            return false;
        }
    }

    /**
     * Opens the camera. The scanned text is passed to onResult on the screen thread.
     * @return false if scanning is not available here (nothing was opened)
     */
    static boolean scan(Consumer<String> onResult) {
        Optional<BarcodeScanService> service;
        try {
            service = BarcodeScanService.create();
        } catch (Throwable notAvailable) {
            return false;
        }
        if (service.isEmpty()) {
            return false;
        }
        BarcodeScanService s = service.get();
        ChangeListener<String> once = new ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends String> o, String old, String text) {
                s.resultProperty().removeListener(this);
                if (text != null && !text.isBlank()) {
                    Platform.runLater(() -> onResult.accept(text));
                }
            }
        };
        s.resultProperty().addListener(once);
        try {
            s.asyncScan("Scan a UENav sign", "Point the camera at the QR code on the sign", "Found: ");
            return true;
        } catch (Throwable failed) {
            s.resultProperty().removeListener(once);
            return false;
        }
    }
}
