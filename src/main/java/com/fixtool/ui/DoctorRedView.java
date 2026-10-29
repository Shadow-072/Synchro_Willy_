package com.fixtool.ui;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.NetworkDoctorService;
import com.fixtool.service.NetworkDoctorService.DoctorDiagnosis;
import com.fixtool.service.NetworkDoctorService.StepResult;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.util.AppIcons;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

/**
 * Herramienta 3: Análisis de Conexión.
 *
 * Muestra el diagrama horizontal reactivo Mi PC → Cable → BOSGAME,
 * los 6 pasos de verificación de red (cable, IP, misma red, gateway, ping/TTL, SMB 445),
 * modos Automático y Paso a Paso, y la tarjeta de resultado estándar.
 */
public class DoctorRedView extends VBox {

    private final DiagnosticAction tool;
    private final ConnectionConfig config;
    private final NetworkDoctorService service = new NetworkDoctorService();

    // Controles de interfaz
    private final TextField txtTargetIp;
    private final Button btnAutoDiag;
    private final Button btnStepDiag;
    private final ProgressBar progressBar;
    private final Label lblStepProgress;

    // Nodos visuales del diagrama horizontal
    private final Circle nodePc;
    private final Circle nodeCable;
    private final Circle nodeTarget;
    private final Line line1;
    private final Line line2;

    // Contenedor de pasos
    private final VBox stepsContainer;
    private final VBox resultCardSlot;

    // Estado del modo paso a paso
    private int currentStepIndex = 0;
    private DoctorDiagnosis stepDiagnosis;

    public DoctorRedView(DiagnosticAction tool, ConnectionConfig config) {
        this.tool = tool;
        this.config = config;

        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(22);
        content.setPadding(new Insets(24, 28, 36, 28));
        content.getStyleClass().add("bg-root");

        // ── 1. Cabecera de la herramienta ─────────────────────────────────────
        HBox headerBar = buildHeaderBar();

        // ── 2. Barra de configuración de IP y modos ───────────────────────────
        HBox configBar = new HBox(14);
        configBar.setAlignment(Pos.CENTER_LEFT);
        configBar.getStyleClass().add("card-panel");
        configBar.setPadding(new Insets(14, 20, 14, 20));

        Label lblIp = new Label("IP Destino:");
        lblIp.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        txtTargetIp = new TextField(config != null ? config.getIpTarget() : "");
        txtTargetIp.setPrefWidth(150);
        txtTargetIp.getStyleClass().add("text-input-field");
        txtTargetIp.textProperty().addListener((obs, oldV, newV) -> {
            if (this.config != null) this.config.setIpTarget(newV != null ? newV.trim() : "");
        });

        btnAutoDiag = new Button("Diagnóstico Automático", AppIcons.create("activity", 14, com.fixtool.ui.theme.ThemeTokens.TEXT_DARK));
        btnAutoDiag.getStyleClass().add("button-primary");
        btnAutoDiag.setOnAction(e -> startAutomaticDiagnostic());

        btnStepDiag = new Button("Paso a Paso", AppIcons.create("chevronright", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnStepDiag.getStyleClass().add("button-secondary");
        btnStepDiag.setOnAction(e -> advanceStepByStep());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        configBar.getChildren().addAll(lblIp, txtTargetIp, btnAutoDiag, btnStepDiag, sp);

        // ── 3. Diagrama horizontal Mi PC ── Cable ── BOSGAME ──────────────────
        HBox diagramWrapper = new HBox(20);
        diagramWrapper.setAlignment(Pos.CENTER);
        diagramWrapper.getStyleClass().add("card-panel");
        diagramWrapper.setPadding(new Insets(22, 32, 22, 32));

        // Nodos
        nodePc = new Circle(18);
        nodePc.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");

        VBox boxPc = new VBox(6, nodePc, new Label("Mi PC") {{
            setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        }});
        boxPc.setAlignment(Pos.CENTER);

        line1 = new Line(0, 0, 80, 0);
        line1.setStyle("-fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-stroke-width: 4;");

        nodeCable = new Circle(14);
        nodeCable.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");

        VBox boxCable = new VBox(6, nodeCable, new Label("Cable Directo") {{
            setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
        }});
        boxCable.setAlignment(Pos.CENTER);

        line2 = new Line(0, 0, 80, 0);
        line2.setStyle("-fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-stroke-width: 4;");

        nodeTarget = new Circle(18);
        nodeTarget.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");

        VBox boxTarget = new VBox(6, nodeTarget, new Label("BOSGAME") {{
            setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        }});
        boxTarget.setAlignment(Pos.CENTER);

        diagramWrapper.getChildren().addAll(boxPc, line1, boxCable, line2, boxTarget);

        // Barra de progreso y texto
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        lblStepProgress = new Label("Listo para iniciar diagnóstico.");
        lblStepProgress.setStyle("-fx-font-size: 12px; -fx-text-fill: #8B949E;");

        VBox progressBox = new VBox(6, lblStepProgress, progressBar);

        // ── 4. Lista de Pasos ─────────────────────────────────────────────────
        stepsContainer = new VBox(10);

        // ── 5. Tarjeta de resultado estándar ──────────────────────────────────
        resultCardSlot = new VBox(12);

        content.getChildren().addAll(headerBar, configBar, diagramWrapper, progressBox, stepsContainer, resultCardSlot);
        scroll.setContent(content);
        this.getChildren().add(scroll);
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, "#00B4D8");

        VBox titleBox = new VBox(2);
        Label title = new Label("3. " + tool.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #E6EDF3;");
        Label subtitle = new Label(tool.getShortDescription());
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #8B949E;");
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
        alert.setTitle("Análisis de Conexión – Ayuda");
        alert.setHeaderText("Diagnóstico de Conectividad con la BOSGAME");
        alert.setContentText("El Análisis de Conexión comprueba de manera metódica los 6 puntos críticos de comunicación:\n\n"
                + "1. Adaptador y Cable: Verifica que el adaptador de red esté encendido y conectado.\n"
                + "2. Dirección IP: Detecta si la IP es válida o si cayó en error automático (169.254.x.x).\n"
                + "3. Misma Red: Comprueba mediante cálculo de bits si ambos equipos comparten subred.\n"
                + "4. Puerta de Enlace: En cable directo informa 'No aplica' sin marcar error.\n"
                + "5. Eco y TTL: Mide la respuesta ICMP y estima el sistema operativo remoto.\n"
                + "6. Puerto 445: Comprueba si las carpetas compartidas están disponibles para transferir.");
        alert.showAndWait();
    }

    private void startAutomaticDiagnostic() {
        String ip = txtTargetIp.getText().trim();
        if (!validarIpv4(ip)) {
            mostrarError("La dirección IP '" + ip + "' no tiene un formato IPv4 válido (ejemplo: 192.168.100.10).");
            return;
        }

        btnAutoDiag.setDisable(true);
        btnStepDiag.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        lblStepProgress.setText("Ejecutando diagnóstico integral de 6 pasos...");
        stepsContainer.getChildren().clear();
        resultCardSlot.getChildren().clear();
        resetDiagram();

        Task<DoctorDiagnosis> task = new Task<>() {
            @Override
            protected DoctorDiagnosis call() {
                return service.runFullDiagnostic(ip);
            }
        };

        task.setOnSucceeded(e -> {
            btnAutoDiag.setDisable(false);
            btnStepDiag.setDisable(false);
            progressBar.setVisible(false);

            DoctorDiagnosis diag = task.getValue();
            lblStepProgress.setText("Diagnóstico completado.");
            renderAllSteps(diag);
            updateDiagram(diag);
            renderResultCard(diag);
        });

        task.setOnFailed(e -> {
            btnAutoDiag.setDisable(false);
            btnStepDiag.setDisable(false);
            progressBar.setVisible(false);
            lblStepProgress.setText("Error en el diagnóstico: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void advanceStepByStep() {
        String ip = txtTargetIp.getText().trim();
        if (!validarIpv4(ip)) {
            mostrarError("La dirección IP '" + ip + "' no es válida.");
            return;
        }

        if (stepDiagnosis == null || currentStepIndex >= 6) {
            currentStepIndex = 0;
            stepsContainer.getChildren().clear();
            resultCardSlot.getChildren().clear();
            resetDiagram();
            lblStepProgress.setText("Iniciando modo Paso a Paso...");
            btnStepDiag.setText("Siguiente Paso →");

            Task<DoctorDiagnosis> task = new Task<>() {
                @Override
                protected DoctorDiagnosis call() {
                    return service.runFullDiagnostic(ip);
                }
            };
            task.setOnSucceeded(e -> {
                stepDiagnosis = task.getValue();
                renderNextStep();
            });
            new Thread(task).start();
        } else {
            renderNextStep();
        }
    }

    private void renderNextStep() {
        if (stepDiagnosis == null || currentStepIndex >= stepDiagnosis.steps.size()) {
            btnStepDiag.setText("Paso a Paso");
            if (stepDiagnosis != null) {
                renderResultCard(stepDiagnosis);
                updateDiagram(stepDiagnosis);
            }
            return;
        }

        StepResult step = stepDiagnosis.steps.get(currentStepIndex);
        stepsContainer.getChildren().add(buildStepRow(step));
        currentStepIndex++;
        lblStepProgress.setText(String.format("Paso %d de 6: %s", currentStepIndex, step.title));

        if (currentStepIndex >= 6) {
            btnStepDiag.setText("Reiniciar Paso a Paso");
            updateDiagram(stepDiagnosis);
            renderResultCard(stepDiagnosis);
        }
    }

    private void renderAllSteps(DoctorDiagnosis diag) {
        stepsContainer.getChildren().clear();
        for (StepResult s : diag.steps) {
            stepsContainer.getChildren().add(buildStepRow(s));
        }
    }

    private Node buildStepRow(StepResult step) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-panel");
        row.setPadding(new Insets(12, 16, 12, 16));

        // Ícono de estado del paso
        String iconKey = step.status == ToolStatus.OK ? "checkcircle" :
                (step.status == ToolStatus.ATENCION ? "alerttriangle" : "xcircle");
        Node icon = AppIcons.create(iconKey, 20, step.status.getColorHex());

        VBox textBox = new VBox(3);
        Label title = new Label(step.stepNumber + ". " + step.title);
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        Label human = new Label(step.humanExplanation);
        human.setStyle("-fx-font-size: 12px; -fx-text-fill: " + step.status.getColorHex() + ";");
        human.setWrapText(true);

        Label tech = new Label(step.technicalDetail);
        tech.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        textBox.getChildren().addAll(title, human, tech);
        row.getChildren().addAll(icon, textBox);
        return row;
    }

    private void updateDiagram(DoctorDiagnosis diag) {
        // Nodos se iluminan según el avance y resultado
        StepResult s1 = diag.steps.size() > 0 ? diag.steps.get(0) : null;
        StepResult s3 = diag.steps.size() > 2 ? diag.steps.get(2) : null;
        StepResult s5 = diag.steps.size() > 4 ? diag.steps.get(4) : null;

        String colorPc = (s1 != null && s1.status != ToolStatus.PROBLEMA) ? com.fixtool.ui.theme.ThemeTokens.COLOR_OK : com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR;
        nodePc.setStyle("-fx-fill: " + colorPc + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-stroke-width: 2;");

        String colorCable = (s3 != null && s3.status != ToolStatus.PROBLEMA) ? com.fixtool.ui.theme.ThemeTokens.COLOR_OK :
                (s3 != null && s3.status == ToolStatus.ATENCION ? com.fixtool.ui.theme.ThemeTokens.COLOR_WARN : com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);
        line1.setStyle("-fx-stroke: " + colorCable + "; -fx-stroke-width: 4;");
        line2.setStyle("-fx-stroke: " + colorCable + "; -fx-stroke-width: 4;");
        nodeCable.setStyle("-fx-fill: " + colorCable + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-stroke-width: 2;");

        String colorTarget = (s5 != null && s5.status == ToolStatus.OK) ? com.fixtool.ui.theme.ThemeTokens.COLOR_OK :
                (s5 != null && s5.status == ToolStatus.ATENCION ? com.fixtool.ui.theme.ThemeTokens.COLOR_WARN : com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);
        nodeTarget.setStyle("-fx-fill: " + colorTarget + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-stroke-width: 2;");
    }

    private void resetDiagram() {
        nodePc.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");
        nodeCable.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");
        nodeTarget.setStyle("-fx-fill: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 3;");
        line1.setStyle("-fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-stroke-width: 4;");
        line2.setStyle("-fx-stroke: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-stroke-width: 4;");
    }

    private void renderResultCard(DoctorDiagnosis diag) {
        DiagnosticResult res = service.toDiagnosticResult(diag);
        SessionState.getInstance().setStatus(tool.getId(), diag.overallStatus, res);

        resultCardSlot.getChildren().clear();
        StandardResultCard card = new StandardResultCard(
                diag.overallStatus,
                "Dictamen de Red: " + diag.overallStatus.getDisplayText(),
                "Destino Evaluado",
                diag.targetIp,
                diag.mainVerdict + " " + String.join(" ", diag.recommendations),
                res.getTelemetry(),
                null
        );
        resultCardSlot.getChildren().add(card);
    }

    private boolean validarIpv4(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void mostrarError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error en Análisis de Conexión");
        alert.setHeaderText("Parámetro inválido");
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
