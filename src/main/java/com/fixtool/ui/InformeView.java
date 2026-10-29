package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.DiagnosticService;
import com.fixtool.service.ReportService;
import com.fixtool.service.ReportService.ReportSummary;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Herramienta 10: Informe de diagnóstico.
 * Permite seleccionar qué herramientas incluir, ejecutar pendientes con un clic
 * y compilar el reporte final HTML para sustentación y entrega.
 */
public class InformeView extends VBox {

    private final DiagnosticAction tool;
    private final ConnectionConfig config;
    private final DiagnosticService diagService = new DiagnosticService();
    private final ReportService reportService = new ReportService();

    // Controles de selección de herramientas
    private final Map<Integer, CheckBox> toolCheckboxes = new HashMap<>();
    private final Map<Integer, Label> statusBadges = new HashMap<>();
    private final VBox checklistContainer;

    // Botones de acción
    private final Button btnGenerate;
    private final Button btnOpenReport;
    private final Button btnOpenFolder;
    private final ProgressIndicator progress;
    private final Label lblStatus;

    // Resumen del último informe generado
    private final VBox previewCard;
    private final VBox resultCardSlot;
    private Path lastReportPath = null;

    public InformeView(DiagnosticAction tool, ConnectionConfig config) {
        this.tool = tool;
        this.config = config != null ? config : new ConnectionConfig();

        setSpacing(0);
        getStyleClass().add("bg-root");

        ScrollPane rootScroll = new ScrollPane();
        rootScroll.setFitToWidth(true);
        rootScroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(rootScroll, Priority.ALWAYS);

        VBox content = new VBox(18);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("tool-content-pane");

        // ── 1. Encabezado ─────────────────────────────────────────────────────
        HBox header = buildHeader();

        // ── 2. Checklist de herramientas a incluir ────────────────────────────
        VBox checkSection = new VBox(10);
        checkSection.setPadding(new Insets(14, 18, 14, 18));
        checkSection.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        HBox checkHeader = new HBox(12);
        checkHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblCheckTitle = new Label("Seleccionar herramientas a incluir en el informe");
        lblCheckTitle.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnSelectAll = new Button("Seleccionar todas");
        btnSelectAll.getStyleClass().add("btn-secondary");
        btnSelectAll.setOnAction(e -> {
            toolCheckboxes.values().forEach(cb -> cb.setSelected(true));
            renderInitialPreview();
        });

        Button btnDeselectAll = new Button("Deseleccionar todas");
        btnDeselectAll.getStyleClass().add("btn-secondary");
        btnDeselectAll.setOnAction(e -> {
            toolCheckboxes.values().forEach(cb -> cb.setSelected(false));
            renderInitialPreview();
        });

        checkHeader.getChildren().addAll(lblCheckTitle, new Region(), btnSelectAll, btnDeselectAll);
        HBox.setHgrow(checkHeader.getChildren().get(1), Priority.ALWAYS);

        checklistContainer = new VBox(6);
        checklistContainer.setPadding(new Insets(8, 0, 4, 0));
        populateChecklist();

        checkSection.getChildren().addAll(checkHeader, checklistContainer);

        // ── 3. Barra de acciones de generación ────────────────────────────────
        btnGenerate = new Button("Generar informe HTML", AppIcons.create("filetext", 16, ThemeTokens.TEXT_PRIMARY));
        btnGenerate.getStyleClass().add("btn-primary");
        btnGenerate.setOnAction(e -> generateReport());

        btnOpenReport = new Button("Abrir informe", AppIcons.create("globe", 16, ThemeTokens.TEXT_PRIMARY));
        btnOpenReport.getStyleClass().add("btn-secondary");
        btnOpenReport.setDisable(true);
        btnOpenReport.setOnAction(e -> openLastReport());

        btnOpenFolder = new Button("Abrir carpeta de informes", AppIcons.create("folder", 16, ThemeTokens.TEXT_PRIMARY));
        btnOpenFolder.getStyleClass().add("btn-secondary");
        btnOpenFolder.setOnAction(e -> openReportsFolder());

        progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        lblStatus = new Label("Configura las herramientas deseadas y pulsa 'Generar informe HTML'.");
        lblStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 13px;");

        HBox actionToolbar = new HBox(12, btnGenerate, btnOpenReport, btnOpenFolder, progress, lblStatus);
        actionToolbar.setAlignment(Pos.CENTER_LEFT);
        actionToolbar.setPadding(new Insets(12, 16, 12, 16));
        actionToolbar.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        // ── 4. Tarjeta de vista previa y confirmación ────────────────────────
        previewCard = new VBox(10);
        previewCard.setPadding(new Insets(16));
        previewCard.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");
        renderInitialPreview();

        // ── 5. Ranura para la tarjeta estándar de resultados ─────────────────
        resultCardSlot = new VBox();
        DiagnosticResult prev = SessionState.getInstance().getResult(tool.getId());
        if (prev != null) {
            resultCardSlot.getChildren().add(new StandardResultCard(prev));
        }

        content.getChildren().addAll(header, checkSection, actionToolbar, previewCard, resultCardSlot);
        rootScroll.setContent(content);
        getChildren().add(rootScroll);
    }

    private HBox buildHeader() {
        Node icon = AppIcons.create(tool.getIconSymbol(), 28, ThemeTokens.ACCENT);
        VBox text = new VBox(4);
        Label title = new Label(tool.getId() + ". " + tool.getTitle());
        title.getStyleClass().add("tool-view-title");
        Label subtitle = new Label(tool.getShortDescription());
        subtitle.getStyleClass().add("tool-view-subtitle");
        text.getChildren().addAll(title, subtitle);

        HBox hb = new HBox(14, icon, text);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private void populateChecklist() {
        checklistContainer.getChildren().clear();
        SessionState state = SessionState.getInstance();

        for (DiagnosticAction act : diagService.getActions()) {
            if (act.getId() == 10) continue; // No auto-incluir la 10

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6;");

            CheckBox cb = new CheckBox(act.getId() + ". " + act.getTitle() + " (" + act.getSubtitle() + ")");
            cb.setSelected(true);
            cb.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 12px;");
            cb.setOnAction(e -> renderInitialPreview());
            toolCheckboxes.put(act.getId(), cb);

            DiagnosticResult res = state.getResult(act.getId());
            Label badge = new Label();
            updateBadgeStyle(badge, res);
            statusBadges.put(act.getId(), badge);

            row.getChildren().addAll(cb, new Region(), badge);
            HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);

            // Si está pendiente, botón "Ejecutar ahora"
            if (res == null) {
                Button btnRunNow = new Button("Ejecutar ahora");
                btnRunNow.getStyleClass().add("btn-secondary");
                btnRunNow.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");
                btnRunNow.setOnAction(e -> {
                    btnRunNow.setDisable(true);
                    btnRunNow.setText("Ejecutando...");
                    Task<DiagnosticResult> t = new Task<>() {
                        @Override
                        protected DiagnosticResult call() {
                            return act.execute(config);
                        }
                    };
                    t.setOnSucceeded(ev -> {
                        DiagnosticResult r = t.getValue();
                        state.setResult(act.getId(), r);
                        updateBadgeStyle(badge, r);
                        row.getChildren().remove(btnRunNow);
                        renderInitialPreview();
                    });
                    t.setOnFailed(ev -> {
                        btnRunNow.setDisable(false);
                        btnRunNow.setText("Reintentar");
                    });
                    new Thread(t).start();
                });
                row.getChildren().add(btnRunNow);
            }

            checklistContainer.getChildren().add(row);
        }
    }

    private void updateBadgeStyle(Label badge, DiagnosticResult res) {
        if (res == null) {
            badge.setText(ToolStatus.PENDIENTE.getDisplayText());
            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 4;",
                    ThemeTokens.COLOR_NEUTRAL, ThemeTokens.TEXT_DARK));
        } else {
            String b = res.getBadge();
            badge.setText(b);
            if (res.isOk()) {
                if (b.equalsIgnoreCase(ToolStatus.ATENCION.getDisplayText())) {
                    badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 4;",
                            ThemeTokens.COLOR_WARN, ThemeTokens.TEXT_DARK));
                } else {
                    badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 4;",
                            ThemeTokens.COLOR_OK, ThemeTokens.TEXT_DARK));
                }
            } else {
                badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 4;",
                        ThemeTokens.COLOR_ERROR, ThemeTokens.TEXT_DARK));
            }
        }
    }

    private void renderInitialPreview() {
        previewCard.getChildren().clear();

        HBox headRow = new HBox(12);
        headRow.setAlignment(Pos.CENTER_LEFT);

        Node logoNode = buildPreviewLogoNode();
        VBox titleBox = new VBox(2);
        Label title = new Label("Vista previa del informe — " + AppConfig.APP_NAME);
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label desc = new Label("El informe se compilará con estilo profesional oscuro (Dark Pro), resumen en semáforo, " +
                "las matrices de evidencia del Laboratorio de permisos y el historial de transferencias. " +
                "Todas las contraseñas quedan omitidas.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, desc);

        headRow.getChildren().addAll(logoNode, titleBox);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        previewCard.getChildren().addAll(headRow, buildSectionsPreviewBox());
    }

    private Node buildPreviewLogoNode() {
        try {
            java.io.InputStream stream = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE);
            if (stream != null) {
                javafx.scene.image.Image img = new javafx.scene.image.Image(stream, 48, 48, true, true);
                if (!img.isError()) {
                    javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                    iv.setFitWidth(48);
                    iv.setFitHeight(48);
                    return iv;
                }
            }
        } catch (Exception ignored) {}
        StackPane placeholder = new StackPane();
        placeholder.getStyleClass().add("logo-placeholder");
        placeholder.setPrefSize(48, 48);
        placeholder.getChildren().add(AppIcons.create("tool", 24, ThemeTokens.ACCENT));
        return placeholder;
    }

    private VBox buildSectionsPreviewBox() {
        VBox sectionsBox = new VBox(6);
        sectionsBox.setPadding(new Insets(8, 0, 0, 0));

        Label lblSec = new Label("Secciones incluidas en el informe:");
        lblSec.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        sectionsBox.getChildren().add(lblSec);

        SessionState state = SessionState.getInstance();
        for (DiagnosticAction act : diagService.getActions()) {
            if (act.getId() == 10) continue;
            CheckBox cb = toolCheckboxes.get(act.getId());
            if (cb != null && !cb.isSelected()) continue;

            DiagnosticResult res = state.getResult(act.getId());
            String summaryText = (res != null) ? res.getSummary() : "Sin ejecutar en esta sesión (Pendiente).";

            HBox itemRow = new HBox(10);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemRow.setPadding(new Insets(5, 10, 5, 10));
            itemRow.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 6; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 6;");

            Label lblTool = new Label(act.getId() + ". " + act.getTitle());
            lblTool.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 12px;");
            lblTool.setMinWidth(190);

            Label lblSum = new Label("— " + summaryText);
            lblSum.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
            lblSum.setWrapText(true);

            itemRow.getChildren().addAll(lblTool, lblSum);
            sectionsBox.getChildren().add(itemRow);
        }
        return sectionsBox;
    }

    private void generateReport() {
        Set<Integer> selected = new HashSet<>();
        toolCheckboxes.forEach((id, cb) -> {
            if (cb.isSelected()) selected.add(id);
        });

        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Selecciona al menos una herramienta para incluir en el informe.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        btnGenerate.setDisable(true);
        progress.setVisible(true);
        lblStatus.setText("Generando informe HTML...");

        Task<ReportSummary> task = new Task<>() {
            @Override
            protected ReportSummary call() throws Exception {
                return reportService.generateHtmlReport(diagService.getActions(), selected, config);
            }
        };

        task.setOnSucceeded(e -> {
            btnGenerate.setDisable(false);
            progress.setVisible(false);
            ReportSummary summary = task.getValue();
            lastReportPath = summary.filePath;
            btnOpenReport.setDisable(false);

            lblStatus.setText("Informe generado con éxito.");

            // Actualizar vista previa
            previewCard.getChildren().clear();
            Label title = new Label("¡Informe generado con éxito! ✔");
            title.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-font-weight: bold; -fx-font-size: 14px;");

            Label pathLbl = new Label("Ubicación: " + summary.filePath.toAbsolutePath());
            pathLbl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-family: monospace; -fx-font-size: 11px;");

            HBox stats = new HBox(16);
            stats.getChildren().addAll(
                    buildStat("Correcto", summary.okCount, ThemeTokens.COLOR_OK),
                    buildStat("Atención", summary.warnCount, ThemeTokens.COLOR_WARN),
                    buildStat("Errores", summary.errorCount, ThemeTokens.COLOR_ERROR),
                    buildStat("Pendientes", summary.pendingCount, ThemeTokens.COLOR_NEUTRAL)
            );

            previewCard.getChildren().addAll(title, pathLbl, stats, buildSectionsPreviewBox());

            // Guardar diagnóstico en sesión
            DiagnosticResult diag = reportService.toDiagnosticResult(summary);
            SessionState.getInstance().setResult(tool.getId(), diag);
            resultCardSlot.getChildren().setAll(new StandardResultCard(diag));
        });

        task.setOnFailed(e -> {
            btnGenerate.setDisable(false);
            progress.setVisible(false);
            lblStatus.setText("Error generando informe: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private VBox buildStat(String label, int val, String color) {
        VBox b = new VBox(2);
        b.setAlignment(Pos.CENTER);
        b.setPadding(new Insets(6, 12, 6, 12));
        b.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 6;");

        Label v = new Label(String.valueOf(val));
        v.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 18px; -fx-font-weight: bold;", color));

        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px;");

        b.getChildren().addAll(v, l);
        return b;
    }

    private void openLastReport() {
        if (lastReportPath == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(lastReportPath.toFile());
            } else {
                new ProcessBuilder("cmd.exe", "/c", "start", lastReportPath.toString()).start();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("cmd.exe", "/c", "start", lastReportPath.toString()).start();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, "No se pudo abrir el informe: " + ex.getMessage(), ButtonType.OK);
                a.showAndWait();
            }
        }
    }

    private void openReportsFolder() {
        String userHome = System.getProperty("user.home", ".");
        Path dir = Paths.get(userHome, AppConfig.WORK_FOLDER_NAME, "Informes");
        try {
            if (!java.nio.file.Files.exists(dir)) {
                java.nio.file.Files.createDirectories(dir);
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                new ProcessBuilder("explorer.exe", dir.toString()).start();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("explorer.exe", dir.toString()).start();
            } catch (Exception ignored) {}
        }
    }
}
