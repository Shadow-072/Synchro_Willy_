package com.fixtool.ui.components;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.util.AppIcons;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Cabecera principal de Synchro Willy.
 *
 * Izquierda: logo (placeholder SVG si no existe el archivo) + nombre de la app.
 * Derecha:   chip de estado de la BOSGAME (gris/azul/verde) + botón Salir con etiqueta.
 *
 * Los datos de conexión viven únicamente en el diálogo (ConnectionDialog),
 * NO en esta barra. El chip abre ese diálogo al hacer clic.
 */
public class TopBar extends HBox {

    // ── Colores del chip de conexión ──────────────────────────────────────────
    private static final String COLOR_DISCONNECTED = com.fixtool.ui.theme.ThemeTokens.COLOR_NEUTRAL; // #A7B3C2
    private static final String COLOR_CONNECTING   = com.fixtool.ui.theme.ThemeTokens.ACCENT;        // #38BDF8
    private static final String COLOR_CONNECTED    = com.fixtool.ui.theme.ThemeTokens.COLOR_OK;       // #4ADE80

    private final ConnectionConfig config;
    private final Label            chipLabel;
    private final Label            chipDot;
    private Timeline               pulseAnimation;

    /**
     * @param config            Modelo de configuración de conexión
     * @param onOpenConnection  Acción al hacer clic en el chip de la BOSGAME
     * @param onExitApp         Acción al hacer clic en Salir
     */
    public TopBar(ConnectionConfig config,
                  Runnable onOpenConnection,
                  Runnable onExitApp) {
        this.config = config;

        this.getStyleClass().add("topbar");
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(0);
        this.setPadding(new Insets(0, 20, 0, 20));

        // ── Lado izquierdo: Logo + Nombre ─────────────────────────────────────
        HBox brandSection = buildBrandSection();

        // ── Espaciador central flexible ───────────────────────────────────────
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Lado derecho: Chip BOSGAME + Botón Salir ──────────────────────────
        // Chip de estado: punto de color + texto descriptivo
        chipDot   = new Label("●");
        chipDot.setStyle("-fx-font-size: 12px;");

        chipLabel = new Label();

        HBox chip = new HBox(6, chipDot, chipLabel);
        chip.getStyleClass().add("connection-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setCursor(javafx.scene.Cursor.HAND);
        chip.setOnMouseClicked(e -> { if (onOpenConnection != null) onOpenConnection.run(); });

        // Botón Salir (con etiqueta, neutro, no rojo)
        Node exitIcon = AppIcons.create("power", 15, com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
        Button btnExit = new Button("Salir", exitIcon);
        btnExit.getStyleClass().add("btn-exit");
        btnExit.setOnAction(e -> { if (onExitApp != null) onExitApp.run(); });

        HBox rightSection = new HBox(12, chip, btnExit);
        rightSection.setAlignment(Pos.CENTER_RIGHT);

        this.getChildren().addAll(brandSection, spacer, rightSection);

        // ── Enlazar chip al estado de conexión ────────────────────────────────
        actualizarChip(false, false);
        config.connectedProperty().addListener((obs, wasConnected, nowConnected) -> {
            actualizarChip(nowConnected, false);
        });
    }

    // ── Construcción de la sección de marca ──────────────────────────────────

    private HBox buildBrandSection() {
        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);

        // Intentar cargar el logo desde recursos; si no existe, usar ícono SVG
        Node logoNode = buildLogoNode();

        VBox textBox = new VBox(2);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Label appName = new Label(AppConfig.APP_NAME);
        appName.getStyleClass().add("topbar-app-name");

        Label devLabel = new Label("por " + AppConfig.DEVELOPER);
        devLabel.getStyleClass().add("topbar-dev-label");

        textBox.getChildren().addAll(appName, devLabel);
        brand.getChildren().addAll(logoNode, textBox);
        return brand;
    }

    /** Logo: imagen PNG si existe, placeholder SVG en caso contrario. */
    private Node buildLogoNode() {
        try {
            // Intentar cargar el logo del classpath
            java.io.InputStream stream = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE);
            if (stream != null) {
                Image img = new Image(stream, 40, 40, true, true);
                if (!img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(40);
                    iv.setFitHeight(40);
                    return iv;
                }
            }
        } catch (Exception ignored) {
            // Si falla la carga del logo, usar placeholder
        }
        // Placeholder SVG: ícono de llave inglesa en un fondo circular
        StackPane placeholder = new StackPane();
        placeholder.getStyleClass().add("logo-placeholder");
        placeholder.setPrefSize(40, 40);
        placeholder.getChildren().add(AppIcons.create("tool", 22, com.fixtool.ui.theme.ThemeTokens.ACCENT));
        return placeholder;
    }

    // ── Actualización visual del chip ─────────────────────────────────────────

    /**
     * Actualiza el color y texto del chip de conexión de la BOSGAME.
     *
     * @param connected   true si la conexión está activa
     * @param connecting  true si se está intentando conectar ahora
     */
    public void actualizarChip(boolean connected, boolean connecting) {
        // Detener animación de pulso si la había
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            chipDot.setOpacity(1.0);
        }

        if (connecting) {
            chipDot.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_CONNECTING + ";");
            chipLabel.setText("Conectando…");
            chipLabel.setStyle("-fx-text-fill: " + COLOR_CONNECTING + "; -fx-font-size: 12px;");
            // Animación de pulso mientras conecta
            pulseAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO,  new KeyValue(chipDot.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(700), new KeyValue(chipDot.opacityProperty(), 0.2)),
                    new KeyFrame(Duration.millis(1400), new KeyValue(chipDot.opacityProperty(), 1.0))
            );
            pulseAnimation.setCycleCount(Animation.INDEFINITE);
            pulseAnimation.play();

        } else if (connected) {
            chipDot.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_CONNECTED + ";");
            chipLabel.setText("Conectada  " + config.getIpTarget());
            chipLabel.setStyle("-fx-text-fill: " + COLOR_CONNECTED + "; -fx-font-size: 12px;");

        } else {
            chipDot.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_DISCONNECTED + ";");
            chipLabel.setText("Sin conexión");
            chipLabel.setStyle("-fx-text-fill: " + COLOR_DISCONNECTED + "; -fx-font-size: 12px;");
        }
    }

    /** Activa el estado "Conectando" (llamar antes de intentar la conexión). */
    public void setConnecting() {
        actualizarChip(false, true);
    }
}
