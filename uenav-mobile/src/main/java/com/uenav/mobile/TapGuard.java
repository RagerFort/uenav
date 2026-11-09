package com.uenav.mobile;

import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;

/**
 * Makes buttons and rows react to a tap only, never to a scroll. On a phone, a finger that starts on a button and
 * drags the list would otherwise open that button when it is lifted. Once the finger has moved more than SLOP
 * (in screen units, about 10 dp), the press is treated as a scroll: buttons are released without firing and the
 * click that follows is swallowed. The map handles its own drags and is not affected.
 */
final class TapGuard {

    /** How far a finger may wobble and still count as a tap. */
    static final double SLOP = 10;

    private double pressX;
    private double pressY;
    private boolean moved;
    private ButtonBase pressedButton;

    private TapGuard() { }

    static void install(Scene scene) {
        TapGuard g = new TapGuard();
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            g.pressX = e.getScreenX();
            g.pressY = e.getScreenY();
            g.moved = false;
            g.pressedButton = buttonOf(e.getTarget());
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!g.moved && Math.hypot(e.getScreenX() - g.pressX, e.getScreenY() - g.pressY) > SLOP) {
                g.moved = true;
            }
            if (g.moved && g.pressedButton != null) {
                g.pressedButton.disarm();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (g.moved && g.pressedButton != null) {
                g.pressedButton.disarm();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (g.moved || Math.hypot(e.getScreenX() - g.pressX, e.getScreenY() - g.pressY) > SLOP) {
                e.consume();
            }
        });
    }

    private static ButtonBase buttonOf(EventTarget target) {
        Node n = target instanceof Node node ? node : null;
        while (n != null) {
            if (n instanceof ButtonBase b) {
                return b;
            }
            n = n.getParent();
        }
        return null;
    }
}
