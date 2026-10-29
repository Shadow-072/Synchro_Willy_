package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio del Laboratorio de permisos (Herramienta 6).
 *
 * Administra el archivo de prueba controlado en
 * FixTool_Willy/prueba_permisos.txt.
 * Aplica permisos reales de Windows mediante dos mecanismos nativos:
 * 1. Atributos estándar de solo lectura / escritura (File.setWritable /
 * File.setReadOnly).
 * 2. Listas de Control de Acceso (ACL) nativas de Windows mediante
 * AclFileAttributeView
 * y la utilidad nativa icacls de Windows, sin requerir privilegios de
 * administrador.
 */
public class PermissionLabService {

    private static final String TEST_FILE_NAME = "prueba_permisos.txt";

    public static class OperationResult {
        public boolean success;
        public String message;
        public String data;

        public OperationResult(boolean success, String message, String data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    public static class ExperimentEntry {
        public String combinationName;
        public boolean allowRead;
        public boolean allowWrite;
        public boolean readExpected;
        public boolean readActual;
        public boolean writeExpected;
        public boolean writeActual;
        public boolean match;

        public ExperimentEntry(String combinationName, boolean allowRead, boolean allowWrite,
                boolean readExpected, boolean readActual,
                boolean writeExpected, boolean writeActual) {
            this.combinationName = combinationName;
            this.allowRead = allowRead;
            this.allowWrite = allowWrite;
            this.readExpected = readExpected;
            this.readActual = readActual;
            this.writeExpected = writeExpected;
            this.writeActual = writeActual;
            this.match = (readExpected == readActual) && (writeExpected == writeActual);
        }
    }

    public static class ExperimentEvidence {
        public List<ExperimentEntry> entries = new ArrayList<>();
        public boolean allMatched = true;
        public String timestamp;

        public ExperimentEvidence() {
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    /**
     * Obtiene la ruta del archivo de prueba en FixTool_Willy/prueba_permisos.txt.
     */
    public Path getTestFilePath() {
        return AppConfig.getWorkDir().resolve(TEST_FILE_NAME);
    }

    /**
     * Asegura que el archivo de prueba exista con contenido base y marca de tiempo.
     */
    public void ensureTestFileExists() throws IOException {
        Path path = getTestFilePath();
        Files.createDirectories(path.getParent());

        if (!Files.exists(path)) {
            String initialContent = "====================================================\n"
                    + "  " + AppConfig.APP_NAME + " - Archivo de Prueba de Permisos\n"
                    + "  Fecha de creación: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n"
                    + "  Usuario propietario: " + System.getProperty("user.name") + "\n"
                    + "====================================================\n"
                    + "Contenido de control: Este texto sirve para verificar\n"
                    + "si el usuario actual cuenta con permisos reales de lectura\n"
                    + "y escritura en el sistema de archivos NTFS de Windows.\n";
            Files.writeString(path, initialContent, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    /**
     * Aplica permisos reales sobre el archivo de prueba para el usuario actual.
     * Utiliza icacls nativo de Windows para restringir o habilitar permisos de
     * lectura/escritura.
     */
    public void applyPermissions(boolean allowRead, boolean allowWrite) {
        Path path = getTestFilePath();
        if (!Files.exists(path)) {
            try {
                ensureTestFileExists();
            } catch (IOException ignored) {
            }
        }

        String username = System.getProperty("user.name");
        File file = path.toFile();

        // 1. Restablecer ACLs base primero
        resetPermissions();

        // 2. Control de Escritura: File.setWritable + icacls
        if (!allowWrite) {
            file.setReadOnly();
            executeIcacls("/deny \"" + username + ":(W)\"");
        } else {
            file.setWritable(true, false);
            executeIcacls("/grant \"" + username + ":(W)\"");
        }

        // 3. Control de Lectura: denegar lectura con icacls si allowRead=false
        if (!allowRead) {
            file.setReadable(false, false);
            executeIcacls("/deny \"" + username + ":(R)\"");
        } else {
            file.setReadable(true, false);
            executeIcacls("/grant \"" + username + ":(R)\"");
        }
    }

    /**
     * Intenta leer el archivo de prueba.
     */
    public OperationResult tryRead() {
        Path path = getTestFilePath();
        if (!Files.exists(path)) {
            return new OperationResult(false, "El archivo de prueba no existe.", null);
        }

        try {
            String content = Files.readString(path);
            return new OperationResult(true, "Lectura exitosa (" + content.length() + " caracteres leídos).", content);
        } catch (AccessDeniedException e) {
            return new OperationResult(false, "Acceso denegado: El sistema operativo bloqueó la lectura del archivo.",
                    null);
        } catch (Exception e) {
            return new OperationResult(false, "Error al leer: " + e.getMessage(), null);
        }
    }

    /**
     * Intenta escribir una línea adicional con marca de tiempo en el archivo.
     */
    public OperationResult tryWrite(String customNote) {
        Path path = getTestFilePath();
        if (!Files.exists(path)) {
            try {
                ensureTestFileExists();
            } catch (IOException e) {
                return new OperationResult(false, "No se pudo crear el archivo: " + e.getMessage(), null);
            }
        }

        String line = String.format("[%s] Línea anexada: %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                (customNote == null || customNote.isBlank()) ? "Prueba de escritura manual" : customNote.trim());

        try {
            Files.writeString(path, line, StandardOpenOption.APPEND);
            return new OperationResult(true, "Escritura exitosa: se añadió una nueva línea al archivo.", line);
        } catch (AccessDeniedException e) {
            return new OperationResult(false,
                    "Acceso denegado: El archivo está bloqueado para escritura (permiso denegado).", null);
        } catch (Exception e) {
            return new OperationResult(false, "Error al escribir: " + e.getMessage(), null);
        }
    }

    /**
     * Guarda el contenido completo editado en el archivo de prueba.
     */
    public OperationResult saveFullContent(String content) {
        Path path = getTestFilePath();
        if (!Files.exists(path)) {
            try {
                ensureTestFileExists();
            } catch (IOException e) {
                return new OperationResult(false, "No se pudo preparar el archivo: " + e.getMessage(), null);
            }
        }

        try {
            Files.writeString(path, content != null ? content : "",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return new OperationResult(true, "Cambios guardados con éxito en el archivo.", content);
        } catch (AccessDeniedException e) {
            return new OperationResult(false,
                    "Acceso denegado: El archivo tiene permisos de escritura bloqueados en el sistema.", null);
        } catch (Exception e) {
            return new OperationResult(false, "Error al guardar el archivo: " + e.getMessage(), null);
        }
    }

    /**
     * Modo Experimento: Recorre automáticamente las 4 combinaciones:
     * 1. Lectura: Sí, Escritura: Sí
     * 2. Lectura: Sí, Escritura: No (Solo lectura)
     * 3. Lectura: No, Escritura: Sí (Solo escritura)
     * 4. Lectura: No, Escritura: No (Bloqueo total)
     * Restablece el archivo entre cada prueba y registra evidencia esperada vs
     * obtenida.
     */
    public ExperimentEvidence runAutomaticExperiment() {
        ExperimentEvidence evidence = new ExperimentEvidence();

        try {
            ensureTestFileExists();
        } catch (IOException ignored) {
        }

        // Combinación 1: Lectura SÍ, Escritura SÍ
        evidence.entries.add(testCombination("1. Lectura y Escritura permitidas", true, true));

        // Combinación 2: Lectura SÍ, Escritura NO
        evidence.entries.add(testCombination("2. Solo Lectura", true, false));

        // Combinación 3: Lectura NO, Escritura SÍ
        evidence.entries.add(testCombination("3. Solo Escritura", false, true));

        // Combinación 4: Lectura NO, Escritura NO
        evidence.entries.add(testCombination("4. Ningún permiso (Bloqueo total)", false, false));

        // Restablecer al finalizar el experimento
        resetPermissions();

        evidence.allMatched = evidence.entries.stream().allMatch(e -> e.match);
        return evidence;
    }

    private ExperimentEntry testCombination(String name, boolean allowRead, boolean allowWrite) {
        applyPermissions(allowRead, allowWrite);

        // Probar lectura
        OperationResult resRead = tryRead();
        boolean actualRead = resRead.success;

        // Probar escritura
        OperationResult resWrite = tryWrite("Test " + name);
        boolean actualWrite = resWrite.success;

        return new ExperimentEntry(name, allowRead, allowWrite, allowRead, actualRead, allowWrite, actualWrite);
    }

    /**
     * Restablece los permisos originales del archivo de prueba y quita bloqueos de
     * denegación.
     */
    public void resetPermissions() {
        Path path = getTestFilePath();
        if (!Files.exists(path))
            return;

        File file = path.toFile();
        file.setWritable(true, false);
        file.setReadable(true, false);

        String username = System.getProperty("user.name");
        executeIcacls("/remove:d \"" + username + "\"");
        executeIcacls("/grant \"" + username + ":(F)\"");
    }

    public void resetFilePermissions() {
        resetPermissions();
    }

    private void executeIcacls(String args) {
        try {
            Path file = getTestFilePath();
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "icacls \"" + file.toAbsolutePath() + "\" " + args);
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    public DiagnosticResult toDiagnosticResult(ExperimentEvidence evidence) {
        boolean ok = evidence != null && evidence.allMatched;
        ToolStatus st = ok ? ToolStatus.OK : ToolStatus.ATENCION;
        String badge = st.getDisplayText();

        String summary = ok
                ? "Laboratorio de permisos completado: Las 4 combinaciones verificaron con 100% de coincidencia esperada vs obtenida."
                : "Se ejecutó el laboratorio de permisos con discrepancias en alguna combinación.";

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Archivo de Prueba", getTestFilePath().toString());
        tele.put("Fecha y Hora", evidence != null ? evidence.timestamp : LocalDateTime.now().toString());

        List<String> audit = new ArrayList<>();
        if (evidence != null) {
            for (ExperimentEntry e : evidence.entries) {
                String desc = String.format("%s | Lectura (Esp:%s / Obt:%s) | Escritura (Esp:%s / Obt:%s) => %s",
                        e.combinationName,
                        e.readExpected ? "✔" : "✘", e.readActual ? "✔" : "✘",
                        e.writeExpected ? "✔" : "✘", e.writeActual ? "✔" : "✘",
                        e.match ? "CORRECTO ✔" : "DISCREPANCIA ✘");
                tele.put(e.combinationName, e.match ? "✔ Superada" : "✘ Fallida");
                audit.add(desc);
            }
        }

        return new DiagnosticResult(ok, badge, summary, tele, audit);
    }
}
