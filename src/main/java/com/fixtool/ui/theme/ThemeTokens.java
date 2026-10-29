package com.fixtool.ui.theme;

/**
 * Tokens centralizados de diseño y colores para Synchro Willy.
 * Diseñado conforme a las pautas WCAG 2.1:
 * - Texto normal: contraste >= 4.5:1
 * - Texto grande, íconos y bordes de controles: contraste >= 3.0:1
 * - Prohibido texto blanco sobre grises medios/claros.
 * - Texto secundario nunca más tenue que TEXT_SECONDARY.
 * - Chips rellenos de color con texto oscuro TEXT_DARK.
 */
public final class ThemeTokens {

    private ThemeTokens() {}

    // ── 1. Fondos base y superficies ──────────────────────────────────────────
    /** Fondo base de la ventana / aplicación (#0E141B). */
    public static final String BG_BASE             = "#0E141B";
    /** Superficie de contenedores principales, cabecera y paneles (#151D27). */
    public static final String BG_SURFACE          = "#151D27";
    /** Superficie elevada: tarjetas secundarias, dropdowns, inputs (#1C2633). */
    public static final String BG_SURFACE_ELEVATED = "#1C2633";
    /** Superficie al pasar cursor o selección activa (#243242). */
    public static final String BG_SURFACE_HOVER    = "#243242";

    // ── 2. Bordes y divisores ─────────────────────────────────────────────────
    /** Borde sutil para división estructural y tarjetas (#2C3A4B). */
    public static final String BORDER_BASE         = "#2C3A4B";
    /** Borde para controles interactivos con ratio >= 3.0:1 (#627690). */
    public static final String BORDER_CONTROL      = "#627690";
    /** Borde de foco y selección activa (#38BDF8). */
    public static final String BORDER_FOCUS        = "#38BDF8";

    // ── 3. Tipografía ─────────────────────────────────────────────────────────
    /** Texto principal de alto contraste (14.42:1 sobre superficie) (#E8EDF3). */
    public static final String TEXT_PRIMARY        = "#E8EDF3";
    /** Texto secundario/descriptivo (7.98:1 sobre superficie) (#A7B3C2). Límite inferior de opacidad. */
    public static final String TEXT_SECONDARY      = "#A7B3C2";
    /** Texto oscuro para chips rellenos o fondos claros (10.62:1 sobre verde) (#0E141B). */
    public static final String TEXT_DARK           = "#0E141B";

    // ── 4. Estados y Semáforo ─────────────────────────────────────────────────
    /** Estado OK / Éxito (10.62:1 con texto oscuro) (#4ADE80). */
    public static final String COLOR_OK            = "#4ADE80";
    /** Estado Atención / Advertencia (12.09:1 con texto oscuro) (#FACC15). */
    public static final String COLOR_WARN          = "#FACC15";
    /** Estado Problema / Error (6.69:1 con texto oscuro) (#F87171). */
    public static final String COLOR_ERROR         = "#F87171";
    /** Estado Neutro / Desconectado / Sin ejecutar (#A7B3C2). */
    public static final String COLOR_NEUTRAL       = "#A7B3C2";

    // ── 5. Acentos y Brand ────────────────────────────────────────────────────
    /** Acento principal cian (#38BDF8) (7.92:1 sobre superficie). */
    public static final String ACCENT              = "#38BDF8";
    /** Hover para acento (#7DD3FC). */
    public static final String ACCENT_HOVER        = "#7DD3FC";

    // ── Métodos utilitarios para construcción de estilos consistentes ─────────

    /** Estilo de chip relleno: fondo de color con texto oscuro (regla de diseño). */
    public static String chipFilled(String bgHex) {
        return String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-background-radius: 12px;",
            bgHex, TEXT_DARK
        );
    }

    /** Estilo de chip outline: superficie elevada con borde y texto coloreados. */
    public static String chipOutline(String colorHex) {
        return String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-width: 1.5px;",
            BG_SURFACE_ELEVATED, colorHex, colorHex
        );
    }

    /** Estilo para tarjeta estándar sobre fondo base. */
    public static String cardStyle() {
        return String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px;",
            BG_SURFACE, BORDER_BASE
        );
    }

    /** Estilo para tarjeta elevada (dentro de un panel de superficie). */
    public static String cardElevatedStyle() {
        return String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;",
            BG_SURFACE_ELEVATED, BORDER_BASE
        );
    }
}
