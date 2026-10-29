package com.fixtool.ui;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.service.DiagnosticService;
import com.fixtool.ui.theme.ThemeTokens;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Menú Principal / Grid Inicial de Synchro Willy.
 * Presenta las 10 funcionalidades del taller de diagnóstico en tarjetas estilizadas
 * Minimalist Tech, cada una con icono único, título, descripción concisa y botón de acceso.
 * Completamente libre de consolas o cajas de texto de logs.
 */
public class GridView extends VBox {

    private final DiagnosticService service;
    private final ConnectionConfig config;
    private final Consumer<DiagnosticAction> onSelectAction;

    public GridView(DiagnosticService service,
                    ConnectionConfig config,
                    Consumer<DiagnosticAction> onSelectAction) {
        this.service = service;
        this.config = config;
        this.onSelectAction = onSelectAction;

        this.setSpacing(18);
        this.setPadding(new Insets(20, 28, 24, 28));
        this.getStyleClass().add("bg-root");

        // 1. Banner Superior Informativo de Estado
        HBox banner = createStatusBanner();

        // 2. Encabezado del Catálogo
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("⚡ Catálogo de Diagnóstico y Herramientas (10 Acciones)");
        lblTitle.getStyleClass().add("text-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblHint = new Label("Seleccione una herramienta para abrir su panel operativo dedicado:");
        lblHint.getStyleClass().add("text-muted");

        headerBox.getChildren().addAll(lblTitle, spacer, lblHint);

        // 3. Cuadrícula de 10 Tarjetas (2 Columnas x 5 Filas)
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        List<DiagnosticAction> actionList = service.getActions();
        for (int i = 0; i < actionList.size(); i++) {
            DiagnosticAction action = actionList.get(i);
            int row = i / 2;
            int col = i % 2;

            VBox card = createActionCard(action);
            grid.add(card, col, row);
        }

        ScrollPane scrollGrid = new ScrollPane(grid);
        scrollGrid.setFitToWidth(true);
        scrollGrid.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollGrid, Priority.ALWAYS);

        this.getChildren().addAll(banner, headerBox, scrollGrid);
    }

    public ConnectionConfig getConfig() {
        return config;
    }

    public DiagnosticService getService() {
        return service;
    }

    private HBox createStatusBanner() {
        HBox banner = new HBox(20);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(12, 18, 12, 18));
        banner.getStyleClass().add("card-panel-elevated");

        Label iconWilly = new Label("🛡️");
        iconWilly.setStyle("-fx-font-size: 24px;");

        VBox textBox = new VBox(2);
        Label title = new Label("Entorno de Red y Diagnóstico Windows • Synchro Willy");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + "; -fx-font-size: 13px;");

        Label desc = new Label("Parámetros vinculados al vuelo con el Target. Las 10 acciones se ejecutan sin requerir privilegios de Administrador.");
        desc.getStyleClass().add("text-muted");
        textBox.getChildren().addAll(title, desc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badgeSec = new Label("100% SEGURO");
        badgeSec.getStyleClass().add("badge-chip");

        banner.getChildren().addAll(iconWilly, textBox, spacer, badgeSec);
        return banner;
    }

    private VBox createActionCard(DiagnosticAction action) {
        VBox card = new VBox(10);
        card.getStyleClass().add("action-card");
        card.setAlignment(Pos.CENTER_LEFT);

        // Fila superior: Icono distintivo + Título + Badge de categoría
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label lblIcon = new Label(action.getIconSymbol());
        lblIcon.setStyle("-fx-font-size: 24px;");

        Label lblTitle = new Label(action.getTitle());
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblCategory = new Label(action.getCategory());
        lblCategory.getStyleClass().add("badge-tag");

        topRow.getChildren().addAll(lblIcon, lblTitle, spacer, lblCategory);

        // Descripción concisa
        Label lblDesc = new Label(action.getShortDescription());
        lblDesc.getStyleClass().add("text-muted");
        lblDesc.setWrapText(true);

        // Fila inferior: Botón moderno de acceso directo a la pestaña
        HBox bottomRow = new HBox(8);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        Button btnOpen = new Button("Abrir Pestaña →");
        btnOpen.getStyleClass().add("button-primary");
        btnOpen.setStyle("-fx-padding: 5px 14px; -fx-font-size: 11px;");
        btnOpen.setOnAction(e -> {
            e.consume();
            if (onSelectAction != null) onSelectAction.accept(action);
        });

        bottomRow.getChildren().add(btnOpen);

        card.getChildren().addAll(topRow, lblDesc, bottomRow);

        // Clic en la tarjeta abre directamente su pestaña
        card.setOnMouseClicked(e -> {
            if (onSelectAction != null) {
                onSelectAction.accept(action);
            }
        });

        return card;
    }
}
