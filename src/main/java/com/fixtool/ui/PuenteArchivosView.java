package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.FileBridgeService;
import com.fixtool.service.FileBridgeService.BridgeFileItem;
import com.fixtool.service.FileBridgeService.TransferRecord;
import com.fixtool.service.SystemInfoService;
import com.fixtool.ui.components.ConnectionDialog;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 5: Puente de archivos.
 *
 * Paneles paralelos Mi PC y BOSGAME, navegación de carpetas,
 * transferencia bidireccional con cálculo y verificación SHA-256 en tiempo real,
 * animación de puente y tabla de historial de auditoría.
 */
public class PuenteArchivosView extends VBox {

    private final DiagnosticAction tool;
    private final ConnectionConfig config;
    private final FileBridgeService service = new FileBridgeService();

    // Rutas actuales de exploración
    private Path localPath = Paths.get(System.getProperty("user.home"));
    private Path remotePath;
    private boolean isRemoteConnected = false;

    // Listas UI y Navegación
    private final ListView<BridgeFileItem> listLocal;
    private final ListView<BridgeFileItem> listRemote;
    private final Label lblLocalPath;
    private final Label lblRemotePath;
    private final Button btnLocalUp;
    private final Button btnRemoteUp;

    // Estados del panel remoto (Conectado / Desconectado / Cargando)
    private final StackPane remoteStack;
    private final VBox remoteListContainer;
    private final VBox remoteDisconnectedBox;
    private final VBox remoteLoadingBox;
    private final Label lblDisconnectedReason;
    private final Label lblConnectionBadge;
    private final Label lblTarget;

    // Controles de transferencia central
    private final Button btnUpload;
    private final Button btnDownload;
    private final ProgressBar progressBar;
    private final Label lblSpeed;
    private final Label lblIntegrityResult;
    private final Line bridgeLine;
    private Timeline bridgeAnimation;

    // Tabla de historial de transferencias
    private final VBox historyTableBox;
    private final VBox resultCardSlot;
    private AtomicBoolean transferCancelFlag;

    public PuenteArchivosView(DiagnosticAction tool, ConnectionConfig config) {
        this.tool = tool;
        this.config = config;
        this.remotePath = resolveRemotePath();

        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        // Inicializar labels reactivos de conexión antes de armar la barra de estado
        lblTarget = new Label(formatTargetString());
        lblTarget.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 12px;");

        lblConnectionBadge = new Label("Comprobando...");
        lblConnectionBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        lblDisconnectedReason = new Label("Comprobando disponibilidad del equipo remoto...");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24, 28, 36, 28));
        content.getStyleClass().add("bg-root");

        // ── 1. Cabecera de la herramienta ─────────────────────────────────────
        HBox headerBar = buildHeaderBar();

        // ── 2. Barra de estado de conexión y botones de navegación ────────────
        HBox statusRow = buildConnectionStatusRow();

        // ── 3. Paneles paralelos Mi PC ── Puente ── BOSGAME ───────────────────
        HBox panelsRow = new HBox(16);
        panelsRow.setAlignment(Pos.CENTER);
        VBox.setVgrow(panelsRow, Priority.ALWAYS);

        // Panel Izquierdo: Mi PC
        VBox panelLocal = new VBox(10);
        panelLocal.getStyleClass().add("card-panel");
        panelLocal.setPadding(new Insets(14));
        HBox.setHgrow(panelLocal, Priority.ALWAYS);

        HBox localHeader = new HBox(8, AppIcons.create("monitor", 16, ThemeTokens.ACCENT), new Label("Mi PC (Archivos Locales)") {{
            setStyle("-fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 13px;");
        }});
        localHeader.setAlignment(Pos.CENTER_LEFT);

        // Barra de ruta local y botón para subir nivel
        HBox localPathBar = new HBox(8);
        localPathBar.setAlignment(Pos.CENTER_LEFT);
        localPathBar.getStyleClass().add("file-path-bar");

        lblLocalPath = new Label(localPath.toString());
        lblLocalPath.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold;");
        lblLocalPath.setEllipsisString("...");
        HBox.setHgrow(lblLocalPath, Priority.ALWAYS);

        btnLocalUp = new Button("⬆ Subir", AppIcons.create("arrowup", 12, ThemeTokens.ACCENT));
        btnLocalUp.getStyleClass().add("button-secondary");
        btnLocalUp.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 11px; -fx-background-radius: 4px;");
        btnLocalUp.setTooltip(new Tooltip("Subir un nivel"));
        btnLocalUp.setOnAction(e -> {
            if (localPath != null && localPath.getParent() != null) {
                localPath = localPath.getParent();
                lblLocalPath.setText(localPath.toString());
                btnLocalUp.setDisable(localPath.getParent() == null);
                refreshLocalList();
            }
        });
        btnLocalUp.setDisable(localPath == null || localPath.getParent() == null);

        localPathBar.getChildren().addAll(btnLocalUp, lblLocalPath);

        HBox localColHeader = createColumnHeader();

        listLocal = new ListView<>();
        listLocal.setPrefHeight(320);
        setupFileListView(listLocal, true);

        panelLocal.getChildren().addAll(localHeader, localPathBar, localColHeader, listLocal);
        VBox.setVgrow(listLocal, Priority.ALWAYS);

        // Centro: Puente interactivo con botones Subir y Bajar
        VBox bridgeCenter = new VBox(14);
        bridgeCenter.setAlignment(Pos.CENTER);
        bridgeCenter.setPrefWidth(160);

        bridgeLine = new Line(0, 0, 80, 0);
        bridgeLine.setStyle("-fx-stroke: " + ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 4;");

        btnUpload = new Button("Subir ➔", AppIcons.create("upload", 14, ThemeTokens.TEXT_DARK));
        btnUpload.getStyleClass().add("button-primary");
        btnUpload.setMaxWidth(Double.MAX_VALUE);
        btnUpload.setDisable(true);
        btnUpload.setOnAction(e -> startTransfer(true));

        btnDownload = new Button("⬅ Bajar", AppIcons.create("download", 14, ThemeTokens.ACCENT));
        btnDownload.getStyleClass().add("button-secondary");
        btnDownload.setMaxWidth(Double.MAX_VALUE);
        btnDownload.setDisable(true);
        btnDownload.setOnAction(e -> startTransfer(false));

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        lblSpeed = new Label("");
        lblSpeed.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        lblIntegrityResult = new Label("");
        lblIntegrityResult.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        bridgeCenter.getChildren().addAll(btnUpload, bridgeLine, btnDownload, progressBar, lblSpeed, lblIntegrityResult);

        // Panel Derecho: BOSGAME
        VBox panelRemote = new VBox(10);
        panelRemote.getStyleClass().add("card-panel");
        panelRemote.setPadding(new Insets(14));
        HBox.setHgrow(panelRemote, Priority.ALWAYS);

        HBox remoteHeader = new HBox(8, AppIcons.create("harddrive", 16, ThemeTokens.COLOR_OK), new Label("BOSGAME (Carpeta Compartida)") {{
            setStyle("-fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 13px;");
        }});
        remoteHeader.setAlignment(Pos.CENTER_LEFT);

        // Barra de ruta remota y botón para subir nivel
        HBox remotePathBar = new HBox(8);
        remotePathBar.setAlignment(Pos.CENTER_LEFT);
        remotePathBar.getStyleClass().add("file-path-bar");

        lblRemotePath = new Label(remotePath != null ? remotePath.toString() : "Sin conexión");
        lblRemotePath.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-font-weight: bold;");
        lblRemotePath.setEllipsisString("...");
        HBox.setHgrow(lblRemotePath, Priority.ALWAYS);

        btnRemoteUp = new Button("⬆ Subir", AppIcons.create("arrowup", 12, ThemeTokens.COLOR_OK));
        btnRemoteUp.getStyleClass().add("button-secondary");
        btnRemoteUp.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 11px; -fx-background-radius: 4px;");
        btnRemoteUp.setTooltip(new Tooltip("Subir un nivel"));
        btnRemoteUp.setOnAction(e -> {
            if (remotePath != null && remotePath.getParent() != null) {
                remotePath = remotePath.getParent();
                lblRemotePath.setText(remotePath.toString());
                btnRemoteUp.setDisable(remotePath.getParent() == null);
                refreshRemoteList();
            }
        });
        btnRemoteUp.setDisable(remotePath == null || remotePath.getParent() == null);

        remotePathBar.getChildren().addAll(btnRemoteUp, lblRemotePath);

        HBox remoteColHeader = createColumnHeader();

        listRemote = new ListView<>();
        listRemote.setPrefHeight(320);
        setupFileListView(listRemote, false);

        remoteListContainer = new VBox(8, remotePathBar, remoteColHeader, listRemote);
        VBox.setVgrow(listRemote, Priority.ALWAYS);

        remoteDisconnectedBox = buildDisconnectedBox();
        remoteLoadingBox = buildLoadingBox();

        remoteStack = new StackPane(remoteListContainer, remoteDisconnectedBox, remoteLoadingBox);
        VBox.setVgrow(remoteStack, Priority.ALWAYS);

        panelRemote.getChildren().addAll(remoteHeader, remoteStack);

        panelsRow.getChildren().addAll(panelLocal, bridgeCenter, panelRemote);

        // ── 4. Historial de transferencias y verificación SHA-256 ─────────────
        VBox historySection = new VBox(10);
        historySection.getStyleClass().add("card-panel");
        historySection.setPadding(new Insets(16));

        Label lblHistTitle = new Label("Historial de Transferencias y Verificación de Integridad SHA-256");
        lblHistTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        historyTableBox = new VBox(6);
        refreshHistoryUI();

        historySection.getChildren().addAll(lblHistTitle, historyTableBox);

        // ── 5. Tarjeta de resultado estándar ──────────────────────────────────
        resultCardSlot = new VBox(12);

        content.getChildren().addAll(headerBar, statusRow, panelsRow, historySection, resultCardSlot);
        scroll.setContent(content);
        this.getChildren().add(scroll);

        // Cargar listas iniciales
        refreshLocalList();
        refreshRemoteList();
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, ThemeTokens.ACCENT);

        VBox titleBox = new VBox(2);
        Label title = new Label("5. " + tool.getTitle());
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
        alert.setTitle("Puente de archivos – Ayuda");
        alert.setHeaderText("Transferencia Segura con Verificación SHA-256");
        alert.setContentText("Esta herramienta te permite explorar y pasar archivos entre tu computador y la BOSGAME.\n\n"
                + "• Subir: Selecciona un archivo en 'Mi PC' y pulsa 'Subir ➔' para guardarlo en la BOSGAME.\n"
                + "• Bajar: Selecciona un archivo en 'BOSGAME' y pulsa '⬅ Bajar' para descargarlo en la subcarpeta Descargas_BOSGAME de tu carpeta de trabajo.\n"
                + "• Integridad SHA-256: Al terminar la copia, calcula la huella digital criptográfica de ambos archivos para "
                + "garantizar al 100% que el archivo llegó idéntico sin corrupciones.");
        alert.showAndWait();
    }

    private Path resolveRemotePath() {
        try {
            String unc = config.getUncPath();
            if (unc != null && !unc.isBlank()) {
                return Paths.get(unc);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String formatTargetString() {
        String ip = config.getIpTarget() != null ? config.getIpTarget() : "";
        String folder = config.getSharedFolder() != null ? config.getSharedFolder().trim() : "";
        return ip + (folder.isBlank() ? "" : " / " + folder);
    }

    private void updateConnectionLabels() {
        lblTarget.setText(formatTargetString());
        if (remotePath != null) {
            lblRemotePath.setText(remotePath.toString());
        }
    }

    private VBox buildDisconnectedBox() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30, 20, 30, 20));
        box.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8px; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8px;");

        Node icon = AppIcons.create("wifi", 36, ThemeTokens.COLOR_WARN);

        Label title = new Label("Sin conexión con el equipo remoto");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        lblDisconnectedReason.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
        lblDisconnectedReason.setWrapText(true);
        lblDisconnectedReason.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        lblDisconnectedReason.setMaxWidth(300);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);

        Button btnConfig = new Button("Conectar / Configurar...", AppIcons.create("settings", 13, ThemeTokens.TEXT_DARK));
        btnConfig.getStyleClass().add("button-primary");
        btnConfig.setOnAction(e -> {
            new ConnectionDialog(config).showAndWait();
            remotePath = resolveRemotePath();
            updateConnectionLabels();
            refreshRemoteList();
        });

        Button btnRetry = new Button("Reintentar", AppIcons.create("refreshcw", 13, ThemeTokens.ACCENT));
        btnRetry.getStyleClass().add("button-secondary");
        btnRetry.setOnAction(e -> refreshRemoteList());

        btnRow.getChildren().addAll(btnConfig, btnRetry);
        box.getChildren().addAll(icon, title, lblDisconnectedReason, btnRow);
        box.setVisible(false);
        return box;
    }

    private VBox buildLoadingBox() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));

        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(32, 32);

        Label lbl = new Label("Comprobando conexión con BOSGAME...");
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        box.getChildren().addAll(pi, lbl);
        box.setVisible(false);
        return box;
    }

    private void showRemoteLoading() {
        remoteLoadingBox.setVisible(true);
        remoteDisconnectedBox.setVisible(false);
        remoteListContainer.setVisible(false);
        lblConnectionBadge.setText("Conectando...");
        lblConnectionBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");
    }

    private void showRemoteConnected(List<BridgeFileItem> items) {
        isRemoteConnected = true;
        remoteLoadingBox.setVisible(false);
        remoteDisconnectedBox.setVisible(false);
        remoteListContainer.setVisible(true);
        listRemote.getItems().setAll(items);

        lblRemotePath.setText(remotePath != null ? remotePath.toString() : "");
        lblRemotePath.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-font-weight: bold;");

        lblConnectionBadge.setText("Conectado ✔");
        lblConnectionBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-padding: 3 8 3 8; -fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + ThemeTokens.COLOR_OK + "44; -fx-border-radius: 12px; -fx-background-radius: 12px;");

        btnRemoteUp.setDisable(remotePath == null || remotePath.getParent() == null);

        BridgeFileItem selLocal = listLocal.getSelectionModel().getSelectedItem();
        btnUpload.setDisable(selLocal == null || selLocal.isDirectory);

        BridgeFileItem selRemote = listRemote.getSelectionModel().getSelectedItem();
        btnDownload.setDisable(selRemote == null || selRemote.isDirectory);
    }

    private void showRemoteDisconnected(Throwable ex) {
        isRemoteConnected = false;
        remoteLoadingBox.setVisible(false);
        remoteListContainer.setVisible(false);
        remoteDisconnectedBox.setVisible(true);
        btnRemoteUp.setDisable(true);

        String msg = (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "No se puede alcanzar el equipo remoto en " + config.getIpTarget() + ". Verifique que esté encendido y en la misma red local.";
        lblDisconnectedReason.setText(msg);

        lblRemotePath.setText("Sin conexión");
        lblRemotePath.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.COLOR_ERROR + "; -fx-font-weight: bold;");

        lblConnectionBadge.setText("Sin conexión ✘");
        lblConnectionBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.COLOR_ERROR + "; -fx-padding: 3 8 3 8; -fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + ThemeTokens.COLOR_ERROR + "44; -fx-border-radius: 12px; -fx-background-radius: 12px;");

        btnUpload.setDisable(true);
        btnDownload.setDisable(true);
    }

    private HBox buildConnectionStatusRow() {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-panel");
        row.setPadding(new Insets(10, 16, 10, 16));

        Label lblStatus = new Label("BOSGAME:");
        lblStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        Button btnChangeConfig = new Button("Conectar / Cambiar...", AppIcons.create("wifi", 12, ThemeTokens.ACCENT));
        btnChangeConfig.getStyleClass().add("button-secondary");
        btnChangeConfig.setOnAction(e -> {
            new ConnectionDialog(config).showAndWait();
            remotePath = resolveRemotePath();
            updateConnectionLabels();
            refreshRemoteList();
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefreshBoth = new Button("Refrescar carpetas", AppIcons.create("refreshcw", 12, ThemeTokens.ACCENT));
        btnRefreshBoth.getStyleClass().add("button-secondary");
        btnRefreshBoth.setOnAction(e -> {
            refreshLocalList();
            refreshRemoteList();
        });

        row.getChildren().addAll(lblStatus, lblTarget, lblConnectionBadge, btnChangeConfig, sp, btnRefreshBoth);
        return row;
    }

    private HBox createColumnHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("file-list-column-header");

        Label colName = new Label("NOMBRE");
        colName.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label colDate = new Label("MODIFICADO");
        colDate.setPrefWidth(125);
        colDate.setAlignment(Pos.CENTER_RIGHT);
        colDate.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label colSize = new Label("TAMAÑO");
        colSize.setPrefWidth(75);
        colSize.setAlignment(Pos.CENTER_RIGHT);
        colSize.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        header.getChildren().addAll(colName, sp, colDate, colSize);
        return header;
    }

    private void setupFileListView(ListView<BridgeFileItem> list, boolean isLocal) {
        list.getStyleClass().add("bridge-file-list");
        if (!isLocal) {
            list.getStyleClass().add("bridge-file-list-remote");
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        list.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(BridgeFileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    // Distinción nítida por forma y color:
                    // Directorio: ícono folder cian (#38BDF8)
                    // Archivo: ícono filetext gris claro (#A7B3C2)
                    Node icon = AppIcons.create(
                            item.isDirectory ? "folder" : "filetext",
                            16,
                            item.isDirectory ? ThemeTokens.ACCENT : ThemeTokens.TEXT_SECONDARY
                    );

                    Label name = new Label(item.name);
                    name.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 12px;"
                            + (item.isDirectory ? " -fx-font-weight: bold;" : ""));
                    name.setMaxWidth(220);
                    name.setTooltip(new Tooltip(item.name));

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    String dateFormatted = "—";
                    if (item.lastModified > 0) {
                        try {
                            dateFormatted = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(item.lastModified),
                                    ZoneId.systemDefault()
                            ).format(dtf);
                        } catch (Exception ignored) {}
                    }
                    Label date = new Label(dateFormatted);
                    date.setPrefWidth(125);
                    date.setAlignment(Pos.CENTER_RIGHT);
                    date.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

                    String meta = item.isDirectory ? "Carpeta" : SystemInfoService.formatBytes(item.sizeBytes);
                    Label size = new Label(meta);
                    size.setPrefWidth(75);
                    size.setAlignment(Pos.CENTER_RIGHT);
                    size.setStyle("-fx-text-fill: " + (item.isDirectory ? ThemeTokens.ACCENT : ThemeTokens.TEXT_PRIMARY) + "; -fx-font-size: 11px;");

                    box.getChildren().addAll(icon, name, sp, date, size);
                    setGraphic(box);
                }
            }
        });

        // Escuchar selección para habilitar/deshabilitar botones de transferencia
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (isLocal) {
                btnUpload.setDisable(newVal == null || !isRemoteConnected || newVal.isDirectory);
            } else {
                btnDownload.setDisable(newVal == null || !isRemoteConnected || newVal.isDirectory);
            }
        });

        // Doble clic para entrar en carpetas
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                BridgeFileItem sel = list.getSelectionModel().getSelectedItem();
                if (sel != null && sel.isDirectory) {
                    if (isLocal) {
                        localPath = Paths.get(sel.absolutePath);
                        lblLocalPath.setText(localPath.toString());
                        btnLocalUp.setDisable(localPath.getParent() == null);
                        refreshLocalList();
                    } else {
                        remotePath = Paths.get(sel.absolutePath);
                        lblRemotePath.setText(remotePath.toString());
                        btnRemoteUp.setDisable(remotePath == null || remotePath.getParent() == null);
                        refreshRemoteList();
                    }
                }
            }
        });
    }

    private void refreshLocalList() {
        Task<List<BridgeFileItem>> task = new Task<>() {
            @Override
            protected List<BridgeFileItem> call() {
                try {
                    return service.listFiles(localPath);
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
        };
        task.setOnSucceeded(e -> {
            listLocal.getItems().setAll(task.getValue());
            btnLocalUp.setDisable(localPath == null || localPath.getParent() == null);
            BridgeFileItem sel = listLocal.getSelectionModel().getSelectedItem();
            btnUpload.setDisable(sel == null || !isRemoteConnected || sel.isDirectory);
        });
        Thread t = new Thread(task, "LocalListThread");
        t.setDaemon(true);
        t.start();
    }

    private void refreshRemoteList() {
        if (remotePath == null) {
            showRemoteDisconnected(new IOException("No se ha configurado una ruta UNC válida para el equipo remoto."));
            return;
        }

        showRemoteLoading();

        Task<List<BridgeFileItem>> task = new Task<>() {
            @Override
            protected List<BridgeFileItem> call() throws Exception {
                return service.listFiles(remotePath);
            }
        };

        task.setOnSucceeded(e -> showRemoteConnected(task.getValue()));
        task.setOnFailed(e -> showRemoteDisconnected(task.getException()));

        Thread t = new Thread(task, "RemoteListThread");
        t.setDaemon(true);
        t.start();
    }

    private void startTransfer(boolean isUpload) {
        if (!isRemoteConnected || remotePath == null) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Sin conexión remota");
            a.setHeaderText(null);
            a.setContentText("No hay conexión con el equipo remoto. Conéctate a la BOSGAME antes de transferir archivos.");
            a.showAndWait();
            return;
        }

        BridgeFileItem selected = isUpload
                ? listLocal.getSelectionModel().getSelectedItem()
                : listRemote.getSelectionModel().getSelectedItem();

        if (selected == null) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Selección requerida");
            a.setHeaderText(null);
            a.setContentText("Por favor selecciona un archivo para " + (isUpload ? "subir" : "descargar") + ".");
            a.showAndWait();
            return;
        }

        if (selected.isDirectory) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Elemento seleccionado");
            a.setHeaderText(null);
            a.setContentText("Haz doble clic en la carpeta para ingresar a ella.");
            a.showAndWait();
            return;
        }

        Path source = Paths.get(selected.absolutePath);
        Path targetDir;
        try {
            targetDir = isUpload ? remotePath : AppConfig.getWorkDir().resolve("Descargas_BOSGAME");
            if (!isUpload && !Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Error de carpeta destino");
            a.setHeaderText(null);
            a.setContentText("No se pudo preparar la carpeta destino: " + ex.getMessage());
            a.showAndWait();
            return;
        }

        // Confirmación de sobreescritura si ya existe
        try {
            Path destinationFile = targetDir.resolve(selected.name);
            if (Files.exists(destinationFile)) {
                Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
                conf.setTitle("Confirmar sobreescritura");
                conf.setHeaderText("El archivo ya existe en destino");
                conf.setContentText("¿Deseas reemplazar el archivo existente '" + selected.name + "'?");
                Optional<ButtonType> opt = conf.showAndWait();
                if (opt.isEmpty() || opt.get() != ButtonType.OK) return;
            }
        } catch (Exception ignored) {}

        btnUpload.setDisable(true);
        btnDownload.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        lblIntegrityResult.setText("Transfiriendo y calculando SHA-256...");
        lblIntegrityResult.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.ACCENT + ";");
        startBridgeAnimation();
        transferCancelFlag = new AtomicBoolean(false);

        Task<TransferRecord> task = new Task<>() {
            @Override
            protected TransferRecord call() throws Exception {
                return service.transferFile(source, targetDir, isUpload, transferCancelFlag, p -> Platform.runLater(() -> {
                    progressBar.setProgress(p.percent);
                    lblSpeed.setText(String.format("%.2f MB/s • %.0f s restantes", p.speedMBps, p.secondsRemaining));
                }));
            }
        };

        task.setOnSucceeded(e -> {
            stopBridgeAnimation();
            btnUpload.setDisable(false);
            btnDownload.setDisable(false);
            progressBar.setVisible(false);
            lblSpeed.setText("");

            TransferRecord rec = task.getValue();
            if (rec.integrityMatch) {
                lblIntegrityResult.setText("Idénticos ✔ (SHA-256 verificado)");
                lblIntegrityResult.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-font-weight: bold;");
            } else {
                lblIntegrityResult.setText("Error: Las huellas SHA-256 no coinciden");
                lblIntegrityResult.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.COLOR_ERROR + "; -fx-font-weight: bold;");
            }

            refreshLocalList();
            refreshRemoteList();
            refreshHistoryUI();

            DiagnosticResult diagRes = service.toDiagnosticResult();
            ToolStatus st = rec.integrityMatch ? ToolStatus.OK : ToolStatus.PROBLEMA;
            SessionState.getInstance().setStatus(tool.getId(), st, diagRes);

            resultCardSlot.getChildren().clear();
            StandardResultCard card = new StandardResultCard(
                    st,
                    "Transferencia Completada: " + rec.fileName,
                    "Integridad Criptográfica",
                    rec.statusText,
                    "Huella SHA-256: " + rec.sha256Source.substring(0, 16) + "...",
                    diagRes.getTelemetry(),
                    null
            );
            resultCardSlot.getChildren().add(card);
        });

        task.setOnFailed(e -> {
            stopBridgeAnimation();
            btnUpload.setDisable(false);
            btnDownload.setDisable(false);
            progressBar.setVisible(false);
            lblSpeed.setText("");
            lblIntegrityResult.setText("Fallo en la transferencia: " + task.getException().getMessage());
            lblIntegrityResult.setStyle("-fx-font-size: 12px; -fx-text-fill: " + ThemeTokens.COLOR_ERROR + ";");
        });

        new Thread(task).start();
    }

    private void startBridgeAnimation() {
        bridgeAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(bridgeLine.strokeProperty(), Color.web(ThemeTokens.ACCENT))),
                new KeyFrame(Duration.millis(350), new KeyValue(bridgeLine.strokeProperty(), Color.web(ThemeTokens.COLOR_OK))),
                new KeyFrame(Duration.millis(700), new KeyValue(bridgeLine.strokeProperty(), Color.web(ThemeTokens.ACCENT)))
        );
        bridgeAnimation.setCycleCount(Timeline.INDEFINITE);
        bridgeAnimation.play();
    }

    private void stopBridgeAnimation() {
        if (bridgeAnimation != null) {
            bridgeAnimation.stop();
        }
        bridgeLine.setStyle("-fx-stroke: " + ThemeTokens.BORDER_CONTROL + "; -fx-stroke-width: 4;");
    }

    private void refreshHistoryUI() {
        historyTableBox.getChildren().clear();
        List<TransferRecord> history = service.getSessionHistory();

        if (history.isEmpty()) {
            Label empty = new Label("No se han realizado transferencias en esta sesión.");
            empty.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
            historyTableBox.getChildren().add(empty);
            return;
        }

        for (TransferRecord r : history) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6px;");

            Node icon = AppIcons.create(r.integrityMatch ? "checkcircle" : "xcircle", 14,
                    r.integrityMatch ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR);

            Label dir = new Label(r.direction);
            dir.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.ACCENT + ";");

            Label name = new Label(r.fileName);
            name.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");
            name.setMaxWidth(200);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label size = new Label(SystemInfoService.formatBytes(r.sizeBytes) + " • " + r.timestamp);
            size.setStyle("-fx-font-size: 10px; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

            Label hashStatus = new Label(r.statusText);
            hashStatus.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " +
                    (r.integrityMatch ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR) + ";");

            row.getChildren().addAll(icon, dir, name, sp, size, hashStatus);
            historyTableBox.getChildren().add(row);
        }
    }
}
