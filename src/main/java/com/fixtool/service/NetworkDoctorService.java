package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de diagnóstico paso a paso de conectividad con la BOSGAME y la LAN.
 * Herramienta 3: Análisis de Conexión.
 */
public class NetworkDoctorService {

    public static class StepResult {
        public int stepNumber;
        public String title;
        public ToolStatus status;
        public String humanExplanation;
        public String technicalDetail;

        public StepResult(int stepNumber, String title, ToolStatus status, String humanExplanation,
                String technicalDetail) {
            this.stepNumber = stepNumber;
            this.title = title;
            this.status = status;
            this.humanExplanation = humanExplanation;
            this.technicalDetail = technicalDetail;
        }
    }

    public static class DoctorDiagnosis {
        public String targetIp;
        public List<StepResult> steps = new ArrayList<>();
        public ToolStatus overallStatus = ToolStatus.OK;
        public String mainVerdict;
        public String osEstimated = "Desconocido";
        public List<String> recommendations = new ArrayList<>();
    }

    /**
     * Ejecuta el diagnóstico completo de 6 pasos.
     */
    public DoctorDiagnosis runFullDiagnostic(String targetIp) {
        DoctorDiagnosis diag = new DoctorDiagnosis();
        diag.targetIp = (targetIp != null && !targetIp.isBlank()) ? targetIp.trim() : AppConfig.DEFAULT_IP;

        // Paso 1: Cable y Adaptador
        StepResult step1 = checkAdapter();
        diag.steps.add(step1);

        // Paso 2: Dirección IP Local y APIPA
        StepResult step2 = checkLocalIp();
        diag.steps.add(step2);

        // Paso 3: Misma Red (operación bit a bit)
        StepResult step3 = checkSameSubnet(diag.targetIp);
        diag.steps.add(step3);

        // Paso 4: Puerta de Enlace (Conexión Directa)
        StepResult step4 = checkGateway();
        diag.steps.add(step4);

        // Paso 5: Respuesta del Destino (Ping y TTL)
        StepResult step5 = checkDestinationPing(diag.targetIp, diag);
        diag.steps.add(step5);

        // Paso 6: Servicio de Archivos Compartidos (Puerto 445 SMB)
        StepResult step6 = checkSmbPort(diag.targetIp);
        diag.steps.add(step6);

        // Calcular veredicto global
        computeOverallVerdict(diag);
        return diag;
    }

    // ── Paso 1: Adaptador de red ──────────────────────────────────────────────
    private StepResult checkAdapter() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            boolean hasEthernetUp = false;
            String adapterName = "Ninguno";

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {
                    String dName = ni.getDisplayName().toLowerCase();
                    if (dName.contains("ethernet") || dName.contains("lan") || dName.contains("gbe")
                            || dName.contains("realtek") || dName.contains("intel")) {
                        hasEthernetUp = true;
                        adapterName = ni.getDisplayName();
                        break;
                    }
                }
            }

            if (hasEthernetUp) {
                return new StepResult(1, "Cable y Adaptador", ToolStatus.OK,
                        "Adaptador de cable detectado y conectado.",
                        "Adaptador activo: " + adapterName);
            } else {
                // Verificar si hay algún otro adaptador activo (Wi-Fi)
                interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isUp() && !ni.isLoopback()) {
                        return new StepResult(1, "Cable y Adaptador", ToolStatus.ATENCION,
                                "Conectado por " + ni.getDisplayName() + ", pero se esperaba cable directo.",
                                "Adaptador: " + ni.getDisplayName());
                    }
                }
                return new StepResult(1, "Cable y Adaptador", ToolStatus.PROBLEMA,
                        "El cable Ethernet parece desconectado o el adaptador está apagado.",
                        "No se detectaron interfaces físicas activas (isUp=false).");
            }
        } catch (Exception e) {
            return new StepResult(1, "Cable y Adaptador", ToolStatus.PROBLEMA,
                    "No se pudo consultar el adaptador de red.",
                    "Error: " + e.getMessage());
        }
    }

    // ── Paso 2: Dirección IP Local y APIPA (169.254.x.x) ───────────────────────
    private StepResult checkLocalIp() {
        try {
            InetAddress local = getLocalActiveAddress();
            if (local == null) {
                return new StepResult(2, "Dirección IP Local", ToolStatus.PROBLEMA,
                        "Tu computador no tiene una dirección IP asignada.",
                        "Sin dirección IPv4 válida en interfaces activas.");
            }

            String ipStr = local.getHostAddress();
            if (ipStr.startsWith("169.254.")) {
                return new StepResult(2, "Dirección IP Local", ToolStatus.PROBLEMA,
                        "Dirección automática de fallo (169.254.x.x): falta configurar la IP fija para el cable directo.",
                        "IP APIPA detectada: " + ipStr + " (DHCP no disponible en cable directo).");
            }

            return new StepResult(2, "Dirección IP Local", ToolStatus.OK,
                    "Tu PC tiene la IP válida " + ipStr,
                    "IPv4 asignada: " + ipStr);
        } catch (Exception e) {
            return new StepResult(2, "Dirección IP Local", ToolStatus.PROBLEMA,
                    "Error al validar dirección IP local.",
                    e.getMessage());
        }
    }

    // ── Paso 3: Misma Red (operación bit a bit) ───────────────────────────────
    private StepResult checkSameSubnet(String targetIpStr) {
        try {
            InetAddress local = getLocalActiveAddress();
            if (local == null) {
                return new StepResult(3, "Misma Subred", ToolStatus.PROBLEMA,
                        "Imposible calcular: no hay IP local activa.",
                        "Falta IP origen.");
            }

            NetworkInterface ni = NetworkInterface.getByInetAddress(local);
            short prefixLength = 24; // Valor por defecto /24
            if (ni != null) {
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress().equals(local)) {
                        prefixLength = ia.getNetworkPrefixLength();
                        break;
                    }
                }
            }

            byte[] localBytes = local.getAddress();
            byte[] targetBytes = InetAddress.getByName(targetIpStr).getAddress();

            // Máscara a partir de prefixLength
            int mask = 0xffffffff << (32 - prefixLength);
            int localInt = bytesToInt(localBytes);
            int targetInt = bytesToInt(targetBytes);

            boolean same = (localInt & mask) == (targetInt & mask);

            if (same) {
                return new StepResult(3, "Misma Subred", ToolStatus.OK,
                        "Tu PC y la BOSGAME están en la misma red (/" + prefixLength + ").",
                        String.format("Local: %s | Destino: %s | Máscara /%d coincide bit a bit.",
                                local.getHostAddress(), targetIpStr, prefixLength));
            } else {
                return new StepResult(3, "Misma Subred", ToolStatus.PROBLEMA,
                        "Tu PC y la BOSGAME están en redes distintas. No podrán comunicarse directamente.",
                        String.format("Local: %s | Destino: %s | Máscara: /%d no coinciden.",
                                local.getHostAddress(), targetIpStr, prefixLength));
            }
        } catch (Exception e) {
            return new StepResult(3, "Misma Subred", ToolStatus.PROBLEMA,
                    "Error al calcular la subred: IP de destino no válida.",
                    e.getMessage());
        }
    }

    // ── Paso 4: Puerta de enlace (Conexión Directa) ───────────────────────────
    private StepResult checkGateway() {
        // En conexión directa por cable a la BOSGAME, no existe router ni gateway.
        // La especificación exige mostrar "No aplica (conexión directa)" como estado
        // informativo, nunca como fallo.
        return new StepResult(4, "Puerta de Enlace", ToolStatus.OK,
                "No aplica (conexión directa por cable hacia la BOSGAME).",
                "Sin enrutador intermedio: comunicación directa de punto a punto.");
    }

    // ── Paso 5: Respuesta del destino (Ping y TTL) ─────────────────────────────
    private StepResult checkDestinationPing(String targetIp, DoctorDiagnosis diag) {
        int successful = 0;
        int total = 4;
        long totalMs = 0;
        int ttl = -1;

        for (int i = 0; i < total; i++) {
            PingSample sample = executeNativePing(targetIp, 800);
            if (sample.success) {
                successful++;
                totalMs += sample.latencyMs;
                if (sample.ttl > 0)
                    ttl = sample.ttl;
            }
        }

        if (ttl > 0) {
            // Estimación del SO a partir del TTL
            if (ttl <= 64) {
                diag.osEstimated = "Linux / Unix (estimado por TTL " + ttl + ")";
            } else if (ttl <= 128) {
                diag.osEstimated = "Windows (estimado por TTL " + ttl + ")";
            } else {
                diag.osEstimated = "Cisco / Red (estimado por TTL " + ttl + ")";
            }
        }

        if (successful == total) {
            long avg = totalMs / total;
            return new StepResult(5, "Respuesta del Destino", ToolStatus.OK,
                    "La BOSGAME respondió a los 4 intentos. Latencia promedio: " + avg + " ms.",
                    "Eco ICMP exitoso 4/4. SO remoto estimado: " + diag.osEstimated);
        } else if (successful > 0) {
            return new StepResult(5, "Respuesta del Destino", ToolStatus.ATENCION,
                    "Respuesta intermitente: respondió " + successful + " de 4 intentos.",
                    "Pérdida de paquetes en el enlace.");
        } else {
            return new StepResult(5, "Respuesta del Destino", ToolStatus.PROBLEMA,
                    "La BOSGAME no responde al eco. Revisa el cable o la IP configurada.",
                    "4/4 paquetes perdidos (tiempo de espera agotado).");
        }
    }

    // ── Paso 6: Servicio de archivos compartidos (Puerto 445 SMB) ──────────────
    private StepResult checkSmbPort(String targetIp) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, 445), 1200);
            return new StepResult(6, "Carpetas Compartidas (SMB)", ToolStatus.OK,
                    "El servicio de archivos (puerto 445) está abierto y listo para transferir.",
                    "Puerto TCP 445 abierto y aceptando conexiones.");
        } catch (Exception e) {
            return new StepResult(6, "Carpetas Compartidas (SMB)", ToolStatus.ATENCION,
                    "El puerto 445 no responde. Las carpetas compartidas pueden estar desactivadas en la BOSGAME.",
                    "Fallo al conectar al puerto TCP 445: " + e.getMessage());
        }
    }

    // ── Veredicto global ──────────────────────────────────────────────────────
    private void computeOverallVerdict(DoctorDiagnosis diag) {
        boolean anyError = diag.steps.stream().anyMatch(s -> s.status == ToolStatus.PROBLEMA);
        boolean anyWarn = diag.steps.stream().anyMatch(s -> s.status == ToolStatus.ATENCION);

        diag.recommendations.clear();

        if (anyError) {
            diag.overallStatus = ToolStatus.PROBLEMA;
            diag.mainVerdict = "Hay un bloqueo en la conexión hacia la BOSGAME.";

            for (StepResult s : diag.steps) {
                if (s.status == ToolStatus.PROBLEMA) {
                    if (s.stepNumber == 1) {
                        diag.recommendations.add(
                                "Conecta firmemente el cable Ethernet en el puerto de red de tu PC y en la BOSGAME.");
                    } else if (s.stepNumber == 2) {
                        diag.recommendations.add(
                                "Asigna una dirección IP fija a tu adaptador (ejemplo: 192.168.100.2 con máscara 255.255.255.0).");
                    } else if (s.stepNumber == 3) {
                        diag.recommendations.add(
                                "Asegúrate de que la IP de tu PC comience con 192.168.100.x para coincidir con la BOSGAME.");
                    } else if (s.stepNumber == 5) {
                        diag.recommendations.add(
                                "Verifica que la BOSGAME esté encendida y que su luz de enlace de red esté parpadeando.");
                    }
                }
            }
        } else if (anyWarn) {
            diag.overallStatus = ToolStatus.ATENCION;
            diag.mainVerdict = "Hay conexión pero con advertencias en algún servicio.";
            diag.recommendations.add(
                    "El equipo responde al ping, pero verifica si el servicio Samba (SMB) está activo en la BOSGAME.");
        } else {
            diag.overallStatus = ToolStatus.OK;
            diag.mainVerdict = "¡Conexión perfecta con la BOSGAME!";
            diag.recommendations.add(
                    "La red directa y el servicio de archivos funcionan correctamente. Puedes usar Puente de archivos.");
        }
    }

    // ── Utilidades de Red ─────────────────────────────────────────────────────
    private InetAddress getLocalActiveAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address) {
                            return a;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class PingSample {
        boolean success;
        long latencyMs;
        int ttl = -1;
    }

    private PingSample executeNativePing(String ip, int timeoutMs) {
        PingSample sample = new PingSample();
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "ping -n 1 -w " + timeoutMs + " " + ip).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern timePat = Pattern.compile("(?:tiempo|time)[=<](\\d+)ms", Pattern.CASE_INSENSITIVE);
                Pattern ttlPat = Pattern.compile("TTL=(\\d+)", Pattern.CASE_INSENSITIVE);

                while ((line = r.readLine()) != null) {
                    Matcher mTime = timePat.matcher(line);
                    Matcher mTtl = ttlPat.matcher(line);

                    if (mTime.find()) {
                        sample.latencyMs = Long.parseLong(mTime.group(1));
                        sample.success = true;
                    }
                    if (mTtl.find()) {
                        sample.ttl = Integer.parseInt(mTtl.group(1));
                    }
                }
            }
        } catch (Exception e) {
            sample.success = false;
        }
        return sample;
    }

    private int bytesToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24) |
                ((b[1] & 0xFF) << 16) |
                ((b[2] & 0xFF) << 8) |
                (b[3] & 0xFF);
    }

    public DiagnosticResult toDiagnosticResult(DoctorDiagnosis diag) {
        boolean ok = diag.overallStatus == ToolStatus.OK;
        String badge = diag.overallStatus.getDisplayText();
        String summary = diag.mainVerdict + " Destino: " + diag.targetIp + ". SO remoto: " + diag.osEstimated;

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("IP Destino Evaluada", diag.targetIp);
        tele.put("Estado General", diag.overallStatus.getDisplayText());
        tele.put("SO Remoto Estimado", diag.osEstimated);

        for (StepResult s : diag.steps) {
            tele.put(s.stepNumber + ". " + s.title, s.status.getDisplayText() + " – " + s.technicalDetail);
        }

        return new DiagnosticResult(ok, badge, summary, tele, diag.recommendations);
    }
}
