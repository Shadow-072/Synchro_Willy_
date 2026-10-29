package com.fixtool;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.service.DiagnosticService;
import com.fixtool.ui.MainView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Punto de entrada de Synchro Willy.
 * Gestiona el ciclo de vida de la ventana, la carga de estilos
 * y la salida controlada (con limpieza de contraseña y conexiones).
 */
public class FixToolApp extends Application {

    private Stage primaryStage;
    private ConnectionConfig connectionConfig;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.connectionConfig = new ConnectionConfig();

        // ── Configuración de la ventana ───────────────────────────────────────
        stage.setTitle(AppConfig.APP_NAME + " " + AppConfig.VERSION);
        stage.setMinWidth(AppConfig.MIN_WIDTH);
        stage.setMinHeight(AppConfig.MIN_HEIGHT);

        // Ícono oficial de la ventana
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE);
            if (iconStream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception ignored) {
        }

        // Construir la vista principal directamente (sin pantalla de bienvenida)
        DiagnosticService service = new DiagnosticService();
        mainView = new MainView(service, connectionConfig, this::confirmarSalida);

        Scene scene = new Scene(mainView, 1280, 840);

        // Cargar hoja de estilos local (sin CDN ni recursos externos)
        java.net.URL cssUrl = getClass().getResource("/styles/dark-minimalist.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        centrarVentana(stage, 1280, 840);

        // Salida controlada al pulsar la 'X' de la ventana
        stage.setOnCloseRequest(e -> {
            e.consume(); // Evitar cierre inmediato
            confirmarSalida();
        });

        stage.show();
    }

    /** Centra la ventana en la pantalla principal. */
    private void centrarVentana(Stage stage, double width, double height) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX(Math.max(0, (screen.getWidth() - width) / 2));
        stage.setY(Math.max(0, (screen.getHeight() - height) / 2));
    }

    /**
     * Muestra un diálogo de confirmación de salida con verificación de informe pendiente.
     * Antes de cerrar: restaura permisos de prueba, borra la contraseña y desconecta BOSGAME.
     */
    public void confirmarSalida() {
        com.fixtool.model.SessionState session = com.fixtool.model.SessionState.getInstance();
        boolean hasResults = session.hasResults();
        boolean hasReport = session.getResult(10) != null;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Salir – " + AppConfig.APP_NAME);
        alert.setHeaderText("¿Deseas cerrar " + AppConfig.APP_NAME + "?");
        alert.initOwner(primaryStage);

        ButtonType btnGenerateAndExit = new ButtonType("Generar informe y salir");
        ButtonType btnJustExit = new ButtonType("Salir sin generar");
        ButtonType btnCancel = new ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        if (hasResults && !hasReport) {
            alert.setContentText("Tienes pruebas ejecutadas pero aún no has generado el informe HTML.\n¿Deseas compilar el informe antes de salir?");
            alert.getButtonTypes().setAll(btnGenerateAndExit, btnJustExit, btnCancel);
        } else {
            alert.setContentText("La conexión con la BOSGAME se cerrará y la contraseña en memoria será borrada.");
            alert.getButtonTypes().setAll(new ButtonType("Salir", javafx.scene.control.ButtonBar.ButtonData.OK_DONE), btnCancel);
        }

        // Aplicar el mismo tema oscuro al diálogo de confirmación
        if (primaryStage.getScene() != null) {
            alert.getDialogPane().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            ButtonType chosen = result.get();
            if (chosen == btnGenerateAndExit) {
                // Generar informe automático antes de cerrar
                try {
                    com.fixtool.service.ReportService rep = new com.fixtool.service.ReportService();
                    com.fixtool.service.DiagnosticService diag = new com.fixtool.service.DiagnosticService();
                    java.util.Set<Integer> allIds = new java.util.HashSet<>();
                    for (int i = 1; i <= 9; i++) allIds.add(i);
                    rep.generateHtmlReport(diag.getActions(), allIds, connectionConfig);
                } catch (Exception ignored) {}
                limpiarAlSalir();
                primaryStage.close();
                System.exit(0);
            } else if (chosen == btnJustExit || chosen.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE) {
                limpiarAlSalir();
                primaryStage.close();
                System.exit(0);
            }
        }
    }

    /**
     * Limpieza segura antes de cerrar la app:
     *  - Restablece permisos sobre el archivo de prueba si se alteraron.
     *  - Borra la contraseña de la memoria.
     *  - Marca la conexión como cerrada.
     */
    private void limpiarAlSalir() {
        try {
            com.fixtool.service.PermissionLabService lab = new com.fixtool.service.PermissionLabService();
            lab.resetFilePermissions();
        } catch (Exception ignored) {}
        connectionConfig.clearPassword();
        connectionConfig.setConnected(false);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
