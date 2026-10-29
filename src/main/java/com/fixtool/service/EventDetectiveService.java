package com.fixtool.service;

import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio para inspección amigable del Visor de Eventos de Windows
 * (Herramienta 8: Monitor de Eventos).
 * Utiliza 'wevtutil' nativo sin requerir permisos de administrador.
 */
public class EventDetectiveService {

    public enum EventSeverity {
        ERROR("Error", com.fixtool.ui.theme.ThemeTokens.COLOR_ERROR, "alerttriangle"),
        WARNING("Advertencia", com.fixtool.ui.theme.ThemeTokens.COLOR_WARN, "alerttriangle"),
        INFO("Información", com.fixtool.ui.theme.ThemeTokens.ACCENT, "infocircle");

        private final String label;
        private final String colorHex;
        private final String iconKey;

        EventSeverity(String label, String colorHex, String iconKey) {
            this.label = label;
            this.colorHex = colorHex;
            this.iconKey = iconKey;
        }

        public String getLabel() {
            return label;
        }

        public String getColorHex() {
            return colorHex;
        }

        public String getIconKey() {
            return iconKey;
        }
    }

    /**
     * Modelo de conocimiento estructurado para eventos de Windows.
     * Responde a las 5 secciones requeridas: Qué pasó, Qué significa, Posible
     * causa, Qué puedes hacer y Detalle técnico.
     */
    public static class EventKnowledge {
        public final String humanTitle;
        public final String whatHappened; // Qué pasó
        public final String whatItMeans; // Qué significa
        public final String possibleCause; // Posible causa
        public final String whatYouCanDo; // Qué puedes hacer

        public EventKnowledge(String humanTitle, String whatHappened, String whatItMeans, String possibleCause,
                String whatYouCanDo) {
            this.humanTitle = humanTitle;
            this.whatHappened = whatHappened;
            this.whatItMeans = whatItMeans;
            this.possibleCause = possibleCause;
            this.whatYouCanDo = whatYouCanDo;
        }
    }

    public static class SystemEventItem {
        public String id;
        public String logName; // "System" o "Application"
        public String source;
        public EventSeverity severity;
        public String timestamp;
        public String humanTitle;
        public String whatHappened;
        public String whatItMeans;
        public String possibleCause;
        public String whatYouCanDo;
        public String humanExplanation; // Compatibilidad
        public String originalMessage;
        public String user;
        public String computer;
        public String technicalSummary;

        public SystemEventItem(String id, String logName, String source, EventSeverity severity,
                String timestamp, String humanTitle, String whatHappened,
                String whatItMeans, String possibleCause, String whatYouCanDo,
                String originalMessage, String user, String computer, String technicalSummary) {
            this.id = id;
            this.logName = logName;
            this.source = source;
            this.severity = severity;
            this.timestamp = timestamp;
            this.humanTitle = humanTitle;
            this.whatHappened = whatHappened;
            this.whatItMeans = whatItMeans;
            this.possibleCause = possibleCause;
            this.whatYouCanDo = whatYouCanDo;
            this.humanExplanation = whatHappened;
            this.originalMessage = originalMessage;
            this.user = (user != null && !user.isBlank()) ? user : "N/A";
            this.computer = (computer != null && !computer.isBlank()) ? computer : "Silver";
            this.technicalSummary = technicalSummary;
        }
    }

    public static class RebootExplanation {
        public boolean found = false;
        public String timestamp = "Desconocido";
        public String eventId = "N/A";
        public String causeSummary = "No se hallaron eventos recientes de apagado o reinicio.";
        public String detail = "El sistema no reporta fallos de energía ni reinicios forzados en los últimos registros.";
        public boolean wasClean = true;
    }

    // Diccionario ampliado por pareja: proveedor normalizado + ":" + eventId
    private static final Map<String, EventKnowledge> EVENT_KNOWLEDGE_DICT = new HashMap<>();

    static {
        // 1. Kernel-Power 41: el equipo se reinició sin apagarse correctamente
        registerKnowledge("kernelpower", "41", new EventKnowledge(
                "Apagado repentino / Corte de energía",
                "El equipo se reinició o apagó sin completar el procedimiento de apagado limpio previo.",
                "Windows perdió la alimentación eléctrica o el procesador se detuvo de forma instantánea antes de poder volcar los datos a disco.",
                "Corte de suministro eléctrico, enchufe flojo, batería completamente agotada, botón de encendido presionado prolongadamente o sobrecalentamiento crítico.",
                "Verifica la conexión del cargador y la temperatura del equipo. Si ocurre repetidamente sin cortes externos, revisa la salud de la fuente de poder o batería."));

        // 2. EventLog 6008: el apagado anterior fue inesperado
        registerKnowledge("eventlog", "6008", new EventKnowledge(
                "Apagado previo inesperado",
                "Al encenderse, Windows detectó que la sesión de trabajo previa finalizó de manera abrupta.",
                "El sistema de registro confirma que no se recibió la señal de cierre ordenado antes de perder corriente.",
                "Apagado forzado por el usuario, pantalla azul (BSOD) o corte súbito de energía.",
                "Verifica si el equipo se calentó en exceso durante una tarea pesada o si hubo interrupciones eléctricas."));

        // 3. EventLog 6005: arranque normal del sistema
        registerKnowledge("eventlog", "6005", new EventKnowledge(
                "Inicio normal del sistema",
                "El servicio de registro de eventos de Windows se inició satisfactoriamente.",
                "Marca el inicio limpio de una sesión operativa del sistema.",
                "Arranque o reinicio habitual del equipo.",
                "Ninguna acción requerida; confirma que el sistema inició con total normalidad."));

        // 4. EventLog 6006: apagado normal del sistema
        registerKnowledge("eventlog", "6006", new EventKnowledge(
                "Apagado limpio del sistema",
                "El servicio de registro de eventos se detuvo de forma ordenada.",
                "Todas las aplicaciones y servicios guardaron su información y cerraron limpiamente.",
                "El usuario o una actualización programada solicitó apagar o reiniciar el equipo.",
                "Ninguna acción requerida; es el comportamiento estándar de apagado de Windows."));

        // 5. User32 1074: apagado o reinicio iniciado por el usuario o un programa
        registerKnowledge("user32", "1074", new EventKnowledge(
                "Apagado o reinicio ordenado",
                "Un usuario o una aplicación solicitó de manera controlada el apagado o reinicio del PC.",
                "Fue una orden de cierre planificada que dio tiempo a los programas de guardar cambios.",
                "El usuario hizo clic en 'Apagar/Reiniciar' o Windows Update programó el reinicio para completar una instalación.",
                "Ninguna acción requerida; revisa el detalle técnico si deseas ver el usuario o proceso solicitante."));

        // 6. Application Error 1000: una aplicación se cerró por un error
        registerKnowledge("applicationerror", "1000", new EventKnowledge(
                "Cierre inesperado de una aplicación",
                "Un programa en ejecución falló repentinamente y Windows tuvo que finalizarlo.",
                "El proceso encontró una excepción no controlada, un fallo de acceso a memoria o una librería incompatible.",
                "Error en el código del software, archivo DLL ausente o corrupto, o conflicto con controladores.",
                "Actualiza el programa a su versión más reciente, repáralo desde Panel de Control o comprueba las librerías de Microsoft Visual C++."));

        // 7. Service Control Manager 7034: un servicio terminó inesperadamente
        registerKnowledge("servicecontrolmanager", "7034", new EventKnowledge(
                "Servicio detenido inesperadamente",
                "Un servicio de Windows que operaba en segundo plano finalizó de manera anómala.",
                "El proceso asociado al servicio se interrumpió sin notificar al Administrador de Servicios de Windows.",
                "Fallo imprevisto del servicio, falta de recursos del sistema o finalización forzada por un antivirus o proceso externo.",
                "Revisa en el detalle técnico el nombre exacto del servicio. Si es de una aplicación de terceros, puedes reinstalarla o cambiar su inicio a Manual."));

        // 8. DistributedCOM 10016: aviso de permisos, normalmente inofensivo
        registerKnowledge("distributedcom", "10016", new EventKnowledge(
                "Aviso de permisos DCOM (Inofensivo)",
                "Una aplicación solicitó permisos de activación local en un componente COM y no los tenía asignados de forma explícita.",
                "Es un evento rutinario de seguridad interna de Windows, completamente inofensivo en el 99% de las ocasiones.",
                "Diseño predeterminado de Windows donde ciertos servicios solicitan componentes COM sin requerir privilegios elevados.",
                "No requiere ninguna acción. Microsoft documenta oficialmente que estos avisos no afectan el rendimiento ni la estabilidad."));

        // 9. WindowsUpdateClient 19: actualización instalada correctamente
        registerKnowledge("windowsupdateclient", "19", new EventKnowledge(
                "Actualización instalada con éxito",
                "Windows Update completó la instalación satisfactoria de un paquete de software, controlador o parche de seguridad.",
                "El sistema operativo se actualizó con nuevas mejoras y protecciones.",
                "Ejecución del ciclo habitual de mantenimiento de Windows Update.",
                "Ninguna acción requerida; tu equipo se encuentra actualizado y protegido."));

        // 10. WindowsUpdateClient 20: actualización con fallo
        registerKnowledge("windowsupdateclient", "20", new EventKnowledge(
                "Error en actualización de Windows",
                "Windows Update no pudo aplicar una actualización y la operación falló.",
                "El paquete descargado tuvo un conflicto con archivos existentes, espacio insuficiente o servicios detenidos.",
                "Falta de espacio en disco, servicio de Windows Update con errores o archivos temporales de descarga corruptos.",
                "Usa la Herramienta 2 (Espacio y Limpieza) para liberar almacenamiento o corre el Solucionador de problemas de Windows Update."));

        // 11. Ntfs 55: posible daño en el sistema de archivos del disco
        registerKnowledge("ntfs", "55", new EventKnowledge(
                "Alerta en sistema de archivos NTFS",
                "Windows detectó una posible inconsistencia o estructura dañada en la unidad de almacenamiento.",
                "La tabla maestra de archivos (MFT) o los índices de disco contienen sectores o metadatos no sincronizados.",
                "Apagados bruscos mientras se realizaban operaciones de escritura en disco o caídas eléctricas.",
                "Haz un respaldo de tus datos inmediatamente y programa un análisis de disco ejecutando 'chkdsk' en modo de solo lectura."));

        // 12. Disk 7: posible bloque defectuoso en el disco
        registerKnowledge("disk", "7", new EventKnowledge(
                "Bloque defectuoso en disco",
                "El controlador del dispositivo de almacenamiento informó de un bloque defectuoso durante una operación de lectura o escritura.",
                "Una celda física del SSD o un sector del disco duro presentó errores de integridad electromagnética.",
                "Envejecimiento natural de la unidad, desgaste de celdas flash o fallo físico de hardware.",
                "Realiza una copia de seguridad urgente de tu información crítica y evalúa la salud SMART de la unidad con herramientas del fabricante."));

        // Eventos adicionales comunes de estabilidad
        registerKnowledge("dhcpclient", "1001", new EventKnowledge(
                "Asignación de IP por DHCP renovada",
                "El adaptador de red renovó satisfactoriamente su dirección IP asignada por el router.",
                "El equipo mantiene comunicación normal con el servidor DHCP local.",
                "Expiración de la concesión DHCP o reconexión a la red Wi-Fi/cable.",
                "Ninguna acción requerida; la red funciona con normalidad."));
        registerKnowledge("dnsclientevents", "1014", new EventKnowledge(
                "Demora en consulta DNS",
                "Una consulta para resolver un nombre de dominio tardó más de lo esperado en responder.",
                "El servidor DNS del router o proveedor tuvo una breve demora.",
                "Microcorte temporal de internet o congestión momentánea.",
                "Si navegas bien, es un evento aislado. Si persiste, prueba cambiar el DNS en tu conexión a 1.1.1.1 o 8.8.8.8."));
    }

    private static void registerKnowledge(String provider, String eventId, EventKnowledge k) {
        EVENT_KNOWLEDGE_DICT.put(normalizeKey(provider, eventId), k);
    }

    private static String normalizeKey(String provider, String eventId) {
        String p = (provider == null) ? ""
                : provider.toLowerCase()
                        .replace("microsoft-windows-", "")
                        .replace("microsoft", "")
                        .replace("windows", "")
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("_", "")
                        .trim();
        return p + ":" + (eventId == null ? "" : eventId.trim());
    }

    /**
     * Consulta los registros de eventos de Windows y devuelve una lista procesada.
     */
    public List<SystemEventItem> queryEvents(int maxCount, AtomicBoolean cancelFlag) {
        List<SystemEventItem> list = new ArrayList<>();
        list.addAll(readLog("System", maxCount / 2, cancelFlag));
        list.addAll(readLog("Application", maxCount / 2, cancelFlag));

        // Ordenar del más reciente al más antiguo
        list.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
        return list;
    }

    /**
     * Analiza el registro para responder "¿Por qué se reinició mi PC?".
     */
    public RebootExplanation explainLastReboot() {
        RebootExplanation explanation = new RebootExplanation();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    "wevtutil qe System /q:\"*[System[(EventID=41 or EventID=1074 or EventID=6008 or EventID=6006)]]\" /c:1 /rd:true /f:text");
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            String text = sb.toString();
            if (!text.isBlank()) {
                explanation.found = true;
                String eventId = extractField(text, "Event ID:");
                String date = extractField(text, "Date:");
                explanation.eventId = eventId;
                explanation.timestamp = date.isEmpty() ? "Reciente" : date;

                if ("41".equals(eventId) || "6008".equals(eventId)) {
                    explanation.wasClean = false;
                    explanation.causeSummary = "Apagado repentino o corte de energía (No ordenado)";
                    explanation.detail = "El PC se apagó bruscamente en " + explanation.timestamp +
                            ". Las causas más comunes son cortes eléctricos, sobrecalentamiento térmico o mantener presionado el botón de encendido.";
                } else if ("1074".equals(eventId)) {
                    explanation.wasClean = true;
                    explanation.causeSummary = "Reinicio o apagado ordenado del sistema";
                    explanation.detail = "El sistema fue reiniciado de manera limpia en " + explanation.timestamp +
                            ". Ocurrió por solicitud del usuario o debido a una actualización completada.";
                } else if ("6006".equals(eventId)) {
                    explanation.wasClean = true;
                    explanation.causeSummary = "Apagado limpio registrado";
                    explanation.detail = "El sistema se cerró correctamente en " + explanation.timestamp + ".";
                }
            }
        } catch (Exception e) {
            explanation.causeSummary = "No fue posible consultar el historial de reinicio: " + e.getMessage();
        }

        return explanation;
    }

    private List<SystemEventItem> readLog(String logName, int count, AtomicBoolean cancelFlag) {
        List<SystemEventItem> items = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    String.format("wevtutil qe %s /c:%d /rd:true /f:text", logName, count));
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String currentId = "";
                String currentDate = "";
                String currentSource = "";
                String currentLevel = "";
                String currentUser = "";
                String currentComputer = "";
                StringBuilder msg = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (cancelFlag != null && cancelFlag.get())
                        break;

                    line = line.trim();
                    if (line.startsWith("Event[")) {
                        if (!currentId.isEmpty()) {
                            items.add(buildItem(currentId, logName, currentSource, currentLevel, currentDate,
                                    currentUser, currentComputer, msg.toString()));
                        }
                        currentId = "";
                        currentDate = "";
                        currentSource = "";
                        currentLevel = "";
                        currentUser = "";
                        currentComputer = "";
                        msg = new StringBuilder();
                    } else if (line.startsWith("Event ID:")) {
                        currentId = line.substring("Event ID:".length()).trim();
                    } else if (line.startsWith("Date:")) {
                        currentDate = line.substring("Date:".length()).trim();
                    } else if (line.startsWith("Source:")) {
                        currentSource = line.substring("Source:".length()).trim();
                    } else if (line.startsWith("Level:")) {
                        currentLevel = line.substring("Level:".length()).trim();
                    } else if (line.startsWith("User:")) {
                        currentUser = line.substring("User:".length()).trim();
                    } else if (line.startsWith("Computer:")) {
                        currentComputer = line.substring("Computer:".length()).trim();
                    } else if (line.startsWith("Description:")) {
                        msg.append(line.substring("Description:".length()).trim()).append("\n");
                    } else if (!line.contains(":") && !line.isEmpty() && msg.length() < 1200) {
                        msg.append(line).append(" ");
                    }
                }

                if (!currentId.isEmpty()) {
                    items.add(buildItem(currentId, logName, currentSource, currentLevel, currentDate, currentUser,
                            currentComputer, msg.toString()));
                }
            }
        } catch (Exception ignored) {
        }

        return items;
    }

    private SystemEventItem buildItem(String id, String logName, String source, String level,
            String date, String user, String computer, String rawDesc) {
        EventSeverity severity = EventSeverity.INFO;
        String l = level.toLowerCase();
        if (l.contains("error") || l.contains("crítico") || l.contains("critical")) {
            severity = EventSeverity.ERROR;
        } else if (l.contains("advertencia") || l.contains("warning")) {
            severity = EventSeverity.WARNING;
        }

        String key = normalizeKey(source, id);
        EventKnowledge k = EVENT_KNOWLEDGE_DICT.get(key);

        // Intento secundario por ID solo si el origen comparte el número
        if (k == null) {
            for (Map.Entry<String, EventKnowledge> entry : EVENT_KNOWLEDGE_DICT.entrySet()) {
                if (entry.getKey().endsWith(":" + id)) {
                    k = entry.getValue();
                    break;
                }
            }
        }

        String humanTitle;
        String whatHappened;
        String whatItMeans;
        String possibleCause;
        String whatYouCanDo;
        String cleanDesc = rawDesc.trim();

        if (k != null) {
            humanTitle = k.humanTitle;
            whatHappened = k.whatHappened;
            whatItMeans = k.whatItMeans;
            possibleCause = k.possibleCause;
            whatYouCanDo = k.whatYouCanDo;
        } else {
            // Respaldo genérico según gravedad y origen
            humanTitle = "Evento #" + id + " (" + (source.isEmpty() ? logName : source) + ")";
            whatHappened = "Se registró un evento de gravedad " + severity.getLabel().toLowerCase()
                    + " emitido por el proveedor '" + (source.isEmpty() ? logName : source) + "'.";
            whatItMeans = "El componente de Windows o la aplicación notificó un cambio de estado o aviso operativo en el registro del sistema.";
            possibleCause = severity == EventSeverity.ERROR
                    ? "Una tarea no pudo finalizar correctamente o un archivo/recurso no estuvo disponible a tiempo."
                    : (severity == EventSeverity.WARNING
                            ? "Una condición inesperada ocurrió, aunque el sistema pudo continuar operando."
                            : "Notificación rutinaria del sistema operativo informando sobre el funcionamiento normal.");
            whatYouCanDo = severity == EventSeverity.ERROR
                    ? "Revisa el Detalle técnico (plegado) para examinar el mensaje original o el módulo específico afectado."
                    : "Si el equipo funciona con agilidad y sin bloqueos, no se requiere ninguna acción.";
        }

        String techSummary = String.format(
                "Proveedor / Origen: %s\nID de Evento: %s\nRegistro: %s | Nivel: %s\nFecha y Hora: %s\nUsuario: %s | Equipo: %s\nMensaje original:\n%s",
                source.isEmpty() ? logName : source, id, logName, severity.getLabel(), date,
                user.isEmpty() ? "No especificado" : user,
                computer.isEmpty() ? "Silver" : computer,
                cleanDesc.isEmpty() ? "(Sin mensaje adicional)" : cleanDesc);

        return new SystemEventItem(
                id, logName, source, severity, date,
                humanTitle, whatHappened, whatItMeans, possibleCause, whatYouCanDo,
                cleanDesc, user, computer, techSummary);
    }

    private String extractField(String text, String prefix) {
        int idx = text.indexOf(prefix);
        if (idx == -1)
            return "";
        int end = text.indexOf("\n", idx);
        if (end == -1)
            end = text.length();
        return text.substring(idx + prefix.length(), end).trim();
    }

    public DiagnosticResult toDiagnosticResult(List<SystemEventItem> events, RebootExplanation reboot) {
        long errorCount = events.stream().filter(e -> e.severity == EventSeverity.ERROR).count();
        long warnCount = events.stream().filter(e -> e.severity == EventSeverity.WARNING).count();

        boolean ok = errorCount == 0 && (reboot == null || reboot.wasClean);
        String badge = ok ? ToolStatus.OK.getDisplayText()
                : (errorCount > 3 ? ToolStatus.ERROR.getDisplayText() : ToolStatus.ATENCION.getDisplayText());

        String summary = String.format(
                "Monitor de Eventos: %d analizados (%d errores, %d advertencias). Último reinicio: %s.",
                events.size(), errorCount, warnCount, reboot != null ? reboot.causeSummary : "Normal");

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Total Eventos Analizados", String.valueOf(events.size()));
        tele.put("Errores Registrados", String.valueOf(errorCount));
        tele.put("Advertencias Registradas", String.valueOf(warnCount));
        if (reboot != null) {
            tele.put("Causa Último Reinicio", reboot.causeSummary);
            tele.put("Fecha del Reinicio", reboot.timestamp);
        }

        List<String> audit = new ArrayList<>();
        if (reboot != null) {
            audit.add("ANÁLISIS DE REINICIO: " + reboot.detail);
        }
        for (SystemEventItem item : events) {
            if (item.severity == EventSeverity.ERROR || item.severity == EventSeverity.WARNING) {
                audit.add(String.format("[%s] ID %s (%s): %s - %s",
                        item.severity.getLabel(), item.id, item.logName, item.humanTitle, item.timestamp));
            }
        }

        return new DiagnosticResult(ok, badge, summary, tele, audit);
    }
}
