package com.uenav.client;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.uenav.core.QrCodes;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javax.imageio.ImageIO;

/**
 * Printable "You are here" signs. Each sign has a QR code holding only the place's id (QrCodes.text, e.g. UENAV:12),
 * so a phone that scans it sets its starting point with no internet. The code is also printed as text for phones
 * without a camera. Signs always use the light look, since they are printed on paper.
 */
final class QrSigns {

    static final double SIGN_WIDTH = 600;

    private QrSigns() { }

    /** The QR code as dark (true) and light modules, with a one-module quiet zone. */
    static boolean[][] matrix(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
            boolean[][] out = new boolean[m.getHeight()][m.getWidth()];
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    out[y][x] = m.get(x, y);
                }
            }
            return out;
        } catch (WriterException e) {
            throw new IllegalArgumentException("Could not make a QR code for " + text, e);
        }
    }

    /** The QR code as a picture about sizePx wide, with sharp square modules. */
    static WritableImage qrImage(String text, int sizePx) {
        boolean[][] m = matrix(text);
        int modules = m.length;
        int scale = Math.max(1, sizePx / modules);
        WritableImage img = new WritableImage(modules * scale, modules * scale);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < modules * scale; y++) {
            for (int x = 0; x < modules * scale; x++) {
                pw.setColor(x, y, m[y / scale][x / scale] ? Color.BLACK : Color.WHITE);
            }
        }
        return img;
    }

    /** The sign as a screen element (also used for the preview). where is "Building · floor" or "". */
    static Node sign(int nodeId, String placeName, String where) {
        Region bar = new Region();
        bar.setStyle("-fx-background-color: #C8102E;");
        bar.setMinHeight(14);
        bar.setMaxWidth(Double.MAX_VALUE);

        Label brand = label("UENav · Campus Navigator", "-fx-text-fill: #C8102E; -fx-font-size: 18px; "
                + "-fx-font-weight: bold;");
        Label here = label("YOU ARE HERE", "-fx-text-fill: #1F2330; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label name = label(placeName, "-fx-text-fill: #1F2330; -fx-font-size: 34px; -fx-font-weight: bold;");
        Label place = label(where == null ? "" : where, "-fx-text-fill: #6B7280; -fx-font-size: 18px;");
        ImageView qr = new ImageView(qrImage(QrCodes.text(nodeId), 380));
        qr.setFitWidth(380);
        qr.setFitHeight(380);
        qr.setSmooth(false);
        Label code = label(QrCodes.text(nodeId), "-fx-text-fill: #1F2330; -fx-font-size: 24px; -fx-font-weight: bold; "
                + "-fx-font-family: 'Consolas', 'Courier New', monospace;");
        Label how = label("Open UENav and tap \"Scan QR\" to start your route from here. No internet needed. "
                + "No camera? Type the code above.", "-fx-text-fill: #4B5563; -fx-font-size: 15px;");

        VBox body = new VBox(10, brand, here, name, place, qr, code, how);
        body.setAlignment(Pos.TOP_CENTER);
        body.setPadding(new Insets(26, 40, 34, 40));
        if (where == null || where.isEmpty()) {
            body.getChildren().remove(place);
        }
        VBox sign = new VBox(bar, body);
        sign.setStyle("-fx-background-color: white;");
        sign.setMinWidth(SIGN_WIDTH);
        sign.setPrefWidth(SIGN_WIDTH);
        sign.setMaxWidth(SIGN_WIDTH);
        return sign;
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.setStyle(style);
        l.setWrapText(true);
        l.setTextAlignment(TextAlignment.CENTER);
        l.setAlignment(Pos.CENTER);
        l.setMaxWidth(SIGN_WIDTH - 80);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    /** A picture of the sign at print size (2x the screen size, so it stays sharp on paper). */
    static WritableImage render(Node sign) {
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.WHITE);
        p.setTransform(javafx.scene.transform.Transform.scale(2, 2));
        return sign.snapshot(p, null);
    }

    static void savePng(WritableImage img, File file) throws IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            pr.getPixels(0, y, w, 1, PixelFormat.getIntArgbInstance(), row, 0, w);
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        if (!ImageIO.write(out, "png", file)) {
            throw new IOException("PNG writing is not available");
        }
    }

    /** "uenav-qr-12-registrars-office.png" */
    static String fileName(int nodeId, String placeName) {
        String slug = placeName.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return "uenav-qr-" + nodeId + (slug.isEmpty() ? "" : "-" + slug) + ".png";
    }
}
