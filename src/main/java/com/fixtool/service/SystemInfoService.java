package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.*;

/**
 * Servicio de recopilación y diagnóstico de hardware y sistema (Herramienta 1: Chequeo de Estado).
 * Obtiene métricas sin requerir permisos de administrador ni conexión a internet.
 */
public class SystemInfoService {

    public static class HardwareSnapshot {
        public String osName = "No disponible";
        public String osVersion = "No disponible";
        public String osBuild = "No disponible";
        public String osArch = "No disponible";
        public String hostName = "No disponible";
        public String currentUser = "No disponible";

        public String cpuModel = "No disponible";
        public int cpuCores = 0;
        public double cpuLoadPct = -1.0;

        public long totalRamBytes = 0;
        public long usedRamBytes = 0;
        public double ramUsagePct = 0.0;

        public List<DiskInfo> disks = new ArrayList<>();
        public long primaryDiskTotalBytes = 0;
        public long primaryDiskFreeBytes = 0;
        public double primaryDiskFreePct = 100.0;

        public long uptimeMillis = 0;
        public int uptimeDays = 0;

        public boolean batteryPresent = false;
        public int batteryPercent = -1;
        public String batteryStatus = "No disponible";
        public int batteryWearPct = 0;

        public int healthScore = 100;
        public ToolStatus healthStatus = ToolStatus.OK;
        public List<String> healthPenalties = new ArrayList<>();
    }

    public static class DiskInfo {
        public String mountPoint;
        public long totalBytes;
        public long freeBytes;
        public double freePct;

        public DiskInfo(String mountPoint, long totalBytes, long freeBytes) {
            this.mountPoint = mountPoint;
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.freePct = totalBytes > 0 ? ((double) freeBytes / totalBytes) * 100.0 : 0.0;
        }
    }

    /**
     * Recopila un snapshot completo del sistema y calcula la puntuación de salud.
     */
    public HardwareSnapshot collectSnapshot() {
        HardwareSnapshot snap = new HardwareSnapshot();

        // 1. Datos del Sistema Operativo
        snap.osName = System.getProperty("os.name", "Windows");
        snap.osVersion = System.getProperty("os.version", "No disponible");
        snap.osArch = System.getProperty("os.arch", "No disponible");
        snap.currentUser = System.getProperty("user.name", "Usuario");

        try {
            snap.hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            snap.hostName = System.getenv("COMPUTERNAME") != null ? System.getenv("COMPUTERNAME") : "PC-Local";
        }

        snap.osBuild = queryWindowsBuild();

        // 2. CPU
        snap.cpuCores = Runtime.getRuntime().availableProcessors();
        snap.cpuModel = System.getenv("PROCESSOR_IDENTIFIER") != null
                ? System.getenv("PROCESSOR_IDENTIFIER")
                : System.getenv("PROCESSOR_ARCHITECTURE") != null
                    ? "Procesador " + System.getenv("PROCESSOR_ARCHITECTURE")
                    : "x86/x64 (" + snap.cpuCores + " núcleos)";

        // 3. RAM y Carga de CPU mediante com.sun.management.OperatingSystemMXBean
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        boolean ramLoaded = false;

        // Opción A: Cast directo a com.sun.management.OperatingSystemMXBean (interfaz pública oficial)
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            try {
                long total = sunBean.getTotalMemorySize();
                long free = sunBean.getFreeMemorySize();
                double cpuLoad = sunBean.getCpuLoad();

                if (total > 0) {
                    snap.totalRamBytes = total;
                    snap.usedRamBytes = Math.max(0, total - free);
                    snap.ramUsagePct = ((double) snap.usedRamBytes / total) * 100.0;
                    snap.cpuLoadPct = cpuLoad >= 0 ? cpuLoad * 100.0 : -1.0;
                    ramLoaded = true;
                }
            } catch (Throwable ignored) {}
        }

        // Opción B: Fallback en Windows consultando Win32_OperatingSystem si el MXBean no estuvo disponible
        if (!ramLoaded) {
            long[] winMem = queryWindowsMemory();
            if (winMem != null && winMem[0] > 0) {
                snap.totalRamBytes = winMem[0];
                snap.usedRamBytes = Math.max(0, winMem[0] - winMem[1]);
                snap.ramUsagePct = ((double) snap.usedRamBytes / snap.totalRamBytes) * 100.0;
                snap.cpuLoadPct = -1.0;
                ramLoaded = true;
            }
        }

        // 4. Discos
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                long total = root.getTotalSpace();
                long free = root.getUsableSpace();
                if (total > 0) {
                    DiskInfo disk = new DiskInfo(root.getAbsolutePath(), total, free);
                    snap.disks.add(disk);
                    if (snap.primaryDiskTotalBytes == 0 || root.getAbsolutePath().toUpperCase().startsWith("C")) {
                        snap.primaryDiskTotalBytes = total;
                        snap.primaryDiskFreeBytes = free;
                        snap.primaryDiskFreePct = disk.freePct;
                    }
                }
            }
        }

        // 5. Uptime (tiempo encendido)
        try {
            snap.uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            snap.uptimeDays = (int) (snap.uptimeMillis / (1000L * 60 * 60 * 24));
        } catch (Exception e) {
            snap.uptimeMillis = 0;
            snap.uptimeDays = 0;
        }

        // 6. Batería (si existe)
        queryBatteryInfo(snap);

        // 7. Puntuación de salud (0-100) según reglas configurables
        calculateHealth(snap);

        return snap;
    }

    /**
     * Calcula la puntuación de salud (0-100) basada en los umbrales de AppConfig.
     */
    private void calculateHealth(HardwareSnapshot snap) {
        int score = 100;
        snap.healthPenalties.clear();

        // Regla: RAM
        if (snap.ramUsagePct > 85.0) {
            score -= AppConfig.HEALTH_PENALTY_MEM_HIGH;
            snap.healthPenalties.add("Memoria RAM en uso muy alta (> 85%)");
        } else if (snap.ramUsagePct > 70.0) {
            score -= AppConfig.HEALTH_PENALTY_MEM_MEDIUM;
            snap.healthPenalties.add("Memoria RAM con uso elevado (> 70%)");
        }

        // Regla: Espacio en disco principal
        if (snap.primaryDiskFreePct < 10.0) {
            score -= AppConfig.HEALTH_PENALTY_DISK_LOW;
            snap.healthPenalties.add("Espacio libre en disco crítico (< 10%)");
        } else if (snap.primaryDiskFreePct < 20.0) {
            score -= AppConfig.HEALTH_PENALTY_DISK_MEDIUM;
            snap.healthPenalties.add("Espacio libre en disco bajo (< 20%)");
        }

        // Regla: Tiempo encendido
        if (snap.uptimeDays > 14) {
            score -= AppConfig.HEALTH_PENALTY_UPTIME;
            snap.healthPenalties.add("El equipo lleva más de 14 días encendido sin reiniciar");
        }

        // Regla: Batería (si existe)
        if (snap.batteryPresent && snap.batteryWearPct > 40) {
            score -= AppConfig.HEALTH_PENALTY_BATTERY;
            snap.healthPenalties.add("Desgaste notable de batería (> 40%)");
        }

        snap.healthScore = Math.max(0, Math.min(100, score));

        if (snap.healthScore >= AppConfig.HEALTH_THRESHOLD_GREEN) {
            snap.healthStatus = ToolStatus.OK;
        } else if (snap.healthScore >= AppConfig.HEALTH_THRESHOLD_YELLOW) {
            snap.healthStatus = ToolStatus.ATENCION;
        } else {
            snap.healthStatus = ToolStatus.PROBLEMA;
        }
    }

    /**
     * Consulta no bloqueante de compilación de Windows mediante powershell o registro.
     */
    private String queryWindowsBuild() {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "ver").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("[") && line.contains("]")) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return System.getProperty("os.version", "Compilación estándar");
    }

    /**
     * Consulta el estado de batería en Windows si existe.
     */
    private void queryBatteryInfo(HardwareSnapshot snap) {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "powershell -NoProfile -Command \"Get-CimInstance Win32_Battery | Select-Object -ExpandProperty EstimatedChargeRemaining\"").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    int val = Integer.parseInt(line.trim());
                    snap.batteryPresent = true;
                    snap.batteryPercent = val;
                    snap.batteryStatus = val + "% disponible";
                    return;
                }
            }
        } catch (Exception ignored) {}

        // En PCs de escritorio o sin batería
        snap.batteryPresent = false;
        snap.batteryPercent = -1;
        snap.batteryStatus = "No disponible (Equipo conectado a corriente directa)";
    }

    /**
     * Convierte el snapshot en un DiagnosticResult estándar para auditoría e informe.
     */
    public DiagnosticResult toDiagnosticResult(HardwareSnapshot snap) {
        boolean ok = snap.healthStatus != ToolStatus.PROBLEMA;
        String badge = snap.healthStatus.getDisplayText();
        String summary = String.format("Puntuación de salud: %d/100. CPU: %d núcleos, RAM: %.1f%% en uso, Disco C: %.1f%% libre.",
                snap.healthScore, snap.cpuCores, snap.ramUsagePct, snap.primaryDiskFreePct);

        Map<String, String> telemetry = new LinkedHashMap<>();
        telemetry.put("Puntuación de Salud", snap.healthScore + " / 100");
        telemetry.put("Estado Global", snap.healthStatus.getDisplayText());
        telemetry.put("Sistema Operativo", snap.osName + " (" + snap.osBuild + ")");
        telemetry.put("Equipo / Usuario", snap.hostName + " / " + snap.currentUser);
        telemetry.put("Procesador", snap.cpuModel + " [" + snap.cpuCores + " núcleos]");
        telemetry.put("Memoria RAM Total", formatGb(snap.totalRamBytes));
        telemetry.put("Memoria RAM Usada", formatGb(snap.usedRamBytes) + " (" + String.format(Locale.US, "%.1f%%", snap.ramUsagePct) + ")");
        telemetry.put("Disco Principal Libre", formatBytes(snap.primaryDiskFreeBytes) + " de " + formatBytes(snap.primaryDiskTotalBytes));
        telemetry.put("Tiempo Encendido", formatUptime(snap.uptimeMillis));
        telemetry.put("Batería", snap.batteryStatus);

        List<String> auditPoints = new ArrayList<>();
        if (snap.healthPenalties.isEmpty()) {
            auditPoints.add("Todos los componentes evaluados se encuentran en rangos óptimos.");
        } else {
            auditPoints.addAll(snap.healthPenalties);
        }

        return new DiagnosticResult(ok, badge, summary, telemetry, auditPoints);
    }

    /**
     * Fallback para consultar memoria física total y libre directamente a Windows en KB.
     * Retorna [totalBytes, freeBytes] o null.
     */
    private long[] queryWindowsMemory() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "powershell -NoProfile -Command \"Get-CimInstance Win32_OperatingSystem | ForEach-Object { '' + $_.TotalVisibleMemorySize + '|' + $_.FreePhysicalMemory }\"").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 2) {
                            long totalKb = Long.parseLong(parts[0].trim());
                            long freeKb = Long.parseLong(parts[1].trim());
                            return new long[]{ totalKb * 1024L, freeKb * 1024L };
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static final long ONE_GB = 1024L * 1024L * 1024L;

    public static double bytesToGb(long bytes) {
        return (double) bytes / ONE_GB;
    }

    public static String formatGb(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytesToGb(bytes));
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatUptime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / (24 * 3600);
        long hours = (seconds % (24 * 3600)) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return String.format("%d d, %d h, %d min", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d h, %d min", hours, minutes);
        } else {
            return String.format("%d min", minutes);
        }
    }
}
