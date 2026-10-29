package com.fixtool.ui.components;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.ui.theme.ThemeTokens;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.InetAddress;
import java.net.Socket;

/**
 * Diálogo de conexión con la BOSGAME.
 *
 * Campos: IP (IPv4 validada), Carpeta compartida, Usuario, Contraseña (campo oculto).
 * Flujo:
 *   1. Valida que el equipo responde (ping).
 *   2. Comprueba si el puerto 445 (carpetas compartidas SMB) está disponible.
 *   3. Intenta conectar. Si falla con credenciales vacías, usa las ingresadas.
 *
 * La contraseña vive solo en memoria mientras dura la sesión.
 * Nunca se graba en disco ni en registros.
 */
public class ConnectionDialog extends Dialog<Boolean> {

    private final ConnectionConfig config;

    // ── Controles del formulario ───────────────────────────────────────────────
    private final TextField     fldIp;
    private final TextField     fldFolder;
    private final TextField     fldUser;
    private final PasswordField fldPass;
    private final CheckBox      chkShowPass;
    private final TextField     fldPassVisible; // alias visible de fldPass
    private final Label         lblStatus;
    private final Button        btnConnect;
    private final Button        btnReset;

    public ConnectionDialog(ConnectionConfig config) {
        this.config = config;

        setTitle("Conexión con la BOSGAME – " + AppConfig.APP_NAME);
        setHeaderText(null);
        setResizable(false);

        // ── Inicializar controles ──────────────────────────────────────────────
        fldIp     = new TextField(config.getIpTarget());
        fldFolder = new TextField(config.getSharedFolder());
        fldUser   = new TextField(config.getUsername());
        fldPass   = new PasswordField();
        fldPass.setPromptText("Contraseña");

        // Campo de contraseña visible (se muestra/oculta con el check)
        fldPassVisible = new TextField();
        fldPassVisible.setPromptText("Contraseña");
        fldPassVisible.setVisible(false);
        fldPassVisible.setManaged(false);

        // Sincronizar ambos campos de contraseña
        fldPass.textProperty().addListener((obs, o, n) -> {
            if (!fldPassVisible.isFocused()) fldPassVisible.setText(n);
        });
        fldPassVisible.textProperty().addListener((obs, o, n) -> {
            if (!fldPass.isFocused()) fldPass.setText(n);
        });

        chkShowPass = new CheckBox("Mostrar contraseña");
        chkShowPass.setOnAction(e -> togglePasswordVisibility());

        lblStatus = new Label();
        lblStatus.setWrapText(true);
        lblStatus.setMaxWidth(380);
        lblStatus.getStyleClass().add("conn-status-label");

        // ── Botones del diálogo ───────────────────────────────────────────────
        ButtonType connectType    = new ButtonType("Conectar",   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType     = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType resetType      = new ButtonType("Restablecer", ButtonBar.ButtonData.LEFT);
        ButtonType disconnectType = new ButtonType("Desconectar", ButtonBar.ButtonData.OTHER);

        // Mostrar "Desconectar" solo si ya hay conexión activa
        if (config.isConnected()) {
            getDialogPane().getButtonTypes().addAll(disconnectType, connectType, cancelType);
        } else {
            getDialogPane().getButtonTypes().addAll(resetType, connectType, cancelType);
        }

        // ── Contenido del diálogo (Cuerpo con ScrollPane, botones y título fijos) ──
        ScrollPane dialogScroll = new ScrollPane(buildContent());
        dialogScroll.setFitToWidth(true);
        dialogScroll.getStyleClass().add("transparent-scroll");
        dialogScroll.setMaxHeight(420);
        getDialogPane().setContent(dialogScroll);
        getDialogPane().getStyleClass().add("connection-dialog-pane");

        // Aplicar CSS de la ventana principal al diálogo
        if (getOwner() != null && getOwner().getScene() != null) {
            getDialogPane().getStylesheets().addAll(getOwner().getScene().getStylesheets());
        }

        // ── Obtener referencias a los botones para controlarlos ───────────────
        btnConnect = (Button) getDialogPane().lookupButton(connectType);
        btnConnect.setOnAction(e -> iniciarConexion());
        btnConnect.addEventFilter(javafx.event.ActionEvent.ACTION, javafx.event.Event::consume);

        btnReset = (Button) getDialogPane().lookupButton(resetType);
        if (btnReset != null) {
            btnReset.setOnAction(e -> restablecerValores());
            btnReset.addEventFilter(javafx.event.ActionEvent.ACTION, javafx.event.Event::consume);
        }

        Button btnDisconnect = (Button) getDialogPane().lookupButton(disconnectType);
        if (btnDisconnect != null) {
            btnDisconnect.setOnAction(e -> desconectar());
            btnDisconnect.addEventFilter(javafx.event.ActionEvent.ACTION, javafx.event.Event::consume);
        }

        // ── Resultado del diálogo: true si conexión activa ────────────────────
        setResultConverter(bt -> {
            if (bt == cancelType || bt == null) return false;
            return config.isConnected();
        });

        // Validar IP al escribir
        fldIp.textProperty().addListener((obs, o, n) -> validarCampos());
        validarCampos();
    }

    // ── Construcción del formulario ───────────────────────────────────────────

    private Node buildContent() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("conn-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 8, 24));

        // Etiqueta descriptiva
        Label desc = new Label("Introduce los datos de acceso a la BOSGAME:");
        desc.getStyleClass().add("conn-desc");
        grid.add(desc, 0, 0, 2, 1);

        // Fila 1: IP
        grid.add(buildLabel("IP de la BOSGAME"), 0, 1);
        fldIp.setPromptText(AppConfig.DEFAULT_IP);
        fldIp.getStyleClass().add("conn-field");
        grid.add(fldIp, 1, 1);

        // Fila 2: Carpeta compartida
        grid.add(buildLabel("Carpeta compartida"), 0, 2);
        fldFolder.setPromptText("Nombre de la carpeta (ej. compartida)");
        fldFolder.getStyleClass().add("conn-field");
        grid.add(fldFolder, 1, 2);

        // Fila 3: Usuario
        grid.add(buildLabel("Usuario"), 0, 3);
        fldUser.setPromptText(AppConfig.DEFAULT_USER);
        fldUser.getStyleClass().add("conn-field");
        grid.add(fldUser, 1, 3);

        // Fila 4: Contraseña (oculta + visible superpuestos)
        grid.add(buildLabel("Contraseña"), 0, 4);
        StackPane passStack = new StackPane(fldPass, fldPassVisible);
        fldPass.getStyleClass().add("conn-field");
        fldPassVisible.getStyleClass().add("conn-field");
        grid.add(passStack, 1, 4);

        // Fila 5: Mostrar contraseña
        grid.add(chkShowPass, 1, 5);

        // Fila 6: Estado de la operación de conexión
        grid.add(lblStatus, 0, 6, 2, 1);

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(130);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setMinWidth(250);
        grid.getColumnConstraints().addAll(col0, col1);

        return grid;
    }

    private Label buildLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("conn-field-label");
        lbl.setAlignment(Pos.CENTER_RIGHT);
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    // ── Lógica de conexión (en hilo de fondo) ─────────────────────────────────

    /**
     * Intenta conectar a la BOSGAME en un hilo de fondo para no bloquear la UI.
     * Pasos: validar IP → ping → puerto 445 → actualizar configuración.
     */
    private void iniciarConexion() {
        if (!validarFormulario()) return;

        String ip     = fldIp.getText().trim();
        String folder = fldFolder.getText().trim();
        String user   = fldUser.getText().trim().isBlank() ? AppConfig.DEFAULT_USER : fldUser.getText().trim();
        String pass   = fldPass.getText(); // Solo en memoria

        btnConnect.setDisable(true);
        setStatus("Verificando conexión con " + ip + "…", ThemeTokens.ACCENT);

        // Tarea de fondo: no bloquea la UI mientras se prueba la red
        Task<String> tareaConexion = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Paso 1: ¿Responde el equipo remoto?
                updateMessage("Verificando que el equipo responde…");
                boolean alcanzable = verificarAlcanzable(ip, 2000);
                if (!alcanzable) {
                    return "ERROR:El equipo " + ip + " no responde. Verifica que está encendido y conectado por cable.";
                }

                // Paso 2: ¿El servicio de carpetas compartidas está disponible?
                updateMessage("Verificando carpetas compartidas…");
                boolean smb = verificarPuerto(ip, 445, 2000);
                if (!smb) {
                    return "ERROR:El equipo responde pero el servicio de carpetas compartidas no está disponible en " + ip + ".";
                }

                // Éxito: la conexión lógica está establecida
                return "OK";
            }
        };

        tareaConexion.messageProperty().addListener((obs, o, n) ->
                Platform.runLater(() -> setStatus(n, com.fixtool.ui.theme.ThemeTokens.ACCENT)));

        tareaConexion.setOnSucceeded(evt -> {
            String resultado = tareaConexion.getValue();
            if (resultado.startsWith("OK")) {
                // Guardar configuración en el modelo (contraseña solo en memoria)
                config.setIpTarget(ip);
                config.setSharedFolder(folder);
                config.setUsername(user);
                config.setPassword(pass); // Solo en memoria
                config.setConnected(true);
                setStatus("Conectada correctamente a " + ip, com.fixtool.ui.theme.ThemeTokens.COLOR_OK);
            } else {
                String msg = resultado.replace("ERROR:", "");
                setStatus(msg, com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);
                btnConnect.setDisable(false);
            }
        });

        tareaConexion.setOnFailed(evt -> {
            setStatus("Error inesperado. Revisa los datos e intenta de nuevo.", com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);
            btnConnect.setDisable(false);
        });

        new Thread(tareaConexion, "SynchroWilly-Conexion").start();
    }

    /** Comprueba si la IP remota responde (equivalente a ping, sin privilegios). */
    private boolean verificarAlcanzable(String ip, int timeoutMs) {
        try {
            return InetAddress.getByName(ip).isReachable(timeoutMs);
        } catch (Exception e) {
            return false;
        }
    }

    /** Comprueba si un puerto específico está abierto en el equipo remoto. */
    private boolean verificarPuerto(String ip, int puerto, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, puerto), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Desconexión ────────────────────────────────────────────────────────────

    private void desconectar() {
        config.setConnected(false);
        config.clearPassword(); // Borra contraseña de memoria
        setStatus("Desconectado. La contraseña ha sido borrada de la memoria.", com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
        getResult(); // Cierra el diálogo
        close();
    }

    // ── Helpers de UI ─────────────────────────────────────────────────────────

    private void togglePasswordVisibility() {
        boolean mostrar = chkShowPass.isSelected();
        fldPass.setVisible(!mostrar);
        fldPass.setManaged(!mostrar);
        fldPassVisible.setVisible(mostrar);
        fldPassVisible.setManaged(mostrar);
    }

    private void restablecerValores() {
        fldIp.setText(AppConfig.DEFAULT_IP);
        fldFolder.setText("");
        fldUser.setText(AppConfig.DEFAULT_USER);
        fldPass.clear();
        fldPassVisible.clear();
        lblStatus.setText("");
    }

    private void setStatus(String mensaje, String colorHex) {
        lblStatus.setText(mensaje);
        lblStatus.setStyle("-fx-text-fill: " + colorHex + ";");
    }

    /** Valida el formato IPv4 del campo IP. */
    private boolean validarFormulario() {
        String ip = fldIp.getText().trim();
        if (!ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            setStatus("Escribe una dirección IP válida (ej. " + AppConfig.DEFAULT_IP + ")", com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);
            return false;
        }
        return true;
    }

    /** Habilita/deshabilita el botón Conectar según el estado del formulario. */
    private void validarCampos() {
        if (btnConnect == null) return;
        boolean ipValida = fldIp.getText().trim()
                .matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
        btnConnect.setDisable(!ipValida);
    }
}
