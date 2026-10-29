package com.fixtool.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/**
 * Utilitario de micro-animaciones fluidas para interfaces modernas en JavaFX:
 * transiciones de fundido (fade), desplazamiento (slide) y cambio de vista.
 */
public class AnimationHelper {

    public static void fadeIn(Node node, int durationMillis, Runnable onFinished) {
        node.setOpacity(0.0);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMillis), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);
        if (onFinished != null) {
            ft.setOnFinished(e -> onFinished.run());
        }
        ft.play();
    }

    public static void fadeOut(Node node, int durationMillis, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMillis), node);
        ft.setFromValue(node.getOpacity());
        ft.setToValue(0.0);
        ft.setInterpolator(Interpolator.EASE_IN);
        if (onFinished != null) {
            ft.setOnFinished(e -> onFinished.run());
        }
        ft.play();
    }

    public static void slideInUp(Node node, double startOffsetY, int durationMillis, Runnable onFinished) {
        node.setOpacity(0.0);
        node.setTranslateY(startOffsetY);

        FadeTransition ft = new FadeTransition(Duration.millis(durationMillis), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMillis), node);
        tt.setFromY(startOffsetY);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        if (onFinished != null) {
            pt.setOnFinished(e -> onFinished.run());
        }
        pt.play();
    }

    public static void switchView(Pane container, Node currentView, Node nextView, Runnable onComplete) {
        if (currentView == null) {
            container.getChildren().setAll(nextView);
            fadeIn(nextView, 280, onComplete);
            return;
        }

        fadeOut(currentView, 140, () -> {
            container.getChildren().setAll(nextView);
            fadeIn(nextView, 220, onComplete);
        });
    }
}
