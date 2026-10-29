package com.fixtool.ui;

import com.fixtool.util.AnimationHelper;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/**
 * Pantalla de Bienvenida (Splash / Landing View) de Synchro Willy.
 * Diseño tipográfico y geométrico puramente minimalista y elegante (Minimalist Tech).
 * Libre de imágenes externas y placeholders de archivos.
 */
public class SplashView extends StackPane {

    private final Runnable onEnterDashboard;
    private final VBox card;
    private final Button btnEnter;

    public SplashView(Runnable onEnterDashboard) {
        this.onEnterDashboard = onEnterDashboard;
        this.getStyleClass().add("bg-root");

        // Tarjeta Central (Hero Card)
        card = new VBox(22);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(44, 56, 44, 56));
        card.setMaxWidth(680);
        card.setMaxHeight(580);
        card.getStyleClass().add("card-panel");

        // 1. Badge superior corporativo
        Label topBadge = new Label("SUITE CORPORATIVA DE ADMINISTRACIÓN & SINCRONIZACIÓN WINDOWS");
        topBadge.getStyleClass().add("badge-tag");

        // 2. Emblema Geométrico Puro Minimalist Tech (Hexágono + Anillo Vectorial)
        StackPane emblem = createGeometricEmblem();

        // 3. Tipografía Principal: "Synchro Willy"
        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER);

        Label titleSynchro = new Label("Synchro");
        titleSynchro.getStyleClass().add("text-hero-title");

        Label titleWilly = new Label("Willy");
        titleWilly.getStyleClass().add("text-hero-accent");

        titleBox.getChildren().addAll(titleSynchro, titleWilly);

        Label subtitle = new Label("Plataforma Modular de Diagnóstico de Red, Sincronización y Auditoría de Sistemas");
        subtitle.getStyleClass().add("text-muted");

        // 4. Píldoras de especificación técnica
        HBox pillsBox = new HBox(12);
        pillsBox.setAlignment(Pos.CENTER);

        Label p1 = new Label("🌐 IP Target Dinámica");
        p1.getStyleClass().add("badge-chip");

        Label p2 = new Label("🛠️ 10 Módulos de Herramientas");
        p2.getStyleClass().add("badge-chip");

        Label p3 = new Label("🔒 Sin Elevación UAC");
        p3.getStyleClass().add("badge-chip");

        pillsBox.getChildren().addAll(p1, p2, p3);

        // 5. Botón de Acceso Principal con Animación
        btnEnter = new Button("🚀 Explorar Funciones");
        btnEnter.getStyleClass().add("button-primary");
        btnEnter.setPrefWidth(260);
        btnEnter.setPrefHeight(46);
        btnEnter.setOnAction(e -> handleEnterTransition());

        btnEnter.setOnMouseEntered(e -> animateButtonScale(1.04));
        btnEnter.setOnMouseExited(e -> animateButtonScale(1.0));

        // 6. Pie de página minimalista
        Label credits = new Label("Diseño Minimalist Tech • FlatLaf Dark Edition • Windows Diagnostic Suite");
        credits.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

        card.getChildren().addAll(topBadge, emblem, titleBox, subtitle, pillsBox, btnEnter, credits);
        this.getChildren().add(card);

        // Animación suave de entrada
        AnimationHelper.slideInUp(card, 25, 420, null);
    }

    private StackPane createGeometricEmblem() {
        StackPane emblemBox = new StackPane();
        emblemBox.setPrefSize(110, 110);
        emblemBox.setMaxSize(110, 110);
        emblemBox.getStyleClass().add("geometric-emblem-outer");

        // Hexágono vectorial estilizado
        Polygon hex = new Polygon();
        double r = 36;
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i + 30);
            hex.getPoints().addAll(r * Math.cos(angle), r * Math.sin(angle));
        }
        hex.setFill(Color.web(com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED));
        hex.setStroke(Color.web(com.fixtool.ui.theme.ThemeTokens.ACCENT));
        hex.setStrokeWidth(2.0);

        // Círculo central con acento
        Circle core = new Circle(14);
        core.setFill(Color.web(com.fixtool.ui.theme.ThemeTokens.BG_BASE));
        core.setStroke(Color.web(com.fixtool.ui.theme.ThemeTokens.ACCENT));
        core.setStrokeWidth(2.0);

        Label centerGlyph = new Label("⚡");
        centerGlyph.setStyle("-fx-font-size: 18px;");

        emblemBox.getChildren().addAll(hex, core, centerGlyph);
        return emblemBox;
    }

    private void animateButtonScale(double targetScale) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(150),
                        new KeyValue(btnEnter.scaleXProperty(), targetScale),
                        new KeyValue(btnEnter.scaleYProperty(), targetScale))
        );
        tl.play();
    }

    private void handleEnterTransition() {
        btnEnter.setDisable(true);
        btnEnter.setText("Accediendo al Panel...");

        AnimationHelper.fadeOut(card, 180, () -> {
            if (onEnterDashboard != null) {
                onEnterDashboard.run();
            }
        });
    }
}
