package com.fixtool.ui.components;

import com.fixtool.model.ToolStatus;
import com.fixtool.util.AppIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;

/**
 * Tarjeta de resultado estándar de Synchro Willy.
 *
 * Todas las herramientas muestran sus resultados con este patrón:
 *  1. Semáforo de color (ícono circular verde/amarillo/rojo).
 *  2. Título claro y valor clave grande.
 *  3. Una o dos frases explicando qué significa el resultado.
 *  4. Botones de acción cuando aplique.
 *  5. Sección "Detalle técnico", plegada por defecto.
 *
 * Uso:
 *   StandardResultCard card = new StandardResultCard(
 *       ToolStatus.OK, "Conexión estable", "Latencia promedio", "3 ms",
 *       "Tu red no tiene pérdidas ni retrasos notables.",
 *       detalles, // Map<String,String> o null
 *       acciones  // List<Button> o null
 *   );
 */
public class StandardResultCard extends VBox {

    public StandardResultCard(com.fixtool.model.DiagnosticResult result) {
        this(
            com.fixtool.model.ToolStatus.fromResult(result),
            result != null ? result.getStatusBadge() : "Resultado",
            null,
            null,
            result != null ? result.getSummary() : "Sin información disponible.",
            result != null ? result.getTelemetry() : java.util.Collections.emptyMap(),
            null
        );
    }

    public StandardResultCard(ToolStatus status,
                              String title,
                              String keyLabel,
                              String keyValue,
                              String explanation,
                              java.util.Map<String, String> technicalDetails,
                              java.util.List<Button> actions) {
        this.getStyleClass().add("result-card");
        this.setSpacing(16);
        this.setPadding(new Insets(20, 24, 20, 24));

        // ── 1. Fila de semáforo + título ──────────────────────────────────────
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Node semaforo = buildSemaphore(status);

        VBox titleBox = new VBox(4);
        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("result-title");

        // Valor clave grande (el dato principal)
        if (keyLabel != null && keyValue != null) {
            Label lblKeyLabel = new Label(keyLabel);
            lblKeyLabel.getStyleClass().add("result-key-label");

            Label lblKeyValue = new Label(keyValue);
            lblKeyValue.getStyleClass().add("result-key-value");

            titleBox.getChildren().addAll(lblTitle, lblKeyLabel, lblKeyValue);
        } else {
            titleBox.getChildren().add(lblTitle);
        }

        headerRow.getChildren().addAll(semaforo, titleBox);

        // ── 2. Explicación en lenguaje claro ─────────────────────────────────
        Label lblExplanation = new Label(explanation);
        lblExplanation.getStyleClass().add("result-explanation");
        lblExplanation.setWrapText(true);

        this.getChildren().addAll(headerRow, lblExplanation);

        // ── 3. Botones de acción (opcionales) ────────────────────────────────
        if (actions != null && !actions.isEmpty()) {
            HBox actionRow = new HBox(10);
            actionRow.setAlignment(Pos.CENTER_LEFT);
            for (Button btn : actions) {
                if (!btn.getStyleClass().contains("btn-primary")) {
                    btn.getStyleClass().add("btn-primary");
                }
            }
            actionRow.getChildren().addAll(actions);
            this.getChildren().add(actionRow);
        }

        // ── 4. Detalle técnico (plegado por defecto) ──────────────────────────
        if (technicalDetails != null && !technicalDetails.isEmpty()) {
            this.technicalDetailPane = buildTechnicalDetail(technicalDetails);
            this.getChildren().add(technicalDetailPane);
        }
    }

    private TitledPane technicalDetailPane;

    public boolean isTechnicalDetailExpanded() {
        return technicalDetailPane != null && technicalDetailPane.isExpanded();
    }

    public void setTechnicalDetailExpanded(boolean expanded) {
        if (technicalDetailPane != null) {
            technicalDetailPane.setExpanded(expanded);
        }
    }

    public TitledPane getTechnicalDetailPane() {
        return technicalDetailPane;
    }

    // ── Helpers de construcción ───────────────────────────────────────────────

    /**
     * Círculo de color según el estado (semáforo).
     * Verde = OK, Amarillo = Atención, Rojo = Problema, Gris = Sin ejecutar.
     */
    private Node buildSemaphore(ToolStatus status) {
        String iconName;
        switch (status) {
            case OK       -> iconName = "checkcircle";
            case ATENCION -> iconName = "alerttriangle";
            case PROBLEMA -> iconName = "xcircle";
            default       -> iconName = "info";
        }
        Node icon = AppIcons.create(iconName, 36, status.getColorHex());

        // Fondo circular del ícono
        StackPane circle = new StackPane(icon);
        circle.getStyleClass().add("semaphore-bg");
        circle.setStyle("-fx-background-color: " + status.getColorHex() + "22;");
        circle.setPrefSize(52, 52);
        circle.setMinSize(52, 52);
        circle.setMaxSize(52, 52);
        return circle;
    }

    /** Sección plegable con los datos técnicos clave-valor. */
    private TitledPane buildTechnicalDetail(java.util.Map<String, String> details) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        grid.setPadding(new Insets(10, 0, 4, 0));

        ColumnConstraints colKey = new ColumnConstraints();
        colKey.setMinWidth(160);
        ColumnConstraints colVal = new ColumnConstraints();
        colVal.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(colKey, colVal);

        int row = 0;
        for (java.util.Map.Entry<String, String> entry : details.entrySet()) {
            Label key = new Label(entry.getKey());
            key.getStyleClass().add("detail-key");

            Label val = new Label(entry.getValue() != null ? entry.getValue() : "No disponible");
            val.getStyleClass().add("detail-value");
            val.setWrapText(true);

            grid.add(key, 0, row);
            grid.add(val, 1, row);
            row++;
        }

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        scroll.setMaxHeight(240);

        TitledPane pane = new TitledPane("Detalle técnico", scroll);
        pane.setExpanded(false); // Plegado por defecto
        pane.getStyleClass().add("detail-pane");
        return pane;
    }
}
