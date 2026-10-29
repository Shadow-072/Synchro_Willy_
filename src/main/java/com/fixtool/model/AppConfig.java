package com.fixtool.model;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Constantes globales configurables de Synchro Willy.
 * Editar aquí para cambiar nombre, IP por defecto, rutas y umbrales.
 * Un solo lugar para todos los valores ajustables del proyecto.
 */
public final class AppConfig {

    // ── Identificación de la aplicación ──────────────────────────────────────
    public static final String APP_NAME      = "Synchro Willy";
    public static final String DEVELOPER     = "Bryan Espinoza";
    public static final String APP_DEVELOPER = DEVELOPER;
    public static final String VERSION       = "2.0.0";

    // ── BOSGAME: valores por defecto ─────────────────────────────────────────
    public static final String DEFAULT_IP   = "192.168.100.10";
    public static final String DEFAULT_USER = "FAST";
    // NOTA: La contraseña nunca se define aquí. Se escribe en el diálogo de conexión.

    // ── Carpeta de trabajo del usuario ───────────────────────────────────────
    /** Nombre de la subcarpeta de trabajo dentro del perfil del usuario. */
    public static final String WORK_FOLDER = "FixTool_Willy";
    public static final String WORK_FOLDER_NAME = WORK_FOLDER;

    /** Ruta absoluta de la carpeta de trabajo (p.ej. C:\Users\Bryan\FixTool_Willy\). */
    public static Path getWorkDir() {
        return Paths.get(System.getProperty("user.home", "."), WORK_FOLDER);
    }

    // ── Recurso gráfico del logo ──────────────────────────────────────────────
    /** Ruta del logo dentro del classpath. Si no existe, se usa un placeholder SVG. */
    public static final String LOGO_RESOURCE = "/assets/logo.png";

    // ── Ventana ───────────────────────────────────────────────────────────────
    public static final double MIN_WIDTH  = 1100;
    public static final double MIN_HEIGHT = 700;

    // ── Umbrales de puntuación de salud (Herramienta 1: Chequeo de Estado) ───────
    public static final int HEALTH_PENALTY_MEM_HIGH    = 15; // RAM > 85 %
    public static final int HEALTH_PENALTY_MEM_MEDIUM  = 7;  // RAM 70-85 %
    public static final int HEALTH_PENALTY_DISK_LOW    = 25; // Disco < 10 % libre
    public static final int HEALTH_PENALTY_DISK_MEDIUM = 10; // Disco 10-20 % libre
    public static final int HEALTH_PENALTY_UPTIME      = 5;  // Tiempo encendido > 14 días
    public static final int HEALTH_PENALTY_BATTERY     = 10; // Desgaste batería > 40 %
    public static final int HEALTH_THRESHOLD_GREEN     = 80; // Puntuación ≥ 80 → verde
    public static final int HEALTH_THRESHOLD_YELLOW    = 60; // Puntuación 60-79 → amarillo
    // Puntuación < 60 → rojo. Mínimo es 0.

    // ── Umbrales de estabilidad de red (Herramienta 4) ───────────────────────
    public static final double STABILITY_EXCELLENT_LOSS_PCT  = 0.0;
    public static final double STABILITY_EXCELLENT_MAX_MS    = 5.0;
    public static final double STABILITY_ACCEPTABLE_LOSS_PCT = 5.0;
    public static final double STABILITY_ACCEPTABLE_MAX_MS   = 50.0;

    private AppConfig() {} // Clase de constantes: no instanciar
}
