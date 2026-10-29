package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Servicio para medición continua de estabilidad de conexión y prueba de velocidad real.
 * Herramienta 4: Estabilidad y velocidad.
 */
public class StabilitySpeedService {

    public static class StabilitySample {
        public double timeSeconds;
        public double latencyMs;
        public boolean lost;
        public String eventMarker; // Nota si se marcó un evento en este instante

        public StabilitySample(double timeSeconds, double latencyMs, boolean lost, String eventMarker) {
            this.timeSeconds = timeSeconds;
            this.latencyMs = latencyMs;
            this.lost = lost;
            this.eventMarker = eventMarker;
        }
    }

    public static class StabilitySummary {
        public double minMs = 9999;
        public double maxMs = 0;
        public double avgMs = 0;
        public double lossPct = 0.0;
        public double jitterMs = 0.0;
        public int totalPackets = 0;
        public int lostPackets = 0;
        public String verdict = "Sin evaluar";
        public ToolStatus status = ToolStatus.OK;
        public boolean usedPort445Fallback = false;
        public String comparisonWithPrevious = "Primera medición realizada.";
    }

    public static class SpeedResult {
        public int testSizeMb;
        public double uploadSpeedMbps;
        public double downloadSpeedMbps;
        public long linkSpeedMbps = 1000;
        public double linkUtilizationPct;
        public boolean isCableLimiting = false;
        public ToolStatus status = ToolStatus.OK;
        public String verdict;
        public String details;
    }

    /**
     * Ejecuta el monitoreo de estabilidad durante la duración especificada.
     */
    public StabilitySummary runStabilityTest(String targetIp,
                                            int durationSeconds,
                                            double intervalSeconds,
                                            AtomicBoolean cancelFlag,
                                            Consumer<StabilitySample> sampleCallback,
                                            Map<Double, String> pendingMarkers) {

        StabilitySummary summary = new StabilitySummary();
        List<Double> validLatencies = new ArrayList<>();
        double elapsed = 0.0;
        double prevLatency = -1;
        double sumJitter = 0;
        int jitterCount = 0;

        while (elapsed < durationSeconds && !cancelFlag.get()) {
            double currentTime = elapsed;
            String marker = pendingMarkers.remove(currentTime);

            // Medición de latencia
            long start = System.nanoTime();
            boolean ok = false;
            double latency = 0.0;

            try {
                // Intentar ping nativo rápido
                Process p = new ProcessBuilder("cmd.exe", "/c", "ping -n 1 -w 700 " + targetIp).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.toLowerCase().contains("ttl=")) {
                            ok = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            long durationNano = System.nanoTime() - start;
            latency = durationNano / 1_000_000.0;

            // Fallback a puerto 445 si ICMP fue bloqueado
            if (!ok) {
                try (Socket s = new Socket()) {
                    long sStart = System.nanoTime();
                    s.connect(new InetSocketAddress(targetIp, 445), 800);
                    latency = (System.nanoTime() - sStart) / 1_000_000.0;
                    ok = true;
                    summary.usedPort445Fallback = true;
                } catch (Exception ignored) {}
            }

            summary.totalPackets++;
            if (ok) {
                validLatencies.add(latency);
                if (latency < summary.minMs) summary.minMs = latency;
                if (latency > summary.maxMs) summary.maxMs = latency;

                if (prevLatency >= 0) {
                    sumJitter += Math.abs(latency - prevLatency);
                    jitterCount++;
                }
                prevLatency = latency;

                StabilitySample sample = new StabilitySample(currentTime, latency, false, marker);
                if (sampleCallback != null) sampleCallback.accept(sample);
            } else {
                summary.lostPackets++;
                StabilitySample sample = new StabilitySample(currentTime, 0, true, marker);
                if (sampleCallback != null) sampleCallback.accept(sample);
            }

            try {
                Thread.sleep((long) (intervalSeconds * 1000));
            } catch (InterruptedException e) {
                break;
            }
            elapsed += intervalSeconds;
        }

        // Cálculos finales
        if (!validLatencies.isEmpty()) {
            summary.avgMs = validLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.jitterMs = jitterCount > 0 ? (sumJitter / jitterCount) : 0.0;
        } else {
            summary.minMs = 0;
            summary.avgMs = 0;
        }

        summary.lossPct = summary.totalPackets > 0 ? ((double) summary.lostPackets / summary.totalPackets) * 100.0 : 100.0;

        // Veredicto según AppConfig
        if (summary.lossPct == AppConfig.STABILITY_EXCELLENT_LOSS_PCT && summary.avgMs <= AppConfig.STABILITY_EXCELLENT_MAX_MS) {
            summary.verdict = "Excelente: Conexión ultrarrápida y sin pérdida de paquetes.";
            summary.status = ToolStatus.OK;
        } else if (summary.lossPct <= AppConfig.STABILITY_ACCEPTABLE_LOSS_PCT && summary.avgMs <= AppConfig.STABILITY_ACCEPTABLE_MAX_MS) {
            summary.verdict = "Aceptable: Conexión operativa con fluctuaciones leves.";
            summary.status = ToolStatus.ATENCION;
        } else {
            summary.verdict = "Inestable: Pérdida de paquetes o latencia excesiva detectada.";
            summary.status = ToolStatus.PROBLEMA;
        }

        // Comparar con historial local
        summary.comparisonWithPrevious = compareWithHistory(summary.avgMs, summary.lossPct);
        saveHistory(summary.avgMs, summary.lossPct);

        return summary;
    }

    private String compareWithHistory(double avgMs, double lossPct) {
        Path histFile = AppConfig.getWorkDir().resolve("estabilidad_historico.txt");
        if (!Files.exists(histFile)) return "Primera medición registrada en el equipo.";

        try {
            List<String> lines = Files.readAllLines(histFile);
            if (!lines.isEmpty()) {
                String last = lines.get(lines.size() - 1);
                String[] parts = last.split(";");
                if (parts.length >= 1) {
                    double prevAvg = Double.parseDouble(parts[0]);
                    double diffAvg = avgMs - prevAvg;
                    if (Math.abs(diffAvg) < 1.0) {
                        return String.format("Similar a la prueba previa (%.1f ms vs %.1f ms anterior).", avgMs, prevAvg);
                    } else if (diffAvg < 0) {
                        return String.format("Mejoró %.1f ms respecto a la prueba anterior.", Math.abs(diffAvg));
                    } else {
                        return String.format("La latencia aumentó en %.1f ms respecto a la anterior.", diffAvg);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Medición guardada en el historial.";
    }

    private void saveHistory(double avgMs, double lossPct) {
        try {
            Path dir = AppConfig.getWorkDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("estabilidad_historico.txt");
            String line = String.format(Locale.US, "%.2f;%.2f;%s%n", avgMs, lossPct, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    /**
     * Prueba de velocidad de transferencia con el recurso compartido.
     */
    public SpeedResult runSpeedTest(ConnectionConfig config, int sizeMb, Consumer<Double> progressCallback) {
        SpeedResult res = new SpeedResult();
        res.testSizeMb = sizeMb;

        String unc = config.getUncPath();
        Path targetFolder = Paths.get(unc);

        if (!Files.exists(targetFolder)) {
            res.status = ToolStatus.PROBLEMA;
            res.verdict = "No se puede acceder al recurso compartido en " + unc;
            res.details = "Verifica que la carpeta compartida exista y que tengas credenciales autorizadas.";
            return res;
        }

        // Obtener velocidad negociada del adaptador
        res.linkSpeedMbps = queryNegotiatedSpeed();

        Path testFile = targetFolder.resolve("speedtest_" + UUID.randomUUID().toString().substring(0, 6) + ".tmp");
        int totalBytes = sizeMb * 1024 * 1024;
        byte[] buffer = new byte[64 * 1024];
        Arrays.fill(buffer, (byte) 0x5A);

        try {
            // 1. Medir Subida
            long startUp = System.nanoTime();
            try (OutputStream out = Files.newOutputStream(testFile)) {
                int written = 0;
                while (written < totalBytes) {
                    int chunk = Math.min(buffer.length, totalBytes - written);
                    out.write(buffer, 0, chunk);
                    written += chunk;
                    if (progressCallback != null) {
                        progressCallback.accept((double) written / (totalBytes * 2)); // 0 a 50%
                    }
                }
                out.flush();
            }
            long upTimeNano = System.nanoTime() - startUp;
            double upSec = upTimeNano / 1_000_000_000.0;
            res.uploadSpeedMbps = (totalBytes * 8.0) / (upSec * 1_000_000.0);

            // 2. Medir Bajada
            long startDown = System.nanoTime();
            try (InputStream in = Files.newInputStream(testFile)) {
                int readTotal = 0;
                while (readTotal < totalBytes) {
                    int r = in.read(buffer);
                    if (r < 0) break;
                    readTotal += r;
                    if (progressCallback != null) {
                        progressCallback.accept(0.5 + ((double) readTotal / (totalBytes * 2))); // 50% a 100%
                    }
                }
            }
            long downTimeNano = System.nanoTime() - startDown;
            double downSec = downTimeNano / 1_000_000_000.0;
            res.downloadSpeedMbps = (totalBytes * 8.0) / (downSec * 1_000_000.0);

            // Limpieza del archivo temporal de prueba
            Files.deleteIfExists(testFile);

            // Cálculo de aprovechamiento
            double maxMeasured = Math.max(res.uploadSpeedMbps, res.downloadSpeedMbps);
            res.linkUtilizationPct = (maxMeasured / res.linkSpeedMbps) * 100.0;

            if (res.linkSpeedMbps <= 100) {
                res.isCableLimiting = true;
                res.status = ToolStatus.ATENCION;
                res.verdict = "El cable o el puerto están negociando a 100 Mbps (Fast Ethernet) en lugar de 1 Gbps.";
                res.details = "Se recomienda verificar la categoría del cable Ethernet (usar Cat 5e o superior) y el conector.";
            } else {
                res.status = ToolStatus.OK;
                res.verdict = String.format("Velocidad óptima: Subida %.1f Mbps | Bajada %.1f Mbps (Aprovechamiento: %.1f%% de enlace Gigabit).",
                        res.uploadSpeedMbps, res.downloadSpeedMbps, res.linkUtilizationPct);
                res.details = "Transferencia fluida a través de la interfaz de red directa.";
            }

        } catch (Exception e) {
            try { Files.deleteIfExists(testFile); } catch (Exception ignored) {}
            res.status = ToolStatus.PROBLEMA;
            res.verdict = "Fallo en la prueba de velocidad: " + e.getMessage();
            res.details = "Error de escritura/lectura en el recurso SMB: " + e.getMessage();
        }

        return res;
    }

    private long queryNegotiatedSpeed() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "powershell -NoProfile -Command \"Get-NetAdapter | Where-Object { $_.Status -eq 'Up' -and $_.InterfaceDescription -notlike '*Virtual*' } | Select-Object -ExpandProperty LinkSpeed -First 1\"").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null) {
                    line = line.trim().toLowerCase();
                    if (line.contains("1 gbps") || line.contains("1000 mbps")) return 1000;
                    if (line.contains("100 mbps")) return 100;
                    if (line.contains("10 mbps")) return 10;
                    if (line.contains("2.5 gbps")) return 2500;
                }
            }
        } catch (Exception ignored) {}
        return 1000; // Por defecto Gigabit
    }

    public DiagnosticResult toDiagnosticResult(StabilitySummary stab, SpeedResult speed) {
        ToolStatus worst = stab != null && stab.status == ToolStatus.PROBLEMA ? ToolStatus.PROBLEMA :
                (speed != null && speed.status == ToolStatus.PROBLEMA ? ToolStatus.PROBLEMA :
                (stab != null && stab.status == ToolStatus.ATENCION ? ToolStatus.ATENCION : ToolStatus.OK));

        String badge = worst.getDisplayText();
        String summary = stab != null ? stab.verdict : (speed != null ? speed.verdict : "Evaluación de red");

        Map<String, String> tele = new LinkedHashMap<>();
        if (stab != null) {
            tele.put("Latencia Mínima", String.format("%.1f ms", stab.minMs));
            tele.put("Latencia Promedio", String.format("%.1f ms", stab.avgMs));
            tele.put("Latencia Máxima", String.format("%.1f ms", stab.maxMs));
            tele.put("Pérdida de Paquetes", String.format("%.1f%% (%d perdidos)", stab.lossPct, stab.lostPackets));
            tele.put("Variación (Jitter)", String.format("%.2f ms", stab.jitterMs));
            tele.put("Comparación Histórica", stab.comparisonWithPrevious);
        }
        if (speed != null && speed.uploadSpeedMbps > 0) {
            tele.put("Velocidad de Subida", String.format("%.1f Mbps (%.2f MB/s)", speed.uploadSpeedMbps, speed.uploadSpeedMbps / 8.0));
            tele.put("Velocidad de Bajada", String.format("%.1f Mbps (%.2f MB/s)", speed.downloadSpeedMbps, speed.downloadSpeedMbps / 8.0));
            tele.put("Enlace Negociado", speed.linkSpeedMbps + " Mbps");
            tele.put("Aprovechamiento", String.format("%.1f%%", speed.linkUtilizationPct));
        }

        List<String> audit = new ArrayList<>();
        if (stab != null) audit.add(stab.verdict);
        if (speed != null) audit.add(speed.verdict);

        return new DiagnosticResult(worst != ToolStatus.PROBLEMA, badge, summary, tele, audit);
    }
}
