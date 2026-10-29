package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.service.SystemInfoService;
import com.fixtool.service.SystemInfoService.HardwareSnapshot;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.util.AppIcons;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Herramienta 1: Chequeo de Estado.
 *
 * Muestra el estado del PC con medidor de salud circular animado (0-100),
 * tarjetas interactivas por componente (CPU, RAM, Disco, Sistema, Batería, Uptime),
 * interruptor Simple/Técnico y refresco en tiempo real cada 2 segundos.
 */
public class RadiografiaView extends VBox {

    private final DiagnosticAction tool;
    private final SystemInfoService service = new SystemInfoService();

    // Controles dinámicos
    private final ScrollPane scroll;
    private final Label lblScoreVal;
    private final Label lblScoreStatus;
    private final Arc   scoreArc;
    private final VBox  cardsContainer;
    private final VBox  resultCardSlot;
    private final ToggleButton btnModeToggle;
    private StandardResultCard currentResultCard;

    private boolean isTechnicalMode = false;
    private Timeline liveRefreshTimeline;
    private HardwareSnapshot currentSnapshot;

    public RadiografiaView(DiagnosticAction tool) {
        this.tool = tool;
        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        this.scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(24);
        content.setPadding(new Insets(24, 28, 36, 28));
        content.getStyleClass().add("bg-root");

        // ── 1. Barra de cabecera de la herramienta ─────────────────────────────
        HBox headerBar = buildHeaderBar();

        // ── 2. Medidor Circular de Salud (0-100) ──────────────────────────────
        HBox gaugeRow = new HBox(32);
        gaugeRow.setAlignment(Pos.CENTER);
        gaugeRow.getStyleClass().add("card-panel");
        gaugeRow.setPadding(new Insets(24, 32, 24, 32));

        // Gráfico de arco circular
        StackPane gaugeCircle = new StackPane();
        gaugeCircle.setPrefSize(160, 160);

        Circle bgTrack = new Circle(70);
        bgTrack.setFill(Color.TRANSPARENT);
        bgTrack.setStroke(Color.web(com.fixtool.ui.theme.ThemeTokens.BORDER_BASE));
        bgTrack.setStrokeWidth(12);

        scoreArc = new Arc(0, 0, 70, 70, 90, 0);
        scoreArc.setType(ArcType.OPEN);
        scoreArc.setFill(Color.TRANSPARENT);
        scoreArc.setStroke(Color.web(com.fixtool.ui.theme.ThemeTokens.COLOR_OK));
        scoreArc.setStrokeWidth(12);

        VBox scoreTextBox = new VBox(2);
        scoreTextBox.setAlignment(Pos.CENTER);

        lblScoreVal = new Label("--");
        lblScoreVal.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        Label lblScoreMax = new Label("de 100");
        lblScoreMax.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        scoreTextBox.getChildren().addAll(lblScoreVal, lblScoreMax);
        gaugeCircle.getChildren().addAll(bgTrack, scoreArc, scoreTextBox);

        // Mensaje descriptivo al lado del medidor
        VBox gaugeInfo = new VBox(8);
        gaugeInfo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(gaugeInfo, Priority.ALWAYS);

        Label lblGaugeTitle = new Label("Puntuación de Salud Global");
        lblGaugeTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        lblScoreStatus = new Label("Evaluando componentes...");
        lblScoreStatus.setStyle("-fx-font-size: 14px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";");
        lblScoreStatus.setWrapText(true);

        Label lblRuleSummary = new Label("Calculado a partir de memoria en uso, espacio disponible en disco, tiempo encendido y estado de batería.");
        lblRuleSummary.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        lblRuleSummary.setWrapText(true);

        gaugeInfo.getChildren().addAll(lblGaugeTitle, lblScoreStatus, lblRuleSummary);
        gaugeRow.getChildren().addAll(gaugeCircle, gaugeInfo);

        // ── 3. Contenedor de Tarjetas por Componente ──────────────────────────
        cardsContainer = new VBox(12);

        // ── 4. Ranura para la Tarjeta de Resultado Estándar ────────────────────
        resultCardSlot = new VBox(12);

        content.getChildren().addAll(headerBar, gaugeRow, cardsContainer, resultCardSlot);
        scroll.setContent(content);
        this.getChildren().add(scroll);

        // Asignar toggle
        btnModeToggle = new ToggleButton("Vista Técnica");
        btnModeToggle.getStyleClass().add("button-secondary");
        btnModeToggle.setOnAction(e -> {
            isTechnicalMode = btnModeToggle.isSelected();
            btnModeToggle.setText(isTechnicalMode ? "Vista Simple" : "Vista Técnica");
            if (currentSnapshot != null) renderComponents(currentSnapshot);
        });

        // Carga inicial y timer de refresco
        refreshData();
        setupLiveRefresh();
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        VBox titleBox = new VBox(2);
        Label title = new Label("1. " + tool.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        Label subtitle = new Label(tool.getShortDescription());
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Botón ayuda discreta (?)
        Button btnHelp = new Button("?");
        btnHelp.getStyleClass().add("button-secondary");
        btnHelp.setStyle("-fx-font-weight: bold; -fx-padding: 4 10 4 10;");
        btnHelp.setOnAction(e -> showHelpDialog());

        // Botón Actualizar
        Button btnRefresh = new Button("Actualizar", AppIcons.create("refreshcw", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnRefresh.getStyleClass().add("button-secondary");
        btnRefresh.setOnAction(e -> refreshData());

        bar.getChildren().addAll(icon, titleBox, spacer, btnHelp, btnRefresh);
        return bar;
    }

    private void showHelpDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Chequeo de Estado – Ayuda");
        alert.setHeaderText("¿Cómo funciona esta herramienta?");
        alert.setContentText("El Chequeo de Estado analiza el estado de tu computador evaluando el uso del procesador, "
                + "la memoria RAM ocupada, el espacio libre en tus discos y los días continuos de encendido.\n\n"
                + "Puntuación de salud:\n"
                + "• 80 a 100 (Verde): Estado óptimo.\n"
                + "• 60 a 79 (Amarillo): Atención requerida (p. ej. RAM alta o poco espacio).\n"
                + "• Menos de 60 (Rojo): Problema que compromete el rendimiento.\n\n"
                + "Puedes alternar entre Vista Simple y Vista Técnica con el botón superior.");
        alert.showAndWait();
    }

    /**
     * Recopila datos en segundo plano y actualiza la interfaz.
     */
    public void refreshData() {
        HardwareSnapshot snap = service.collectSnapshot();
        this.currentSnapshot = snap;
        updateUI(snap);
    }

    private void updateUI(HardwareSnapshot snap) {
        // Actualizar medidor
        int score = snap.healthScore;
        lblScoreVal.setText(String.valueOf(score));

        // Semáforo de color
        String colorHex;
        if (score >= AppConfig.HEALTH_THRESHOLD_GREEN) {
            colorHex = com.fixtool.ui.theme.ThemeTokens.COLOR_OK;
            lblScoreStatus.setText("Tu computador está en excelentes condiciones");
            lblScoreStatus.setStyle("-fx-font-size: 14px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + "; -fx-font-weight: bold;");
        } else if (score >= AppConfig.HEALTH_THRESHOLD_YELLOW) {
            colorHex = com.fixtool.ui.theme.ThemeTokens.COLOR_WARN;
            lblScoreStatus.setText("Atención: Hay componentes que requieren revisión");
            lblScoreStatus.setStyle("-fx-font-size: 14px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + "; -fx-font-weight: bold;");
        } else {
            colorHex = com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR;
            lblScoreStatus.setText("Problema detectado: El rendimiento del equipo está comprometido");
            lblScoreStatus.setStyle("-fx-font-size: 14px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR + "; -fx-font-weight: bold;");
        }

        // Animación suave del arco circular
        double targetAngle = -(score / 100.0) * 360.0;
        Timeline arcAnim = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(scoreArc.lengthProperty(), targetAngle),
                        new KeyValue(scoreArc.strokeProperty(), Color.web(colorHex))
                )
        );
        arcAnim.play();

        // Renderizar tarjetas de componentes
        renderComponents(snap);

        // Registrar en SessionState y renderizar StandardResultCard preservando estado expandido y scroll
        DiagnosticResult result = service.toDiagnosticResult(snap);
        SessionState.getInstance().setStatus(tool.getId(), snap.healthStatus, result);

        boolean wasExpanded = (currentResultCard != null && currentResultCard.isTechnicalDetailExpanded());
        double vpos = (scroll != null) ? scroll.getVvalue() : 0.0;

        currentResultCard = new StandardResultCard(
                snap.healthStatus,
                "Diagnóstico del Equipo: " + snap.healthStatus.getDisplayText(),
                "Puntuación de Salud",
                score + " / 100",
                snap.healthPenalties.isEmpty()
                        ? "Tu equipo opera de forma estable con recursos suficientes."
                        : String.join(". ", snap.healthPenalties) + ".",
                result.getTelemetry(),
                null
        );
        if (wasExpanded) {
            currentResultCard.setTechnicalDetailExpanded(true);
        }

        resultCardSlot.getChildren().setAll(currentResultCard);

        if (scroll != null && vpos > 0) {
            Platform.runLater(() -> scroll.setVvalue(vpos));
        }
    }

    private void renderComponents(HardwareSnapshot snap) {
        cardsContainer.getChildren().clear();

        // Fila de interruptor Simple / Técnico
        HBox toggleRow = new HBox(12);
        toggleRow.setAlignment(Pos.CENTER_RIGHT);
        Label lblModo = new Label(isTechnicalMode ? "Modo: Técnico detallado" : "Modo: Simple amigable");
        lblModo.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        toggleRow.getChildren().addAll(lblModo, btnModeToggle);
        cardsContainer.getChildren().add(toggleRow);

        // Tarjetas
        cardsContainer.getChildren().add(buildComponentCard(
                "monitor", "Procesador (CPU)",
                isTechnicalMode ? snap.cpuModel : "Procesador multinúcleo",
                String.format(Locale.US, "%.1f%% de uso actual (%d núcleos lógicos)", Math.max(0, snap.cpuLoadPct), snap.cpuCores),
                isTechnicalMode
                        ? "Arquitectura: " + snap.osArch + " | Hilos de ejecución disponibles: " + snap.cpuCores
                        : (snap.cpuLoadPct > 80 ? "El procesador está bajo carga pesada." : "El procesador trabaja con holgura.")
        ));

        cardsContainer.getChildren().add(buildComponentCard(
                "activity", "Memoria RAM",
                String.format(Locale.US, "%s en uso de %s", SystemInfoService.formatGb(snap.usedRamBytes), SystemInfoService.formatGb(snap.totalRamBytes)),
                String.format(Locale.US, "%.1f%% ocupada", snap.ramUsagePct),
                isTechnicalMode
                        ? "Bytes libres: " + SystemInfoService.formatBytes(snap.totalRamBytes - snap.usedRamBytes)
                        : (snap.ramUsagePct > 85 ? "La memoria está casi llena; cerrar programas pesados ayudará." : "Tienes memoria suficiente para tus tareas.")
        ));

        String diskSummary = snap.disks.isEmpty() ? "No disponible" :
                snap.disks.get(0).mountPoint + "  " + SystemInfoService.formatBytes(snap.primaryDiskFreeBytes) + " libres (" + String.format(Locale.US, "%.1f%%", snap.primaryDiskFreePct) + ")";
        cardsContainer.getChildren().add(buildComponentCard(
                "harddrive", "Almacenamiento (Disco)",
                diskSummary,
                snap.primaryDiskFreePct < 15 ? "Espacio reducido" : "Espacio adecuado",
                isTechnicalMode
                        ? "Capacidad total: " + SystemInfoService.formatBytes(snap.primaryDiskTotalBytes) + " | Libre: " + SystemInfoService.formatBytes(snap.primaryDiskFreeBytes)
                        : (snap.primaryDiskFreePct < 15 ? "Queda poco espacio libre en el disco principal." : "Tu disco tiene suficiente espacio para trabajar.")
        ));

        cardsContainer.getChildren().add(buildComponentCard(
                "info", "Sistema y Equipo",
                snap.osName,
                snap.hostName + " (" + snap.currentUser + ")",
                isTechnicalMode
                        ? "Versión: " + snap.osVersion + " | " + snap.osBuild
                        : "Windows funcionando con usuario regular sin privilegios de administrador."
        ));

        cardsContainer.getChildren().add(buildComponentCard(
                "link", "Tiempo de Encendido (Uptime)",
                SystemInfoService.formatUptime(snap.uptimeMillis),
                snap.uptimeDays > 14 ? "Reinicio recomendado" : "Normal",
                isTechnicalMode
                        ? "Milisegundos de actividad de JVM: " + snap.uptimeMillis + " ms"
                        : (snap.uptimeDays > 14 ? "Lleva más de 2 semanas sin reiniciar; un reinicio refresca el sistema." : "El equipo se reinicia con frecuencia adecuada.")
        ));

        cardsContainer.getChildren().add(buildComponentCard(
                "power", "Batería",
                snap.batteryStatus,
                snap.batteryPresent ? (snap.batteryPercent + "%") : "Alimentación fija",
                isTechnicalMode
                        ? (snap.batteryPresent ? "Dispositivo portátil con sensor ACPI activo." : "Sensor de batería no detectado en bus de sistema.")
                        : (snap.batteryPresent ? "Batería operativa." : "Equipo de escritorio o conectado a la red eléctrica.")
        ));
    }

    private Node buildComponentCard(String iconKey, String title, String mainValue, String badge, String explanation) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(iconKey, 18, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblBadge = new Label(badge);
        lblBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        topRow.getChildren().addAll(icon, lblTitle, spacer, lblBadge);

        Label lblMain = new Label(mainValue);
        lblMain.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";");

        Label lblExp = new Label(explanation);
        lblExp.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        lblExp.setWrapText(true);

        card.getChildren().addAll(topRow, lblMain, lblExp);

        // Efecto hover sutil
        card.setOnMouseEntered(e -> card.setStyle("-fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";"));
        card.setOnMouseExited(e -> card.setStyle(""));

        return card;
    }

    /**
     * Refresco suave cada 2 segundos mientras la vista está activa.
     */
    private void setupLiveRefresh() {
        liveRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (this.getScene() != null && this.isVisible()) {
                // Si el usuario tiene abierto el detalle técnico, pausar la reconstrucción
                // periódica para evitar que se cierre y que el scroll salte (Criterio B2)
                if (currentResultCard != null && currentResultCard.isTechnicalDetailExpanded()) {
                    return;
                }
                refreshData();
            }
        }));
        liveRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        liveRefreshTimeline.play();
    }

    /**
     * Detiene el timer cuando se abandona la vista.
     */
    public void cleanup() {
        if (liveRefreshTimeline != null) {
            liveRefreshTimeline.stop();
        }
    }

    /**
     * Reanuda el timer cuando se vuelve a la vista.
     */
    public void resume() {
        if (liveRefreshTimeline != null) {
            liveRefreshTimeline.play();
        }
    }
}
