package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.DiagnosticService;
import com.fixtool.util.AppIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pantalla de inicio de Synchro Willy.
 *
 * Contiene dos secciones:
 *  1. Panel de estado global: semáforo, contador "X de 10 ejecutadas" y botón de informe.
 *  2. Cuadrícula de 10 tarjetas (2 columnas × 5 filas), cada una clicable.
 *     Cada tarjeta muestra: ícono SVG, nombre, línea descriptiva y chip de estado.
 */
public class HomeView extends VBox {

    public HomeView(DiagnosticService service,
                    Consumer<DiagnosticAction> onSelectTool,
                    Runnable onGenerateReport) {

        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(24);
        content.setPadding(new Insets(28, 28, 32, 28));
        content.getStyleClass().add("bg-root");

        // ── 1. Panel de estado global ─────────────────────────────────────────
        Node statusPanel = buildStatusPanel(onGenerateReport);

        // ── 2. Cuadrícula de 10 tarjetas ──────────────────────────────────────
        GridPane grid = buildToolGrid(service, onSelectTool);

        content.getChildren().addAll(statusPanel, grid);
        scroll.setContent(content);
        this.getChildren().add(scroll);
    }

    // ── Panel de estado global ────────────────────────────────────────────────

    private Node buildStatusPanel(Runnable onGenerateReport) {
        HBox panel = new HBox(20);
        panel.getStyleClass().add("status-panel");
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setPadding(new Insets(18, 24, 18, 24));

        SessionState session = SessionState.getInstance();

        // Semáforo global: ícono de color dinámico
        Label globalIcon = new Label("●");
        globalIcon.setStyle("-fx-font-size: 28px;");
        // Actualizar color cuando cambie cualquier herramienta
        Runnable updateGlobal = () -> {
            ToolStatus g = session.getGlobalStatus();
            globalIcon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + g.getColorHex() + ";");
        };
        updateGlobal.run();
        // Escuchar cambios de cada herramienta
        for (int i = 1; i <= 10; i++) {
            session.statusProperty(i).addListener((obs, o, n) -> updateGlobal.run());
        }

        // Contador de herramientas ejecutadas y nombre de la app en inicio
        VBox counters = new VBox(4);
        Label lblAppTitle = new Label(AppConfig.APP_NAME);
        lblAppTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";");

        Label lblCount = new Label();
        lblCount.getStyleClass().add("status-count-label");
        // Binding dinámico al contador de sesión
        Runnable updateCount = () -> {
            long exec = session.getExecutedCount();
            lblCount.setText(exec + " de 10 herramientas ejecutadas");
        };
        updateCount.run();
        for (int i = 1; i <= 10; i++) {
            session.statusProperty(i).addListener((obs, o, n) -> updateCount.run());
        }

        Label lblHint = new Label("Haz clic en una herramienta para comenzar");
        lblHint.getStyleClass().add("status-hint-label");

        counters.getChildren().addAll(lblAppTitle, lblCount, lblHint);

        // Espaciador
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Botón Generar informe
        Node reportIcon = AppIcons.create("filetext", 16, com.fixtool.ui.theme.ThemeTokens.TEXT_DARK);
        Button btnReport = new Button("Generar informe", reportIcon);
        btnReport.getStyleClass().add("btn-primary");
        btnReport.setOnAction(e -> { if (onGenerateReport != null) onGenerateReport.run(); });

        Node logoLarge = buildLargeLogoNode();

        panel.getChildren().addAll(logoLarge, globalIcon, counters, spacer, btnReport);
        return panel;
    }

    /** Logo grande para el panel de bienvenida de la pantalla de inicio. */
    private Node buildLargeLogoNode() {
        try {
            java.io.InputStream stream = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE);
            if (stream != null) {
                javafx.scene.image.Image img = new javafx.scene.image.Image(stream, 76, 76, true, true);
                if (!img.isError()) {
                    javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                    iv.setFitWidth(76);
                    iv.setFitHeight(76);
                    return iv;
                }
            }
        } catch (Exception ignored) {}
        StackPane placeholder = new StackPane();
        placeholder.getStyleClass().add("logo-placeholder");
        placeholder.setPrefSize(76, 76);
        placeholder.getChildren().add(AppIcons.create("tool", 36, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        return placeholder;
    }

    // ── Cuadrícula de 10 tarjetas ─────────────────────────────────────────────

    private GridPane buildToolGrid(DiagnosticService service,
                                   Consumer<DiagnosticAction> onSelectTool) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);

        // Dos columnas de igual tamaño
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col, new ColumnConstraints() {{ setPercentWidth(50); }});

        List<DiagnosticAction> tools = service.getActions();
        for (int i = 0; i < tools.size(); i++) {
            DiagnosticAction tool = tools.get(i);
            int row = i / 2;
            int col2 = i % 2;
            Node card = buildToolCard(tool, onSelectTool);
            grid.add(card, col2, row);
        }

        return grid;
    }

    /**
     * Construye la tarjeta de una herramienta.
     * Toda la tarjeta es clicable (sin botón separado "Abrir").
     * Muestra: número, ícono SVG, nombre, línea descriptiva, chip de estado.
     */
    private Node buildToolCard(DiagnosticAction tool,
                               Consumer<DiagnosticAction> onSelectTool) {
        VBox card = new VBox(10);
        card.getStyleClass().add("tool-card");
        card.setPadding(new Insets(18, 18, 14, 18));
        card.setCursor(javafx.scene.Cursor.HAND);

        // ── Fila superior: número + ícono + nombre ────────────────────────────
        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Número de la herramienta
        Label lblNum = new Label(String.valueOf(tool.getId()));
        lblNum.getStyleClass().add("tool-card-number");

        // Ícono SVG
        Node icon = AppIcons.create(tool.getIconSymbol(), 22, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        // Nombre de la herramienta
        Label lblName = new Label(tool.getTitle());
        lblName.getStyleClass().add("tool-card-name");

        topRow.getChildren().addAll(lblNum, icon, lblName);

        // ── Línea descriptiva (en lenguaje claro) ─────────────────────────────
        Label lblTagline = new Label(tool.getShortDescription());
        lblTagline.getStyleClass().add("tool-card-tagline");

        // ── Chip de estado (sincronizado con SessionState) ────────────────────
        SessionState session = SessionState.getInstance();
        Label chipStatus = buildStatusChip(session.getStatus(tool.getId()),
                                           session.lastRunProperty(tool.getId()).get());

        // Escuchar cambios de estado de esta herramienta específica
        session.statusProperty(tool.getId()).addListener((obs, oldStatus, newStatus) -> {
            String lastRun = session.lastRunProperty(tool.getId()).get();
            HBox bottom = (HBox) card.getChildren().get(card.getChildren().size() - 1);
            bottom.getChildren().set(bottom.getChildren().size() - 1,
                    buildStatusChip(newStatus, lastRun));
        });
        session.lastRunProperty(tool.getId()).addListener((obs, o, lastRun) -> {
            ToolStatus st = session.getStatus(tool.getId());
            HBox bottom = (HBox) card.getChildren().get(card.getChildren().size() - 1);
            bottom.getChildren().set(bottom.getChildren().size() - 1,
                    buildStatusChip(st, lastRun));
        });

        // ── Fila inferior: chip de estado ─────────────────────────────────────
        HBox bottomRow = new HBox();
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomRow.getChildren().addAll(spacer, chipStatus);

        card.getChildren().addAll(topRow, lblTagline, bottomRow);

        // Toda la tarjeta es clicable
        card.setOnMouseClicked(e -> {
            if (onSelectTool != null) onSelectTool.accept(tool);
        });

        // Efecto hover: leve subida de opacidad del borde
        card.setOnMouseEntered(e -> card.getStyleClass().add("tool-card-hover"));
        card.setOnMouseExited(e -> card.getStyleClass().remove("tool-card-hover"));

        return card;
    }

    /** Construye el chip de estado de una herramienta. */
    private Label buildStatusChip(ToolStatus status, String lastRun) {
        String text = status.getLabel();
        if (status != ToolStatus.SIN_EJECUTAR && lastRun != null && !lastRun.isBlank()) {
            text = status.getLabel() + " · " + lastRun;
        }

        Label chip = new Label(text);
        chip.getStyleClass().add("tool-status-chip");
        chip.setStyle("-fx-text-fill: " + status.getColorHex() + ";"
                + "-fx-border-color: " + status.getColorHex() + ";"
                + "-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + ";");
        return chip;
    }
}
