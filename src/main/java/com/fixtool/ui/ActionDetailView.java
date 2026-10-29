package com.fixtool.ui;

import com.fixtool.model.DiagnosticAction;
import com.fixtool.util.AppIcons;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Vista de cada herramienta mientras su implementación está en construcción.
 * Muestra el ícono grande de la herramienta, su nombre y una indicación clara
 * de que la funcionalidad estará disponible en una fase de desarrollo próxima.
 *
 * Esta vista se reemplaza herramienta por herramienta en las Fases 2-6.
 */
public class ActionDetailView extends StackPane {

    public ActionDetailView(DiagnosticAction tool) {
        this.getStyleClass().add("bg-root");
        this.setAlignment(Pos.CENTER);

        // ── Tarjeta central ───────────────────────────────────────────────────
        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(48, 60, 48, 60));
        card.setMaxWidth(480);
        card.getStyleClass().add("card-panel");

        // Ícono grande de la herramienta (SVG, 64px)
        Node icon = AppIcons.create(tool.getIconSymbol(), 64, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        // Nombre de la herramienta
        Label lblName = new Label(tool.getTitle());
        lblName.getStyleClass().add("placeholder-tool-name");
        lblName.setAlignment(Pos.CENTER);

        // Texto de estado
        Label lblStatus = new Label("Herramienta en construcción");
        lblStatus.getStyleClass().add("placeholder-status");
        lblStatus.setAlignment(Pos.CENTER);

        // Descripción breve de qué hará esta herramienta
        Label lblDesc = new Label(tool.getDetailedDescription());
        lblDesc.getStyleClass().add("placeholder-description");
        lblDesc.setWrapText(true);
        lblDesc.setAlignment(Pos.CENTER);
        lblDesc.setMaxWidth(340);

        // Indicador de fase
        Label lblPhase = new Label("Disponible en la próxima fase de desarrollo");
        lblPhase.getStyleClass().add("placeholder-phase");
        lblPhase.setAlignment(Pos.CENTER);

        card.getChildren().addAll(icon, lblName, lblStatus, lblDesc, lblPhase);

        // Animación suave de entrada (slide + fade)
        card.setOpacity(0);
        card.setTranslateY(16);
        this.getChildren().add(card);

        Timeline entradaAnim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.translateYProperty(), 16)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT))
        );
        entradaAnim.play();
    }
}
