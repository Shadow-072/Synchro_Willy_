package com.fixtool.ui.components;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.ui.theme.ThemeTokens;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Panel Superior de Configuración Dinámica Ampliado de Synchro Willy.
 * Diseñado con mayor amplitud, prominencia visual y contraste elevado.
 * Incluye el botón de control para expandir/colapsar el panel lateral izquierdo,
 * campos al vuelo para IP Target, Usuario y Contraseña, e indicadores de estado reactivo.
 */
public class DynamicTopBar extends HBox {

    private final ConnectionConfig config;
    private final Label liveTargetBadge;
    private final TextField txtIp;
    private final TextField txtUser;
    private final PasswordField txtPass;

    public DynamicTopBar(ConnectionConfig config,
                         Runnable onToggleSidebar,
                         Runnable onNavigateSplash,
                         Runnable onExitApp) {
        this.config = config;

        this.getStyleClass().add("topbar-prominent");
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(18);
        this.setPadding(new Insets(14, 24, 14, 24));

        // 1. Botón Hamburguesa / Expansión de Sidebar
        Button btnToggleSidebar = new Button("☰");
        btnToggleSidebar.getStyleClass().add("hamburger-btn");
        btnToggleSidebar.setTooltip(new Tooltip("Expandir u ocultar el panel lateral izquierdo"));
        btnToggleSidebar.setOnAction(e -> {
            if (onToggleSidebar != null) onToggleSidebar.run();
        });

        // 2. Branding Oficial: "Synchro Willy"
        VBox brandBox = new VBox(2);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLogo = new Label("⚡");
        iconLogo.setStyle("-fx-font-size: 22px; -fx-text-fill: " + ThemeTokens.ACCENT + ";");

        Label brandSynchro = new Label("Synchro");
        brandSynchro.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_PRIMARY + ";");

        Label brandWilly = new Label("Willy");
        brandWilly.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.ACCENT + ";");

        titleRow.getChildren().addAll(iconLogo, brandSynchro, brandWilly);

        Label subBrand = new Label("DYNAMIC NETWORK CONTROL");
        subBrand.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + "; -fx-padding: 0 0 0 28;");

        brandBox.getChildren().addAll(titleRow, subBrand);

        // Separador vertical translúcido
        Separator sep1 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep1.setStyle("-fx-opacity: 0.25; -fx-padding: 0 4 0 4;");

        // 3. Grupo de Campos de Configuración Dinámica (Ampliados y Estilizados)
        HBox fieldsRow = new HBox(12);
        fieldsRow.setAlignment(Pos.CENTER_LEFT);

        // Campo 1: IP Target
        VBox ipBox = createFieldCard("🌐 IP TARGET / BOSGAME", txtIp = new TextField());
        txtIp.setPromptText("192.168.100.10");
        txtIp.setPrefWidth(140);
        txtIp.textProperty().bindBidirectional(config.ipTargetProperty());

        // Campo 2: Usuario
        VBox userBox = createFieldCard("👤 USUARIO", txtUser = new TextField());
        txtUser.setPromptText("FAST");
        txtUser.setPrefWidth(100);
        txtUser.textProperty().bindBidirectional(config.usernameProperty());

        // Campo 3: Contraseña
        VBox passBox = createFieldCard("🔑 CONTRASEÑA", txtPass = new PasswordField());
        txtPass.setPromptText("12345");
        txtPass.setPrefWidth(100);
        txtPass.textProperty().bindBidirectional(config.passwordProperty());

        // Botón Restablecer valores
        Button btnReset = new Button("🔄 Restablecer");
        btnReset.getStyleClass().add("button-secondary");
        btnReset.setTooltip(new Tooltip("Restablecer a valores por defecto (192.168.100.10 / FAST / 12345)"));
        btnReset.setOnAction(e -> config.resetToDefaults());

        fieldsRow.getChildren().addAll(ipBox, userBox, passBox, btnReset);

        // Espaciador flexible
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 4. Indicador de Estado en Vivo (Live Chip)
        liveTargetBadge = new Label();
        liveTargetBadge.getStyleClass().add("badge-chip");
        updateLiveBadge();

        config.ipTargetProperty().addListener((obs, oldVal, newVal) -> updateLiveBadge());
        config.usernameProperty().addListener((obs, oldVal, newVal) -> updateLiveBadge());

        // 5. Botones de Navegación Rápida
        HBox navBox = new HBox(8);
        navBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnSplash = new Button("🏠 Portada");
        btnSplash.getStyleClass().add("button-secondary");
        btnSplash.setTooltip(new Tooltip("Regresar a la pantalla de bienvenida"));
        btnSplash.setOnAction(e -> {
            if (onNavigateSplash != null) onNavigateSplash.run();
        });

        Button btnExit = new Button("✕");
        btnExit.getStyleClass().add("button-danger");
        btnExit.setTooltip(new Tooltip("Salir de Synchro Willy"));
        btnExit.setOnAction(e -> {
            if (onExitApp != null) onExitApp.run();
        });

        navBox.getChildren().addAll(liveTargetBadge, btnSplash, btnExit);

        this.getChildren().addAll(btnToggleSidebar, brandBox, sep1, fieldsRow, spacer, navBox);
    }

    private VBox createFieldCard(String labelText, TextField inputField) {
        VBox box = new VBox(3);
        box.getStyleClass().add("topbar-field-container");
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + ThemeTokens.TEXT_SECONDARY + ";");

        inputField.getStyleClass().add("text-input-field");

        box.getChildren().addAll(label, inputField);
        return box;
    }

    private void updateLiveBadge() {
        String ip = config.getIpTarget().isBlank() ? "192.168.100.10" : config.getIpTarget();
        String user = config.getUsername().isBlank() ? "FAST" : config.getUsername();
        liveTargetBadge.setText(String.format("● CONECTADO: %s (%s)", ip, user));
    }
}
