package com.fixtool.ui;

import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.service.EventDetectiveService;
import com.fixtool.service.EventDetectiveService.EventSeverity;
import com.fixtool.service.EventDetectiveService.RebootExplanation;
import com.fixtool.service.EventDetectiveService.SystemEventItem;
import com.fixtool.ui.components.StandardResultCard;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Herramienta 8: Monitor de Eventos.
 * Muestra el historial amigable de eventos del sistema, errores de aplicaciones,
 * visor expandido con 5 secciones y respuesta directa a "¿Por qué se reinició mi PC?".
 */
public class DetectiveEventosView extends VBox {

    private final DiagnosticAction tool;
    private final EventDetectiveService service = new EventDetectiveService();
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // Controles
    private final Button btnScan;
    private final Button btnWhyReboot;
    private final ComboBox<String> cbSeverity;
    private final TextField txtFilter;
    private final ProgressIndicator progress;
    private final Label lblStatus;

    // Secciones visuales
    private final VBox rebootBanner;
    private final HBox timelineBar;
    private final VBox eventsListContainer;
    private final ScrollPane scrollList;
    private final VBox detailPanel;
    private final VBox resultCardSlot;

    // Datos y estado de selección
    private List<SystemEventItem> currentEvents = new ArrayList<>();
    private RebootExplanation currentReboot = null;
    private String selectedEventKey = null; // Preservar selección entre refrescos

    public DetectiveEventosView(DiagnosticAction tool) {
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

        // ── 2. Banner "¿Por qué se reinició mi PC?" ───────────────────────────
        rebootBanner = new VBox(6);
        rebootBanner.setPadding(new Insets(14, 18, 14, 18));
        rebootBanner.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");
        renderInitialRebootBanner();

        // ── 3. Barra de herramientas ──────────────────────────────────────────
        btnScan = new Button("Consultar eventos", AppIcons.create("search", 16, ThemeTokens.TEXT_PRIMARY));
        btnScan.getStyleClass().add("btn-primary");
        btnScan.setOnAction(e -> startScan());

        btnWhyReboot = new Button("¿Por qué se reinició?", AppIcons.create("activity", 16, ThemeTokens.ACCENT));
        btnWhyReboot.getStyleClass().add("btn-secondary");
        btnWhyReboot.setOnAction(e -> queryRebootReason());

        progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        lblStatus = new Label("Presiona 'Consultar eventos' para leer los registros recientes de Windows.");
        lblStatus.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 13px;");

        cbSeverity = new ComboBox<>();
        cbSeverity.getItems().addAll("Todos los niveles", "Solo Errores", "Solo Advertencias", "Solo Información");
        cbSeverity.setValue("Todos los niveles");
        cbSeverity.setOnAction(e -> renderEventsList());

        txtFilter = new TextField();
        txtFilter.setPromptText("Buscar por texto, ID o proveedor...");
        txtFilter.getStyleClass().add("search-field");
        txtFilter.setPrefWidth(220);
        txtFilter.textProperty().addListener((obs, oldV, newV) -> renderEventsList());

        HBox toolbar = new HBox(10, btnScan, btnWhyReboot, progress, lblStatus, new Region(), cbSeverity, txtFilter);
        HBox.setHgrow(toolbar.getChildren().get(4), Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        // ── 4. Mini Timeline visual de eventos ───────────────────────────────
        timelineBar = new HBox(4);
        timelineBar.setAlignment(Pos.CENTER_LEFT);
        timelineBar.setPadding(new Insets(8, 12, 8, 12));
        timelineBar.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-background-radius: 6; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 6;");
        Label lblTl = new Label("Línea de tiempo de eventos: ");
        lblTl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
        timelineBar.getChildren().add(lblTl);

        // ── 5. Área central: Lista de eventos + Panel de detalle ──────────────
        eventsListContainer = new VBox(8);
        eventsListContainer.setPadding(new Insets(4));

        scrollList = new ScrollPane(eventsListContainer);
        scrollList.setFitToWidth(true);
        scrollList.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollList.setPrefHeight(420);

        detailPanel = buildDetailPanel();
        detailPanel.setPrefWidth(380);
        detailPanel.setMinWidth(320);

        HBox mainSplit = new HBox(16, scrollList, detailPanel);
        HBox.setHgrow(scrollList, Priority.ALWAYS);

        // ── 6. Ranura para la tarjeta estándar de resultados ─────────────────
        resultCardSlot = new VBox();
        DiagnosticResult prev = SessionState.getInstance().getResult(tool.getId());
        if (prev != null) {
            resultCardSlot.getChildren().add(new StandardResultCard(prev));
        }

        content.getChildren().addAll(header, rebootBanner, toolbar, timelineBar, mainSplit, resultCardSlot);
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

    private void renderInitialRebootBanner() {
        rebootBanner.getChildren().clear();
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create("helpcircle", 20, ThemeTokens.ACCENT);
        VBox texts = new VBox(2);
        Label title = new Label("¿Quieres saber por qué se reinició tu equipo por última vez?");
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label sub = new Label("Haz clic en '¿Por qué se reinició?' para analizar de inmediato los eventos de energía de Windows.");
        sub.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");
        texts.getChildren().addAll(title, sub);

        row.getChildren().addAll(icon, texts);
        rebootBanner.getChildren().add(row);
    }

    private VBox buildDetailPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18));
        panel.setStyle("-fx-background-color: " + ThemeTokens.BG_SURFACE + "; -fx-background-radius: 8; -fx-border-color: " + ThemeTokens.BORDER_BASE + "; -fx-border-radius: 8;");

        Label title = new Label("Descripción Ampliada del Evento");
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label placeholder = new Label("Haz clic en cualquier evento de la lista para ver sus 5 secciones explicativas (Qué pasó, Qué significa, Posible causa, Qué puedes hacer y Detalle técnico).");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        panel.getChildren().addAll(title, placeholder);
        return panel;
    }

    private void queryRebootReason() {
        btnWhyReboot.setDisable(true);
        progress.setVisible(true);

        Task<RebootExplanation> task = new Task<>() {
            @Override
            protected RebootExplanation call() {
                return service.explainLastReboot();
            }
        };

        task.setOnSucceeded(e -> {
            btnWhyReboot.setDisable(false);
            progress.setVisible(false);
            currentReboot = task.getValue();
            updateRebootBanner(currentReboot);
        });

        task.setOnFailed(e -> {
            btnWhyReboot.setDisable(false);
            progress.setVisible(false);
        });

        new Thread(task).start();
    }

    private void updateRebootBanner(RebootExplanation r) {
        rebootBanner.getChildren().clear();

        String borderColor = r.wasClean ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR;
        String bgColor = ThemeTokens.BG_SURFACE_ELEVATED;
        String iconKey = r.wasClean ? "checkcircle" : "alerttriangle";
        String iconColor = r.wasClean ? ThemeTokens.COLOR_OK : ThemeTokens.COLOR_ERROR;

        rebootBanner.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-border-color: %s; -fx-border-radius: 8; -fx-border-width: 1.5;", bgColor, borderColor));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Node icon = AppIcons.create(iconKey, 24, iconColor);
        VBox texts = new VBox(3);

        Label title = new Label("Último reinicio registrado: " + r.causeSummary);
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label detail = new Label(r.detail);
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        texts.getChildren().addAll(title, detail);
        row.getChildren().addAll(icon, texts);

        rebootBanner.getChildren().add(row);
    }

    private void startScan() {
        btnScan.setDisable(true);
        progress.setVisible(true);
        lblStatus.setText("Consultando eventos de Sistema y Aplicaciones vía wevtutil...");
        cancelFlag.set(false);

        Task<List<SystemEventItem>> task = new Task<>() {
            @Override
            protected List<SystemEventItem> call() {
                return service.queryEvents(60, cancelFlag);
            }
        };

        task.setOnSucceeded(e -> {
            btnScan.setDisable(false);
            progress.setVisible(false);
            currentEvents = task.getValue();

            long errs = currentEvents.stream().filter(it -> it.severity == EventSeverity.ERROR).count();
            long warns = currentEvents.stream().filter(it -> it.severity == EventSeverity.WARNING).count();
            lblStatus.setText(String.format("Leídos %d eventos (%d errores, %d advertencias).", currentEvents.size(), errs, warns));

            if (currentReboot == null) {
                queryRebootReason();
            }

            renderTimeline();
            renderEventsList();

            // Guardar diagnóstico en sesión
            DiagnosticResult diag = service.toDiagnosticResult(currentEvents, currentReboot);
            SessionState.getInstance().setResult(tool.getId(), diag);
            resultCardSlot.getChildren().setAll(new StandardResultCard(diag));
        });

        task.setOnFailed(e -> {
            btnScan.setDisable(false);
            progress.setVisible(false);
            lblStatus.setText("Error leyendo eventos: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void renderTimeline() {
        timelineBar.getChildren().clear();
        Label lblTl = new Label("Eventos recientes: ");
        lblTl.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");
        timelineBar.getChildren().add(lblTl);

        int count = Math.min(currentEvents.size(), 40);
        for (int i = 0; i < count; i++) {
            SystemEventItem item = currentEvents.get(i);
            Region dot = new Region();
            dot.setPrefSize(10, 10);
            dot.setMaxSize(10, 10);
            dot.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 5; -fx-cursor: hand;", item.severity.getColorHex()));

            Tooltip tt = new Tooltip(String.format("[%s] ID %s: %s (%s)",
                    item.severity.getLabel(), item.id, item.humanTitle, item.timestamp));
            Tooltip.install(dot, tt);

            dot.setOnMouseClicked(e -> selectEvent(item));
            timelineBar.getChildren().add(dot);
        }
    }

    private void renderEventsList() {
        double prevVpos = (scrollList != null) ? scrollList.getVvalue() : 0.0;
        eventsListContainer.getChildren().clear();
        String sevFilter = cbSeverity.getValue();
        String textFilter = txtFilter.getText() != null ? txtFilter.getText().trim().toLowerCase() : "";

        int shown = 0;
        SystemEventItem selectedItemToRestore = null;

        for (SystemEventItem item : currentEvents) {
            // Filtro por severidad
            if ("Solo Errores".equals(sevFilter) && item.severity != EventSeverity.ERROR) continue;
            if ("Solo Advertencias".equals(sevFilter) && item.severity != EventSeverity.WARNING) continue;
            if ("Solo Información".equals(sevFilter) && item.severity != EventSeverity.INFO) continue;

            // Filtro por texto
            if (!textFilter.isEmpty()) {
                boolean match = item.id.toLowerCase().contains(textFilter)
                        || item.humanTitle.toLowerCase().contains(textFilter)
                        || item.whatHappened.toLowerCase().contains(textFilter)
                        || item.source.toLowerCase().contains(textFilter);
                if (!match) continue;
            }

            String key = getItemKey(item);
            boolean isSelected = key.equals(selectedEventKey);
            if (isSelected) {
                selectedItemToRestore = item;
            }

            VBox card = buildEventCard(item, isSelected);
            eventsListContainer.getChildren().add(card);
            shown++;
        }

        if (shown == 0) {
            Label noEv = new Label(currentEvents.isEmpty() ? "Presiona 'Consultar eventos' para iniciar." : "Sin eventos que coincidan con el filtro.");
            noEv.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 12px; -fx-padding: 20 0 0 10;");
            eventsListContainer.getChildren().add(noEv);
        } else if (selectedItemToRestore != null) {
            // Mantener detalle abierto sin cerrarse al refrescar
            showEventDetail(selectedItemToRestore);
        }

        if (scrollList != null && prevVpos > 0) {
            javafx.application.Platform.runLater(() -> scrollList.setVvalue(prevVpos));
        }
    }

    private String getItemKey(SystemEventItem item) {
        return item.logName + ":" + item.source + ":" + item.id + ":" + item.timestamp;
    }

    private void selectEvent(SystemEventItem item) {
        String key = getItemKey(item);
        if (key.equals(selectedEventKey)) {
            // Toggle contraer si se hace clic en el mismo
            selectedEventKey = null;
            detailPanel.getChildren().clear();
            detailPanel.getChildren().addAll(new Label("Descripción Ampliada del Evento"), new Label("Selecciona un evento para ver sus 5 secciones."));
        } else {
            selectedEventKey = key;
            showEventDetail(item);
        }
        renderEventsList();
    }

    private VBox buildEventCard(SystemEventItem item, boolean isSelected) {
        VBox container = new VBox(0);

        HBox cardRow = new HBox(12);
        cardRow.setAlignment(Pos.CENTER_LEFT);
        cardRow.setPadding(new Insets(10, 14, 10, 14));

        String borderCol = isSelected ? ThemeTokens.ACCENT : ThemeTokens.BORDER_CONTROL;
        String bgCol = isSelected ? ThemeTokens.BG_SURFACE_HOVER : ThemeTokens.BG_SURFACE_ELEVATED;

        cardRow.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6; -fx-border-color: %s; -fx-border-radius: 6; -fx-border-width: %s; -fx-cursor: hand;",
                bgCol, borderCol, isSelected ? "1.5" : "1"));

        Node icon = AppIcons.create(item.severity.getIconKey(), 18, item.severity.getColorHex());

        VBox texts = new VBox(3);
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(item.humanTitle);
        title.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label badge = new Label(item.severity.getLabel());
        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 1 6; -fx-background-radius: 4;",
                item.severity.getColorHex(), ThemeTokens.TEXT_DARK));

        Label originBadge = new Label(item.source.isEmpty() ? item.logName : item.source);
        originBadge.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 9px; -fx-padding: 1 6; -fx-background-radius: 4;");

        Label lblTime = new Label(item.timestamp);
        lblTime.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        top.getChildren().addAll(title, badge, originBadge, sp, lblTime);

        // Fila muestra: título humano, una línea de qué pasó, gravedad, hora y origen
        Label sub = new Label("Qué pasó: " + item.whatHappened);
        sub.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 11px;");

        texts.getChildren().addAll(top, sub);
        cardRow.getChildren().addAll(icon, texts);
        HBox.setHgrow(texts, Priority.ALWAYS);

        cardRow.setOnMouseClicked(e -> selectEvent(item));
        container.getChildren().add(cardRow);

        return container;
    }

    /**
     * Muestra la tarjeta con las 5 secciones requeridas:
     * 1. Qué pasó
     * 2. Qué significa
     * 3. Posible causa
     * 4. Qué puedes hacer
     * 5. Detalle técnico (plegado: proveedor, ID, mensaje original, usuario, equipo).
     */
    private void showEventDetail(SystemEventItem item) {
        detailPanel.getChildren().clear();

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Node icon = AppIcons.create(item.severity.getIconKey(), 20, item.severity.getColorHex());

        Label title = new Label(item.humanTitle);
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: " + ThemeTokens.ACCENT + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().addAll(icon, title);

        // Contenedor scrolleable si las 5 secciones sobrepasan la altura
        VBox sectionsBox = new VBox(12);

        // 1. Qué pasó
        sectionsBox.getChildren().add(buildSectionCard("1. Qué pasó", item.whatHappened, ThemeTokens.TEXT_PRIMARY));

        // 2. Qué significa
        sectionsBox.getChildren().add(buildSectionCard("2. Qué significa", item.whatItMeans, ThemeTokens.TEXT_SECONDARY));

        // 3. Posible causa
        sectionsBox.getChildren().add(buildSectionCard("3. Posible causa", item.possibleCause, ThemeTokens.TEXT_SECONDARY));

        // 4. Qué puedes hacer
        sectionsBox.getChildren().add(buildSectionCard("4. Qué puedes hacer", item.whatYouCanDo, ThemeTokens.COLOR_OK));

        // 5. Detalle técnico (plegado: proveedor, ID, mensaje original, usuario, equipo)
        TitledPane techPane = new TitledPane();
        techPane.setText("5. Detalle técnico (Avanzado)");
        techPane.setExpanded(false);
        techPane.setStyle("-fx-font-size: 11px; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        VBox techContent = new VBox(6);
        techContent.setStyle("-fx-background-color: " + ThemeTokens.BG_BASE + "; -fx-padding: 8; -fx-background-radius: 6;");

        techContent.getChildren().addAll(
                buildDetailRow("Proveedor / Origen:", item.source.isEmpty() ? item.logName : item.source),
                buildDetailRow("ID de Evento:", item.id),
                buildDetailRow("Registro origen:", item.logName),
                buildDetailRow("Gravedad / Nivel:", item.severity.getLabel()),
                buildDetailRow("Fecha y Hora:", item.timestamp),
                buildDetailRow("Usuario asociado:", item.user),
                buildDetailRow("Equipo registrado:", item.computer)
        );

        Label lblMsg = new Label("Mensaje original del sistema:");
        lblMsg.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 4 0 0 0;");

        TextArea txtTech = new TextArea(item.technicalSummary);
        txtTech.setEditable(false);
        txtTech.setWrapText(true);
        txtTech.setPrefRowCount(4);
        txtTech.setStyle(
                "-fx-control-inner-background: " + ThemeTokens.BG_SURFACE + ";" +
                "-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";" +
                "-fx-font-family: monospace;" +
                "-fx-font-size: 10px;"
        );

        techContent.getChildren().addAll(lblMsg, txtTech);
        techPane.setContent(techContent);

        sectionsBox.getChildren().add(techPane);

        ScrollPane detailScroll = new ScrollPane(sectionsBox);
        detailScroll.setFitToWidth(true);
        detailScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        detailScroll.setPrefHeight(380);

        detailPanel.getChildren().addAll(header, detailScroll);
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

    private HBox buildDetailRow(String label, String value) {
        HBox row = new HBox(6);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-font-size: 10px;");
        l.setPrefWidth(120);

        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 10px;");
        v.setWrapText(true);

        row.getChildren().addAll(l, v);
        return row;
    }
}
