package com.uenav.mobile;

import javafx.animation.AnimationTimer;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * Lets a scrolling list keep gliding after a flick and slow down smoothly, like other phone apps. JavaFX on Android
 * stops the list the moment the finger is lifted (measured on a Galaxy A35: a quick flick gave 5 frames and no
 * glide). Only touch scrolling is affected; a mouse wheel on a PC works as before. A touch while it glides stops it
 * and does not open what is under the finger.
 */
final class Fling {

    /** How quickly the glide slows down: about two thirds of the speed is lost every TIME_CONSTANT_MS. */
    static final double TIME_CONSTANT_MS = 325;
    /** Slower than this (pixels per millisecond) the glide stops. */
    static final double MIN_SPEED = 0.05;
    /** Faster flicks are capped, so a list does not shoot past far too many rows. */
    static final double MAX_SPEED = 6;

    private final ScrollPane pane;
    private final long[] times = new long[6];
    private final double[] deltas = new double[6];
    private int count;
    private double speed;
    private long lastFrame;
    private boolean gliding;
    private boolean swallow;
    private boolean platformInertia;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            double dt = lastFrame == 0 ? 16 : (now - lastFrame) / 1e6;
            lastFrame = now;
            if (!step(dt)) {
                stop();
                gliding = false;
            }
        }
    };

    private Fling(ScrollPane pane) {
        this.pane = pane;
    }

    static void install(ScrollPane pane) {
        Fling f = new Fling(pane);
        pane.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isInertia()) {
                f.platformInertia = true; // this platform glides by itself: leave it alone
                return;
            }
            if (e.isDirect() || e.getTouchCount() > 0) {
                f.track(e.getDeltaY());
            }
        });
        pane.addEventFilter(ScrollEvent.SCROLL_FINISHED, e -> f.release());
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (f.swallow) {
                e.consume();
            } else {
                f.release();
            }
        });
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            f.count = 0;
            if (f.gliding) {
                f.halt();
                f.swallow = true;
                e.consume();
            } else {
                f.swallow = false;
            }
        });
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (f.swallow) {
                e.consume();
                f.swallow = false;
            }
        });
    }

    private void track(double deltaY) {
        long now = System.nanoTime();
        if (gliding) {
            halt();
        }
        times[count % times.length] = now;
        deltas[count % times.length] = deltaY;
        count++;
    }

    /** The finger was lifted: glide on at the speed of the last ~80 ms of the drag. */
    private void release() {
        if (platformInertia || gliding || count == 0) {
            return;
        }
        long now = System.nanoTime();
        int n = Math.min(count, times.length);
        double sum = 0;
        long oldest = now;
        for (int i = 0; i < n; i++) {
            int k = (count - 1 - i) % times.length;
            if ((now - times[k]) / 1e6 > 80) {
                break;
            }
            sum += deltas[k];
            oldest = times[k];
        }
        count = 0;
        double ms = Math.max(16, (now - oldest) / 1e6);
        double v = sum / ms;
        if (Math.abs(v) < 0.3) {
            return; // the finger had stopped before it was lifted
        }
        speed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, v));
        lastFrame = 0;
        gliding = true;
        timer.start();
    }

    /** Moves the list by one frame. @return false when the glide is over */
    private boolean step(double dtMs) {
        double contentH = pane.getContent() == null ? 0 : pane.getContent().getLayoutBounds().getHeight();
        double range = contentH - pane.getViewportBounds().getHeight();
        if (range <= 0) {
            return false;
        }
        double v = pane.getVvalue() - speed * dtMs / range * (pane.getVmax() - pane.getVmin());
        if (v <= pane.getVmin() || v >= pane.getVmax()) {
            pane.setVvalue(Math.max(pane.getVmin(), Math.min(pane.getVmax(), v)));
            return false;
        }
        pane.setVvalue(v);
        speed *= Math.exp(-dtMs / TIME_CONSTANT_MS);
        return Math.abs(speed) >= MIN_SPEED;
    }

    private void halt() {
        timer.stop();
        gliding = false;
        speed = 0;
    }
}
