package com.fixtool.ui;

import com.fixtool.model.AppConfig;
import com.fixtool.ui.theme.ThemeTokens;
import com.fixtool.util.AppIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.InputStream;

/**
 * Sección "Acerca de" de Synchro Willy.
 * Ubicada al final de la barra lateral tras la herramienta 10, sin numeración.
 * Muestra la información académica y del desarrollador en una tarjeta centrada.
 */
public class AcercaDeView extends VBox {

    // ── CONSTANTES AGRUPADAS DE LA SECCIÓN (ABOUT_INFO) ────────────────────────
    public static final String LABEL_DEVELOPER = "Desarrollador";
    public static final String VALUE_DEVELOPER = "Bryan Espinoza";

    public static final String LABEL_CAREER    = "Carrera";
    public static final String VALUE_CAREER    = "Lic. Desarrollo y Gestión de Software";

    public static final String LABEL_CLASS     = "Clase";
    public static final String VALUE_CLASS     = "Sistemas Operativos";

    public static final String LABEL_SEMESTER  = "Semestre";
    public static final String VALUE_SEMESTER  = "Segundo Semestre tercer año";

    public static final String[][] ABOUT_INFO = {
        { LABEL_DEVELOPER, VALUE_DEVELOPER, "user" },
        { LABEL_CAREER,    VALUE_CAREER,    "award" },
        { LABEL_CLASS,     VALUE_CLASS,     "book" },
        { LABEL_SEMESTER,  VALUE_SEMESTER,  "calendar" }
    };

    public AcercaDeView() {
        this.setSpacing(0);
        this.getStyleClass().add("bg-root");

        // ScrollPane transparente para que todo sea accesible en 1100x700 y escala 150 %
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Contenedor centrado
        StackPane centerWrapper = new StackPane();
        centerWrapper.setPadding(new Insets(32, 24, 40, 24));
        centerWrapper.setAlignment(Pos.CENTER);
        centerWrapper.getStyleClass().add("bg-root");

        // Tarjeta principal
        VBox card = new VBox(24);
        card.setMaxWidth(540);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(32, 36, 36, 36));
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(
            "-fx-background-color: " + ThemeTokens.BG_SURFACE + ";" +
            "-fx-background-radius: 12px;" +
            "-fx-border-color: " + ThemeTokens.BORDER_BASE + ";" +
            "-fx-border-radius: 12px;" +
            "-fx-border-width: 1px;"
        );

        // 1. Cabecera con Logo de Willy grande y Nombre de la app
        VBox headerBox = new VBox(12);
        headerBox.setAlignment(Pos.CENTER);

        Node logoNode = buildLogoNode(104);

        Label lblAppName = new Label(AppConfig.APP_NAME);
        lblAppName.setStyle(
            "-fx-font-size: 22px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";"
        );

        headerBox.getChildren().addAll(logoNode, lblAppName);

        // Separador sutil
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: " + ThemeTokens.BORDER_BASE + ";");

        // 2. Las 4 filas de información académica y de autoría
        VBox rowsContainer = new VBox(14);
        rowsContainer.setAlignment(Pos.CENTER_LEFT);

        for (String[] entry : ABOUT_INFO) {
            String label = entry[0];
            String value = entry[1];
            String iconKey = entry[2];
            rowsContainer.getChildren().add(buildInfoRow(iconKey, label, value));
        }

        card.getChildren().addAll(headerBox, separator, rowsContainer);
        centerWrapper.getChildren().add(card);
        scroll.setContent(centerWrapper);
        this.getChildren().add(scroll);
    }

    /**
     * Construye una fila con ícono temático, etiqueta secundaria y valor principal destacado.
     */
    private HBox buildInfoRow(String iconKey, String labelText, String valueText) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle(
            "-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + ";" +
            "-fx-background-radius: 8px;" +
            "-fx-border-color: " + ThemeTokens.BORDER_CONTROL + ";" +
            "-fx-border-radius: 8px;" +
            "-fx-border-width: 1px;"
        );

        Node icon = AppIcons.create(iconKey, 20, ThemeTokens.ACCENT);

        VBox textCol = new VBox(2);
        textCol.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(labelText);
        lbl.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";" +
            "-fx-text-transform: uppercase;"
        );

        Label val = new Label(valueText);
        val.setWrapText(true);
        val.setStyle(
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";"
        );

        textCol.getChildren().addAll(lbl, val);
        row.getChildren().addAll(icon, textCol);
        HBox.setHgrow(textCol, Priority.ALWAYS);
        return row;
    }

    /**
     * Carga el logo oficial de Willy o un placeholder circular de contingencia.
     */
    private Node buildLogoNode(double size) {
        try {
            InputStream is = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE);
            if (is != null) {
                Image img = new Image(is, size, size, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(size);
                iv.setFitHeight(size);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                return iv;
            }
        } catch (Exception ignored) {}

        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(size, size);
        placeholder.setMinSize(size, size);
        placeholder.setMaxSize(size, size);
        placeholder.setStyle(
            "-fx-background-color: " + ThemeTokens.BG_SURFACE_ELEVATED + ";" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: " + ThemeTokens.ACCENT + ";" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 2px;"
        );
        placeholder.getChildren().add(AppIcons.create("info", size * 0.5, ThemeTokens.ACCENT));
        return placeholder;
    }
}
