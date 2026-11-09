package com.uenav.mobile;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * The loading screen shown while UENav opens: the brand red background, a white logo tile with a bouncing map pin,
 * the name and a spinner. It covers the whole screen (also under the status bar) and fades out once the app is
 * ready. It stays at least MIN_MILLIS so it does not just flash.
 */
final class SplashScreen {

    static final double MIN_MILLIS = 1100;

    private final StackPane root = new StackPane();
    private final Label status = new Label("Loading the campus map…");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final javafx.animation.Animation bounce;
    private final Logo mark;

    SplashScreen() {
        root.getStyleClass().add("nv-splash");

        Circle big = new Circle(170);
        big.getStyleClass().add("nv-hero-circle");
        StackPane.setAlignment(big, Pos.TOP_RIGHT);
        StackPane.setMargin(big, new Insets(-90, -110, 0, 0));
        Circle small = new Circle(120);
        small.getStyleClass().add("nv-hero-circle");
        StackPane.setAlignment(small, Pos.BOTTOM_LEFT);
        StackPane.setMargin(small, new Insets(0, 0, -60, -70));
        big.setMouseTransparent(true);
        small.setMouseTransparent(true);

        Logo mark = new Logo(108);
        StackPane logo = mark.view();
        this.mark = mark;

        Label name = new Label("UENav");
        name.getStyleClass().add("nv-splash-title");
        Label sub = new Label("Campus Navigator · UE Caloocan");
        sub.getStyleClass().add("nv-hero-sub");

        spinner.getStyleClass().add("nv-splash-spinner");
        spinner.setMaxSize(30, 30);
        status.getStyleClass().add("nv-hero-sub");

        VBox centre = new VBox(14, logo, name, sub);
        centre.setAlignment(Pos.CENTER);
        VBox.setMargin(name, new Insets(10, 0, 0, 0));
        VBox bottom = new VBox(12, spinner, status);
        bottom.setAlignment(Pos.CENTER);
        StackPane.setAlignment(bottom, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bottom, new Insets(0, 24, 70, 24));
        bottom.setMaxHeight(Region.USE_PREF_SIZE);

        root.getChildren().addAll(big, small, centre, bottom);

        // the route draws itself, the pin drops onto it, then hops gently while the app loads
        bounce = mark.intro();
        bounce.play();
        ScaleTransition in = new ScaleTransition(Duration.millis(450), logo);
        in.setFromX(0.6);
        in.setFromY(0.6);
        in.setToX(1);
        in.setToY(1);
        in.setInterpolator(Interpolator.EASE_OUT);
        in.play();
    }

    Parent view() {
        return root;
    }

    /** Jumps to the end of the logo animation (for still pictures). */
    void skipIntro() {
        bounce.stop();
        mark.finish();
    }

    /** The map could not be loaded: stop the spinner and say why. */
    void showError(String text) {
        bounce.stop();
        spinner.setVisible(false);
        status.setText(text);
        status.setWrapText(true);
    }

    /** Fades the loading screen out (and grows it a little), then runs done. */
    void fadeOut(Runnable done) {
        root.setMouseTransparent(true);
        FadeTransition fade = new FadeTransition(Duration.millis(380), root);
        fade.setToValue(0);
        ScaleTransition grow = new ScaleTransition(Duration.millis(380), root);
        grow.setToX(1.06);
        grow.setToY(1.06);
        ParallelTransition out = new ParallelTransition(fade, grow);
        out.setOnFinished(e -> {
            bounce.stop();
            done.run();
        });
        out.play();
    }
}
