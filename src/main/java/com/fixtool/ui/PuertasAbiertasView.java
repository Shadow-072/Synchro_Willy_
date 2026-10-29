package com.fixtool.ui;

import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.service.OpenPortsService;
import com.fixtool.service.OpenPortsService.OpenPortItem;
import com.fixtool.service.OpenPortsService.PortScope;
import com.fixtool.service.OpenPortsService.PortsScanResult;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 9: Puertos Abiertos (Inspección de puertos en escucha y sockets activos).
 * Detalla los 6 aspectos clave de cada puerto: Qué es, Para qué sirve, ¿Es normal?,
 * Programa que lo abrió, Alcance y Riesgo/Qué hacer.
 */
public class PuertasAbiertasView extends VBox {

    private final DiagnosticAction tool;
    private final OpenPortsService service = new OpenPortsService();
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // Controles
    private final Button btnScan;
    private final ComboBox<String> cbScopeFilter;
    private final TextField txtFilter;
    private final ProgressIndicator progress;
    private final Label lblStatus;

    // Métricas superiores
    private final Label lblCountExposed;
    private final Label lblCountLocal;

    // Área principal
    private final VBox portsListContainer;
    private final ScrollPane scrollList;
    private final VBox detailPanel;
    private final VBox resultCardSlot;

    // Datos y estado de selección preservado
    private PortsScanResult currentScan = null;
    private String selectedPortKey = null;

    public PuertasAbiertasView(DiagnosticAction tool) {
        this.tool = tool;

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

        // ── 2. Tarjetas de resumen (Puertos visibles en la red vs Solo local) ─
        lblCountExposed = new Label("0");
        lblCountLocal = new Label("0");

        VBox cardExposed = buildScopeSummaryCard(
                "Puertos visibles en la red", lblCountExposed,
                "Puertos que escuchan conexiones desde otros dispositivos en tu red local.",
                ThemeTokens.COLOR_WARN, "globe"
        );
        VBox cardLocal = buildScopeSummaryCard(
                "Solo en este PC (Localhost)", lblCountLocal,
                "Puertos seguros asignados para comunicación interna exclusivamente entre programas locales (127.0.0.1).",
                ThemeTokens.ACCENT, "shield"
        );

        HBox summaryCards = new HBox(16, cardExposed, cardLocal);
        HBox.setHgrow(cardExposed, Priority.ALWAYS);
        HBox.setHgrow(cardLocal, Priority.ALWAYS);

        // ── 3. Barra de herramientas ──────────────────────────────────────────
        btnScan = new Button("Escanear puertos", AppIcons.create("activity", 16, ThemeTokens.TEXT_PRIMARY));
        btnScan.getStyleClass().add("btn-primary");
        btnScan.setOnAction(e -> startScan());

        progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        lblStatus = new Label("Haz clic en 'Escanear puertos' para consultar los sockets en escucha.");
        lblStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 13px;");

        cbScopeFilter = new ComboBox<>();
        cbScopeFilter.getItems().addAll("Todos los puertos", "Solo puertos visibles en la red", "Solo Locales (127.0.0.1)");
        cbScopeFilter.setValue("Todos los puertos");
        cbScopeFilter.setOnAction(e -> renderPortsList());

        txtFilter = new TextField();
        txtFilter.setPromptText("Buscar por puerto, programa o PID...");
        txtFilter.getStyleClass().add("search-field");
        txtFilter.setPrefWidth(240);
        txtFilter.textProperty().addListener((obs, oldV, newV) -> renderPortsList());

        HBox toolbar = new HBox(10, btnScan, progress, lblStatus, new Region(), cbScopeFilter, txtFilter);
        HBox.setHgrow(toolbar.getChildren().get(3), Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        // ── 4. Área central: Lista de puertos + Panel de detalle (6 campos) ──
        portsListContainer = new VBox(6);
        portsListContainer.setPadding(new Insets(4));

        scrollList = new ScrollPane(portsListContainer);
        scrollList.setFitToWidth(true);
        scrollList.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollList.setPrefHeight(420);

        detailPanel = buildDetailPanel();
        detailPanel.setPrefWidth(380);
        detailPanel.setMinWidth(320);

        HBox mainSplit = new HBox(16, scrollList, detailPanel);
        HBox.setHgrow(scrollList, Priority.ALWAYS);

        // ── 5. Ranura para la tarjeta estándar de resultados ─────────────────
        resultCardSlot = new VBox();
        DiagnosticResult prev = SessionState.getInstance().getResult(tool.getId());
        if (prev != null) {
            resultCardSlot.getChildren().add(new StandardResultCard(prev));
        }

        content.getChildren().addAll(header, summaryCards, toolbar, mainSplit, resultCardSlot);
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

    private VBox buildScopeSummaryCard(String title, Label countLabel, String desc, String colorHex, String iconKey) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-border-color: %s; -fx-border-width: 1.5; -fx-border-radius: 8;", ThemeTokens.BG_SURFACE, colorHex));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(iconKey, 22, colorHex);
        Label lblT = new Label(title);
        lblT.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 13px;");

        countLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 20px; -fx-font-weight: bold;", colorHex));

        top.getChildren().addAll(icon, lblT, new Region(), countLabel);
        HBox.setHgrow(top.getChildren().get(2), Priority.ALWAYS);

        Label lblD = new Label(desc);
        lblD.setWrapText(true);
        lblD.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

        card.getChildren().addAll(top, lblD);
        return card;
    }

    private VBox buildDetailPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18));
        panel.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        Label title = new Label("Detalles de Seguridad del Puerto");
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label placeholder = new Label("Selecciona un puerto de la lista para ver los 6 aspectos de seguridad: Qué es, Para qué sirve, ¿Es normal?, Programa, Alcance y Riesgo.");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        panel.getChildren().addAll(title, placeholder);
        return panel;
    }

    private void startScan() {
        btnScan.setDisable(true);
        progress.setVisible(true);
        lblStatus.setText("Escaneando puertos TCP y UDP activos vía netstat...");
        cancelFlag.set(false);

        Task<PortsScanResult> task = new Task<>() {
            @Override
            protected PortsScanResult call() {
                return service.scanOpenPorts(cancelFlag);
            }
        };

        task.setOnSucceeded(e -> {
            btnScan.setDisable(false);
            progress.setVisible(false);
            currentScan = task.getValue();

            lblCountExposed.setText(String.valueOf(currentScan.totalExposed));
            lblCountLocal.setText(String.valueOf(currentScan.totalLocalOnly));

            lblStatus.setText(String.format("Detectados %d puertos en escucha (%d puertos visibles en la red, %d locales).",
                    currentScan.totalListening, currentScan.totalExposed, currentScan.totalLocalOnly));

            renderPortsList();

            // Guardar diagnóstico en sesión
            DiagnosticResult diag = service.toDiagnosticResult(currentScan);
            SessionState.getInstance().setResult(tool.getId(), diag);
            resultCardSlot.getChildren().setAll(new StandardResultCard(diag));
        });

        task.setOnFailed(e -> {
            btnScan.setDisable(false);
            progress.setVisible(false);
            lblStatus.setText("Error escaneando puertos: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private String getPortKey(OpenPortItem item) {
        return item.protocol + ":" + item.port + ":" + item.pid;
    }

    private void renderPortsList() {
        double prevVpos = (scrollList != null) ? scrollList.getVvalue() : 0.0;
        portsListContainer.getChildren().clear();
        if (currentScan == null) return;

        String scopeFilter = cbScopeFilter.getValue();
        String textFilter = txtFilter.getText() != null ? txtFilter.getText().trim().toLowerCase() : "";

        int shown = 0;
        OpenPortItem itemToRestore = null;

        for (OpenPortItem item : currentScan.ports) {
            // Filtro por ámbito
            if ("Solo puertos visibles en la red".equals(scopeFilter) && item.scope != PortScope.NETWORK_EXPOSED) continue;
            if ("Solo Locales (127.0.0.1)".equals(scopeFilter) && item.scope != PortScope.LOCAL_ONLY) continue;

            // Filtro por texto
            if (!textFilter.isEmpty()) {
                boolean match = String.valueOf(item.port).contains(textFilter)
                        || item.protocol.toLowerCase().contains(textFilter)
                        || item.processName.toLowerCase().contains(textFilter)
                        || item.serviceFriendlyName.toLowerCase().contains(textFilter)
                        || String.valueOf(item.pid).contains(textFilter);
                if (!match) continue;
            }

            String key = getPortKey(item);
            boolean isSelected = key.equals(selectedPortKey);
            if (isSelected) {
                itemToRestore = item;
            }

            HBox card = buildPortCard(item, isSelected);
            portsListContainer.getChildren().add(card);
            shown++;
        }

        if (shown == 0) {
            Label noPorts = new Label("No se encontraron puertos que coincidan con los filtros.");
            noPorts.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px; -fx-padding: 20 0 0 10;");
            portsListContainer.getChildren().add(noPorts);
        } else if (itemToRestore != null) {
            showPortDetails(itemToRestore);
        }

        if (scrollList != null && prevVpos > 0) {
            javafx.application.Platform.runLater(() -> scrollList.setVvalue(prevVpos));
        }
    }

    private void selectPort(OpenPortItem item) {
        selectedPortKey = getPortKey(item);
        showPortDetails(item);
        renderPortsList();
    }

    private HBox buildPortCard(OpenPortItem item, boolean isSelected) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 14, 10, 14));

        String borderCol = isSelected ? ThemeTokens.ACCENT : ThemeTokens.BORDER_CONTROL;
        String bgCol = isSelected ? ThemeTokens.BG_SURFACE_HOVER : ThemeTokens.BG_SURFACE_ELEVATED;

        card.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6; -fx-border-color: %s; -fx-border-radius: 6; -fx-border-width: %s; -fx-cursor: hand;",
                bgCol, borderCol, isSelected ? "1.5" : "1"));

        Node icon = AppIcons.create(item.scope == PortScope.NETWORK_EXPOSED ? "globe" : "shield", 18, item.scope.getColorHex());

        VBox texts = new VBox(2);
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label portLbl = new Label("Puerto " + item.port + " / " + item.protocol);
        portLbl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label badgeScope = new Label(item.closedByUser ? "Cerrado ✔" : item.scope.getLabel());
        String scopeColor = item.closedByUser ? ThemeTokens.COLOR_OK : item.scope.getColorHex();
        badgeScope.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 1 6; -fx-background-radius: 4;",
                scopeColor, ThemeTokens.TEXT_DARK));

        Label badgeProc = new Label(item.processName);
        badgeProc.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 9px; -fx-padding: 1 6; -fx-background-radius: 4;");

        top.getChildren().addAll(portLbl, badgeScope, badgeProc);

        Label meta = new Label(item.serviceFriendlyName + " (PID " + item.pid + ")");
        meta.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

        texts.getChildren().addAll(top, meta);
        card.getChildren().addAll(icon, texts);
        HBox.setHgrow(texts, Priority.ALWAYS);

        card.setOnMouseClicked(e -> selectPort(item));
        return card;
    }

    /**
     * Construye el panel con las 6 secciones requeridas:
     * 1. Qué es (nombre del servicio en lenguaje humano)
     * 2. Para qué sirve (con un ejemplo cotidiano)
     * 3. ¿Es normal?
     * 4. Programa que lo abrió
     * 5. Alcance (solo dentro del PC o visible en la red)
     * 6. Riesgo y qué hacer (con nota clara sobre el firewall y opción de cierre si no es crítico).
     */
    private void showPortDetails(OpenPortItem item) {
        detailPanel.getChildren().clear();

        HBox topHeader = new HBox(8);
        topHeader.setAlignment(Pos.CENTER_LEFT);
        Node icon = AppIcons.create(item.scope == PortScope.NETWORK_EXPOSED ? "globe" : "shield", 20, item.scope.getColorHex());

        Label title = new Label("Puerto " + item.port + " (" + item.protocol + ")");
        title.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        topHeader.getChildren().addAll(icon, title);

        VBox contentBox = new VBox(10);

        // 1. Qué es
        contentBox.getChildren().add(buildSectionCard("1. Qué es", item.serviceFriendlyName, ThemeTokens.TEXT_PRIMARY));

        // 2. Para qué sirve (con ejemplo cotidiano)
        contentBox.getChildren().add(buildSectionCard("2. Para qué sirve (Uso cotidiano)", item.everydayPurpose, ThemeTokens.TEXT_SECONDARY));

        // 3. ¿Es normal?
        contentBox.getChildren().add(buildSectionCard("3. ¿Es normal?", item.isNormal, ThemeTokens.COLOR_OK));

        // 4. Programa que lo abrió
        VBox progCard = new VBox(4);
        progCard.setPadding(new Insets(8, 10, 8, 10));
        progCard.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6; -fx-border-color: " + ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 6;");

        Label lblPTitle = new Label("4. Programa que lo abrió");
        lblPTitle.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        HBox progRow = new HBox(8);
        progRow.setAlignment(Pos.CENTER_LEFT);
        Label lblPName = new Label(item.processName + " (PID " + item.pid + ")");
        lblPName.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label badgeCrit = new Label(item.isSystemCritical ? "Esencial de Windows" : "Programa de usuario");
        badgeCrit.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 1 6; -fx-background-radius: 4;",
                item.isSystemCritical ? ThemeTokens.COLOR_WARN : ThemeTokens.COLOR_OK, ThemeTokens.TEXT_DARK));

        progRow.getChildren().addAll(lblPName, badgeCrit);
        progCard.getChildren().addAll(lblPTitle, progRow);
        contentBox.getChildren().add(progCard);

        // 5. Alcance (solo dentro del PC o visible en la red)
        contentBox.getChildren().add(buildSectionCard("5. Alcance",
                item.scope.getLabel() + ": " + item.scopeDescription + " (Dirección de enlace: " + item.localAddress + ")",
                ThemeTokens.TEXT_SECONDARY));

        // 6. Riesgo y qué hacer
        contentBox.getChildren().add(buildSectionCard("6. Riesgo y qué hacer", item.riskAndAction, ThemeTokens.TEXT_PRIMARY));

        // Botón de acción (Cerrar proceso si no es crítico)
        Button btnKill = new Button(
                item.closedByUser ? "Cerrado ✔" : "Cerrar programa (PID " + item.pid + ")",
                AppIcons.create(item.closedByUser ? "checkcircle" : "xcircle", 14, item.closedByUser ? ThemeTokens.TEXT_DARK : ThemeTokens.TEXT_PRIMARY)
        );
        btnKill.setMaxWidth(Double.MAX_VALUE);

        if (item.closedByUser) {
            btnKill.setDisable(true);
            btnKill.setStyle("-fx-background-color: " + ThemeTokens.COLOR_OK + "; -fx-text-fill: " + ThemeTokens.TEXT_DARK + "; -fx-font-weight: bold;");
        } else if (item.isSystemCritical) {
            btnKill.getStyleClass().add("btn-secondary");
            btnKill.setDisable(true);
            Tooltip.install(btnKill, new Tooltip("Este programa es fundamental para el funcionamiento del sistema operativo y no puede ser finalizado."));
        } else {
            btnKill.getStyleClass().add("btn-danger");
            btnKill.setOnAction(e -> confirmAndKillProcess(item));
        }

        contentBox.getChildren().add(btnKill);

        ScrollPane detailScroll = new ScrollPane(contentBox);
        detailScroll.setFitToWidth(true);
        detailScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        detailScroll.setPrefHeight(380);

        detailPanel.getChildren().addAll(topHeader, detailScroll);
    }

    private VBox buildSectionCard(String sectionTitle, String content, String textColor) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + "; -fx-background-radius: 6; -fx-border-color: " + ThemeTokens.BORDER_CONTROL + "; -fx-border-radius: 6;");

        Label lblTitle = new Label(sectionTitle);
        lblTitle.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label lblContent = new Label(content);
        lblContent.setWrapText(true);
        lblContent.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 11px; -fx-line-spacing: 2px;");

        box.getChildren().addAll(lblTitle, lblContent);
        return box;
    }

    private void confirmAndKillProcess(OpenPortItem item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cerrar programa");
        alert.setHeaderText("¿Detener el proceso '" + item.processName + "' (PID " + item.pid + ")?");
        alert.setContentText("Al cerrarlo, el puerto " + item.port + " dejará de responder a conexiones de red.");

        ButtonType btnYes = new ButtonType("Sí, cerrar proceso", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnNo = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnYes, btnNo);

        alert.showAndWait().ifPresent(type -> {
            if (type == btnYes) {
                StringBuilder err = new StringBuilder();
                boolean ok = service.killProcess(item.pid, item.processName, err);
                if (ok) {
                    if (currentScan != null) {
                        int exposed = 0;
                        int local = 0;
                        int total = 0;
                        for (OpenPortItem p : currentScan.ports) {
                            if (p.pid == item.pid) {
                                p.closedByUser = true;
                                p.state = "Cerrado ✔";
                            }
                            if (!p.closedByUser) {
                                total++;
                                if (p.scope == PortScope.NETWORK_EXPOSED) exposed++;
                                else local++;
                            }
                        }
                        currentScan.totalListening = total;
                        currentScan.totalExposed = exposed;
                        currentScan.totalLocalOnly = local;
                        lblCountExposed.setText(String.valueOf(exposed));
                        lblCountLocal.setText(String.valueOf(local));
                    }
                    lblStatus.setText("Puerto " + item.port + " (" + item.processName + "): Cerrado ✔");
                    renderPortsList();
                    showPortDetails(item);
                    Alert inf = new Alert(Alert.AlertType.INFORMATION, "Estado del puerto " + item.port + " (" + item.processName + "): Cerrado ✔", ButtonType.OK);
                    inf.showAndWait();
                } else {
                    Alert inf = new Alert(Alert.AlertType.ERROR, err.toString(), ButtonType.OK);
                    inf.showAndWait();
                }
            }
        });
    }
}
