package com.fixtool.ui;

import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.service.DiagnosticService;
import com.fixtool.ui.components.ConnectionDialog;
import com.fixtool.ui.components.TopBar;
import com.fixtool.util.AnimationHelper;
import com.fixtool.util.AppIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Vista principal de Synchro Willy.
 *
 * Estructura:
 *  ┌─────────────────────────────────────────────┐
 *  │  TopBar (cabecera fija)                     │
 *  ├──────────┬──────────────────────────────────┤
 *  │ Sidebar  │  Contenido central               │
 *  │  (fija)  │  (HomeView o vista de herramienta)│
 *  └──────────┴──────────────────────────────────┘
 *
 * La barra lateral es fija (no colapsable), siempre visible,
 * con Inicio y las 10 herramientas numeradas con íconos SVG.
 */
public class MainView extends BorderPane {

    private final DiagnosticService service;
    private final ConnectionConfig  config;

    private final TopBar    topBar;
    private final VBox      sidebar;
    private final StackPane contentArea;

    // Vistas activas en memoria para reutilización y conservación de scroll
    private HomeView homeView;
    private Node     currentView;
    private final Map<Integer, Node> viewCache = new HashMap<>();

    // Botones del sidebar para resaltar el activo
    private Button                  btnHome;
    private Button                  btnAbout;
    private AcercaDeView            aboutView;
    private final Map<Integer, Button> toolButtons = new HashMap<>();

    public MainView(DiagnosticService service,
                    ConnectionConfig  config,
                    Runnable          onExitApp) {
        this.service    = service;
        this.config     = config;

        this.getStyleClass().add("bg-root");

        // ── 1. Cabecera superior ──────────────────────────────────────────────
        topBar = new TopBar(
                config,
                this::abrirDialogoConexion,
                onExitApp
        );
        this.setTop(topBar);

        // ── 2. Barra lateral fija con ScrollPane vertical ─────────────────────
        sidebar = buildSidebar();
        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScroll.getStyleClass().add("sidebar-scroll");
        sidebarScroll.setMinWidth(220);
        sidebarScroll.setPrefWidth(220);
        sidebarScroll.setMaxWidth(220);
        this.setLeft(sidebarScroll);

        // ── 3. Área de contenido central ──────────────────────────────────────
        contentArea = new StackPane();
        contentArea.getStyleClass().add("bg-root");
        this.setCenter(contentArea);

        // Habilitar navegación por teclado en el scroll activo
        installKeyboardScroll();

        // Mostrar la pantalla de inicio al arrancar
        showHome();
    }

    // ── Construcción de la barra lateral ─────────────────────────────────────

    /**
     * Sidebar fija con "Inicio" y las 10 herramientas numeradas.
     * Cada ítem tiene ícono SVG + nombre corto.
     */
    private VBox buildSidebar() {
        VBox box = new VBox(4);
        box.getStyleClass().add("sidebar");
        box.setPrefWidth(220);
        box.setMinWidth(220);
        box.setMaxWidth(220);
        box.setPadding(new Insets(16, 10, 16, 10));

        // Etiqueta de sección
        Label lblSection = new Label("HERRAMIENTAS");
        lblSection.getStyleClass().add("sidebar-section-label");
        lblSection.setPadding(new Insets(0, 0, 8, 8));

        // Botón Inicio
        btnHome = buildSidebarButton("home", "Inicio", () -> showHome());

        // Separador visual
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-margin: 4 0;");

        box.getChildren().addAll(lblSection, btnHome, sep);

        // Un botón por cada herramienta (1-10)
        for (DiagnosticAction tool : service.getActions()) {
            Button btn = buildSidebarButton(
                    tool.getIconSymbol(),
                    tool.getId() + ". " + tool.getTitle(),
                    () -> showToolView(tool)
            );
            toolButtons.put(tool.getId(), btn);
            box.getChildren().add(btn);
        }

        // Separador fino antes de Acerca de
        Region sepAbout = new Region();
        sepAbout.setPrefHeight(1);
        sepAbout.setStyle("-fx-background-color: " + com.fixtool.ui.theme.ThemeTokens.BORDER_BASE + "; -fx-margin: 6 0;");
        box.getChildren().add(sepAbout);

        // Botón Acerca de (sin número)
        btnAbout = buildSidebarButton("info", "Acerca de", this::showAboutView);
        box.getChildren().add(btnAbout);

        return box;
    }

    /**
     * Construye un botón del sidebar con ícono SVG a la izquierda y etiqueta de texto.
     */
    private Button buildSidebarButton(String iconKey, String label, Runnable action) {
        Node icon = AppIcons.create(iconKey, 16, com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);

        Button btn = new Button(label, icon);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setGraphicTextGap(10);
        btn.setOnAction(e -> action.run());

        // Micro-animación de escala al hacer clic
        btn.setOnMousePressed(e -> btn.setScaleX(0.97));
        btn.setOnMouseReleased(e -> btn.setScaleX(1.0));

        return btn;
    }

    // ── Navegación entre vistas ───────────────────────────────────────────────

    /** Muestra la pantalla de inicio. */
    public void showHome() {
        if (homeView == null) {
            homeView = new HomeView(service, this::showToolView, this::irAInforme);
        }
        switchTo(homeView);
        marcarActivo(-1); // -1 = Inicio
    }

    /** Muestra la pantalla Acerca de (autoría y datos académicos). */
    public void showAboutView() {
        if (aboutView == null) {
            aboutView = new AcercaDeView();
        }
        switchTo(aboutView);
        marcarActivo(-2); // -2 = Acerca de
    }

    /**
     * Muestra la vista de una herramienta (reutilizando instancias para preservar el scroll).
     */
    public void showToolView(DiagnosticAction tool) {
        Node vista = viewCache.computeIfAbsent(tool.getId(), id -> resolverVista(tool));
        switchTo(vista);
        marcarActivo(tool.getId());
    }

    /**
     * Resuelve qué vista mostrar para cada herramienta por su id.
     */
    private Node resolverVista(DiagnosticAction tool) {
        return switch (tool.getId()) {
            case 1 -> new RadiografiaView(tool);
            case 2 -> new EspacioView(tool);
            case 3 -> new DoctorRedView(tool, config);
            case 4 -> new EstabilidadVelocidadView(tool, config);
            case 5 -> new PuenteArchivosView(tool, config);
            case 6 -> new LaboratorioPermisosView(tool);
            case 7 -> new MapaPcView(tool, config);
            case 8 -> new DetectiveEventosView(tool);
            case 9 -> new PuertasAbiertasView(tool);
            case 10 -> new InformeView(tool, config);
            default -> new ActionDetailView(tool);
        };
    }

    /** Navega a la herramienta 10 (Informe de diagnóstico). */
    private void irAInforme() {
        DiagnosticAction informe = service.getActionById(10);
        if (informe != null) showToolView(informe);
    }

    // ── Transición suave entre vistas ────────────────────────────────────────

    private void switchTo(Node next) {
        if (next == currentView) return;
        if (currentView instanceof RadiografiaView) {
            ((RadiografiaView) currentView).cleanup();
        }
        if (next instanceof RadiografiaView) {
            ((RadiografiaView) next).resume();
        }
        Node prev = currentView;
        currentView = next;
        AnimationHelper.switchView(contentArea, prev, next, null);
    }

    // ── Navegación con teclado (Flechas, Re Pág, Av Pág, Inicio, Fin) ─────────

    private void installKeyboardScroll() {
        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl) return;
            ScrollPane sp = findActiveScrollPane(contentArea);
            if (sp == null) return;

            switch (event.getCode()) {
                case PAGE_DOWN -> {
                    sp.setVvalue(Math.min(1.0, sp.getVvalue() + 0.25));
                    event.consume();
                }
                case PAGE_UP -> {
                    sp.setVvalue(Math.max(0.0, sp.getVvalue() - 0.25));
                    event.consume();
                }
                case HOME -> {
                    sp.setVvalue(0.0);
                    event.consume();
                }
                case END -> {
                    sp.setVvalue(1.0);
                    event.consume();
                }
                case DOWN -> {
                    sp.setVvalue(Math.min(1.0, sp.getVvalue() + 0.05));
                    event.consume();
                }
                case UP -> {
                    sp.setVvalue(Math.max(0.0, sp.getVvalue() - 0.05));
                    event.consume();
                }
                default -> {}
            }
        });
    }

    private ScrollPane findActiveScrollPane(Node root) {
        if (root instanceof ScrollPane sp) return sp;
        if (root instanceof Parent p) {
            Node found = p.lookup(".scroll-pane");
            if (found instanceof ScrollPane sp) return sp;
        }
        return null;
    }

    // ── Resalte del ítem activo en el sidebar ─────────────────────────────────

    /**
     * Resalta el ítem activo en el sidebar.
     * @param activeId id de la herramienta activa, o -1 para Inicio.
     */
    private void marcarActivo(int activeId) {
        // Restablecer estilos
        btnHome.getStyleClass().setAll("sidebar-btn");
        if (btnAbout != null) btnAbout.getStyleClass().setAll("sidebar-btn");
        toolButtons.forEach((id, btn) -> btn.getStyleClass().setAll("sidebar-btn"));

        // Actualizar ícono del botón activo al color de acento
        if (activeId == -1) {
            btnHome.getStyleClass().setAll("sidebar-btn-active");
            replaceIcon(btnHome, "home", com.fixtool.ui.theme.ThemeTokens.ACCENT);
        } else if (activeId == -2) {
            if (btnAbout != null) {
                btnAbout.getStyleClass().setAll("sidebar-btn-active");
                replaceIcon(btnAbout, "info", com.fixtool.ui.theme.ThemeTokens.ACCENT);
            }
        } else {
            Button active = toolButtons.get(activeId);
            if (active != null) {
                active.getStyleClass().setAll("sidebar-btn-active");
                DiagnosticAction tool = service.getActionById(activeId);
                if (tool != null) replaceIcon(active, tool.getIconSymbol(), com.fixtool.ui.theme.ThemeTokens.ACCENT);
            }
        }

        // Restaurar ícono a todos los inactivos
        replaceIcon(btnHome, "home", activeId == -1 ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
        if (btnAbout != null) {
            replaceIcon(btnAbout, "info", activeId == -2 ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
        }
        toolButtons.forEach((id, btn) -> {
            DiagnosticAction t = service.getActionById(id);
            if (t != null) {
                replaceIcon(btn, t.getIconSymbol(), id == activeId ? com.fixtool.ui.theme.ThemeTokens.ACCENT : com.fixtool.ui.theme.ThemeTokens.TEXT_SECONDARY);
            }
        });
    }

    /** Reemplaza el ícono de un botón del sidebar con el color indicado. */
    private void replaceIcon(Button btn, String iconKey, String colorHex) {
        btn.setGraphic(AppIcons.create(iconKey, 16, colorHex));
    }

    // ── Diálogo de conexión con la BOSGAME ───────────────────────────────────

    private void abrirDialogoConexion() {
        ConnectionDialog dialog = new ConnectionDialog(config);
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);

        // Aplicar estilos del tema oscuro al diálogo
        if (this.getScene() != null) {
            dialog.getDialogPane().getStylesheets()
                  .addAll(this.getScene().getStylesheets());
        }

        // Mostrar estado "Conectando" en el chip mientras el diálogo está abierto
        dialog.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (!isShowing && config.isConnected()) {
                topBar.actualizarChip(true, false);
            } else if (!isShowing) {
                topBar.actualizarChip(false, false);
            }
        });

        dialog.showAndWait();
    }
}
