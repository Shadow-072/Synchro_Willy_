package com.fixtool.ui;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.StabilitySpeedService;
import com.fixtool.service.StabilitySpeedService.SpeedResult;
import com.fixtool.service.StabilitySpeedService.StabilitySummary;
import com.fixtool.ui.components.ConnectionDialog;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.util.AppIcons;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 4: Estabilidad y velocidad.
 *
 * Incluye dos pestañas:
 *  1. Estabilidad: Gráfica de latencia en tiempo real, contadores (mín, prom, máx, pérdida, jitter),
 *     marcador de eventos y comparación con el histórico local.
 *  2. Velocidad: Prueba real de subida/bajada hacia el recurso compartido, velocímetro animado
 *     y comparación contra la velocidad negociada del adaptador.
 */
public class EstabilidadVelocidadView extends VBox {

    private final DiagnosticAction tool;
    private final ConnectionConfig config;
    private final StabilitySpeedService service = new StabilitySpeedService();

    // Controles pestaña 1: Estabilidad
    private final ComboBox<Integer> cmbDuration;
    private final ComboBox<Double>  cmbInterval;
    private final Button btnStartStability;
    private final Button btnStopStability;
    private final Button btnAddMarker;
    private final LineChart<Number, Number> latencyChart;
    private final XYChart.Series<Number, Number> latencySeries;
    private final Label lblMinMs;
    private final Label lblAvgMs;
    private final Label lblMaxMs;
    private final Label lblLossPct;
    private final Label lblJitterMs;
    private final Label lblHistoricalComp;
    private final Label lblStabilityVerdict;
    private AtomicBoolean stabilityCancelFlag;
    private final Map<Double, String> pendingMarkers = new ConcurrentHashMap<>();

    // Controles pestaña 2: Velocidad
    private final ComboBox<Integer> cmbSpeedSize;
    private final Button btnStartSpeed;
    private final Label lblShareStatus;
    private final ProgressBar speedProgress;
    private final Label lblUploadSpeed;
    private final Label lblDownloadSpeed;
    private final Label lblLinkSpeed;
    private final Label lblUtilization;
    private final Label lblSpeedVerdict;
    private final VBox resultCardSlot;

    public EstabilidadVelocidadView(DiagnosticAction tool, ConnectionConfig config) {
        this.tool = tool;
        this.config = config;

        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        // Cabecera
        HBox headerBar = buildHeaderBar();
        headerBar.setPadding(new Insets(20, 28, 12, 28));

        // Pestañas
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("plain-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // ── Instanciación de Controles Tab 1 (antes de buildTabEstabilidad) ─────
        cmbDuration = new ComboBox<>();
        cmbDuration.getItems().addAll(10, 30, 60);
        cmbDuration.setValue(10);
        cmbDuration.getStyleClass().add("button-secondary");

        cmbInterval = new ComboBox<>();
        cmbInterval.getItems().addAll(0.5, 1.0);
        cmbInterval.setValue(1.0);
        cmbInterval.getStyleClass().add("button-secondary");

        btnStartStability = new Button("Iniciar Prueba", AppIcons.create("activity", 14, "#0D1117"));
        btnStartStability.getStyleClass().add("button-primary");

        btnStopStability = new Button("Detener", AppIcons.create("stopcircle", 14, "#EF4444"));
        btnStopStability.getStyleClass().add("button-danger");
        btnStopStability.setDisable(true);

        btnAddMarker = new Button("Marcar evento...", AppIcons.create("link", 14, "#00B4D8"));
        btnAddMarker.getStyleClass().add("button-secondary");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Tiempo (s)");
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Latencia (ms)");
        yAxis.setAutoRanging(true);

        latencyChart = new LineChart<>(xAxis, yAxis);
        latencyChart.setTitle("Latencia hacia la BOSGAME");
        latencyChart.setLegendVisible(false);
        latencyChart.setAnimated(false);
        latencyChart.setPrefHeight(320);

        latencySeries = new XYChart.Series<>();
        latencyChart.getData().add(latencySeries);

        lblMinMs = new Label("--");
        lblAvgMs = new Label("--");
        lblMaxMs = new Label("--");
        lblLossPct = new Label("0%");
        lblJitterMs = new Label("--");
        lblHistoricalComp = new Label("Listo para iniciar medición.");
        lblHistoricalComp.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        lblStabilityVerdict = new Label("");
        lblStabilityVerdict.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + ";");

        // ── Instanciación de Controles Tab 2 (antes de buildTabVelocidad) ───────
        cmbSpeedSize = new ComboBox<>();
        cmbSpeedSize.getItems().addAll(10, 50, 100);
        cmbSpeedSize.setValue(10);
        cmbSpeedSize.getStyleClass().add("button-secondary");

        btnStartSpeed = new Button("Iniciar Test de Velocidad", AppIcons.create("upload", 14, com.fixtool.ui.theme.ThemeTokens.TEXT_DARK));
        btnStartSpeed.getStyleClass().add("button-primary");

        lblShareStatus = new Label("Recurso SMB: " + config.getUncPath());
        lblShareStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        speedProgress = new ProgressBar(0);
        speedProgress.setMaxWidth(Double.MAX_VALUE);
        speedProgress.setVisible(false);

        lblUploadSpeed = new Label("-- Mbps");
        lblDownloadSpeed = new Label("-- Mbps");
        lblLinkSpeed = new Label("1000 Mbps");
        lblUtilization = new Label("-- %");
        lblSpeedVerdict = new Label("");
        lblSpeedVerdict.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + ";");
        lblSpeedVerdict.setWrapText(true);

        resultCardSlot = new VBox(12);
        resultCardSlot.setPadding(new Insets(16, 0, 0, 0));

        // Pestaña 1
        Tab tab1 = new Tab("Estabilidad", buildTabEstabilidad());
        tab1.setClosable(false);

        // Pestaña 2
        Tab tab2 = new Tab("Velocidad", buildTabVelocidad());
        tab2.setClosable(false);

        tabPane.getTabs().addAll(tab1, tab2);
        this.getChildren().addAll(headerBar, tabPane);
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        VBox titleBox = new VBox(2);
        Label title = new Label("4. " + tool.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        Label subtitle = new Label(tool.getShortDescription());
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnHelp = new Button("?");
        btnHelp.getStyleClass().add("button-secondary");
        btnHelp.setStyle("-fx-font-weight: bold; -fx-padding: 4 10 4 10;");
        btnHelp.setOnAction(e -> showHelp());

        bar.getChildren().addAll(icon, titleBox, spacer, btnHelp);
        return bar;
    }

    private void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Estabilidad y velocidad – Ayuda");
        alert.setHeaderText("Evaluación de Desempeño de Enlace");
        alert.setContentText("• Pestaña Estabilidad: Envía ráfagas continuas de paquetes para graficar la latencia en tiempo real, "
                + "detectar pérdida de paquetes y calcular el jitter (variación de retraso). Puedes usar 'Marcar evento' cuando muevas el cable.\n\n"
                + "• Pestaña Velocidad: Mide el ancho de banda real escribiendo y leyendo un archivo de prueba temporal en la carpeta "
                + "compartida de la BOSGAME, y lo compara contra la capacidad negociada del cable (100 Mbps vs 1 Gbps).");
        alert.showAndWait();
    }

    // ── Tab 1: Estabilidad ────────────────────────────────────────────────────
    private Node buildTabEstabilidad() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");

        VBox root = new VBox(18);
        root.setPadding(new Insets(20, 28, 28, 28));
        root.getStyleClass().add("bg-root");

        // Barra de configuración
        HBox controlBar = new HBox(14);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.getStyleClass().add("card-panel");
        controlBar.setPadding(new Insets(12, 16, 12, 16));

        Label lblDur = new Label("Duración:");
        lblDur.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        Label lblInt = new Label("Intervalo:");
        lblInt.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        btnStartStability.setOnAction(e -> startStabilityTest());
        btnStopStability.setOnAction(e -> {
            if (stabilityCancelFlag != null) stabilityCancelFlag.set(true);
        });

        btnAddMarker.setOnAction(e -> addMarkerPrompt());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        controlBar.getChildren().addAll(lblDur, cmbDuration, lblInt, cmbInterval, btnStartStability, btnStopStability, sp, btnAddMarker);

        // Tarjetas de Métricas en vivo (Mínimo, Promedio, Máximo, Pérdida, Jitter)
        HBox metricsRow = new HBox(12);
        metricsRow.setAlignment(Pos.CENTER);

        metricsRow.getChildren().addAll(
                buildMetricCard("MÍNIMO", lblMinMs, com.fixtool.ui.theme.ThemeTokens.COLOR_OK),
                buildMetricCard("PROMEDIO", lblAvgMs, com.fixtool.ui.theme.ThemeTokens.ACCENT),
                buildMetricCard("MÁXIMO", lblMaxMs, com.fixtool.ui.theme.ThemeTokens.COLOR_WARN),
                buildMetricCard("PÉRDIDA", lblLossPct, com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR),
                buildMetricCard("JITTER", lblJitterMs, com.fixtool.ui.theme.ThemeTokens.ACCENT)
        );

        // Contenedor de la gráfica
        VBox chartBox = new VBox(10);
        chartBox.getStyleClass().add("card-panel");
        chartBox.setPadding(new Insets(16));
        chartBox.getChildren().addAll(latencyChart, lblHistoricalComp, lblStabilityVerdict);

        root.getChildren().addAll(controlBar, metricsRow, chartBox, resultCardSlot);
        scroll.setContent(root);
        return scroll;
    }

    private Node buildMetricCard(String label, Label valueLabel, String accentColor) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.getStyleClass().add("card-panel");
        HBox.setHgrow(box, Priority.ALWAYS);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        box.getChildren().addAll(lbl, valueLabel);
        return box;
    }

    private void startStabilityTest() {
        btnStartStability.setDisable(true);
        btnStopStability.setDisable(false);
        latencySeries.getData().clear();
        stabilityCancelFlag = new AtomicBoolean(false);
        pendingMarkers.clear();

        int dur = cmbDuration.getValue();
        double interval = cmbInterval.getValue();
        String ip = config.getIpTarget();

        Task<StabilitySummary> task = new Task<>() {
            @Override
            protected StabilitySummary call() {
                return service.runStabilityTest(
                        ip, dur, interval, stabilityCancelFlag,
                        sample -> Platform.runLater(() -> {
                            latencySeries.getData().add(new XYChart.Data<>(sample.timeSeconds, sample.latencyMs));
                            if (sample.eventMarker != null) {
                                lblHistoricalComp.setText("Evento marcado en " + sample.timeSeconds + "s: " + sample.eventMarker);
                            }
                        }),
                        pendingMarkers
                );
            }
        };

        task.setOnSucceeded(e -> {
            btnStartStability.setDisable(false);
            btnStopStability.setDisable(true);

            StabilitySummary summary = task.getValue();
            lblMinMs.setText(String.format("%.1f ms", summary.minMs));
            lblAvgMs.setText(String.format("%.1f ms", summary.avgMs));
            lblMaxMs.setText(String.format("%.1f ms", summary.maxMs));
            lblLossPct.setText(String.format("%.1f%%", summary.lossPct));
            lblJitterMs.setText(String.format("%.2f ms", summary.jitterMs));
            lblHistoricalComp.setText("Historial: " + summary.comparisonWithPrevious);
            lblStabilityVerdict.setText("Dictamen: " + summary.verdict);

            DiagnosticResult res = service.toDiagnosticResult(summary, null);
            SessionState.getInstance().setStatus(tool.getId(), summary.status, res);

            resultCardSlot.getChildren().clear();
            StandardResultCard card = new StandardResultCard(
                    summary.status,
                    "Estabilidad de Conexión: " + summary.status.getDisplayText(),
                    "Latencia Promedio",
                    String.format("%.1f ms", summary.avgMs),
                    summary.verdict + " " + summary.comparisonWithPrevious,
                    res.getTelemetry(),
                    null
            );
            resultCardSlot.getChildren().add(card);
        });

        task.setOnFailed(e -> {
            btnStartStability.setDisable(false);
            btnStopStability.setDisable(true);
            lblStabilityVerdict.setText("Fallo en la prueba de estabilidad: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void addMarkerPrompt() {
        TextInputDialog dialog = new TextInputDialog("Moví el cable Ethernet");
        dialog.setTitle("Marcar evento en la gráfica");
        dialog.setHeaderText("Añadir anotación temporal");
        dialog.setContentText("Escribe una nota corta para este momento:");
        Optional<String> res = dialog.showAndWait();
        res.ifPresent(note -> {
            // Se asocia al siguiente intervalo disponible
            pendingMarkers.put(999.0, note);
            lblHistoricalComp.setText("Evento marcado: '" + note + "'");
        });
    }

    // ── Tab 2: Velocidad ──────────────────────────────────────────────────────
    private Node buildTabVelocidad() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");

        VBox root = new VBox(18);
        root.setPadding(new Insets(20, 28, 28, 28));
        root.getStyleClass().add("bg-root");

        // Barra de control de velocidad
        HBox controlBar = new HBox(14);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.getStyleClass().add("card-panel");
        controlBar.setPadding(new Insets(14, 18, 14, 18));

        Label lblSize = new Label("Tamaño de prueba:");
        lblSize.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        btnStartSpeed.setOnAction(e -> startSpeedTest());

        Button btnConnectModal = new Button("Configurar Conexión", AppIcons.create("wifi", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnConnectModal.getStyleClass().add("button-secondary");
        btnConnectModal.setOnAction(e -> new ConnectionDialog(config).showAndWait());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        controlBar.getChildren().addAll(lblSize, cmbSpeedSize, new Label("MB"), btnStartSpeed, sp, btnConnectModal);

        // Tarjetas de Velocímetro y Métricas
        HBox speedCards = new HBox(12);
        speedCards.setAlignment(Pos.CENTER);

        lblUploadSpeed.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";");
        lblDownloadSpeed.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + ";");
        lblLinkSpeed.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        lblUtilization.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + ";");

        speedCards.getChildren().addAll(
                buildSpeedCard("SUBIDA", lblUploadSpeed, "upload"),
                buildSpeedCard("BAJADA", lblDownloadSpeed, "download"),
                buildSpeedCard("ENLACE FÍSICO", lblLinkSpeed, "link"),
                buildSpeedCard("APROVECHAMIENTO", lblUtilization, "activity")
        );

        // Panel de progreso y veredicto
        VBox resultBox = new VBox(10);
        resultBox.getStyleClass().add("card-panel");
        resultBox.setPadding(new Insets(16));
        resultBox.getChildren().addAll(lblShareStatus, speedProgress, lblSpeedVerdict);

        root.getChildren().addAll(controlBar, speedCards, resultBox);
        scroll.setContent(root);
        return scroll;
    }

    private Node buildSpeedCard(String title, Label valLabel, String iconKey) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("card-panel");
        HBox.setHgrow(card, Priority.ALWAYS);

        Node icon = AppIcons.create(iconKey, 20, com.fixtool.ui.theme.ThemeTokens.ACCENT);
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        card.getChildren().addAll(icon, t, valLabel);
        return card;
    }

    private void startSpeedTest() {
        int size = cmbSpeedSize.getValue();

        // Confirmación obligatoria antes de escribir en la BOSGAME
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar test de velocidad");
        confirm.setHeaderText("Prueba de velocidad hacia la BOSGAME");
        confirm.setContentText(String.format("Se escribirá un archivo temporal de %d MB en el recurso compartido (%s) y se leerá de vuelta.\n\n¿Deseas continuar?",
                size, config.getUncPath()));

        Optional<ButtonType> opt = confirm.showAndWait();
        if (opt.isEmpty() || opt.get() != ButtonType.OK) return;

        btnStartSpeed.setDisable(true);
        speedProgress.setVisible(true);
        speedProgress.setProgress(0);
        lblSpeedVerdict.setText("Realizando prueba de velocidad (Subida y Bajada)...");

        Task<SpeedResult> task = new Task<>() {
            @Override
            protected SpeedResult call() {
                return service.runSpeedTest(config, size, p -> Platform.runLater(() -> speedProgress.setProgress(p)));
            }
        };

        task.setOnSucceeded(e -> {
            btnStartSpeed.setDisable(false);
            speedProgress.setVisible(false);

            SpeedResult res = task.getValue();
            lblUploadSpeed.setText(String.format("%.1f Mbps", res.uploadSpeedMbps));
            lblDownloadSpeed.setText(String.format("%.1f Mbps", res.downloadSpeedMbps));
            lblLinkSpeed.setText(res.linkSpeedMbps + " Mbps");
            lblUtilization.setText(String.format("%.1f%%", res.linkUtilizationPct));
            lblSpeedVerdict.setText(res.verdict);

            if (res.isCableLimiting) {
                lblSpeedVerdict.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + ";");
            } else if (res.status == ToolStatus.OK) {
                lblSpeedVerdict.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + ";");
            } else {
                lblSpeedVerdict.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR + ";");
            }

            DiagnosticResult diagRes = service.toDiagnosticResult(null, res);
            SessionState.getInstance().setStatus(tool.getId(), res.status, diagRes);
        });

        task.setOnFailed(e -> {
            btnStartSpeed.setDisable(false);
            speedProgress.setVisible(false);
            lblSpeedVerdict.setText("Error en la prueba: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }
}
