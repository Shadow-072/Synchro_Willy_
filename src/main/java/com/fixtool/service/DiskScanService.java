package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Servicio de exploración de almacenamiento, mapa de espacio y limpieza segura con cuarentena.
 * Herramienta 2: Espacio y limpieza.
 */
public class DiskScanService {

    public static class FileItem {
        public String name;
        public String path;
        public long sizeBytes;
        public long lastModified;
        public boolean isDirectory;

        public FileItem(String name, String path, long sizeBytes, long lastModified, boolean isDirectory) {
            this.name = name;
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
            this.isDirectory = isDirectory;
        }

        public boolean isOlderThanOneYear() {
            long oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L);
            return lastModified < oneYearAgo;
        }
    }

    public static class FolderNode {
        public String name;
        public String path;
        public long totalBytes = 0;
        public int fileCount = 0;
        public List<FolderNode> subFolders = new ArrayList<>();
        public List<FileItem> files = new ArrayList<>();
    }

    public static class ScanResult {
        public FolderNode rootNode;
        public int inaccessibleCount = 0;
        public List<FileItem> top10Files = new ArrayList<>();
        public List<FileItem> oldFiles = new ArrayList<>();
        public long totalBytes = 0;
        public int totalFiles = 0;
        public boolean cancelled = false;
    }

    public static class QuarantineBatch {
        public String id;
        public String folderPath;
        public int fileCount;
        public long totalBytes;
        public String timestamp;

        public QuarantineBatch(String id, String folderPath, int fileCount, long totalBytes, String timestamp) {
            this.id = id;
            this.folderPath = folderPath;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.timestamp = timestamp;
        }
    }

    public static class QuarantineItem {
        public String originalPath;
        public String quarantinedPath;
        public String name;
        public long sizeBytes;
        public String dateStr;
        public boolean isDirectory;
        public String batchId;

        public QuarantineItem(String originalPath, String quarantinedPath, String name, long sizeBytes, String dateStr, boolean isDirectory, String batchId) {
            this.originalPath = originalPath;
            this.quarantinedPath = quarantinedPath;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.dateStr = dateStr;
            this.isDirectory = isDirectory;
            this.batchId = batchId;
        }
    }

    public static class MoveToQuarantineResult {
        public int movedCount = 0;
        public int failedCount = 0;
        public long bytesFreed = 0;
        public List<String> details = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
        public String batchId;
        public String quarantinePath;
    }

    public static class CleanCategory {
        public String title;
        public String description;
        public List<File> filesToClean = new ArrayList<>();
        public long totalBytes = 0;
        public boolean selected = true;

        public CleanCategory(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    public static class CleanReport {
        public boolean simulation;
        public int filesProcessed;
        public int filesSkipped;
        public long bytesFreed;
        public String quarantinePath;
        public List<String> details = new ArrayList<>();
    }

    /**
     * Escanea recursivamente un directorio para generar el mapa de bloques.
     * Reporta progreso y permite cancelación suave.
     */
    public ScanResult scanDirectory(File targetDir, AtomicBoolean cancelFlag, Consumer<Double> progressCallback) {
        ScanResult result = new ScanResult();
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
            return result;
        }

        List<FileItem> allFiles = new ArrayList<>();
        AtomicInteger inaccessible = new AtomicInteger(0);
        AtomicLong scannedBytes = new AtomicLong(0);

        FolderNode root = buildFolderNode(targetDir, cancelFlag, allFiles, inaccessible, scannedBytes, 0, 3);
        result.rootNode = root;
        result.inaccessibleCount = inaccessible.get();
        result.totalBytes = root != null ? root.totalBytes : 0;
        result.totalFiles = allFiles.size();
        result.cancelled = cancelFlag.get();

        // Top 10 archivos más pesados
        allFiles.sort((a, b) -> Long.compare(b.sizeBytes, a.sizeBytes));
        result.top10Files = allFiles.stream().limit(10).collect(java.util.stream.Collectors.toList());

        // Archivos sin uso hace más de 1 año
        result.oldFiles = allFiles.stream().filter(FileItem::isOlderThanOneYear).limit(100).collect(java.util.stream.Collectors.toList());

        return result;
    }

    private FolderNode buildFolderNode(File dir, AtomicBoolean cancelFlag, List<FileItem> allFiles,
                                       AtomicInteger inaccessible, AtomicLong scannedBytes, int depth, int maxDepth) {
        if (cancelFlag.get() || dir == null) return null;

        FolderNode node = new FolderNode();
        node.name = dir.getName().isEmpty() ? dir.getAbsolutePath() : dir.getName();
        node.path = dir.getAbsolutePath();

        File[] list = null;
        try {
            list = dir.listFiles();
        } catch (SecurityException e) {
            inaccessible.incrementAndGet();
            return node;
        }

        if (list == null) {
            inaccessible.incrementAndGet();
            return node;
        }

        long sumBytes = 0;
        int sumFiles = 0;

        for (File child : list) {
            if (cancelFlag.get()) break;

            try {
                if (child.isDirectory()) {
                    if (depth < maxDepth && !isJunctionOrSymlink(child)) {
                        FolderNode sub = buildFolderNode(child, cancelFlag, allFiles, inaccessible, scannedBytes, depth + 1, maxDepth);
                        if (sub != null) {
                            node.subFolders.add(sub);
                            sumBytes += sub.totalBytes;
                            sumFiles += sub.fileCount;
                        }
                    }
                } else if (child.isFile()) {
                    long len = child.length();
                    long mod = child.lastModified();
                    FileItem item = new FileItem(child.getName(), child.getAbsolutePath(), len, mod, false);
                    node.files.add(item);
                    allFiles.add(item);
                    sumBytes += len;
                    sumFiles++;
                    scannedBytes.addAndGet(len);
                }
            } catch (Exception e) {
                inaccessible.incrementAndGet();
            }
        }

        node.totalBytes = sumBytes;
        node.fileCount = sumFiles;
        return node;
    }

    private boolean isJunctionOrSymlink(File f) {
        try {
            return Files.isSymbolicLink(f.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Analiza las 3 categorías de limpieza:
     * 1. Archivos temporales de usuario.
     * 2. Descargas antiguas (días configurables, defecto 90).
     * 3. Pruebas e informes antiguos de Synchro Willy.
     */
    public List<CleanCategory> analyzeCleaning(int downloadDaysThreshold) {
        List<CleanCategory> categories = new ArrayList<>();

        // Categoría 1: Temporales
        CleanCategory catTemp = new CleanCategory("Archivos temporales del usuario", "Caché y archivos descartables del sistema en %TEMP%");
        String tmpPath = System.getProperty("java.io.tmpdir");
        if (tmpPath != null) {
            collectFilesUnder(new File(tmpPath), catTemp, 0, 2);
        }
        categories.add(catTemp);

        // Categoría 2: Descargas antiguas
        CleanCategory catDownloads = new CleanCategory("Descargas antiguas (> " + downloadDaysThreshold + " días)", "Archivos en tu carpeta de Descargas que no has modificado recientemente");
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.exists() && downloads.isDirectory()) {
            long thresholdMillis = System.currentTimeMillis() - (downloadDaysThreshold * 24L * 60 * 60 * 1000L);
            File[] files = downloads.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.lastModified() < thresholdMillis) {
                        catDownloads.filesToClean.add(f);
                        catDownloads.totalBytes += f.length();
                    }
                }
            }
        }
        categories.add(catDownloads);

        // Categoría 3: Synchro Willy pruebas temporales
        CleanCategory catSelf = new CleanCategory("Archivos de prueba propios", "Archivos temporales generados durante pruebas previas");
        File workDir = AppConfig.getWorkDir().toFile();
        if (workDir.exists() && workDir.isDirectory()) {
            File testFile = new File(workDir, "prueba_permisos.txt");
            if (testFile.exists() && testFile.isFile()) {
                catSelf.filesToClean.add(testFile);
                catSelf.totalBytes += testFile.length();
            }
            File testDir = new File(workDir, "test_temp");
            if (testDir.exists()) {
                collectFilesUnder(testDir, catSelf, 0, 2);
            }
        }
        categories.add(catSelf);

        return categories;
    }

    private void collectFilesUnder(File dir, CleanCategory cat, int depth, int maxDepth) {
        if (dir == null || !dir.exists() || depth > maxDepth) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isFile()) {
                cat.filesToClean.add(f);
                cat.totalBytes += f.length();
            } else if (f.isDirectory() && depth < maxDepth) {
                collectFilesUnder(f, cat, depth + 1, maxDepth);
            }
        }
    }

    /**
     * Ejecuta la limpieza. Si simulation=true, solo calcula sin mover nada.
     * Si simulation=false, mueve de forma segura a FixTool_Willy/Cuarentena/<fecha_hora>/
     */
    public CleanReport executeCleaning(List<CleanCategory> categories, boolean simulation) {
        CleanReport report = new CleanReport();
        report.simulation = simulation;

        List<File> allToClean = new ArrayList<>();
        for (CleanCategory cat : categories) {
            if (cat.selected) {
                allToClean.addAll(cat.filesToClean);
            }
        }

        if (simulation) {
            report.filesProcessed = allToClean.size();
            report.bytesFreed = allToClean.stream().mapToLong(File::length).sum();
            report.details.add(String.format("Modo Simulación: Se identificaron %d archivos (%.2f MB) listos para liberar sin riesgo.",
                    report.filesProcessed, report.bytesFreed / (1024.0 * 1024.0)));
            return report;
        }

        // Ejecución real con Cuarentena
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path quarantineRoot = AppConfig.getWorkDir().resolve("Cuarentena").resolve(timestamp);
        report.quarantinePath = quarantineRoot.toAbsolutePath().toString();

        try {
            Files.createDirectories(quarantineRoot);
        } catch (IOException e) {
            report.details.add("Error al crear carpeta de cuarentena: " + e.getMessage());
            return report;
        }

        // Archivo manifiesto para restaurar
        Path manifestPath = quarantineRoot.resolve("manifest.txt");
        List<String> manifestLines = new ArrayList<>();

        Path userHome = Paths.get(System.getProperty("user.home"));

        for (File f : allToClean) {
            Path source = f.toPath();

            // Regla de seguridad absoluta: no tocar nada fuera de user.home
            if (!source.toAbsolutePath().startsWith(userHome)) {
                report.filesSkipped++;
                continue;
            }

            try {
                // Destino en cuarentena manteniendo nombre único
                String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + f.getName();
                Path dest = quarantineRoot.resolve(safeName);

                // Mover archivo
                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                manifestLines.add(source.toAbsolutePath() + ":::" + dest.toAbsolutePath());
                report.filesProcessed++;
                report.bytesFreed += f.length();
            } catch (Exception e) {
                // Archivo en uso o bloqueado: se omite en silencio y se cuenta
                report.filesSkipped++;
            }
        }

        try {
            Files.write(manifestPath, manifestLines, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException ignored) {}

        report.details.add(String.format("Limpieza completada: %d archivos trasladados a cuarentena. Espacio liberado: %.2f MB.",
                report.filesProcessed, report.bytesFreed / (1024.0 * 1024.0)));
        if (report.filesSkipped > 0) {
            report.details.add(String.format("%d archivos omitidos por estar en uso o protegidos.", report.filesSkipped));
        }

        return report;
    }

    /**
     * Lista los lotes de cuarentena existentes.
     */
    public List<QuarantineBatch> listQuarantineBatches() {
        List<QuarantineBatch> batches = new ArrayList<>();
        Path root = AppConfig.getWorkDir().resolve("Cuarentena");
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return batches;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    int count = 0;
                    long bytes = 0;
                    try (DirectoryStream<Path> inner = Files.newDirectoryStream(p)) {
                        for (Path item : inner) {
                            if (!item.getFileName().toString().equals("manifest.txt")) {
                                count++;
                                bytes += Files.size(item);
                            }
                        }
                    } catch (Exception ignored) {}

                    batches.add(new QuarantineBatch(
                            p.getFileName().toString(),
                            p.toAbsolutePath().toString(),
                            count,
                            bytes,
                            p.getFileName().toString()
                    ));
                }
            }
        } catch (Exception ignored) {}

        batches.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
        return batches;
    }

    /**
     * Mueve elementos seleccionados libremente por el usuario a la cuarentena.
     * Aplica límites estrictos de seguridad: solo dentro de user.home,
     * nunca la carpeta de cuarentena ni la de trabajo de la app.
     */
    public MoveToQuarantineResult moveCustomItemsToQuarantine(List<File> items) {
        MoveToQuarantineResult res = new MoveToQuarantineResult();
        if (items == null || items.isEmpty()) return res;

        Path userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path workDir = AppConfig.getWorkDir().toAbsolutePath().normalize();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        res.batchId = timestamp;
        Path quarantineRoot = AppConfig.getWorkDir().resolve("Cuarentena").resolve(timestamp);
        res.quarantinePath = quarantineRoot.toAbsolutePath().toString();

        try {
            Files.createDirectories(quarantineRoot);
        } catch (IOException e) {
            res.errors.add("Error al crear carpeta de cuarentena: " + e.getMessage());
            return res;
        }

        Path manifestPath = quarantineRoot.resolve("manifest.txt");
        List<String> manifestLines = new ArrayList<>();

        for (File f : items) {
            if (f == null) continue;
            Path source = f.toPath().toAbsolutePath().normalize();

            // 1. Límite de seguridad: Solo dentro de user.home
            if (!source.startsWith(userHome)) {
                res.failedCount++;
                res.errors.add("Solo se pueden mover elementos de tu carpeta personal (" + f.getName() + " está fuera).");
                continue;
            }

            // 2. Límite de seguridad: No se puede enviar la carpeta de trabajo ni de cuarentena
            if (source.startsWith(workDir) || source.equals(workDir)) {
                res.failedCount++;
                res.errors.add("No se puede mover la carpeta de trabajo ni la cuarentena de la app (" + f.getName() + ").");
                continue;
            }

            // 3. Verificar existencia
            if (!Files.exists(source)) {
                res.failedCount++;
                res.errors.add("El elemento '" + f.getName() + "' ya no existe en disco.");
                continue;
            }

            try {
                long size = f.isDirectory() ? calculateDirSize(f) : f.length();
                String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + f.getName();
                Path dest = quarantineRoot.resolve(safeName);

                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                manifestLines.add(source.toString() + ":::" + dest.toString() + ":::" + size + ":::" + dateStr + ":::" + (f.isDirectory() ? "DIR" : "FILE"));
                res.movedCount++;
                res.bytesFreed += size;
                res.details.add("Movido a cuarentena: " + f.getName() + " (" + SystemInfoService.formatBytes(size) + ")");
            } catch (Exception e) {
                res.failedCount++;
                res.errors.add("No se pudo mover '" + f.getName() + "': archivo en uso o sin permiso suficiente (" + e.getMessage() + ").");
            }
        }

        try {
            Files.write(manifestPath, manifestLines, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException ignored) {}

        return res;
    }

    private long calculateDirSize(File dir) {
        long total = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) total += calculateDirSize(c);
                else total += c.length();
            }
        }
        return total;
    }

    /**
     * Lista todos los elementos individuales alojados en la cuarentena.
     */
    public List<QuarantineItem> listAllQuarantineItems() {
        List<QuarantineItem> items = new ArrayList<>();
        Path root = AppConfig.getWorkDir().resolve("Cuarentena");
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return items;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path batchDir : stream) {
                if (Files.isDirectory(batchDir)) {
                    Path manifest = batchDir.resolve("manifest.txt");
                    if (Files.exists(manifest)) {
                        List<String> lines = Files.readAllLines(manifest);
                        for (String line : lines) {
                            String[] parts = line.split(":::");
                            if (parts.length >= 2) {
                                String orig = parts[0];
                                String dest = parts[1];
                                File qFile = new File(dest);
                                if (!qFile.exists()) continue;

                                String name = Paths.get(orig).getFileName().toString();
                                long size = qFile.length();
                                String dateStr = "";
                                boolean isDir = qFile.isDirectory();

                                if (parts.length >= 5) {
                                    try { size = Long.parseLong(parts[2]); } catch (Exception ignored) {}
                                    dateStr = parts[3];
                                    isDir = "DIR".equalsIgnoreCase(parts[4]);
                                } else {
                                    dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                                }

                                items.add(new QuarantineItem(
                                        orig,
                                        dest,
                                        name,
                                        size,
                                        dateStr,
                                        isDir,
                                        batchDir.getFileName().toString()
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return items;
    }

    /**
     * Restaura un elemento individual a su ubicación original (o alternativa).
     */
    public boolean restoreSingleItem(QuarantineItem item, Path alternativeTargetDir) {
        Path quarantined = Paths.get(item.quarantinedPath);
        if (!Files.exists(quarantined)) return false;

        Path target = (alternativeTargetDir != null)
                ? alternativeTargetDir.resolve(item.name)
                : Paths.get(item.originalPath);

        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.move(quarantined, target, StandardCopyOption.REPLACE_EXISTING);
            removeLineFromManifest(item);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void removeLineFromManifest(QuarantineItem item) {
        try {
            Path batchDir = Paths.get(item.quarantinedPath).getParent();
            Path manifest = batchDir.resolve("manifest.txt");
            if (Files.exists(manifest)) {
                List<String> lines = Files.readAllLines(manifest);
                List<String> remaining = new ArrayList<>();
                for (String l : lines) {
                    if (!l.contains(item.quarantinedPath)) {
                        remaining.add(l);
                    }
                }
                if (remaining.isEmpty()) {
                    deleteDirectoryRecursively(batchDir.toFile());
                } else {
                    Files.write(manifest, remaining, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Restaura un lote completo de cuarentena a sus ubicaciones originales.
     */
    public int restoreBatch(QuarantineBatch batch) {
        Path manifest = Paths.get(batch.folderPath).resolve("manifest.txt");
        if (!Files.exists(manifest)) return 0;

        int restored = 0;
        try {
            List<String> lines = Files.readAllLines(manifest);
            for (String line : lines) {
                String[] parts = line.split(":::");
                if (parts.length >= 2) {
                    Path orig = Paths.get(parts[0]);
                    Path quarantined = Paths.get(parts[1]);
                    if (Files.exists(quarantined)) {
                        if (orig.getParent() != null) {
                            Files.createDirectories(orig.getParent());
                        }
                        Files.move(quarantined, orig, StandardCopyOption.REPLACE_EXISTING);
                        restored++;
                    }
                }
            }
            // Eliminar carpeta de lote si quedó vacía
            deleteDirectoryRecursively(Paths.get(batch.folderPath).toFile());
        } catch (Exception ignored) {}

        return restored;
    }

    /**
     * Borrado definitivo de toda la cuarentena (exige doble confirmación en UI).
     */
    public void purgeAllQuarantine() {
        Path root = AppConfig.getWorkDir().resolve("Cuarentena");
        if (Files.exists(root)) {
            deleteDirectoryRecursively(root.toFile());
        }
    }

    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteDirectoryRecursively(c);
                }
            }
        }
        file.delete();
    }

    public DiagnosticResult toDiagnosticResult(CleanReport report) {
        boolean ok = report.filesSkipped == 0;
        String badge = ok ? ToolStatus.OK.getDisplayText() : ToolStatus.ATENCION.getDisplayText();
        String summary = String.format("Espacio y limpieza: %d archivos procesados, %.2f MB liberados.",
                report.filesProcessed, report.bytesFreed / (1024.0 * 1024.0));

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Modo", report.simulation ? "Simulación (sin cambios en disco)" : "Ejecución real con Cuarentena");
        tele.put("Archivos Procesados", String.valueOf(report.filesProcessed));
        tele.put("Espacio Liberado", String.format("%.2f MB", report.bytesFreed / (1024.0 * 1024.0)));
        tele.put("Archivos Omitidos", String.valueOf(report.filesSkipped));
        if (report.quarantinePath != null) {
            tele.put("Carpeta de Cuarentena", report.quarantinePath);
        }

        return new DiagnosticResult(ok, badge, summary, tele, report.details);
    }
}
