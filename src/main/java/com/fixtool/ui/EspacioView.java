package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;
import com.fixtool.service.DiskScanService;
import com.fixtool.service.DiskScanService.*;
import com.fixtool.service.SystemInfoService;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.util.AppIcons;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 2: Espacio y limpieza.
 *
 * Incluye dos pestañas principales:
 *  1. Mapa de espacio: Exploración visual de bloques proporcionales, ruta de migas,
 *     top 10 archivos más pesados y filtro de archivos sin uso hace más de 1 año.
 *  2. Limpieza con cuarentena: Modo simulación por defecto, control deslizante de días,
 *     traslado seguro a carpeta de cuarentena, restauración por lote y vaciado con doble confirmación.
 */
public class EspacioView extends VBox {

    private final DiagnosticAction tool;
    private final DiskScanService  service = new DiskScanService();

    // Controles pestaña 1: Mapa
    private File currentSelectedDir = new File(System.getProperty("user.home"));
    private FolderNode currentRootNode;
    private FolderNode currentNavNode;
    private final Stack<FolderNode> navHistory = new Stack<>();
    private final Label lblCurrentPath;
    private final FlowPane blocksContainer;
    private final VBox top10Container;
    private final CheckBox chkFilterOldFiles;
    private final ProgressBar mapProgress;
    private final Label lblScanStatus;
    private final Button btnScan;
    private final Button btnCancelScan;
    private final HBox breadcrumbsBar;
    private AtomicBoolean scanCancelFlag;
    private ScanResult lastScanResult;

    // Controles de selección libre para cuarentena (Ruta 1 y Ruta 2)
    private final Set<File> selectedMapFiles = new LinkedHashSet<>();
    private HBox selectionBar;
    private Label lblSelectedCount;
    private Button btnQuarantineSelected;

    // Controles pestaña 2: Limpieza
    private final CheckBox chkModeSimulacion;
    private final TextField txtDownloadAge;
    private final ComboBox<String> cmbAgeUnit;
    private final Label lblAgeError;
    private Timeline ageDebounceTimeline;
    private final VBox     cleaningCategoriesBox;
    private final Button   btnCleanAction;
    private final Label    lblCleanResult;
    private final VBox     quarantineBatchesBox;
    private final VBox     resultCardSlot;
    private List<CleanCategory> currentCategories = new ArrayList<>();

    public EspacioView(DiagnosticAction tool) {
        this.tool = tool;
        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        // ── Cabecera de la herramienta ─────────────────────────────────────────
        HBox headerBar = buildHeaderBar();
        headerBar.setPadding(new Insets(20, 28, 12, 28));

        // ── Pestañas (TabPane con estilo Dark Pro) ─────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("plain-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // ── Instanciación de Controles Tab 1 (antes de buildTabMapa) ─────────────
        lblCurrentPath = new Label(currentSelectedDir.getAbsolutePath());
        lblCurrentPath.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold;");
        breadcrumbsBar = new HBox(6);
        breadcrumbsBar.setAlignment(Pos.CENTER_LEFT);
        blocksContainer = new FlowPane();
        blocksContainer.setHgap(10);
        blocksContainer.setVgap(10);
        blocksContainer.setPadding(new Insets(10));
        top10Container = new VBox(8);
        chkFilterOldFiles = new CheckBox("Solo sin uso hace más de 1 año");
        chkFilterOldFiles.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
        mapProgress = new ProgressBar(0);
        mapProgress.setVisible(false);
        mapProgress.setMaxWidth(Double.MAX_VALUE);
        lblScanStatus = new Label("Listo para escanear");
        lblScanStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
        btnScan = new Button("Escanear", AppIcons.create("search", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnScan.getStyleClass().add("button-primary");
        btnCancelScan = new Button("Cancelar", AppIcons.create("x", 14, com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR));
        btnCancelScan.getStyleClass().add("button-danger");
        btnCancelScan.setVisible(false);

        // ── Instanciación de Controles Tab 2 (antes de buildTabLimpieza) ─────────
        chkModeSimulacion = new CheckBox("Modo simulación (recomendado: solo calcula sin mover nada)");
        chkModeSimulacion.setSelected(true);
        chkModeSimulacion.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        txtDownloadAge = new TextField("90");
        txtDownloadAge.setPrefWidth(75);
        txtDownloadAge.setMaxWidth(85);

        cmbAgeUnit = new ComboBox<>();
        cmbAgeUnit.getItems().addAll("días", "semanas", "meses", "años");
        cmbAgeUnit.setValue("días");
        cmbAgeUnit.getStyleClass().add("button-secondary");

        lblAgeError = new Label("");
        lblAgeError.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR + "; -fx-font-weight: bold;");
        lblAgeError.setVisible(false);

        cleaningCategoriesBox = new VBox(10);
        btnCleanAction = new Button("Simular Limpieza", AppIcons.create("activity", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnCleanAction.getStyleClass().add("button-primary");
        lblCleanResult = new Label("");
        lblCleanResult.setStyle("-fx-font-size: 13px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.COLOR_OK + ";");
        lblCleanResult.setWrapText(true);
        quarantineBatchesBox = new VBox(8);
        resultCardSlot = new VBox(12);
        resultCardSlot.setPadding(new Insets(16, 0, 0, 0));

        // Pestaña 1: Mapa de espacio
        Tab tabMapa = new Tab("Mapa de espacio", buildTabMapa());
        tabMapa.setClosable(false);

        // Pestaña 2: Limpieza con cuarentena
        Tab tabLimpieza = new Tab("Limpieza con cuarentena", buildTabLimpieza());
        tabLimpieza.setClosable(false);

        tabPane.getTabs().addAll(tabMapa, tabLimpieza);
        this.getChildren().addAll(headerBar, tabPane);

        // Cargar análisis inicial de limpieza en segundo plano
        recargarAnalisisLimpieza();
        refrescarLotesCuarentena();
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(tool.getIconSymbol(), 22, com.fixtool.ui.theme.ThemeTokens.ACCENT);

        VBox titleBox = new VBox(2);
        Label title = new Label("2. " + tool.getTitle());
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
        alert.setTitle("Espacio y limpieza – Ayuda");
        alert.setHeaderText("Seguridad en la gestión de espacio");
        alert.setContentText("Esta herramienta te permite ver qué consume tu disco y liberar espacio de forma segura.\n\n"
                + "• Mapa de espacio: Muestra carpetas proporcionales a su tamaño y los archivos más pesados.\n"
                + "• Limpieza segura con Cuarentena: Nunca se eliminan archivos de inmediato. Se trasladan a la carpeta "
                + "de cuarentena donde puedes restaurarlos en cualquier momento.\n"
                + "• Modo simulación: Viene activado por defecto para que verifiques cuántos megabytes liberarás antes de realizar cambios.");
        alert.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PESTAÑA 1: MAPA DE ESPACIO
    // ═══════════════════════════════════════════════════════════════════════════

    private Node buildTabMapa() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20, 28, 28, 28));
        root.getStyleClass().add("bg-root");

        // Barra de selección de carpeta
        HBox selectorRow = new HBox(12);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.getStyleClass().add("card-panel");
        selectorRow.setPadding(new Insets(12, 16, 12, 16));

        Label lblCarpeta = new Label("Carpeta a examinar:");
        lblCarpeta.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        Button btnBrowse = new Button("Cambiar...", AppIcons.create("folder", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnBrowse.getStyleClass().add("button-secondary");
        btnBrowse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta para mapa de espacio");
            chooser.setInitialDirectory(currentSelectedDir.exists() ? currentSelectedDir : new File(System.getProperty("user.home")));
            File chosen = chooser.showDialog(this.getScene().getWindow());
            if (chosen != null) {
                currentSelectedDir = chosen;
                lblCurrentPath.setText(chosen.getAbsolutePath());
            }
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        btnScan.setOnAction(e -> startScan());
        btnCancelScan.setOnAction(e -> {
            if (scanCancelFlag != null) scanCancelFlag.set(true);
        });

        selectorRow.getChildren().addAll(lblCarpeta, lblCurrentPath, btnBrowse, sp, btnScan, btnCancelScan);

        // Barra de progreso y estado
        VBox progressBox = new VBox(6);
        progressBox.getChildren().addAll(lblScanStatus, mapProgress);

        // Barra de migas (Breadcrumbs)
        HBox breadcrumbWrapper = new HBox(8);
        breadcrumbWrapper.setAlignment(Pos.CENTER_LEFT);
        Label lblUbicacion = new Label("Ubicación:");
        lblUbicacion.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
        breadcrumbWrapper.getChildren().addAll(lblUbicacion, breadcrumbsBar);

        // Barra flotante de selección para cuarentena (Ruta 1: desde el Mapa de espacio)
        selectionBar = new HBox(12);
        selectionBar.setAlignment(Pos.CENTER_LEFT);
        selectionBar.setPadding(new Insets(10, 16, 10, 16));
        selectionBar.setStyle(
            "-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + ";" +
            "-fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + ";" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-border-width: 1.5px;"
        );
        selectionBar.setVisible(false);
        selectionBar.setManaged(false);

        lblSelectedCount = new Label("0 elementos seleccionados");
        lblSelectedCount.setStyle("-fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 12px;");

        btnQuarantineSelected = new Button("Enviar a cuarentena", AppIcons.create("shield", 14, com.fixtool.ui.theme.ThemeTokens.TEXT_DARK));
        btnQuarantineSelected.getStyleClass().add("button-primary");
        btnQuarantineSelected.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + "; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_DARK + "; -fx-font-weight: bold;");
        btnQuarantineSelected.setOnAction(e -> enviarSeleccionACuarentena());

        Button btnClearSel = new Button("Deseleccionar todo");
        btnClearSel.getStyleClass().add("button-secondary");
        btnClearSel.setOnAction(e -> clearMapSelection());

        Region spSel = new Region();
        HBox.setHgrow(spSel, Priority.ALWAYS);
        selectionBar.getChildren().addAll(lblSelectedCount, spSel, btnQuarantineSelected, btnClearSel);

        // Contenedor principal: Mapa de bloques (izquierda) + Top 10 más pesados (derecha)
        HBox mainSplit = new HBox(16);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        // Mapa de bloques con ScrollPane
        ScrollPane blocksScroll = new ScrollPane(blocksContainer);
        blocksScroll.setFitToWidth(true);
        blocksScroll.getStyleClass().add("card-panel");
        blocksScroll.setPrefHeight(450);
        HBox.setHgrow(blocksScroll, Priority.ALWAYS);

        // Panel lateral: Top 10 archivos
        VBox sidePanel = new VBox(12);
        sidePanel.setPrefWidth(320);
        sidePanel.setMinWidth(320);
        sidePanel.getStyleClass().add("card-panel");
        sidePanel.setPadding(new Insets(16));

        Label lblTop10Title = new Label("Archivos Más Pesados");
        lblTop10Title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        chkFilterOldFiles.setOnAction(e -> {
            if (lastScanResult != null) renderTop10(lastScanResult);
        });

        ScrollPane top10Scroll = new ScrollPane(top10Container);
        top10Scroll.setFitToWidth(true);
        top10Scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(top10Scroll, Priority.ALWAYS);

        sidePanel.getChildren().addAll(lblTop10Title, chkFilterOldFiles, top10Scroll);
        mainSplit.getChildren().addAll(blocksScroll, sidePanel);

        root.getChildren().addAll(selectorRow, progressBox, breadcrumbWrapper, selectionBar, mainSplit);

        ScrollPane tabScroll = new ScrollPane(root);
        tabScroll.setFitToWidth(true);
        tabScroll.getStyleClass().add("transparent-scroll");
        return tabScroll;
    }

    private void startScan() {
        btnScan.setDisable(true);
        btnCancelScan.setVisible(true);
        mapProgress.setVisible(true);
        lblScanStatus.setText("Escaneando contenido de: " + currentSelectedDir.getName() + "...");
        scanCancelFlag = new AtomicBoolean(false);

        Task<ScanResult> task = new Task<>() {
            @Override
            protected ScanResult call() {
                return service.scanDirectory(currentSelectedDir, scanCancelFlag, p -> Platform.runLater(() -> mapProgress.setProgress(p)));
            }
        };

        task.setOnSucceeded(e -> {
            btnScan.setDisable(false);
            btnCancelScan.setVisible(false);
            mapProgress.setVisible(false);

            ScanResult res = task.getValue();
            this.lastScanResult = res;

            if (res.cancelled) {
                lblScanStatus.setText("Escaneo cancelado por el usuario.");
            } else {
                lblScanStatus.setText(String.format("Escaneo finalizado: %s en %d archivos (%d elementos sin acceso omitidos).",
                        SystemInfoService.formatBytes(res.totalBytes), res.totalFiles, res.inaccessibleCount));
                currentRootNode = res.rootNode;
                navHistory.clear();
                navigateToFolder(currentRootNode);
                renderTop10(res);
            }
        });

        task.setOnFailed(e -> {
            btnScan.setDisable(false);
            btnCancelScan.setVisible(false);
            mapProgress.setVisible(false);
            lblScanStatus.setText("Error durante el escaneo: " + task.getException().getMessage());
        });

        new Thread(task, "ScanDirThread").start();
    }

    private void navigateToFolder(FolderNode node) {
        if (node == null) return;
        currentNavNode = node;
        updateBreadcrumbs();
        renderBlocks(node);
    }

    private void updateBreadcrumbs() {
        breadcrumbsBar.getChildren().clear();

        List<FolderNode> chain = new ArrayList<>(navHistory);
        chain.add(currentNavNode);

        for (int i = 0; i < chain.size(); i++) {
            FolderNode step = chain.get(i);
            Hyperlink link = new Hyperlink(step.name);
            link.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-font-size: 12px; -fx-font-weight: bold;");
            final int targetIndex = i;
            link.setOnAction(e -> {
                while (navHistory.size() > targetIndex) {
                    navHistory.pop();
                }
                navigateToFolder(step);
            });

            breadcrumbsBar.getChildren().add(link);
            if (i < chain.size() - 1) {
                Label sep = new Label(" / ");
                sep.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");
                breadcrumbsBar.getChildren().add(sep);
            }
        }
    }

    private void renderBlocks(FolderNode node) {
        blocksContainer.getChildren().clear();
        if (node.subFolders.isEmpty() && node.files.isEmpty()) {
            Label empty = new Label("Carpeta vacía o sin elementos accesibles.");
            empty.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 13px;");
            blocksContainer.getChildren().add(empty);
            return;
        }

        long parentBytes = Math.max(1, node.totalBytes);

        // Renderizar subcarpetas primero
        for (FolderNode sub : node.subFolders) {
            double pct = ((double) sub.totalBytes / parentBytes) * 100.0;
            Node block = createFolderBlock(sub, pct);
            blocksContainer.getChildren().add(block);
        }

        // Renderizar archivos directos
        for (FileItem file : node.files) {
            double pct = ((double) file.sizeBytes / parentBytes) * 100.0;
            Node block = createFileBlock(file, pct);
            blocksContainer.getChildren().add(block);
        }
    }

    private Node createFolderBlock(FolderNode folder, double pct) {
        File folderFile = new File(folder.path);
        boolean isSelected = selectedMapFiles.contains(folderFile);

        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.getStyleClass().add("card-panel");
        card.setPrefSize(140, 115);

        String baseBg = isSelected ? "#243142" : com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED;
        String baseBorder = isSelected ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL;
        double borderWidth = isSelected ? 2.0 : 1.0;
        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: %.1f; -fx-cursor: hand;", baseBg, baseBorder, borderWidth));

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        CheckBox chk = new CheckBox();
        chk.setSelected(isSelected);
        chk.setOnAction(e -> {
            toggleFileSelection(folderFile, chk.isSelected());
            e.consume();
        });
        topRow.getChildren().add(chk);

        Node icon = AppIcons.create("folder", 22, com.fixtool.ui.theme.ThemeTokens.ACCENT);
        Label lblName = new Label(folder.name);
        lblName.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        lblName.setMaxWidth(120);

        Label lblSize = new Label(SystemInfoService.formatBytes(folder.totalBytes));
        lblSize.setStyle("-fx-font-size: 10px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        Label lblPct = new Label(String.format("%.1f%%", pct));
        lblPct.setStyle("-fx-font-size: 10px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-font-weight: bold;");

        card.getChildren().addAll(topRow, icon, lblName, lblSize, lblPct);

        card.setOnMouseClicked(e -> {
            if (e.isControlDown() || e.isShiftDown()) {
                toggleFileSelection(folderFile, !selectedMapFiles.contains(folderFile));
            } else {
                navHistory.push(currentNavNode);
                navigateToFolder(folder);
            }
        });

        card.setOnMouseEntered(e -> {
            if (!selectedMapFiles.contains(folderFile)) {
                card.setStyle("-fx-background-color: #243142; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-cursor: hand;");
            }
        });
        card.setOnMouseExited(e -> {
            if (!selectedMapFiles.contains(folderFile)) {
                card.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-cursor: hand;");
            }
        });

        return card;
    }

    private Node createFileBlock(FileItem file, double pct) {
        File fileObj = new File(file.path);
        boolean isSelected = selectedMapFiles.contains(fileObj);

        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.getStyleClass().add("card-panel");
        card.setPrefSize(120, 105);

        String baseBg = isSelected ? "#243142" : com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED;
        String baseBorder = isSelected ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL;
        double borderWidth = isSelected ? 2.0 : 1.0;
        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: %.1f; -fx-cursor: hand;", baseBg, baseBorder, borderWidth));

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        CheckBox chk = new CheckBox();
        chk.setSelected(isSelected);
        chk.setOnAction(e -> {
            toggleFileSelection(fileObj, chk.isSelected());
            e.consume();
        });
        topRow.getChildren().add(chk);

        Node icon = AppIcons.create("filetext", 20, com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
        Label lblName = new Label(file.name);
        lblName.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        lblName.setMaxWidth(100);

        Label lblSize = new Label(SystemInfoService.formatBytes(file.sizeBytes));
        lblSize.setStyle("-fx-font-size: 10px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        card.getChildren().addAll(topRow, icon, lblName, lblSize);

        card.setOnMouseClicked(e -> {
            toggleFileSelection(fileObj, !selectedMapFiles.contains(fileObj));
        });

        card.setOnMouseEntered(e -> {
            if (!selectedMapFiles.contains(fileObj)) {
                card.setStyle("-fx-background-color: #243142; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-cursor: hand;");
            }
        });
        card.setOnMouseExited(e -> {
            if (!selectedMapFiles.contains(fileObj)) {
                card.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-cursor: hand;");
            }
        });

        return card;
    }

    private void renderTop10(ScanResult res) {
        top10Container.getChildren().clear();
        boolean filterOld = chkFilterOldFiles.isSelected();
        List<FileItem> list = filterOld ? res.oldFiles : res.top10Files;

        if (list.isEmpty()) {
            Label empty = new Label(filterOld ? "Sin archivos > 1 año" : "No se hallaron archivos.");
            empty.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
            top10Container.getChildren().add(empty);
            return;
        }

        for (FileItem item : list) {
            File fileObj = new File(item.path);
            boolean isSelected = selectedMapFiles.contains(fileObj);

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));

            String baseBg = isSelected ? "#243142" : com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED;
            String baseBorder = isSelected ? com.fixtool.ui.theme.ThemeTokens.ACCENT : "transparent";
            row.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6px; -fx-border-color: %s; -fx-border-radius: 6px; -fx-cursor: hand;", baseBg, baseBorder));

            CheckBox chk = new CheckBox();
            chk.setSelected(isSelected);
            chk.setOnAction(e -> {
                toggleFileSelection(fileObj, chk.isSelected());
                e.consume();
            });

            Node icon = AppIcons.create("filetext", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT);

            VBox info = new VBox(2);
            Label name = new Label(item.name);
            name.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold;");
            name.setMaxWidth(130);

            Label size = new Label(SystemInfoService.formatBytes(item.sizeBytes));
            size.setStyle("-fx-font-size: 10px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
            info.getChildren().addAll(name, size);

            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);

            if (item.isOlderThanOneYear()) {
                Label oldBadge = new Label("> 1 año");
                oldBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + "; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_DARK + "; -fx-padding: 2 6 2 6; -fx-background-radius: 10px;");
                row.getChildren().addAll(chk, icon, info, r, oldBadge);
            } else {
                row.getChildren().addAll(chk, icon, info);
            }

            row.setOnMouseClicked(e -> {
                toggleFileSelection(fileObj, !selectedMapFiles.contains(fileObj));
            });

            top10Container.getChildren().add(row);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PESTAÑA 2: LIMPIEZA CON CUARENTENA
    // ═══════════════════════════════════════════════════════════════════════════

    private Node buildTabLimpieza() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");

        VBox root = new VBox(20);
        root.setPadding(new Insets(20, 28, 28, 28));
        root.getStyleClass().add("bg-root");

        // Modo simulación banner
        HBox simBox = new HBox(12);
        simBox.setAlignment(Pos.CENTER_LEFT);
        simBox.getStyleClass().add("card-panel");
        simBox.setPadding(new Insets(16));
        simBox.getChildren().addAll(chkModeSimulacion);

        chkModeSimulacion.setOnAction(e -> {
            boolean sim = chkModeSimulacion.isSelected();
            btnCleanAction.setText(sim ? "Simular Limpieza" : "Mover a Cuarentena");
            btnCleanAction.setStyle(sim ? "" : "-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.COLOR_WARN + "; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_DARK + ";");
        });

        // Controles de antigüedad de descargas personalizable con selector de unidad y chips rápidos
        VBox ageBox = new VBox(10);
        ageBox.getStyleClass().add("card-panel");
        ageBox.setPadding(new Insets(16));

        Label lblAgeTitle = new Label("Filtro para descargas antiguas:");
        lblAgeTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        Label lblElegir = new Label("Limpiar archivos con más de:");
        lblElegir.setStyle("-fx-font-size: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        // Listener en el campo de texto y combo para recálculo debounced en vivo
        txtDownloadAge.textProperty().addListener((obs, oldVal, newVal) -> triggerDebouncedRecalculation());
        cmbAgeUnit.valueProperty().addListener((obs, oldVal, newVal) -> triggerDebouncedRecalculation());

        Button btnRecalcular = new Button("Recalcular Selección", AppIcons.create("refreshcw", 12, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnRecalcular.getStyleClass().add("button-secondary");
        btnRecalcular.setOnAction(e -> recargarAnalisisLimpieza());

        inputRow.getChildren().addAll(lblElegir, txtDownloadAge, cmbAgeUnit, btnRecalcular);

        // Chips de atajos rápidos
        HBox chipsRow = new HBox(8);
        chipsRow.setAlignment(Pos.CENTER_LEFT);
        Label lblChips = new Label("Atajos rápidos:");
        lblChips.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

        Button chip7d = createQuickChip("7 días", "7", "días");
        Button chip30d = createQuickChip("30 días", "30", "días");
        Button chip90d = createQuickChip("90 días", "90", "días");
        Button chip1y = createQuickChip("1 año", "1", "años");

        chipsRow.getChildren().addAll(lblChips, chip7d, chip30d, chip90d, chip1y);

        ageBox.getChildren().addAll(lblAgeTitle, inputRow, lblAgeError, chipsRow);

        // Lista de Categorías con casillas
        VBox catContainer = new VBox(10);
        Label lblCatTitle = new Label("Categorías de limpieza disponibles:");
        lblCatTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        catContainer.getChildren().addAll(lblCatTitle, cleaningCategoriesBox);

        // Botón de ejecución y resultado
        HBox actionRow = new HBox(16);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.getChildren().addAll(btnCleanAction, lblCleanResult);

        btnCleanAction.setOnAction(e -> ejecutarAccionLimpieza());

        // Sección de gestión de Cuarentena (Lotes guardados)
        VBox quarantineSection = new VBox(12);
        quarantineSection.getStyleClass().add("card-panel");
        quarantineSection.setPadding(new Insets(16));

        HBox qHeader = new HBox(12);
        qHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblQTitle = new Label("Cuarentena de Archivos (Aislamiento y Restauración Segura)");
        lblQTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
        Region qSp = new Region();
        HBox.setHgrow(qSp, Priority.ALWAYS);

        Button btnAddCustom = new Button("Agregar archivo o carpeta…", AppIcons.create("folder", 14, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        btnAddCustom.getStyleClass().add("button-secondary");
        btnAddCustom.setOnAction(e -> abrirSelectorParaCuarentena());

        Button btnPurgeAll = new Button("Vaciar Cuarentena Definitivamente", AppIcons.create("stopcircle", 14, com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR));
        btnPurgeAll.getStyleClass().add("button-danger");
        btnPurgeAll.setOnAction(e -> confirmarVaciadoCuarentena());

        qHeader.getChildren().addAll(lblQTitle, qSp, btnAddCustom, btnPurgeAll);
        quarantineSection.getChildren().addAll(qHeader, quarantineBatchesBox);

        root.getChildren().addAll(simBox, ageBox, catContainer, actionRow, quarantineSection, resultCardSlot);
        scroll.setContent(root);
        return scroll;
    }

    private Button createQuickChip(String label, String value, String unit) {
        Button btn = new Button(label);
        btn.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + "; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            txtDownloadAge.setText(value);
            cmbAgeUnit.setValue(unit);
            triggerDebouncedRecalculation();
        });
        return btn;
    }

    private Integer parseValidDays() {
        String raw = txtDownloadAge.getText() != null ? txtDownloadAge.getText().trim() : "";
        if (raw.isEmpty()) {
            showAgeError("Ingresa un número entero.");
            return null;
        }
        try {
            int num = Integer.parseInt(raw);
            if (num < 1) {
                showAgeError("Debe ser un número entero mayor o igual a 1 (ej. 1 o más).");
                return null;
            }
            clearAgeError();
            String unit = cmbAgeUnit.getValue() != null ? cmbAgeUnit.getValue() : "días";
            return switch (unit) {
                case "semanas" -> num * 7;
                case "meses" -> num * 30;
                case "años" -> num * 365;
                default -> num;
            };
        } catch (NumberFormatException e) {
            showAgeError("El valor debe ser un número entero válido.");
            return null;
        }
    }

    private void showAgeError(String msg) {
        lblAgeError.setText(msg);
        lblAgeError.setVisible(true);
        txtDownloadAge.setStyle("-fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR + "; -fx-border-radius: 4px;");
    }

    private void clearAgeError() {
        lblAgeError.setText("");
        lblAgeError.setVisible(false);
        txtDownloadAge.setStyle("");
    }

    private void triggerDebouncedRecalculation() {
        if (ageDebounceTimeline != null) {
            ageDebounceTimeline.stop();
        }
        Integer days = parseValidDays();
        if (days == null) return;

        ageDebounceTimeline = new Timeline(new KeyFrame(Duration.millis(350), e -> {
            recargarAnalisisLimpieza(days);
        }));
        ageDebounceTimeline.play();
    }

    private void recargarAnalisisLimpieza() {
        Integer days = parseValidDays();
        recargarAnalisisLimpieza(days != null ? days : 90);
    }

    private void recargarAnalisisLimpieza(int dias) {
        Task<List<CleanCategory>> task = new Task<>() {
            @Override
            protected List<CleanCategory> call() {
                return service.analyzeCleaning(dias);
            }
        };

        task.setOnSucceeded(e -> {
            currentCategories = task.getValue();
            renderCategories();
        });

        Thread t = new Thread(task, "CleaningAnalysisThread");
        t.setDaemon(true);
        t.start();
    }

    private void renderCategories() {
        cleaningCategoriesBox.getChildren().clear();
        for (CleanCategory cat : currentCategories) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-panel");
            row.setPadding(new Insets(12, 16, 12, 16));

            CheckBox chk = new CheckBox();
            chk.setSelected(cat.selected);
            chk.setOnAction(e -> cat.selected = chk.isSelected());

            VBox info = new VBox(3);
            Label name = new Label(cat.title);
            name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
            Label desc = new Label(cat.description);
            desc.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
            info.getChildren().addAll(name, desc);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label badge = new Label(String.format("%d archivos  •  %s", cat.filesToClean.size(), SystemInfoService.formatBytes(cat.totalBytes)));
            badge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 4 10 4 10;");

            row.getChildren().addAll(chk, info, sp, badge);
            cleaningCategoriesBox.getChildren().add(row);
        }
    }

    private void ejecutarAccionLimpieza() {
        boolean sim = chkModeSimulacion.isSelected();

        if (!sim) {
            // Confirmación antes de mover
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar traslado a cuarentena");
            confirm.setHeaderText("¿Deseas mover los archivos seleccionados a Cuarentena?");
            confirm.setContentText("Los archivos no serán eliminados. Se guardarán de forma segura en:\n"
                    + AppConfig.getWorkDir().resolve("Cuarentena") + "\n\nPodrás restaurarlos cuando quieras.");
            Optional<ButtonType> opt = confirm.showAndWait();
            if (opt.isEmpty() || opt.get() != ButtonType.OK) {
                return;
            }
        }

        btnCleanAction.setDisable(true);
        CleanReport report = service.executeCleaning(currentCategories, sim);
        btnCleanAction.setDisable(false);

        lblCleanResult.setText(String.join("\n", report.details));

        // Registrar en SessionState y mostrar StandardResultCard
        DiagnosticResult res = service.toDiagnosticResult(report);
        ToolStatus st = report.filesSkipped > 0 ? ToolStatus.ATENCION : ToolStatus.OK;
        SessionState.getInstance().setStatus(tool.getId(), st, res);

        resultCardSlot.getChildren().clear();
        StandardResultCard card = new StandardResultCard(
                st,
                sim ? "Simulación de Limpieza Completada" : "Archivos Protegidos en Cuarentena",
                "Espacio Procesado",
                String.format("%.2f MB", report.bytesFreed / (1024.0 * 1024.0)),
                sim ? "Análisis preventivo: No se alteró ningún archivo en disco." : "Archivos aislados con posibilidad de restauración por lote.",
                res.getTelemetry(),
                null
        );
        resultCardSlot.getChildren().add(card);

        // Actualizar lotes
        refrescarLotesCuarentena();
        recargarAnalisisLimpieza();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SELECCIÓN LIBRE Y GESTIÓN DE CUARENTENA (RUTAS 1 Y 2)
    // ═══════════════════════════════════════════════════════════════════════════

    private void toggleFileSelection(File file, boolean select) {
        if (select) {
            selectedMapFiles.add(file);
        } else {
            selectedMapFiles.remove(file);
        }
        updateMapSelectionUI();
        if (currentNavNode != null) {
            renderBlocks(currentNavNode);
        }
        if (lastScanResult != null) {
            renderTop10(lastScanResult);
        }
    }

    private void clearMapSelection() {
        selectedMapFiles.clear();
        updateMapSelectionUI();
        if (currentNavNode != null) {
            renderBlocks(currentNavNode);
        }
        if (lastScanResult != null) {
            renderTop10(lastScanResult);
        }
    }

    private void updateMapSelectionUI() {
        if (selectedMapFiles.isEmpty()) {
            selectionBar.setVisible(false);
            selectionBar.setManaged(false);
        } else {
            long totalBytes = 0;
            for (File f : selectedMapFiles) {
                totalBytes += f.isDirectory() ? calculateDirSize(f) : f.length();
            }
            lblSelectedCount.setText(String.format("%d elemento(s) seleccionado(s)  •  %s total",
                    selectedMapFiles.size(), SystemInfoService.formatBytes(totalBytes)));
            selectionBar.setVisible(true);
            selectionBar.setManaged(true);
        }
    }

    private long calculateDirSize(File dir) {
        long total = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) total += calculateDirSize(c);
                else total += c.length();
            }
        }
        return total;
    }

    private void enviarSeleccionACuarentena() {
        if (selectedMapFiles.isEmpty()) return;
        confirmarYEnviarACuarentena(new ArrayList<>(selectedMapFiles));
    }

    private void abrirSelectorParaCuarentena() {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Agregar a Cuarentena");
        dialog.setHeaderText("¿Qué tipo de elemento deseas enviar a la cuarentena?");
        dialog.setContentText("Puedes seleccionar archivos individuales o una carpeta completa de tu carpeta personal:");

        ButtonType btnFiles = new ButtonType("Seleccionar Archivo(s)");
        ButtonType btnFolder = new ButtonType("Seleccionar Carpeta");
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(btnFiles, btnFolder, btnCancel);
        Optional<ButtonType> choice = dialog.showAndWait();

        if (choice.isEmpty() || choice.get() == btnCancel) return;

        if (choice.get() == btnFiles) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar archivos para enviar a cuarentena");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            List<File> files = chooser.showOpenMultipleDialog(this.getScene().getWindow());
            if (files != null && !files.isEmpty()) {
                confirmarYEnviarACuarentena(files);
            }
        } else if (choice.get() == btnFolder) {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Seleccionar carpeta para enviar a cuarentena");
            dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            File chosen = dirChooser.showDialog(this.getScene().getWindow());
            if (chosen != null) {
                confirmarYEnviarACuarentena(Collections.singletonList(chosen));
            }
        }
    }

    private void confirmarYEnviarACuarentena(List<File> files) {
        if (files == null || files.isEmpty()) return;

        java.nio.file.Path userHome = java.nio.file.Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        java.nio.file.Path workDir = AppConfig.getWorkDir().toAbsolutePath().normalize();

        List<File> invalidFiles = new ArrayList<>();
        List<File> validFiles = new ArrayList<>();
        long totalBytes = 0;

        for (File f : files) {
            java.nio.file.Path p = f.toPath().toAbsolutePath().normalize();
            if (!p.startsWith(userHome)) {
                invalidFiles.add(f);
            } else if (p.startsWith(workDir) || p.equals(workDir)) {
                invalidFiles.add(f);
            } else {
                validFiles.add(f);
                totalBytes += (f.isDirectory() ? calculateDirSize(f) : f.length());
            }
        }

        if (!invalidFiles.isEmpty() && validFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Límite de seguridad");
            alert.setHeaderText("Solo se pueden mover elementos de tu carpeta personal");
            StringBuilder sb = new StringBuilder("Los siguientes elementos están fuera de tu carpeta personal o son carpetas protegidas de la aplicación:\n\n");
            for (File inv : invalidFiles) {
                sb.append("• ").append(inv.getAbsolutePath()).append("\n");
            }
            sb.append("\nPor tu seguridad, no se puede realizar la operación.");
            alert.setContentText(sb.toString());
            alert.showAndWait();
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("Se trasladarán a la carpeta de cuarentena los siguientes elementos:\n\n");
        int count = 0;
        for (File f : validFiles) {
            if (count < 6) {
                content.append("• ").append(f.getName())
                       .append(" (").append(f.getAbsolutePath()).append(" - ")
                       .append(SystemInfoService.formatBytes(f.isDirectory() ? calculateDirSize(f) : f.length())).append(")\n");
            }
            count++;
        }
        if (validFiles.size() > 6) {
            content.append("... y ").append(validFiles.size() - 6).append(" elemento(s) más.\n");
        }
        content.append("\nTamaño total: ").append(SystemInfoService.formatBytes(totalBytes));
        content.append("\n\nLos elementos no se borrarán: se moverán a FixTool_Willy/Cuarentena y podrás restaurarlos en cualquier momento.");

        if (!invalidFiles.isEmpty()) {
            content.append("\n\n(Aviso: Se omitirán ").append(invalidFiles.size()).append(" elemento(s) por estar fuera de tu carpeta personal).");
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar traslado a Cuarentena");
        confirm.setHeaderText("¿Enviar " + validFiles.size() + " elemento(s) a Cuarentena?");
        confirm.setContentText(content.toString());

        ButtonType btnMover = new ButtonType("Mover a Cuarentena", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnMover, btnCancelar);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != btnMover) return;

        // Ejecutar traslado seguro
        MoveToQuarantineResult moveRes = service.moveCustomItemsToQuarantine(validFiles);

        // Actualizar vistas y selecciones
        clearMapSelection();
        refrescarLotesCuarentena();

        if (lastScanResult != null) {
            startScan();
        }

        // Mostrar tarjeta de resultado estándar
        resultCardSlot.getChildren().clear();
        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Elementos procesados", String.valueOf(validFiles.size()));
        tele.put("Elementos trasladados", String.valueOf(moveRes.movedCount));
        tele.put("Elementos con error", String.valueOf(moveRes.failedCount));
        tele.put("Espacio liberado", SystemInfoService.formatBytes(moveRes.bytesFreed));
        if (moveRes.quarantinePath != null) {
            tele.put("Carpeta de cuarentena", moveRes.quarantinePath);
        }

        String verdict;
        ToolStatus status;
        if (moveRes.failedCount == 0 && moveRes.movedCount > 0) {
            status = ToolStatus.OK;
            verdict = String.format("%d elemento(s) enviados con éxito a cuarentena. Espacio aislado: %s.",
                    moveRes.movedCount, SystemInfoService.formatBytes(moveRes.bytesFreed));
        } else if (moveRes.movedCount > 0) {
            status = ToolStatus.ATENCION;
            verdict = String.format("%d elemento(s) trasladados a cuarentena, pero %d no se pudieron mover: %s",
                    moveRes.movedCount, moveRes.failedCount, String.join("; ", moveRes.errors));
        } else {
            status = ToolStatus.PROBLEMA;
            verdict = "No se pudo mover ningún elemento: " + String.join("; ", moveRes.errors);
        }

        StandardResultCard card = new StandardResultCard(
                status,
                "Traslado a Cuarentena: " + status.getDisplayText(),
                "Espacio Aislado",
                SystemInfoService.formatBytes(moveRes.bytesFreed),
                verdict,
                tele,
                null
        );
        resultCardSlot.getChildren().add(card);
        lblCleanResult.setText(verdict);
    }

    private void restaurarElementoIndividual(QuarantineItem item) {
        boolean ok = service.restoreSingleItem(item, null);
        if (ok) {
            lblCleanResult.setText("Elemento '" + item.name + "' restaurado con éxito a su ubicación original.");
            refrescarLotesCuarentena();
            if (lastScanResult != null) startScan();
        } else {
            // Ofrecer restaurar a otra carpeta si la original no existe o falla
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Restaurar en otra ubicación");
            alert.setHeaderText("No se pudo restaurar en su ubicación original");
            alert.setContentText("La ruta original no está disponible o no se tienen permisos.\n¿Deseas elegir otra carpeta para restaurarlo?");
            Optional<ButtonType> res = alert.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Elegir carpeta destino para restaurar");
                chooser.setInitialDirectory(new File(System.getProperty("user.home")));
                File chosen = chooser.showDialog(this.getScene().getWindow());
                if (chosen != null) {
                    boolean ok2 = service.restoreSingleItem(item, chosen.toPath());
                    if (ok2) {
                        lblCleanResult.setText("Elemento '" + item.name + "' restaurado con éxito en: " + chosen.getAbsolutePath());
                        refrescarLotesCuarentena();
                        if (lastScanResult != null) startScan();
                    } else {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Error al restaurar");
                        err.setHeaderText("Fallo en la restauración");
                        err.setContentText("No se pudo restaurar el archivo en la carpeta elegida.");
                        err.showAndWait();
                    }
                }
            }
        }
    }

    private void refrescarLotesCuarentena() {
        quarantineBatchesBox.getChildren().clear();
        List<QuarantineItem> items = service.listAllQuarantineItems();
        List<QuarantineBatch> batches = service.listQuarantineBatches();

        if (items.isEmpty() && batches.isEmpty()) {
            Label empty = new Label("No hay archivos en cuarentena actualmente.");
            empty.setStyle("-fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");
            quarantineBatchesBox.getChildren().add(empty);
            return;
        }

        // Mostrar elementos individuales con nombre, ruta original, fecha y tamaño
        for (QuarantineItem item : items) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 8px; -fx-border-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 8px; -fx-border-width: 1px;");

            Node icon = AppIcons.create(item.isDirectory ? "folder" : "filetext", 18, item.isDirectory ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY);

            VBox info = new VBox(3);
            Label name = new Label(item.name);
            name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");

            Label path = new Label("Ruta original: " + item.originalPath);
            path.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");

            Label meta = new Label(item.dateStr + "  •  " + SystemInfoService.formatBytes(item.sizeBytes) + (item.isDirectory ? " (Carpeta)" : ""));
            meta.setStyle("-fx-font-size: 10px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.ACCENT + "; -fx-font-weight: bold;");

            info.getChildren().addAll(name, path, meta);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button btnRestore = new Button("Restaurar", AppIcons.create("refreshcw", 12, com.fixtool.ui.theme.ThemeTokens.ACCENT));
            btnRestore.getStyleClass().add("button-secondary");
            btnRestore.setOnAction(e -> restaurarElementoIndividual(item));

            row.getChildren().addAll(icon, info, sp, btnRestore);
            quarantineBatchesBox.getChildren().add(row);
        }

        // Si hubiera lotes previos no individualizados
        if (items.isEmpty()) {
            for (QuarantineBatch b : batches) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));
                row.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 8px;");

                Node icon = AppIcons.create("shield", 16, com.fixtool.ui.theme.ThemeTokens.COLOR_WARN);

                VBox info = new VBox(2);
                Label name = new Label("Lote: " + b.id);
                name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_PRIMARY + ";");
                Label meta = new Label(b.fileCount + " archivos  •  " + SystemInfoService.formatBytes(b.totalBytes));
                meta.setStyle("-fx-font-size: 11px; -fx-text-fill: " + com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY + ";");
                info.getChildren().addAll(name, meta);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button btnRestore = new Button("Restaurar Lote", AppIcons.create("refreshcw", 12, com.fixtool.ui.theme.ThemeTokens.ACCENT));
                btnRestore.getStyleClass().add("button-secondary");
                btnRestore.setOnAction(e -> {
                    int restored = service.restoreBatch(b);
                    lblCleanResult.setText("Lote restaurado con éxito: " + restored + " archivos devueltos a sus rutas originales.");
                    refrescarLotesCuarentena();
                    recargarAnalisisLimpieza();
                });

                row.getChildren().addAll(icon, info, sp, btnRestore);
                quarantineBatchesBox.getChildren().add(row);
            }
        }
    }

    private void confirmarVaciadoCuarentena() {
        // Primera confirmación
        Alert a1 = new Alert(Alert.AlertType.CONFIRMATION);
        a1.setTitle("Vaciar Cuarentena – Confirmación 1 de 2");
        a1.setHeaderText("¿Estás seguro de que deseas eliminar permanentemente la cuarentena?");
        a1.setContentText("Esta acción borrará de manera definitiva todos los archivos aislados en la carpeta de cuarentena.\n"
                + "No podrás volver a restaurarlos.");
        Optional<ButtonType> r1 = a1.showAndWait();
        if (r1.isEmpty() || r1.get() != ButtonType.OK) return;

        // Segunda confirmación (obligatoria por especificación)
        Alert a2 = new Alert(Alert.AlertType.WARNING);
        a2.setTitle("Vaciar Cuarentena – Confirmación 2 de 2");
        a2.setHeaderText("ADVERTENCIA FINAL: Esta acción no se puede deshacer.");
        a2.setContentText("¿Confirmas la eliminación irrecuperable de todos los archivos en cuarentena?");
        Optional<ButtonType> r2 = a2.showAndWait();
        if (r2.isPresent() && r2.get() == ButtonType.OK) {
            service.purgeAllQuarantine();
            lblCleanResult.setText("La carpeta de cuarentena ha sido vaciada de forma definitiva.");
            refrescarLotesCuarentena();
        }
    }
}
