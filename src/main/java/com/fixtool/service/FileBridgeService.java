package com.fixtool.service;

import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Servicio de exploración y transferencia bidireccional con verificación SHA-256.
 * Herramienta 5: Puente de archivos.
 */
public class FileBridgeService {

    public static class BridgeFileItem {
        public String name;
        public String absolutePath;
        public long sizeBytes;
        public long lastModified;
        public boolean isDirectory;

        public BridgeFileItem(String name, String absolutePath, long sizeBytes, long lastModified, boolean isDirectory) {
            this.name = name;
            this.absolutePath = absolutePath;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
            this.isDirectory = isDirectory;
        }
    }

    public static class TransferRecord {
        public String fileName;
        public String direction; // "Subida (PC → BOSGAME)" o "Descarga (BOSGAME → PC)"
        public long sizeBytes;
        public String timestamp;
        public String sha256Source;
        public String sha256Dest;
        public boolean integrityMatch;
        public String statusText;

        public TransferRecord(String fileName, String direction, long sizeBytes, String sha256Source, String sha256Dest, boolean integrityMatch) {
            this.fileName = fileName;
            this.direction = direction;
            this.sizeBytes = sizeBytes;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.sha256Source = sha256Source;
            this.sha256Dest = sha256Dest;
            this.integrityMatch = integrityMatch;
            this.statusText = integrityMatch ? "Idénticos ✔" : "Error de Integridad ✘";
        }
    }

    public static class TransferProgress {
        public double percent;
        public double speedMBps;
        public double secondsRemaining;
        public long bytesTransferred;
        public long totalBytes;
    }

    private final List<TransferRecord> sessionHistory = new ArrayList<>();

    public List<TransferRecord> getSessionHistory() {
        return Collections.unmodifiableList(sessionHistory);
    }

    /**
     * Verifica si una ruta es de red UNC (\\host\share).
     */
    public static boolean isUncPath(Path p) {
        if (p == null) return false;
        String s = p.toString();
        return s.startsWith("\\\\") || s.startsWith("//");
    }

    /**
     * Extrae el nombre de host o dirección IP de una ruta UNC.
     */
    public static String extractHostFromUnc(String unc) {
        if (unc == null) return "";
        String s = unc.replace('/', '\\');
        if (s.startsWith("\\\\")) s = s.substring(2);
        int slash = s.indexOf('\\');
        return slash != -1 ? s.substring(0, slash) : s;
    }

    /**
     * Comprueba rápidamente si el host remoto responde en el puerto SMB (445 o 139)
     * con un timeout corto para no congelar hilos si la red está caída o el equipo apagado.
     */
    public static boolean isHostReachable(String host, int timeoutMs) {
        if (host == null || host.isBlank()) return false;
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")) return true;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, 445), timeoutMs);
            return true;
        } catch (Exception e) {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, 139), Math.min(timeoutMs, 500));
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * Lista los elementos de una carpeta (local o UNC remota).
     * Lanza IOException con mensaje descriptivo si el recurso remoto no responde o es inaccesible.
     */
    public List<BridgeFileItem> listFiles(Path dir) throws IOException {
        List<BridgeFileItem> items = new ArrayList<>();
        if (dir == null) {
            return items;
        }

        // Si es ruta remota de red UNC, validar disponibilidad del host antes de invocar mrxsmb
        if (isUncPath(dir)) {
            String host = extractHostFromUnc(dir.toString());
            if (!isHostReachable(host, 1500)) {
                throw new IOException("No se puede alcanzar el equipo remoto en " + host + " (puerto 445 SMB no responde). Verifique que esté encendido y conectado a la red.");
            }
        }

        if (!Files.exists(dir)) {
            throw new IOException("La carpeta o recurso '" + dir + "' no existe o no está compartida.");
        }

        if (!Files.isDirectory(dir)) {
            throw new IOException("La ruta '" + dir + "' no es un directorio válido.");
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                try {
                    boolean isDir = Files.isDirectory(p);
                    long size = isDir ? 0 : Files.size(p);
                    long mod = Files.getLastModifiedTime(p).toMillis();
                    items.add(new BridgeFileItem(p.getFileName().toString(), p.toAbsolutePath().toString(), size, mod, isDir));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            throw new IOException("Error al leer la carpeta '" + dir + "': " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }

        items.sort((a, b) -> {
            if (a.isDirectory && !b.isDirectory) return -1;
            if (!a.isDirectory && b.isDirectory) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        return items;
    }

    /**
     * Realiza la transferencia de archivo con cálculo de SHA-256 en tiempo real.
     */
    public TransferRecord transferFile(Path source,
                                      Path destinationDir,
                                      boolean isUpload,
                                      AtomicBoolean cancelFlag,
                                      Consumer<TransferProgress> progressCallback) throws Exception {

        if (!Files.exists(source) || Files.isDirectory(source)) {
            throw new IllegalArgumentException("El archivo de origen no existe o es un directorio.");
        }

        // Validar caracteres prohibidos en nombres de Windows
        String fileName = source.getFileName().toString();
        if (fileName.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException("El nombre del archivo contiene caracteres inválidos: " + fileName);
        }

        Files.createDirectories(destinationDir);
        Path destFile = destinationDir.resolve(fileName);

        long totalSize = Files.size(source);
        MessageDigest mdSource = MessageDigest.getInstance("SHA-256");
        MessageDigest mdDest   = MessageDigest.getInstance("SHA-256");

        long startNano = System.nanoTime();
        long transferred = 0;
        byte[] buffer = new byte[64 * 1024];

        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(destFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelFlag.get()) {
                    out.close();
                    Files.deleteIfExists(destFile);
                    throw new InterruptedException("Transferencia cancelada por el usuario.");
                }

                out.write(buffer, 0, read);
                mdSource.update(buffer, 0, read);
                transferred += read;

                long now = System.nanoTime();
                double elapsedSec = Math.max(0.001, (now - startNano) / 1_000_000_000.0);
                double speedMBps = (transferred / (1024.0 * 1024.0)) / elapsedSec;
                long remainingBytes = Math.max(0, totalSize - transferred);
                double remainingSec = speedMBps > 0 ? (remainingBytes / (1024.0 * 1024.0)) / speedMBps : 0;

                if (progressCallback != null) {
                    TransferProgress tp = new TransferProgress();
                    tp.bytesTransferred = transferred;
                    tp.totalBytes = totalSize;
                    tp.percent = totalSize > 0 ? ((double) transferred / totalSize) : 1.0;
                    tp.speedMBps = speedMBps;
                    tp.secondsRemaining = remainingSec;
                    progressCallback.accept(tp);
                }
            }
            out.flush();
        }

        // Calcular SHA-256 del archivo destino para comprobación cruzada
        try (InputStream inDest = Files.newInputStream(destFile)) {
            int read;
            while ((read = inDest.read(buffer)) != -1) {
                mdDest.update(buffer, 0, read);
            }
        }

        String hashSource = bytesToHex(mdSource.digest());
        String hashDest   = bytesToHex(mdDest.digest());
        boolean match = hashSource.equalsIgnoreCase(hashDest);

        TransferRecord record = new TransferRecord(
                fileName,
                isUpload ? "Subida (PC → BOSGAME)" : "Descarga (BOSGAME → PC)",
                totalSize,
                hashSource,
                hashDest,
                match
        );

        sessionHistory.add(record);
        return record;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public DiagnosticResult toDiagnosticResult() {
        boolean ok = sessionHistory.stream().allMatch(r -> r.integrityMatch);
        ToolStatus st = sessionHistory.isEmpty() ? ToolStatus.OK : (ok ? ToolStatus.OK : ToolStatus.PROBLEMA);
        String badge = st.getDisplayText();

        String summary = String.format("Puente de archivos: %d transferencias realizadas en la sesión.", sessionHistory.size());

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Total Transferencias", String.valueOf(sessionHistory.size()));
        long totalBytes = sessionHistory.stream().mapToLong(r -> r.sizeBytes).sum();
        tele.put("Volumen Total", SystemInfoService.formatBytes(totalBytes));

        List<String> audit = new ArrayList<>();
        if (sessionHistory.isEmpty()) {
            audit.add("No se registraron transferencias en la sesión actual.");
        } else {
            for (TransferRecord r : sessionHistory) {
                audit.add(String.format("[%s] %s: %s (%s) - SHA-256: %s",
                        r.timestamp, r.direction, r.fileName, SystemInfoService.formatBytes(r.sizeBytes), r.statusText));
            }
        }

        return new DiagnosticResult(ok, badge, summary, tele, audit);
    }
}
