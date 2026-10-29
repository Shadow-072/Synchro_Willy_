package com.fixtool.ui;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.service.PcMapService;
import com.fixtool.service.PcMapService.AdapterNode;
import com.fixtool.service.PcMapService.DeviceNode;
import com.fixtool.service.PcMapService.DeviceType;
import com.fixtool.service.PcMapService.MapScanResult;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 7: Mapeo de Red Local.
 * Diagrama visual de topología en red:
 * - Centro: Mi PC (Silver)
 * - Nodos de adaptadores activos (Wi-Fi / Ethernet)
 * - Nodos de dispositivos descubiertos con identificación por MAC OUI, TTL y puertos
 * - Líneas continua (cable) y punteada (Wi-Fi)
 * - BOSGAME siempre visible y resaltada
 * - Zoom, leyenda, etiquetado manual persistente en FixTool_Willy/device_labels.json
 */
public class MapaPcView extends VBox {

    private final DiagnosticAction tool;
    private final ConnectionConfig config;
    private final PcMapService service = new PcMapService();
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // Controles de barra
    private final Button btnScan;
    private final Button btnCancel;
    private final CheckBox chkShowVirtuals;
    private final TextField txtFilter;
    private final Label lblStatus;
    private final ProgressIndicator progress;

    // Controles de zoom
    private double zoomFactor = 1.0;
    private final Pane diagramPane;
    private final Group zoomGroup;

    // Área de detalles y resultados
    private final VBox detailCard;
    private final VBox resultCardSlot;

    // Estado del escaneo
    private MapScanResult currentResult = null;
    private DeviceNode selectedDevice = null;

    public MapaPcView(DiagnosticAction tool, ConnectionConfig config) {
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

        // ── 1. Encabezado de la herramienta ──────────────────────────────────
        HBox header = buildHeader();

        // ── 2. Barra de acciones, filtros y radar ────────────────────────────
        btnScan = new Button("Escanear red", AppIcons.create("compass", 16, ThemeTokens.TEXT_PRIMARY));
        btnScan.getStyleClass().add("btn-primary");
        btnScan.setOnAction(e -> startScan());

        btnCancel = new Button("Cancelar", AppIcons.create("xcircle", 15, ThemeTokens.TEXT_SECONDARY));
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setDisable(true);
        btnCancel.setOnAction(e -> cancelScan());

        progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        lblStatus = new Label("Presiona 'Escanear red' para generar la topología visual interactiva.");
        lblStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 13px;");

        chkShowVirtuals = new CheckBox("Mostrar virtuales");
        chkShowVirtuals.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");
        chkShowVirtuals.setOnAction(e -> renderDiagram());

        txtFilter = new TextField();
        txtFilter.setPromptText("Buscar dispositivo...");
        txtFilter.getStyleClass().add("search-field");
        txtFilter.setPrefWidth(180);
        txtFilter.textProperty().addListener((obs, oldV, newV) -> renderDiagram());

        HBox actionToolbar = new HBox(10, btnScan, btnCancel, progress, lblStatus, new Region(), chkShowVirtuals, txtFilter);
        HBox.setHgrow(actionToolbar.getChildren().get(4), Priority.ALWAYS);
        actionToolbar.setAlignment(Pos.CENTER_LEFT);
        actionToolbar.setPadding(new Insets(10, 16, 10, 16));
        actionToolbar.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        // ── 3. Vista central: Diagrama de Topología + Panel de detalles ──────
        diagramPane = new Pane();
        diagramPane.setPrefSize(760, 520);
        diagramPane.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        zoomGroup = new Group(diagramPane);

        ScrollPane scrollDiagram = new ScrollPane(zoomGroup);
        scrollDiagram.setPannable(true);
        scrollDiagram.setFitToWidth(false);
        scrollDiagram.setFitToHeight(false);
        scrollDiagram.setPrefHeight(520);
        scrollDiagram.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Controles de zoom y leyenda superpuestos en la vista de diagrama
        VBox diagramContainer = new VBox(8);
        HBox topDiagramBar = buildDiagramOverlayBar();
        diagramContainer.getChildren().addAll(topDiagramBar, scrollDiagram);
        HBox.setHgrow(diagramContainer, Priority.ALWAYS);

        detailCard = buildDetailPanel();
        detailCard.setPrefWidth(340);
        detailCard.setMinWidth(300);

        HBox mainSplit = new HBox(16, diagramContainer, detailCard);
        HBox.setHgrow(diagramContainer, Priority.ALWAYS);

        // ── 4. Ranura para la tarjeta estándar de resultados ─────────────────
        resultCardSlot = new VBox();
        DiagnosticResult prev = SessionState.getInstance().getResult(tool.getId());
        if (prev != null) {
            resultCardSlot.getChildren().add(new StandardResultCard(prev));
        }

        content.getChildren().addAll(header, actionToolbar, mainSplit, resultCardSlot);
        rootScroll.setContent(content);
        getChildren().add(rootScroll);

        // Estado inicial amigable
        renderEmptyState();
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

    private HBox buildDiagramOverlayBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));

        // Botones de zoom
        Button btnZoomIn = new Button("+");
        btnZoomIn.setStyle("-fx-font-weight: bold; -fx-padding: 3 8 3 8;");
        btnZoomIn.setOnAction(e -> applyZoom(zoomFactor + 0.15));

        Button btnZoomOut = new Button("-");
        btnZoomOut.setStyle("-fx-font-weight: bold; -fx-padding: 3 8 3 8;");
        btnZoomOut.setOnAction(e -> applyZoom(zoomFactor - 0.15));

        Button btnZoomReset = new Button("100%");
        btnZoomReset.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8;");
        btnZoomReset.setOnAction(e -> applyZoom(1.0));

        // Mini Leyenda
        HBox legend = new HBox(12);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
                buildLegendItem("Cable (Línea continua)", ThemeTokens.BORDER_CONTROL, false),
                buildLegendItem("Wi-Fi (Línea punteada)", ThemeTokens.ACCENT, true),
                buildLegendItem("En línea", ThemeTokens.COLOR_OK, false),
                buildLegendItem("No responde", ThemeTokens.COLOR_NEUTRAL, false),
                buildLegendItem("BOSGAME (Destacada)", ThemeTokens.COLOR_WARN, false)
        );

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        bar.getChildren().addAll(btnZoomIn, btnZoomOut, btnZoomReset, sp, legend);
        return bar;
    }

    private HBox buildLegendItem(String labelText, String colorHex, boolean dashed) {
        HBox item = new HBox(5);
        item.setAlignment(Pos.CENTER_LEFT);

        Node marker;
        if (dashed) {
            Line line = new Line(0, 0, 16, 0);
            line.setStroke(Color.web(colorHex));
            line.setStrokeWidth(2.5);
            line.getStrokeDashArray().addAll(4.0, 3.0);
            marker = line;
        } else {
            Circle c = new Circle(4.5, Color.web(colorHex));
            marker = c;
        }

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px;");

        item.getChildren().addAll(marker, lbl);
        return item;
    }

    private void applyZoom(double newZoom) {
        zoomFactor = Math.max(0.6, Math.min(2.0, newZoom));
        diagramPane.setScaleX(zoomFactor);
        diagramPane.setScaleY(zoomFactor);
    }

    private VBox buildDetailPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18));
        panel.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        Label lblTitle = new Label("Detalles del Dispositivo");
        lblTitle.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label placeholder = new Label("Selecciona cualquier equipo en el diagrama interactivo para ver su fabricante, sistema operativo estimado por TTL, servicios detectados o personalizar su nombre.");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        panel.getChildren().addAll(lblTitle, placeholder);
        return panel;
    }

    private void renderEmptyState() {
        diagramPane.getChildren().clear();
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(760, 520);

        Node icon = AppIcons.create("compass", 52, ThemeTokens.BORDER_CONTROL);
        Label lbl = new Label("Diagrama de red no generado");
        lbl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sub = new Label("Presiona 'Escanear red' para trazar los adaptadores de Mi PC y los equipos de la LAN.");
        sub.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        box.getChildren().addAll(icon, lbl, sub);
        diagramPane.getChildren().add(box);
    }

    private void startScan() {
        btnScan.setDisable(true);
        btnCancel.setDisable(false);
        progress.setVisible(true);
        lblStatus.setText("Escaneando adaptadores, tabla ARP y sondeo de servicios...");
        cancelFlag.set(false);

        Task<MapScanResult> task = new Task<>() {
            @Override
            protected MapScanResult call() {
                return service.scanNetwork(config != null ? config.getIpTarget() : null, cancelFlag);
            }
        };

        task.setOnSucceeded(e -> {
            btnScan.setDisable(false);
            btnCancel.setDisable(true);
            progress.setVisible(false);
            currentResult = task.getValue();

            lblStatus.setText(String.format("Escaneo completado: %d adaptadores, %d dispositivos encontrados.",
                    currentResult.adapters.size(), currentResult.totalDevicesFound));

            renderDiagram();

            // Guardar diagnóstico en sesión
            DiagnosticResult diag = service.toDiagnosticResult(currentResult);
            SessionState.getInstance().setResult(tool.getId(), diag);
            resultCardSlot.getChildren().setAll(new StandardResultCard(diag));
        });

        task.setOnFailed(e -> {
            btnScan.setDisable(false);
            btnCancel.setDisable(true);
            progress.setVisible(false);
            lblStatus.setText("Error al escanear la red: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void cancelScan() {
        cancelFlag.set(true);
        btnCancel.setDisable(true);
        lblStatus.setText("Cancelando escaneo...");
    }

    /**
     * Dibuja el diagrama radial interactivo de red:
     * - Centro: Mi PC (Silver)
     * - Anillo 1: Adaptadores activos
     * - Anillo 2: Dispositivos descubiertos alrededor de su adaptador
     */
    private void renderDiagram() {
        diagramPane.getChildren().clear();
        if (currentResult == null) {
            renderEmptyState();
            return;
        }

        boolean showVirtuals = chkShowVirtuals.isSelected();
        String filter = txtFilter.getText() != null ? txtFilter.getText().trim().toLowerCase() : "";

        // Filtrar adaptadores según opción virtual
        List<AdapterNode> activeAdapters = new ArrayList<>();
        for (AdapterNode a : currentResult.adapters) {
            if (!a.isVirtual || showVirtuals) {
                activeAdapters.add(a);
            }
        }

        if (activeAdapters.isEmpty()) {
            renderEmptyState();
            return;
        }

        double centerX = 380;
        double centerY = 260;

        // ── 1. NODO CENTRAL: Mi PC (Silver) ──────────────────────────────────
        Node centralPcNode = buildCenterPcNode(currentResult.localPcName);
        centralPcNode.setLayoutX(centerX - 45);
        centralPcNode.setLayoutY(centerY - 45);

        // Capas para dibujo (Líneas debajo, Nodos encima)
        Group linesGroup = new Group();
        Group nodesGroup = new Group();

        nodesGroup.getChildren().add(centralPcNode);

        int adapterCount = activeAdapters.size();
        double adapterRadius = 135;

        // Distribuir adaptadores alrededor del centro
        for (int i = 0; i < adapterCount; i++) {
            AdapterNode adapter = activeAdapters.get(i);
            double angle = (2 * Math.PI / adapterCount) * i - (Math.PI / 2);

            double adpX = centerX + adapterRadius * Math.cos(angle);
            double adpY = centerY + adapterRadius * Math.sin(angle);

            // Línea del centro al adaptador
            Line lineToCenter = new Line(centerX, centerY, adpX, adpY);
            if (adapter.isWifi) {
                lineToCenter.setStroke(Color.web(ThemeTokens.ACCENT));
                lineToCenter.setStrokeWidth(2.2);
                lineToCenter.getStrokeDashArray().addAll(6.0, 4.0); // Punteada para Wi-Fi
            } else {
                lineToCenter.setStroke(Color.web(ThemeTokens.BORDER_CONTROL));
                lineToCenter.setStrokeWidth(2.2); // Continua para cable
            }
            linesGroup.getChildren().add(lineToCenter);

            // Nodo de Adaptador
            Node adpNode = buildAdapterNode(adapter);
            adpNode.setLayoutX(adpX - 35);
            adpNode.setLayoutY(adpY - 35);
            nodesGroup.getChildren().add(adpNode);

            // Dispositivos conectados a este adaptador
            List<DeviceNode> devList = adapter.devices;
            int devCount = devList.size();
            double deviceOrbitRadius = 140;

            for (int j = 0; j < devCount; j++) {
                DeviceNode dev = devList.get(j);

                // Filtrar si el usuario escribió en el buscador
                if (!filter.isEmpty()) {
                    boolean match = dev.ip.contains(filter)
                            || dev.humanTitle.toLowerCase().contains(filter)
                            || dev.hostName.toLowerCase().contains(filter)
                            || dev.manufacturer.toLowerCase().contains(filter);
                    if (!match) continue;
                }

                // Distribuir en abanico alrededor del adaptador
                double spread = (devCount > 1) ? (Math.PI / 1.5) : 0;
                double devAngle = angle - (spread / 2) + (devCount > 1 ? (spread / (devCount - 1)) * j : 0);

                double devX = adpX + deviceOrbitRadius * Math.cos(devAngle);
                double devY = adpY + deviceOrbitRadius * Math.sin(devAngle);

                // Línea del adaptador al dispositivo
                Line lineToDev = new Line(adpX, adpY, devX, devY);
                if (adapter.isWifi) {
                    lineToDev.setStroke(Color.web(ThemeTokens.ACCENT));
                    lineToDev.setStrokeWidth(1.6);
                    lineToDev.getStrokeDashArray().addAll(4.0, 3.0);
                } else {
                    lineToDev.setStroke(Color.web(ThemeTokens.BORDER_CONTROL));
                    lineToDev.setStrokeWidth(1.6);
                }
                linesGroup.getChildren().add(lineToDev);

                // Nodo del Dispositivo
                Node devNode = buildDeviceNode(dev);
                devNode.setLayoutX(devX - 30);
                devNode.setLayoutY(devY - 30);
                nodesGroup.getChildren().add(devNode);
            }
        }

        diagramPane.getChildren().addAll(linesGroup, nodesGroup);
    }

    private Node buildCenterPcNode(String pcName) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(90, 90);
        box.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 45; -fx-border-color: %s; -fx-border-width: 2.5; -fx-border-radius: 45;",
                ThemeTokens.BG_SURFACE_ELEVATED, ThemeTokens.ACCENT));

        Node icon = AppIcons.create("monitor", 24, ThemeTokens.ACCENT);
        Label title = new Label(pcName);
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label sub = new Label("Mi PC (Este equipo)");
        sub.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 9px;");

        box.getChildren().addAll(icon, title, sub);
        Tooltip.install(box, new Tooltip("Equipo Local: " + pcName + "\nCentro de la red"));
        return box;
    }

    private Node buildAdapterNode(AdapterNode adapter) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(70, 70);

        String col = adapter.isWifi ? ThemeTokens.ACCENT : ThemeTokens.COLOR_OK;
        box.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 35; -fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 35;",
                ThemeTokens.BG_BASE, col));

        Node icon = AppIcons.create(adapter.isWifi ? "wifi" : "activity", 20, col);
        Label name = new Label(adapter.isWifi ? "Wi-Fi" : "Ethernet");
        name.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 10px;");

        Label ip = new Label(adapter.ipAddress);
        ip.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 8px;");

        box.getChildren().addAll(icon, name, ip);
        Tooltip.install(box, new Tooltip(String.format("Adaptador: %s\nIP: %s\nMAC: %s\nVecinos detectados: %d",
                adapter.displayName, adapter.ipAddress, adapter.macAddress, adapter.devices.size())));
        return box;
    }

    private Node buildDeviceNode(DeviceNode dev) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(60, 60);

        // Color de estado: BOSGAME resaltada en amarillo/ámbar; verde si responde; gris si no responde
        String borderCol;
        String bgCol = ThemeTokens.BG_BASE;

        if (dev.isBosgame) {
            borderCol = dev.isReachable ? ThemeTokens.COLOR_WARN : ThemeTokens.COLOR_NEUTRAL;
            bgCol = dev.isReachable ? "#2A2415" : ThemeTokens.BG_BASE;
        } else if (dev.isReachable) {
            borderCol = dev.type.getColorHex();
        } else {
            borderCol = ThemeTokens.COLOR_NEUTRAL;
        }

        boolean isSelected = selectedDevice != null && dev.ip.equals(selectedDevice.ip);
        if (isSelected) {
            borderCol = ThemeTokens.ACCENT;
            bgCol = ThemeTokens.BG_SURFACE_HOVER;
        }

        box.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 30; -fx-border-color: %s; -fx-border-width: %s; -fx-border-radius: 30; -fx-cursor: hand;",
                bgCol, borderCol, (dev.isBosgame || isSelected) ? "2.5" : "1.5"));

        Node icon = AppIcons.create(dev.type.getIconKey(), 18, borderCol);

        String shortTitle = dev.customLabel != null ? dev.customLabel : (dev.isBosgame ? "BOSGAME" : (dev.isGateway ? "Router" : dev.ip.substring(dev.ip.lastIndexOf('.') + 1)));
        if (shortTitle.length() > 9) shortTitle = shortTitle.substring(0, 8) + "…";

        Label lblName = new Label(shortTitle);
        lblName.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 9px; -fx-font-weight: bold;", dev.isReachable ? ThemeTokens.TEXT_PRIMARY : ThemeTokens.TEXT_SECONDARY));

        Label lblStatus = new Label(dev.isReachable ? (dev.responseTimeMs >= 0 ? dev.responseTimeMs + "ms" : "OK") : "No responde");
        lblStatus.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 8px;", dev.isReachable ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_NEUTRAL));

        box.getChildren().addAll(icon, lblName, lblStatus);

        // Tooltip al pasar el cursor
        String tt = String.format("%s\nIP: %s\nMAC: %s\nFabricante: %s\nSistema: %s\nEstado: %s",
                dev.humanTitle, dev.ip, dev.mac, dev.manufacturer, dev.probableOs, dev.isReachable ? "En línea" : "No responde");
        Tooltip.install(box, new Tooltip(tt));

        box.setOnMouseClicked(e -> {
            selectedDevice = dev;
            renderDiagram();
            showDeviceDetails(dev);
        });

        return box;
    }

    /**
     * Muestra el panel lateral interactivo con los detalles completos y botón de renombrar.
     */
    private void showDeviceDetails(DeviceNode dev) {
        detailCard.getChildren().clear();

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Node icon = AppIcons.create(dev.type.getIconKey(), 22, dev.isReachable ? dev.type.getColorHex() : ThemeTokens.COLOR_NEUTRAL);

        Label title = new Label(dev.humanTitle);
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        HBox.setHgrow(title, Priority.ALWAYS);
        top.getChildren().addAll(icon, title);

        VBox contentBox = new VBox(8);

        // Badge de estado y rol
        HBox badges = new HBox(6);
        Label bStatus = new Label(dev.isReachable ? "EN LÍNEA" : "NO RESPONDE");
        bStatus.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;",
                dev.isReachable ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_NEUTRAL, ThemeTokens.TEXT_DARK));
        badges.getChildren().add(bStatus);

        if (dev.isBosgame) {
            Label bBos = new Label("BOSGAME TARGET");
            bBos.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;",
                    ThemeTokens.COLOR_WARN, ThemeTokens.TEXT_DARK));
            badges.getChildren().add(bBos);
        }
        if (dev.isGateway) {
            Label bGw = new Label("GATEWAY / ROUTER");
            bGw.setStyle("-fx-background-color: " + ThemeTokens.ACCENT + "; -fx-text-fill: " + ThemeTokens.TEXT_DARK + "; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");
            badges.getChildren().add(bGw);
        }
        if (dev.isNew) {
            Label bNew = new Label("NUEVO");
            bNew.setStyle("-fx-background-color: " + ThemeTokens.COLOR_OK + "; -fx-text-fill: " + ThemeTokens.TEXT_DARK + "; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");
            badges.getChildren().add(bNew);
        }

        // Metadatos detallados
        VBox metaBox = new VBox(6);
        metaBox.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-padding: 10; -fx-background-radius: 6;");
        metaBox.getChildren().addAll(
                buildDetailRow("Dirección IP:", dev.ip),
                buildDetailRow("Dirección MAC:", dev.mac != null ? dev.mac : "Desconocida"),
                buildDetailRow("Nombre de Host:", dev.hostName),
                buildDetailRow("Fabricante (OUI):", dev.manufacturer),
                buildDetailRow("Sistema Estimado:", dev.probableOs + (dev.ttl > 0 ? " (TTL=" + dev.ttl + ")" : "")),
                buildDetailRow("Latencia / Eco:", dev.responseTimeMs >= 0 ? dev.responseTimeMs + " ms" : "Sin respuesta"),
                buildDetailRow("Tipo inferido:", dev.type.getDisplay())
        );

        // Servicios visibles
        VBox servBox = new VBox(4);
        servBox.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-padding: 8; -fx-background-radius: 6;");
        Label servTitle = new Label("Servicios de red visibles:");
        servTitle.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        servBox.getChildren().add(servTitle);

        if (dev.visibleServices.isEmpty()) {
            Label noServ = new Label("No se detectaron puertos de servicio estándar abiertos.");
            noServ.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px;");
            servBox.getChildren().add(noServ);
        } else {
            for (String s : dev.visibleServices) {
                Label sLbl = new Label("• " + s);
                sLbl.setStyle("-fx-text-fill: " + ThemeTokens.COLOR_OK + "; -fx-font-size: 10px;");
                servBox.getChildren().add(sLbl);
            }
        }

        // Botón para renombrar y personalizar tipo (Etiquetado manual con persistencia)
        Button btnRename = new Button("Renombrar / Editar etiqueta", AppIcons.create("edit", 13, ThemeTokens.TEXT_PRIMARY));
        btnRename.getStyleClass().add("btn-secondary");
        btnRename.setMaxWidth(Double.MAX_VALUE);
        btnRename.setOnAction(e -> showRenameDialog(dev));

        contentBox.getChildren().addAll(badges, metaBox, servBox, btnRename);

        ScrollPane scrollDetail = new ScrollPane(contentBox);
        scrollDetail.setFitToWidth(true);
        scrollDetail.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollDetail.setPrefHeight(420);

        detailCard.getChildren().addAll(top, scrollDetail);
    }

    private void showRenameDialog(DeviceNode dev) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Etiquetar Dispositivo");
        dialog.setHeaderText("Personalizar nombre y tipo para " + dev.ip);

        TextField txtName = new TextField(dev.customLabel != null ? dev.customLabel : dev.humanTitle);
        txtName.setPromptText("Ej: PC Oficina, Impresora Bryan...");

        ComboBox<DeviceType> cbType = new ComboBox<>();
        cbType.getItems().addAll(DeviceType.values());
        cbType.setValue(dev.customType != null ? dev.customType : dev.type);

        VBox form = new VBox(8);
        form.setPadding(new Insets(10));
        form.getChildren().addAll(
                new Label("Nombre personalizado:"), txtName,
                new Label("Tipo de dispositivo:"), cbType
        );

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String name = txtName.getText().trim();
                if (!name.isEmpty()) {
                    DeviceType t = cbType.getValue();
                    PcMapService.saveManualLabel(dev.mac != null && !dev.mac.isBlank() ? dev.mac : dev.ip, name, t);
                    dev.customLabel = name;
                    dev.customType = t;
                    dev.humanTitle = name;
                    dev.type = t;
                    dev.isManuallyLabeled = true;

                    renderDiagram();
                    showDeviceDetails(dev);
                }
            }
        });
    }

    private HBox buildDetailRow(String label, String value) {
        HBox row = new HBox(6);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px;");
        l.setPrefWidth(110);

        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 10px;");
        v.setWrapText(true);

        row.getChildren().addAll(l, v);
        return row;
    }
}
