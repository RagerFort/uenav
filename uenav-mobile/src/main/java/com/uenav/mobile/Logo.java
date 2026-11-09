package com.uenav.mobile;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

/**
 * The UENav mark: a red route shaped like a "U" (for UE) that starts at a dark "you are here" dot, passes small
 * station dots (the nodes of the route graph) and ends under a red map pin. Drawn with shapes, so it is sharp at any
 * size and needs no picture file. intro() draws the route on, drops the pin and then lets it hop gently.
 */
final class Logo {

    private static final Color RED = Color.web("#C8102E");
    private static final Color INK = Color.web("#1F2330");
    /** A little more than the length of the U route, for the draw-on animation. */
    private static final double ROUTE_LENGTH = 120;

    private final StackPane tile;
    private final SVGPath route = new SVGPath();
    private final Group pin;
    private final Group stations = new Group();

    /** @param size width and height of the white tile, in screen units */
    Logo(double size) {
        Rectangle back = new Rectangle(100, 100);
        back.setArcWidth(52);
        back.setArcHeight(52);
        back.setFill(Color.WHITE);

        // the route: down from the start, round the bottom of the U, up to the pin
        route.setContent("M30 32 L30 56 Q30 78 50 78 Q70 78 70 56 L70 47");
        route.setFill(null);
        route.setStroke(RED);
        route.setStrokeWidth(10);
        route.setStrokeLineCap(StrokeLineCap.ROUND);
        route.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Circle startOuter = new Circle(30, 30, 9, INK);
        Circle startInner = new Circle(30, 30, 3.6, Color.WHITE);
        for (double[] p : new double[][] {{30, 58}, {50, 78}, {70, 58}}) {
            stations.getChildren().add(new Circle(p[0], p[1], 3.2, Color.WHITE));
        }

        SVGPath drop = new SVGPath();
        drop.setContent("M70 48 C70 48 56 34 56 25 A14 14 0 0 1 84 25 C84 34 70 48 70 48 Z");
        drop.setFill(RED);
        Circle eye = new Circle(70, 25, 5.2, Color.WHITE);
        pin = new Group(drop, eye);

        Group art = new Group(back, route, stations, startOuter, startInner, pin);
        art.setScaleX(size / 100);
        art.setScaleY(size / 100);
        tile = new StackPane(new Group(art));
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        tile.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 18, 0, 0, 6);");
    }

    StackPane view() {
        return tile;
    }

    /** The route draws itself, the stations appear, the pin drops in, then it keeps hopping. */
    Animation intro() {
        route.getStrokeDashArray().setAll(ROUTE_LENGTH, ROUTE_LENGTH);
        route.setStrokeDashOffset(ROUTE_LENGTH);
        stations.setOpacity(0);
        pin.setOpacity(0);
        pin.setTranslateY(-26);

        Timeline draw = new Timeline(new KeyFrame(Duration.millis(750),
                new KeyValue(route.strokeDashOffsetProperty(), 0, Interpolator.EASE_BOTH)));
        FadeTransition dots = new FadeTransition(Duration.millis(250), stations);
        dots.setToValue(1);
        TranslateTransition fall = new TranslateTransition(Duration.millis(320), pin);
        fall.setToY(0);
        fall.setInterpolator(Interpolator.EASE_IN);
        FadeTransition show = new FadeTransition(Duration.millis(200), pin);
        show.setToValue(1);
        TranslateTransition settle = new TranslateTransition(Duration.millis(160), pin);
        settle.setByY(-6);
        settle.setAutoReverse(true);
        settle.setCycleCount(2);

        TranslateTransition hop = new TranslateTransition(Duration.millis(560), pin);
        hop.setFromY(0);
        hop.setToY(-5);
        hop.setAutoReverse(true);
        hop.setCycleCount(Animation.INDEFINITE);
        hop.setInterpolator(Interpolator.EASE_BOTH);

        return new SequentialTransition(draw, dots, new ParallelTransition(fall, show), settle, hop);
    }

    /** Shows the finished mark with no animation (still pictures, tests). */
    void finish() {
        route.getStrokeDashArray().clear();
        route.setStrokeDashOffset(0);
        stations.setOpacity(1);
        pin.setOpacity(1);
        pin.setTranslateY(0);
    }
}
