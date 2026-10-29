package com.fixtool.util;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.Group;
import javafx.scene.transform.Scale;

import java.util.HashMap;
import java.util.Map;

/**
 * Biblioteca de íconos de línea embebidos en SVG para Synchro Willy.
 * Todos los paths pertenecen al set Feather Icons (MIT License), viewBox 24×24.
 * No requiere archivos externos, fuentes especiales ni conexión a Internet.
 *
 * Uso:  Node icon = AppIcons.create("home", 20, "#00B4D8");
 */
public final class AppIcons {

    // ── Tabla de paths SVG por nombre de ícono ────────────────────────────────
    // Cada entrada es un arreglo de strings: cada string es un "d" de <path>.
    // Íconos con múltiples trazos tienen múltiples entradas en el arreglo.
    private static final Map<String, String[]> PATHS = new HashMap<>();

    static {
        // Inicio / Home
        PATHS.put("home", new String[]{
            "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M9 22V12h6v10"
        });
        // Monitor (Chequeo de Estado)
        PATHS.put("monitor", new String[]{
            "M2 3h20v14H2z",
            "M8 21h8",
            "M12 17v4"
        });
        // Disco duro (Espacio y limpieza)
        PATHS.put("harddrive", new String[]{
            "M22 12H2",
            "M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            "M6 16h.01",
            "M10 16h.01"
        });
        // Wifi / Análisis de Conexión
        PATHS.put("wifi", new String[]{
            "M1.42 9a16 16 0 0 1 21.16 0",
            "M5 12.55a11 11 0 0 1 14.08 0",
            "M8.53 16.11a6 6 0 0 1 6.95 0",
            "M12 20h.01"
        });
        // Actividad / Estabilidad y velocidad
        PATHS.put("activity", new String[]{
            "M22 12h-4l-3 9L9 3l-3 9H2"
        });
        // Flechas opuestas / Puente de archivos
        PATHS.put("shuffle", new String[]{
            "M16 3h5v5",
            "M4 20L21 3",
            "M21 16v5h-5",
            "M15 15l5.1 5.1",
            "M4 4l5 5"
        });
        // Escudo / Laboratorio de permisos
        PATHS.put("shield", new String[]{
            "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
        });
        // Brújula / Mapeo de Red Local
        PATHS.put("compass", new String[]{
            "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z",
            "M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36 6.36-2.12z"
        });
        // Lupa / Monitor de Eventos
        PATHS.put("search", new String[]{
            "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z",
            "M21 21l-4.35-4.35"
        });
        // Candado abierto / Puertos Abiertos
        PATHS.put("unlock", new String[]{
            "M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2z",
            "M7 11V7a5 5 0 0 1 9.9-1"
        });
        // Documento / Informe de diagnóstico
        PATHS.put("filetext", new String[]{
            "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
            "M14 2v6h6",
            "M16 13H8",
            "M16 17H8",
            "M10 9H8"
        });
        // Llave inglesa / placeholder del logo
        PATHS.put("tool", new String[]{
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"
        });
        // X / cerrar
        PATHS.put("x", new String[]{
            "M18 6L6 18",
            "M6 6l12 12"
        });
        // Checkmark
        PATHS.put("check", new String[]{
            "M20 6L9 17l-5-5"
        });
        // Triángulo de alerta
        PATHS.put("alerttriangle", new String[]{
            "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
            "M12 9v4",
            "M12 17h.01"
        });
        // Círculo de información
        PATHS.put("info", new String[]{
            "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10z",
            "M12 16v-4",
            "M12 8h.01"
        });
        // Refrescar / Actualizar
        PATHS.put("refreshcw", new String[]{
            "M23 4v6h-6",
            "M1 20v-6h6",
            "M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"
        });
        // Chevron derecho
        PATHS.put("chevronright", new String[]{
            "M9 18l6-6-6-6"
        });
        // Chevron abajo
        PATHS.put("chevrondown", new String[]{
            "M6 9l6 6 6-6"
        });
        // Power / Salir
        PATHS.put("power", new String[]{
            "M18.36 6.64a9 9 0 1 1-12.73 0",
            "M12 2v10"
        });
        // Enlace / Ethernet
        PATHS.put("link", new String[]{
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
        });
        // Gráfica de barras
        PATHS.put("barchart", new String[]{
            "M12 20V10",
            "M18 20V4",
            "M6 20v-4"
        });
        // Wifi desconectado
        PATHS.put("wifioff", new String[]{
            "M1 1l22 22",
            "M16.72 11.06A10.94 10.94 0 0 1 19 12.55",
            "M5 12.55a10.94 10.94 0 0 1 5.17-2.39",
            "M10.71 5.05A16 16 0 0 1 22.54 9",
            "M1.42 9a15.91 15.91 0 0 1 4.7-2.88",
            "M8.53 16.11a6 6 0 0 1 6.95 0",
            "M12 20h.01"
        });
        // Cargando (spinner estático)
        PATHS.put("loader", new String[]{
            "M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"
        });
        // Círculo-check / éxito
        PATHS.put("checkcircle", new String[]{
            "M22 11.08V12a10 10 0 1 1-5.93-9.14",
            "M22 4L12 14.01l-3-3"
        });
        // Círculo-X / error
        PATHS.put("xcircle", new String[]{
            "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10z",
            "M15 9l-6 6",
            "M9 9l6 6"
        });
        // Upload / subir
        PATHS.put("upload", new String[]{
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "M17 8l-5-5-5 5",
            "M12 3v12"
        });
        // Download / bajar
        PATHS.put("download", new String[]{
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "M7 10l5 5 5-5",
            "M12 15V3"
        });
        // Portapapeles / reporte
        PATHS.put("clipboard", new String[]{
            "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
            "M15 2H9a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1z"
        });
        // Terminar / stop circle
        PATHS.put("stopcircle", new String[]{
            "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10z",
            "M9 9h6v6H9z"
        });
        // Carpeta / folder
        PATHS.put("folder", new String[]{
            "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"
        });
        // Flecha hacia arriba / subir nivel
        PATHS.put("arrowup", new String[]{
            "M12 19V5",
            "M5 12l7-7 7 7"
        });
        // Usuario (Acerca de)
        PATHS.put("user", new String[]{
            "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2",
            "M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"
        });
        // Titulación / Carrera (Acerca de)
        PATHS.put("award", new String[]{
            "M12 15a7 7 0 1 0 0-14a7 7 0 0 0 0 14z",
            "M8.21 13.89L7 23l5-3l5 3l-1.21-9.12"
        });
        // Libro / Clase (Acerca de)
        PATHS.put("book", new String[]{
            "M4 19.5A2.5 2.5 0 0 1 6.5 17H20",
            "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
        });
        // Calendario / Semestre (Acerca de)
        PATHS.put("calendar", new String[]{
            "M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z",
            "M16 2v4",
            "M8 2v4",
            "M3 10h18"
        });
    }

    /**
     * Crea un nodo JavaFX con el ícono SVG de línea indicado.
     *
     * @param name     Nombre del ícono (ej. "home", "wifi", "shield")
     * @param size     Tamaño en píxeles (cuadrado). El viewBox siempre es 24×24.
     * @param colorHex Color de trazo en hexadecimal (ej. "#00B4D8")
     * @return         Nodo de tamaño fijo, listo para insertar en cualquier layout JavaFX
     */
    public static Node create(String name, double size, String colorHex) {
        // Obtener los paths del ícono; si no existe, usar "info" como fallback
        String[] pathData = PATHS.getOrDefault(name, PATHS.get("info"));

        // Crear un grupo con un SVGPath por cada trazo del ícono
        Group group = new Group();
        for (String d : pathData) {
            SVGPath svgPath = new SVGPath();
            svgPath.setContent(d);
            svgPath.setFill(Color.TRANSPARENT);
            svgPath.setStroke(Color.web(colorHex));
            svgPath.setStrokeWidth(2.0);
            svgPath.setStrokeLineCap(StrokeLineCap.ROUND);
            svgPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
            group.getChildren().add(svgPath);
        }

        // Escalar el grupo de 24×24 al tamaño solicitado, pivotando en el origen (0,0)
        double scale = size / 24.0;
        group.getTransforms().add(new Scale(scale, scale, 0, 0));

        // Envolver en un Pane de tamaño fijo para correcta integración con layouts JavaFX
        Pane wrapper = new Pane(group);
        wrapper.setPrefSize(size, size);
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);
        // Clip para evitar desbordamiento visual
        wrapper.setClip(new Rectangle(size, size));

        return wrapper;
    }

    private AppIcons() {} // Clase utilitaria: no instanciar
}
