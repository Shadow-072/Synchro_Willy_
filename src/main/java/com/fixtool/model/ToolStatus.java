package com.fixtool.model;

/**
 * Estado posible de cada herramienta durante la sesión de Synchro Willy.
 * Alimenta los chips de estado en la pantalla de inicio y en el informe final.
 */
public enum ToolStatus {

    SIN_EJECUTAR("Sin ejecutar", com.fixtool.ui.theme.ThemeTokens.COLOR_NEUTRAL),
    PENDIENTE   ("Pendiente",    com.fixtool.ui.theme.ThemeTokens.COLOR_NEUTRAL),
    OK          ("OK",          com.fixtool.ui.theme.ThemeTokens.COLOR_OK),
    ATENCION    ("Atención",    com.fixtool.ui.theme.ThemeTokens.COLOR_WARN),
    PROBLEMA    ("Problema",    com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR),
    ERROR       ("Error",       com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR);

    private final String label;
    private final String colorHex;

    ToolStatus(String label, String colorHex) {
        this.label    = label;
        this.colorHex = colorHex;
    }

    /** Etiqueta legible para mostrar al usuario. */
    public String getLabel() { return label; }

    /** Texto para mostrar en badges y chips (alias de getLabel). */
    public String getDisplayText() { return label; }

    /** Color hexadecimal asociado al estado (#RRGGBB). */
    public String getColorHex() { return colorHex; }

    /**
     * Deriva el estado a partir de un resultado de diagnóstico.
     * Las herramientas pueden devolver Atención mediante lógica propia en fases posteriores.
     */
    public static ToolStatus fromResult(DiagnosticResult result) {
        if (result == null) return SIN_EJECUTAR;
        return result.isSuccess() ? OK : PROBLEMA;
    }
}
