package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.net.InetAddress;
import java.util.*;

/**
 * Servicio central de diagnóstico de Synchro Willy.
 * Define el catálogo oficial de las 10 herramientas.
 * En la Fase 1 todas devuelven un resultado placeholder.
 * Las implementaciones reales se añaden en Fases 2-6.
 */
public class DiagnosticService {

    private final List<DiagnosticAction> actions = new ArrayList<>();

    public DiagnosticService() {
        inicializarCatalogo();
    }

    /** Lista completa de las 10 herramientas (inmutable). */
    public List<DiagnosticAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    /** Busca una herramienta por su id (1-10). Devuelve null si no existe. */
    public DiagnosticAction getActionById(int id) {
        return actions.stream()
                .filter(a -> a.getId() == id)
                .findFirst()
                .orElse(null);
    }

    // ── Catálogo de herramientas ──────────────────────────────────────────────

    private void inicializarCatalogo() {
        // Herramienta 1: Chequeo de Estado
        actions.add(new DiagnosticAction(
                1,
                "Chequeo de Estado",
                "Cómo está tu PC",
                "Muestra el estado de tu PC con una puntuación de salud basada en CPU, RAM, disco y batería.",
                "monitor",
                "Sistema",
                cfg -> {
                    SystemInfoService sys = new SystemInfoService();
                    SystemInfoService.HardwareSnapshot snap = sys.collectSnapshot();
                    return sys.toDiagnosticResult(snap);
                }
        ));

        // Herramienta 2: Espacio y limpieza
        actions.add(new DiagnosticAction(
                2,
                "Espacio y limpieza",
                "Libera espacio sin riesgo",
                "Visualiza qué ocupa espacio en tu PC y mueve a cuarentena lo que ya no necesitas.",
                "harddrive",
                "Almacenamiento",
                cfg -> {
                    DiskScanService disk = new DiskScanService();
                    List<DiskScanService.CleanCategory> cats = disk.analyzeCleaning(90);
                    DiskScanService.CleanReport rep = disk.executeCleaning(cats, true); // Modo simulación por defecto
                    return disk.toDiagnosticResult(rep);
                }
        ));

        // Herramienta 3: Análisis de Conexión
        actions.add(new DiagnosticAction(
                3,
                "Análisis de Conexión",
                "Por qué conecta o no",
                "Revisa paso a paso si tu PC puede comunicarse con la BOSGAME y qué impide la conexión.",
                "wifi",
                "Red",
                cfg -> {
                    NetworkDoctorService net = new NetworkDoctorService();
                    String ip = cfg != null ? cfg.getIpTarget() : AppConfig.DEFAULT_IP;
                    NetworkDoctorService.DoctorDiagnosis diag = net.runFullDiagnostic(ip);
                    return net.toDiagnosticResult(diag);
                }
        ));

        // Herramienta 4: Estabilidad y velocidad
        actions.add(new DiagnosticAction(
                4,
                "Estabilidad y velocidad",
                "Qué tan buena es la conexión",
                "Mide la estabilidad en el tiempo y la velocidad real de transferencia a la BOSGAME.",
                "activity",
                "Red",
                cfg -> {
                    StabilitySpeedService stab = new StabilitySpeedService();
                    String ip = cfg != null ? cfg.getIpTarget() : AppConfig.DEFAULT_IP;
                    StabilitySpeedService.StabilitySummary sum = stab.runStabilityTest(ip, 5, 1.0, new java.util.concurrent.atomic.AtomicBoolean(false), null, new java.util.HashMap<>());
                    return stab.toDiagnosticResult(sum, null);
                }
        ));

        // Herramienta 5: Puente de archivos
        actions.add(new DiagnosticAction(
                5,
                "Puente de archivos",
                "Pasa archivos a la BOSGAME",
                "Explora y transfiere archivos entre tu PC y la BOSGAME con verificación de integridad.",
                "shuffle",
                "Transferencia",
                cfg -> {
                    FileBridgeService bridge = new FileBridgeService();
                    return bridge.toDiagnosticResult();
                }
        ));

        // Herramienta 6: Laboratorio de permisos
        actions.add(new DiagnosticAction(
                6,
                "Laboratorio de permisos",
                "Prueba leer y escribir",
                "Experimenta con los permisos de lectura y escritura sobre un archivo de prueba controlado.",
                "shield",
                "Seguridad",
                cfg -> {
                    PermissionLabService lab = new PermissionLabService();
                    PermissionLabService.ExperimentEvidence ev = lab.runAutomaticExperiment();
                    return lab.toDiagnosticResult(ev);
                }
        ));

        // Herramienta 7: Mapeo de Red Local
        actions.add(new DiagnosticAction(
                7,
                "Mapeo de Red Local",
                "Tus conexiones y dispositivos",
                "Dibuja un diagrama de todos los adaptadores de red y los dispositivos detectados.",
                "compass",
                "Red",
                cfg -> {
                    PcMapService map = new PcMapService();
                    String targetIp = cfg != null ? cfg.getIpTarget() : AppConfig.DEFAULT_IP;
                    PcMapService.MapScanResult res = map.scanNetwork(targetIp, new java.util.concurrent.atomic.AtomicBoolean(false));
                    return map.toDiagnosticResult(res);
                }
        ));

        // Herramienta 8: Monitor de Eventos
        actions.add(new DiagnosticAction(
                8,
                "Monitor de Eventos",
                "Qué le pasó a tu PC",
                "Muestra en una línea de tiempo los eventos importantes del sistema: errores, reinicios, actualizaciones.",
                "search",
                "Sistema",
                cfg -> {
                    EventDetectiveService det = new EventDetectiveService();
                    List<EventDetectiveService.SystemEventItem> evs = det.queryEvents(40, new java.util.concurrent.atomic.AtomicBoolean(false));
                    EventDetectiveService.RebootExplanation reb = det.explainLastReboot();
                    return det.toDiagnosticResult(evs, reb);
                }
        ));

        // Herramienta 9: Puertos Abiertos
        actions.add(new DiagnosticAction(
                9,
                "Puertos Abiertos",
                "Qué está expuesto en la red",
                "Lista qué puertos tiene abiertos tu PC, qué programa los usa y si deberían preocuparte.",
                "unlock",
                "Seguridad",
                cfg -> {
                    OpenPortsService ports = new OpenPortsService();
                    OpenPortsService.PortsScanResult res = ports.scanOpenPorts(new java.util.concurrent.atomic.AtomicBoolean(false));
                    return ports.toDiagnosticResult(res);
                }
        ));

        // Herramienta 10: Informe de diagnóstico
        actions.add(new DiagnosticAction(
                10,
                "Informe de diagnóstico",
                "Resumen para entregar",
                "Genera el informe HTML con los resultados de la sesión, listo para entregar.",
                "filetext",
                "Informe",
                cfg -> {
                    ReportService rep = new ReportService();
                    Set<Integer> allIds = new HashSet<>();
                    for (int i = 1; i <= 9; i++) allIds.add(i);
                    try {
                        ReportService.ReportSummary sum = rep.generateHtmlReport(this.actions, allIds, cfg);
                        return rep.toDiagnosticResult(sum);
                    } catch (Exception e) {
                        return new DiagnosticResult(false, ToolStatus.ERROR.getDisplayText(), "Error al generar informe: " + e.getMessage(), Map.of(), List.of());
                    }
                }
        ));
    }

    // ── Utilidades internas ───────────────────────────────────────────────────

    /** Obtiene el nombre del equipo de forma segura (sin elevar privilegios). */
    protected String getHostNameSafe() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // Fallback a la variable de entorno de Windows
            String computerName = System.getenv("COMPUTERNAME");
            return (computerName != null && !computerName.isBlank()) ? computerName : "PC-WINDOWS";
        }
    }
}
