package com.fixtool.ui;

import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.PermissionLabService;
import com.fixtool.service.PermissionLabService.ExperimentEntry;
import com.fixtool.service.PermissionLabService.ExperimentEvidence;
import com.fixtool.service.PermissionLabService.OperationResult;
import com.fixtool.service.SystemInfoService;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Herramienta 6: Laboratorio de permisos (prueba obligatoria).
 *
 * Demuestra experimentalmente la efectividad de los permisos NTFS de lectura y escritura.
 * Incluye archivo de prueba en el centro, dos interruptores de permisos reales,
 * botones interactivos de lectura y escritura, y el Modo Experimento con tabla
 * de evidencia esperada vs. obtenida para la sustentación del proyecto.
 */
public class LaboratorioPermisosView extends VBox {

    private final DiagnosticAction tool;
    private final PermissionLabService service = new PermissionLabService();

    // Origen de archivo (Local / Remoto extensible para Fase posterior)
    public enum FileOrigin {
        LOCAL("Local"),
        REMOTE("Remoto (BOSGAME)");

        private final String display;
        FileOrigin(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private FileOrigin currentOrigin = FileOrigin.LOCAL;
    private final Label lblOriginBadge;
    private final Button btnSaveContent;
    private final Button btnDiscardContent;
    private String lastLoadedContent = "";

    // Controles de archivo central
    private final Label lblFilePath;
    private final Label lblFileSize;
    private final Label lblFileStatus;
    private final TextArea txtFileContent;

    // Interruptores de permisos
    private final ToggleButton btnToggleRead;
    private final ToggleButton btnToggleWrite;

    // Botones de acción manual
    private final Button btnTryRead;
    private final Button btnTryWrite;
    private final Button btnResetPermissions;

    // Modo Experimento
    private final Button btnRunExperiment;
    private final Button btnAddEvidence;
    private final VBox experimentTableBox;
    private final VBox resultCardSlot;
    private ExperimentEvidence currentEvidence;

    public LaboratorioPermisosView(DiagnosticAction tool) {
        this.tool = tool;

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

        // ── 2. Tarjeta Central del Archivo de Prueba ──────────────────────────
        VBox fileCard = new VBox(14);
        fileCard.getStyleClass().add("card-panel");
        fileCard.setPadding(new Insets(18, 24, 18, 24));

        HBox fileHeader = new HBox(12);
        fileHeader.setAlignment(Pos.CENTER_LEFT);

        Node iconFile = AppIcons.create("filetext", 22, ThemeTokens.ACCENT);
        VBox fileInfo = new VBox(2);
        Label titleFile = new Label("Archivo de Prueba de Permisos (prueba_permisos.txt)");
        titleFile.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        lblFilePath = new Label(service.getTestFilePath().toString());
        lblFilePath.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        fileInfo.getChildren().addAll(titleFile, lblFilePath);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblOriginBadge = new Label("Origen: " + currentOrigin.getDisplay());
        lblOriginBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 3 8 3 8; -fx-font-weight: bold;");

        lblFileSize = new Label("-- B");
        lblFileSize.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s; -fx-background-color: %s; -fx-padding: 3 8 3 8; -fx-background-radius: 10px; -fx-font-weight: bold;",
                ThemeTokens.TEXT_DARK, ThemeTokens.ACCENT));

        fileHeader.getChildren().addAll(iconFile, fileInfo, sp, lblOriginBadge, lblFileSize);

        // Interruptores a los lados del archivo
        HBox togglesRow = new HBox(20);
        togglesRow.setAlignment(Pos.CENTER);
        togglesRow.setPadding(new Insets(6, 0, 6, 0));

        btnToggleRead = new ToggleButton("Lectura: PERMITIDA");
        btnToggleRead.setSelected(true);
        btnToggleRead.getStyleClass().add("button-primary");
        btnToggleRead.setOnAction(e -> applyManualToggles());

        btnToggleWrite = new ToggleButton("Escritura: PERMITIDA");
        btnToggleWrite.setSelected(true);
        btnToggleWrite.getStyleClass().add("button-primary");
        btnToggleWrite.setOnAction(e -> applyManualToggles());

        btnResetPermissions = new Button("Restablecer", AppIcons.create("refreshcw", 12, ThemeTokens.ACCENT));
        btnResetPermissions.getStyleClass().add("button-secondary");
        btnResetPermissions.setOnAction(e -> resetOriginalState());

        togglesRow.getChildren().addAll(btnToggleRead, btnToggleWrite, btnResetPermissions);

        // Barra de herramientas del editor (Guardar cambios, Descartar, Intentar leer)
        HBox editorToolbar = new HBox(12);
        editorToolbar.setAlignment(Pos.CENTER_LEFT);

        Label lblEditorTitle = new Label("Editor de Contenido del Archivo:");
        lblEditorTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        Region spToolbar = new Region();
        HBox.setHgrow(spToolbar, Priority.ALWAYS);

        btnSaveContent = new Button("Guardar cambios", AppIcons.create("checkcircle", 13, ThemeTokens.TEXT_DARK));
        btnSaveContent.getStyleClass().add("button-primary");
        btnSaveContent.setOnAction(e -> executeSaveContent());

        btnDiscardContent = new Button("Descartar", AppIcons.create("xcircle", 13, ThemeTokens.TEXT_SECONDARY));
        btnDiscardContent.getStyleClass().add("button-secondary");
        btnDiscardContent.setOnAction(e -> executeDiscardContent());

        btnTryRead = new Button("Recargar", AppIcons.create("refreshcw", 12, ThemeTokens.ACCENT));
        btnTryRead.getStyleClass().add("button-secondary");
        btnTryRead.setOnAction(e -> executeManualRead());

        btnTryWrite = new Button("Anexar Línea", AppIcons.create("filetext", 12, ThemeTokens.ACCENT));
        btnTryWrite.getStyleClass().add("button-secondary");
        btnTryWrite.setOnAction(e -> executeManualWrite());

        editorToolbar.getChildren().addAll(lblEditorTitle, spToolbar, btnSaveContent, btnDiscardContent, btnTryRead, btnTryWrite);

        lblFileStatus = new Label("Permisos normales: Lectura y escritura activas.");
        lblFileStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.COLOR_OK + ";");
        lblFileStatus.setAlignment(Pos.CENTER_LEFT);

        txtFileContent = new TextArea();
        txtFileContent.setPrefHeight(180);
        txtFileContent.setEditable(true);
        txtFileContent.setWrapText(true);
        txtFileContent.setStyle(
                "-fx-control-inner-background: #151D27;" +
                "-fx-background-color: #151D27;" +
                "-fx-text-fill: #E8EDF3;" +
                "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
                "-fx-font-size: 14px;" +
                "-fx-highlight-fill: " + ThemeTokens.ACCENT + ";" +
                "-fx-highlight-text-fill: " + ThemeTokens.TEXT_DARK + ";" +
                "-fx-padding: 10px;" +
                "-fx-border-color: " + ThemeTokens.BORDER_CONTROL + ";" +
                "-fx-border-radius: 6px;" +
                "-fx-background-radius: 6px;"
        );
        txtFileContent.setPromptText("Escribe aquí el contenido del archivo de prueba...");

        fileCard.getChildren().addAll(fileHeader, togglesRow, editorToolbar, lblFileStatus, txtFileContent);

        // ── 3. Modo Experimento (4 Combinaciones) ─────────────────────────────
        VBox experimentSection = new VBox(12);
        experimentSection.getStyleClass().add("card-panel");
        experimentSection.setPadding(new Insets(18, 24, 18, 24));

        HBox expHeader = new HBox(12);
        expHeader.setAlignment(Pos.CENTER_LEFT);

        Label expTitle = new Label("Modo Experimento: Validación de las 4 Combinaciones de Permisos");
        expTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        Region expSp = new Region();
        HBox.setHgrow(expSp, Priority.ALWAYS);

        btnRunExperiment = new Button("Ejecutar Experimento Completo", AppIcons.create("activity", 14, ThemeTokens.TEXT_DARK));
        btnRunExperiment.getStyleClass().add("button-primary");
        btnRunExperiment.setOnAction(e -> runFullExperiment());

        btnAddEvidence = new Button("Agregar evidencia al informe", AppIcons.create("checkcircle", 14, ThemeTokens.COLOR_OK));
        btnAddEvidence.getStyleClass().add("button-secondary");
        btnAddEvidence.setDisable(true);
        btnAddEvidence.setOnAction(e -> addEvidenceToSession());

        expHeader.getChildren().addAll(expTitle, expSp, btnRunExperiment, btnAddEvidence);

        Label expDesc = new Label("Recorre automáticamente las 4 combinaciones posibles, aplicando y probando permisos reales para certificar la tabla de evidencia.");
        expDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        experimentTableBox = new VBox(6);
        renderEmptyExperimentTable();

        experimentSection.getChildren().addAll(expHeader, expDesc, experimentTableBox);

        // ── 4. Tarjeta de resultado estándar ──────────────────────────────────
        resultCardSlot = new VBox(12);

        content.getChildren().addAll(headerBar, fileCard, experimentSection, resultCardSlot);
        scroll.setContent(content);
        this.getChildren().add(scroll);

        // Inicialización del archivo de prueba
        initializeTestFile();
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, ThemeTokens.ACCENT);

        VBox titleBox = new VBox(2);
        Label title = new Label("6. " + tool.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");
        Label subtitle = new Label(tool.getShortDescription());
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
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
        alert.setTitle("Laboratorio de permisos – Ayuda");
        alert.setHeaderText("Comprobación Experimental de Permisos NTFS");
        alert.setContentText("Esta herramienta prueba en un entorno seguro y controlado cómo afectan los permisos de Windows al acceso a archivos:\n\n"
                + "• Prueba de Lectura: Demuestra que al denegar lectura, el sistema operativo bloquea la consulta del contenido.\n"
                + "• Prueba de Escritura: Demuestra que al denegar escritura, no se pueden realizar modificaciones ni anexos.\n"
                + "• Modo Experimento: Evalúa las 4 combinaciones posibles (Ambos, Solo Lectura, Solo Escritura, Ninguno) "
                + "y genera una tabla con la evidencia 'Esperado vs. Obtenido' que certifica el resultado para tu informe.");
        alert.showAndWait();
    }

    private void initializeTestFile() {
        try {
            service.ensureTestFileExists();
            updateFileInfo();
            executeManualRead();
        } catch (IOException e) {
            lblFileStatus.setText("Error al inicializar archivo: " + e.getMessage());
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_ERROR + ";");
        }
    }

    private void updateFileInfo() {
        Path p = service.getTestFilePath();
        if (Files.exists(p)) {
            try {
                long size = Files.size(p);
                lblFileSize.setText(SystemInfoService.formatBytes(size));
            } catch (IOException ignored) {}
        }
    }

    private void applyManualToggles() {
        boolean r = btnToggleRead.isSelected();
        boolean w = btnToggleWrite.isSelected();

        btnToggleRead.setText(r ? "Lectura: PERMITIDA" : "Lectura: BLOQUEADA");
        btnToggleRead.setStyle(r ? "" : "-fx-background-color: " + ThemeTokens.COLOR_ERROR + "; -fx-text-fill: " + ThemeTokens.TEXT_DARK + "; -fx-font-weight: bold;");

        btnToggleWrite.setText(w ? "Escritura: PERMITIDA" : "Escritura: BLOQUEADA");
        btnToggleWrite.setStyle(w ? "" : "-fx-background-color: " + ThemeTokens.COLOR_ERROR + "; -fx-text-fill: " + ThemeTokens.TEXT_DARK + "; -fx-font-weight: bold;");

        service.applyPermissions(r, w);
        lblFileStatus.setText(String.format("Permisos aplicados en Windows: Lectura [%s] | Escritura [%s]",
                r ? "Permitida" : "Bloqueada", w ? "Permitida" : "Bloqueada"));
        lblFileStatus.setStyle("-fx-text-fill: " + (r && w ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_WARN) + ";");
    }

    private void executeSaveContent() {
        String content = txtFileContent.getText();
        PermissionLabService.OperationResult res = service.saveFullContent(content);
        if (res.success) {
            lastLoadedContent = content;
            lblFileStatus.setText("Cambios guardados con éxito en prueba_permisos.txt");
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + ";");
            updateFileInfo();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Permiso denegado");
            alert.setHeaderText("No se pudieron guardar los cambios");
            alert.setContentText("El sistema operativo bloqueó la escritura del archivo.\nCausa: " + res.message + "\n\nTu texto no se ha perdido. Puedes corregir los permisos y volver a intentarlo.");
            alert.showAndWait();

            lblFileStatus.setText("Error al guardar: " + res.message);
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_ERROR + ";");
        }
    }

    private void executeDiscardContent() {
        txtFileContent.setText(lastLoadedContent);
        lblFileStatus.setText("Cambios descartados. Contenido restablecido.");
        lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
    }

    private void executeManualRead() {
        OperationResult res = service.tryRead();
        if (res.success) {
            lastLoadedContent = res.data;
            txtFileContent.setText(res.data);
            lblFileStatus.setText("Lectura exitosa: El archivo pudo ser consultado.");
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + ";");
        } else {
            txtFileContent.setText("[BLOQUEO DEL SISTEMA OPERATIVO]\n" + res.message);
            lblFileStatus.setText(res.message);
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_ERROR + ";");
        }
        updateFileInfo();
    }

    private void executeManualWrite() {
        OperationResult res = service.tryWrite("Prueba manual desde la interfaz");
        if (res.success) {
            lblFileStatus.setText(res.message);
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + ";");
            executeManualRead(); // Refrescar contenido si es legible
        } else {
            lblFileStatus.setText(res.message);
            lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_ERROR + ";");
        }
        updateFileInfo();
    }

    private void resetOriginalState() {
        service.resetPermissions();
        btnToggleRead.setSelected(true);
        btnToggleRead.setText("Lectura: PERMITIDA");
        btnToggleRead.setStyle("");

        btnToggleWrite.setSelected(true);
        btnToggleWrite.setText("Escritura: PERMITIDA");
        btnToggleWrite.setStyle("");

        lblFileStatus.setText("Permisos restablecidos a su estado original.");
        lblFileStatus.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + ";");
        executeManualRead();
    }

    private void renderEmptyExperimentTable() {
        experimentTableBox.getChildren().clear();
        Label hint = new Label("Presiona 'Ejecutar Experimento Completo' para comprobar las 4 combinaciones.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        experimentTableBox.getChildren().add(hint);
    }

    private void runFullExperiment() {
        btnRunExperiment.setDisable(true);
        lblFileStatus.setText("Ejecutando experimento de las 4 combinaciones en segundo plano...");

        Task<ExperimentEvidence> task = new Task<>() {
            @Override
            protected ExperimentEvidence call() {
                return service.runAutomaticExperiment();
            }
        };

        task.setOnSucceeded(e -> {
            btnRunExperiment.setDisable(false);
            btnAddEvidence.setDisable(false);

            this.currentEvidence = task.getValue();
            renderEvidenceTable(currentEvidence);
            resetOriginalState();

            // Registrar en SessionState
            addEvidenceToSession();
        });

        task.setOnFailed(e -> {
            btnRunExperiment.setDisable(false);
            lblFileStatus.setText("Error al ejecutar experimento: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void renderEvidenceTable(ExperimentEvidence ev) {
        experimentTableBox.getChildren().clear();

        // Encabezado de la tabla
        HBox header = new HBox(12);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6px;");

        Label colComb = new Label("COMBINACIÓN EVALUADA");
        colComb.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        colComb.setPrefWidth(240);

        Label colRead = new Label("LECTURA (ESP / OBT)");
        colRead.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        colRead.setPrefWidth(160);

        Label colWrite = new Label("ESCRITURA (ESP / OBT)");
        colWrite.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        colWrite.setPrefWidth(160);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label colVerdict = new Label("VEREDICTO");
        colVerdict.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        header.getChildren().addAll(colComb, colRead, colWrite, sp, colVerdict);
        experimentTableBox.getChildren().add(header);

        // Filas
        for (ExperimentEntry row : ev.entries) {
            HBox r = new HBox(12);
            r.setAlignment(Pos.CENTER_LEFT);
            r.setPadding(new Insets(8, 12, 8, 12));
            r.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6px;");

            Label name = new Label(row.combinationName);
            name.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold;");
            name.setPrefWidth(240);

            Label read = new Label(String.format("Esp: %s  |  Obt: %s", row.readExpected ? "✔" : "✘", row.readActual ? "✔" : "✘"));
            read.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (row.readExpected == row.readActual ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR) + ";");
            read.setPrefWidth(160);

            Label write = new Label(String.format("Esp: %s  |  Obt: %s", row.writeExpected ? "✔" : "✘", row.writeActual ? "✔" : "✘"));
            write.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (row.writeExpected == row.writeActual ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR) + ";");
            write.setPrefWidth(160);

            Region spRow = new Region();
            HBox.setHgrow(spRow, Priority.ALWAYS);

            Label badge = new Label(row.match ? "CORRECTO ✔" : "FALLIDO ✘");
            badge.setStyle(String.format("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-background-color: %s; -fx-padding: 3 8 3 8; -fx-background-radius: 10px;",
                    ThemeTokens.TEXT_DARK, row.match ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR));

            r.getChildren().addAll(name, read, write, spRow, badge);
            experimentTableBox.getChildren().add(r);
        }
    }

    private void addEvidenceToSession() {
        if (currentEvidence == null) return;

        DiagnosticResult res = service.toDiagnosticResult(currentEvidence);
        ToolStatus st = currentEvidence.allMatched ? ToolStatus.OK : ToolStatus.ATENCION;
        SessionState.getInstance().setStatus(tool.getId(), st, res);

        resultCardSlot.getChildren().clear();
        StandardResultCard card = new StandardResultCard(
                st,
                "Evidencia de Permisos Registrada",
                "Resultado del Experimento",
                currentEvidence.allMatched ? "4 de 4 Correctas (100%)" : "Atención requerida",
                "Se validó la respuesta del sistema de archivos NTFS en todas las combinaciones.",
                res.getTelemetry(),
                null
        );
        resultCardSlot.getChildren().add(card);
    }
}
