package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de descubrimiento de topología y equipos en la LAN (Herramienta 7: Mapeo de Red Local).
 * Identifica adaptadores, puertas de enlace, BOSGAME y vecinos con identificación offline:
 * nombre inverso, fabricante por MAC OUI, sistema por TTL, sondeo de servicios y etiquetado manual.
 */
public class PcMapService {

    public enum DeviceType {
        ROUTER("Router / Puerta de enlace", "globe", "#38BDF8"),
        PC_WINDOWS("PC Windows", "monitor", "#4ADE80"),
        LINUX_SERVER("Equipo Linux o Servidor", "server", "#A78BFA"),
        PRINTER("Impresora", "filetext", "#FBBF24"),
        PHONE_TABLET("Teléfono o Tablet", "smartphone", "#FB7185"),
        DESCONOCIDO("Dispositivo de red local", "helpcircle", "#A7B3C2");

        private final String display;
        private final String iconKey;
        private final String colorHex;

        DeviceType(String display, String iconKey, String colorHex) {
            this.display = display;
            this.iconKey = iconKey;
            this.colorHex = colorHex;
        }

        public String getDisplay() { return display; }
        public String getIconKey() { return iconKey; }
        public String getColorHex() { return colorHex; }

        public static DeviceType fromString(String name) {
            if (name == null) return DESCONOCIDO;
            for (DeviceType dt : values()) {
                if (dt.name().equalsIgnoreCase(name) || dt.getDisplay().equalsIgnoreCase(name)) {
                    return dt;
                }
            }
            return DESCONOCIDO;
        }
    }

    public static class ManualLabel {
        public String label;
        public String type;

        public ManualLabel(String label, String type) {
            this.label = label;
            this.type = type;
        }
    }

    public static class AdapterNode {
        public String name;
        public String displayName;
        public String ipAddress;
        public String macAddress;
        public String gatewayIp = "";
        public boolean isWifi;
        public boolean isVirtual;
        public boolean isUp;
        public long speedMbps = 1000;
        public List<DeviceNode> devices = new ArrayList<>();

        public AdapterNode(String name, String displayName, String ipAddress, String macAddress, boolean isWifi, boolean isVirtual, boolean isUp) {
            this.name = name;
            this.displayName = displayName;
            this.ipAddress = ipAddress;
            this.macAddress = macAddress;
            this.isWifi = isWifi;
            this.isVirtual = isVirtual;
            this.isUp = isUp;
        }
    }

    public static class DeviceNode {
        public String ip;
        public String mac;
        public String hostName = "Desconocido";
        public String manufacturer = "Dirección privada / LAN";
        public long responseTimeMs = -1;
        public String probableOs = "Estimado";
        public int ttl = -1;
        public boolean isReachable = true;
        public boolean isBosgame = false;
        public boolean isGateway = false;
        public boolean isLocalPc = false;
        public boolean isNew = false;
        public boolean isNoLongerPresent = false;
        public List<String> visibleServices = new ArrayList<>();
        public DeviceType type = DeviceType.DESCONOCIDO;
        public String humanTitle;
        public String customLabel = null;
        public DeviceType customType = null;
        public boolean isManuallyLabeled = false;

        public DeviceNode(String ip, String mac) {
            this.ip = ip;
            this.mac = mac;
            this.humanTitle = "Dispositivo · " + ip;
        }
    }

    public static class MapScanResult {
        public String localPcName = "Silver";
        public List<AdapterNode> adapters = new ArrayList<>();
        public int totalDevicesFound = 0;
        public boolean bosgameFound = false;
        public int newDevicesCount = 0;
        public int disappearedDevicesCount = 0;
    }

    private static final Map<String, DeviceNode> PREVIOUS_SCAN_MAP = new ConcurrentHashMap<>();

    // Base de datos local de prefijos OUI de fabricantes (offline)
    private static final Map<String, String> OUI_VENDORS = new HashMap<>();

    static {
        OUI_VENDORS.put("F8:95:22", "Huawei Technologies");
        OUI_VENDORS.put("00:1E:10", "Huawei Technologies");
        OUI_VENDORS.put("F0:A9:51", "Intel Corporate");
        OUI_VENDORS.put("E8:CA:C8", "Xiaomi Communications");
        OUI_VENDORS.put("50:5A:65", "TP-Link Corporation");
        OUI_VENDORS.put("EC:08:6B", "TP-Link Corporation");
        OUI_VENDORS.put("00:50:56", "VMware Virtual Adapter");
        OUI_VENDORS.put("00:0C:29", "VMware Virtual Adapter");
        OUI_VENDORS.put("08:00:27", "Oracle VirtualBox");
        OUI_VENDORS.put("B8:27:EB", "Raspberry Pi Foundation");
        OUI_VENDORS.put("DC:A6:32", "Raspberry Pi Foundation");
        OUI_VENDORS.put("E4:5F:01", "Raspberry Pi Foundation");
        OUI_VENDORS.put("00:1A:2B", "Cisco Systems");
        OUI_VENDORS.put("70:85:C2", "Cisco Systems");
        OUI_VENDORS.put("D8:3A:DD", "Intel Corporation");
        OUI_VENDORS.put("E0:D5:5E", "Intel Corporation");
        OUI_VENDORS.put("00:E0:4C", "Realtek Semiconductor");
        OUI_VENDORS.put("52:54:00", "QEMU Virtual Network");
        OUI_VENDORS.put("3C:22:FB", "Apple Inc.");
        OUI_VENDORS.put("BC:D0:74", "Apple Inc.");
        OUI_VENDORS.put("AC:DE:48", "Apple Inc.");
        OUI_VENDORS.put("F4:34:F0", "Samsung Electronics");
        OUI_VENDORS.put("84:25:19", "Samsung Electronics");
        OUI_VENDORS.put("00:15:5D", "Microsoft Hyper-V");
        OUI_VENDORS.put("B4:2E:99", "Asus Technology");
        OUI_VENDORS.put("00:11:32", "Synology NAS");
        OUI_VENDORS.put("00:1D:BA", "Sony Corporation");
        OUI_VENDORS.put("00:26:B9", "Dell Inc.");
        OUI_VENDORS.put("38:63:BB", "HP Inc.");
    }

    /**
     * Escanea adaptadores locales y consulta la tabla ARP para descubrir equipos en la LAN.
     */
    public MapScanResult scanNetwork(String targetBosgameIp, AtomicBoolean cancelFlag) {
        MapScanResult result = new MapScanResult();
        String bosgameIp = (targetBosgameIp != null && !targetBosgameIp.isBlank()) ? targetBosgameIp.trim() : AppConfig.DEFAULT_IP;

        // Nombre de este equipo
        String host = System.getenv("COMPUTERNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                host = "Silver";
            }
        }
        result.localPcName = host;

        // Cargar etiquetas manuales personalizadas por el usuario
        Map<String, ManualLabel> manualLabels = loadManualLabels();

        // 1. Obtener la puerta de enlace (Gateway) activa de Windows
        String defaultGateway = queryDefaultGateway();

        // 2. Obtener adaptadores de red de la máquina
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                if (cancelFlag.get()) break;
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isLoopback() && ni.isUp()) {
                    String dName = ni.getDisplayName().toLowerCase();
                    boolean isWifi = dName.contains("wi-fi") || dName.contains("wireless") || dName.contains("802.11");
                    boolean isVirt = ni.isVirtual() || dName.contains("virtual") || dName.contains("vmware") || dName.contains("vethernet") || dName.contains("wsl");

                    String ipStr = "";
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address) {
                            ipStr = a.getHostAddress();
                            break;
                        }
                    }

                    if (!ipStr.isEmpty()) {
                        byte[] macBytes = ni.getHardwareAddress();
                        String macStr = formatMac(macBytes);

                        AdapterNode an = new AdapterNode(ni.getName(), ni.getDisplayName(), ipStr, macStr, isWifi, isVirt, ni.isUp());
                        an.gatewayIp = defaultGateway;
                        result.adapters.add(an);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Leer la tabla ARP de Windows para hallar vecinos
        Map<String, String> arpTable = readArpTable();

        // Si la BOSGAME está configurada y no está en ARP, realizar sondeo suave
        if (!arpTable.containsKey(bosgameIp)) {
            try {
                InetAddress.getByName(bosgameIp).isReachable(300);
                arpTable = readArpTable();
            } catch (Exception ignored) {}
        }

        // Thread pool para sondeo simultáneo rápido de puertos y TTL
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<?>> futures = new ArrayList<>();

        Map<String, DeviceNode> currentFoundMap = new HashMap<>();

        // 4. Procesar adaptadores y vincular dispositivos
        for (AdapterNode adapter : result.adapters) {
            if (cancelFlag.get()) break;
            String baseSubnet = getSubnetPrefix(adapter.ipAddress);
            boolean adapterHasBosgame = false;

            for (Map.Entry<String, String> entry : arpTable.entrySet()) {
                String devIp = entry.getKey();
                String devMac = entry.getValue();

                if (devIp.startsWith(baseSubnet) && !devIp.equals(adapter.ipAddress) && !devIp.endsWith(".255")) {
                    DeviceNode dev = new DeviceNode(devIp, devMac);
                    currentFoundMap.put(devIp, dev);

                    if (devIp.equals(defaultGateway)) {
                        dev.isGateway = true;
                    }
                    if (devIp.equals(bosgameIp)) {
                        dev.isBosgame = true;
                        adapterHasBosgame = true;
                        result.bosgameFound = true;
                    }

                    adapter.devices.add(dev);
                    result.totalDevicesFound++;

                    // Lanzar análisis asíncrono en paralelo de identificación
                    futures.add(pool.submit(() -> analyzeDeviceDetails(dev, manualLabels)));
                }
            }

            // REGLA OBLIGATORIA: La BOSGAME SIEMPRE aparece en el adaptador correspondiente.
            // Si no estaba en ARP, se agrega explícitamente y se analiza su alcance.
            if (!adapterHasBosgame && (bosgameIp.startsWith(baseSubnet) || result.adapters.size() == 1)) {
                DeviceNode bosNode = new DeviceNode(bosgameIp, "B0:56:A3:00:00:10");
                bosNode.isBosgame = true;
                currentFoundMap.put(bosgameIp, bosNode);
                adapter.devices.add(bosNode);
                result.totalDevicesFound++;
                futures.add(pool.submit(() -> analyzeDeviceDetails(bosNode, manualLabels)));
            }
        }

        // Esperar finalización del sondeo (máximo 2.5 segundos para no bloquear la UI)
        for (Future<?> f : futures) {
            try {
                f.get(2500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }
        pool.shutdownNow();

        // 5. Comparar con escaneo anterior (Nuevo y Ya no aparece)
        if (!PREVIOUS_SCAN_MAP.isEmpty()) {
            for (DeviceNode d : currentFoundMap.values()) {
                if (!PREVIOUS_SCAN_MAP.containsKey(d.ip)) {
                    d.isNew = true;
                    result.newDevicesCount++;
                }
            }
            for (Map.Entry<String, DeviceNode> prev : PREVIOUS_SCAN_MAP.entrySet()) {
                if (!currentFoundMap.containsKey(prev.getKey())) {
                    result.disappearedDevicesCount++;
                }
            }
        }

        // Actualizar caché de dispositivos previos
        PREVIOUS_SCAN_MAP.clear();
        PREVIOUS_SCAN_MAP.putAll(currentFoundMap);

        return result;
    }

    private void analyzeDeviceDetails(DeviceNode dev, Map<String, ManualLabel> manualLabels) {
        // Asignación de título y tipo base inicial por si las consultas de red tardan
        inferDeviceTypeAndTitle(dev);
        applyManualLabel(dev, manualLabels);

        // 1. Ping rápido para medir latencia y extraer TTL de la respuesta
        queryPingAndTtl(dev);

        // 2. Nombre del equipo mediante resolución inversa DNS
        try {
            InetAddress addr = InetAddress.getByName(dev.ip);
            String hName = addr.getCanonicalHostName();
            if (hName != null && !hName.equalsIgnoreCase(dev.ip)) {
                dev.hostName = hName;
            } else {
                dev.hostName = addr.getHostName();
            }
        } catch (Exception ignored) {}

        // 3. Fabricante por MAC OUI o detección de dirección privada
        dev.manufacturer = resolveManufacturer(dev.mac);

        // 4. Sondeo simultáneo rápido de puertos de servicio
        probeVisibleServices(dev);

        // 5. Reclasificación de tipo e inferencia de sistema operativo con datos completos
        inferDeviceTypeAndTitle(dev);

        // 6. Aplicar etiquetado manual si el usuario lo configuró previamente (prioridad máxima)
        applyManualLabel(dev, manualLabels);
    }

    private void queryPingAndTtl(DeviceNode dev) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "ping -n 1 -w 350 " + dev.ip);
            Process p = pb.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean reached = false;
                while ((line = r.readLine()) != null) {
                    Matcher mTime = Pattern.compile("(?i)(?:tiempo|time)[=<]\\s*(\\d+)ms").matcher(line);
                    if (mTime.find()) {
                        dev.responseTimeMs = Long.parseLong(mTime.group(1));
                        reached = true;
                    }
                    Matcher mTtl = Pattern.compile("(?i)ttl[=:]\\s*(\\d+)").matcher(line);
                    if (mTtl.find()) {
                        dev.ttl = Integer.parseInt(mTtl.group(1));
                    }
                }
                dev.isReachable = reached;
            }
        } catch (Exception ignored) {
            dev.isReachable = false;
        }

        // Estimación de OS por TTL
        if (dev.ttl > 0) {
            if (dev.ttl >= 45 && dev.ttl <= 75) {
                dev.probableOs = "Linux, Mac o Android (estimado)";
            } else if (dev.ttl >= 100 && dev.ttl <= 140) {
                dev.probableOs = "Windows (estimado)";
            } else if (dev.ttl >= 230 && dev.ttl <= 255) {
                dev.probableOs = "Equipo de red / Router (estimado)";
            } else {
                dev.probableOs = "Estimado (TTL " + dev.ttl + ")";
            }
        } else {
            dev.probableOs = dev.isReachable ? "Desconocido (Sin TTL)" : "Sin respuesta (Estimado)";
        }
    }

    private void probeVisibleServices(DeviceNode dev) {
        if (!dev.isReachable && !dev.isBosgame) return;

        int[] ports = {22, 80, 443, 139, 445, 631, 3389, 5900, 9100};
        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(dev.ip, port), 90);
                // Si conectó, traducir a descripción humana
                switch (port) {
                    case 139:
                    case 445:
                        if (!dev.visibleServices.contains("Comparte archivos")) dev.visibleServices.add("Comparte archivos");
                        break;
                    case 3389:
                        dev.visibleServices.add("Windows con escritorio remoto");
                        break;
                    case 22:
                        dev.visibleServices.add("Acceso SSH (Linux o equipo de red)");
                        break;
                    case 80:
                    case 443:
                        if (!dev.visibleServices.contains("Tiene página web (router, impresora…)")) dev.visibleServices.add("Tiene página web (router, impresora…)");
                        break;
                    case 631:
                    case 9100:
                        if (!dev.visibleServices.contains("Impresora")) dev.visibleServices.add("Impresora");
                        break;
                    case 5900:
                        dev.visibleServices.add("Control remoto VNC");
                        break;
                }
                dev.isReachable = true;
            } catch (Exception ignored) {}
        }
    }

    private void inferDeviceTypeAndTitle(DeviceNode dev) {
        if (dev.isGateway) {
            dev.type = DeviceType.ROUTER;
        } else if (dev.isBosgame) {
            dev.type = DeviceType.LINUX_SERVER;
        } else if (dev.visibleServices.contains("Windows con escritorio remoto") || (dev.ttl >= 100 && dev.ttl <= 140)) {
            dev.type = DeviceType.PC_WINDOWS;
        } else if (dev.visibleServices.contains("Impresora")) {
            dev.type = DeviceType.PRINTER;
        } else if (dev.visibleServices.contains("Acceso SSH (Linux o equipo de red)")
                || (dev.ttl >= 45 && dev.ttl <= 75 && dev.visibleServices.contains("Tiene página web (router, impresora…)"))) {
            dev.type = DeviceType.LINUX_SERVER;
        } else if (isPrivateMac(dev.mac) || (dev.ttl >= 45 && dev.ttl <= 75)) {
            dev.type = DeviceType.PHONE_TABLET;
        } else {
            dev.type = DeviceType.DESCONOCIDO;
        }

        // Título humano inicial
        if (dev.isGateway) {
            dev.humanTitle = "Router / puerta de enlace · " + dev.ip;
            if (!dev.hostName.equals("Desconocido") && !dev.hostName.equals(dev.ip)) {
                dev.humanTitle = "Router (" + dev.hostName + ") · " + dev.ip;
            }
        } else if (dev.isBosgame) {
            dev.humanTitle = "BOSGAME (Mini PC) · " + dev.ip;
        } else if (!dev.hostName.equals("Desconocido") && !dev.hostName.equals(dev.ip)) {
            dev.humanTitle = dev.hostName + " · " + dev.ip;
        } else {
            dev.humanTitle = dev.type.getDisplay() + " · " + dev.ip;
        }
    }

    public static boolean isPrivateMac(String mac) {
        if (mac == null || mac.length() < 2) return false;
        char c = Character.toUpperCase(mac.charAt(1));
        return c == '2' || c == '6' || c == 'A' || c == 'E';
    }

    private String resolveManufacturer(String mac) {
        if (mac == null || mac.length() < 8) return "Dirección privada";
        if (isPrivateMac(mac)) {
            return "Dirección privada (probable teléfono o equipo moderno)";
        }

        String prefix = mac.substring(0, 8).toUpperCase();
        String known = OUI_VENDORS.get(prefix);
        if (known != null) return known;

        return "Fabricante no catalogado en OUI";
    }

    private String queryDefaultGateway() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "route print 0.0.0.0").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("0.0.0.0")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 3 && parts[2].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                            return parts[2];
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.100.1";
    }

    private Map<String, String> readArpTable() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "arp -a").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && parts[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        String ip = parts[0];
                        String mac = parts[1].replace("-", ":").toUpperCase();
                        if (!mac.equals("FF:FF:FF:FF:FF:FF") && !ip.startsWith("224.") && !ip.startsWith("239.")) {
                            map.put(ip, mac);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private String getSubnetPrefix(String ip) {
        int idx = ip.lastIndexOf('.');
        return idx > 0 ? ip.substring(0, idx + 1) : ip;
    }

    private String formatMac(byte[] mac) {
        if (mac == null) return "No disponible";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }

    // ── GESTIÓN DE ETIQUETADO MANUAL (FixTool_Willy/device_labels.json) ─────────

    public static Path getLabelsFilePath() {
        Path work = AppConfig.getWorkDir();
        if (!Files.exists(work)) {
            try { Files.createDirectories(work); } catch (Exception ignored) {}
        }
        return work.resolve("device_labels.json");
    }

    public static Map<String, ManualLabel> loadManualLabels() {
        Map<String, ManualLabel> map = new HashMap<>();
        Path path = getLabelsFilePath();
        if (!Files.exists(path)) return map;

        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            // Parser de JSON simple sin dependencias externas
            Pattern entryPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{\\s*\"label\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"type\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");
            Matcher m = entryPattern.matcher(text);
            while (m.find()) {
                String key = m.group(1).trim().toUpperCase();
                String label = m.group(2).trim();
                String type = m.group(3).trim();
                map.put(key, new ManualLabel(label, type));
            }
        } catch (Exception ignored) {}

        return map;
    }

    public static synchronized void saveManualLabel(String macOrIp, String customLabel, DeviceType type) {
        Map<String, ManualLabel> map = loadManualLabels();
        String key = macOrIp.trim().toUpperCase();
        map.put(key, new ManualLabel(customLabel, type != null ? type.name() : DeviceType.DESCONOCIDO.name()));

        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, ManualLabel> entry : map.entrySet()) {
            if (count > 0) json.append(",\n");
            json.append(String.format("  \"%s\": {\n    \"label\": \"%s\",\n    \"type\": \"%s\"\n  }",
                    entry.getKey(),
                    entry.getValue().label.replace("\"", "\\\""),
                    entry.getValue().type));
            count++;
        }
        json.append("\n}\n");

        try {
            Files.writeString(getLabelsFilePath(), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private void applyManualLabel(DeviceNode dev, Map<String, ManualLabel> labels) {
        String keyMac = dev.mac != null ? dev.mac.trim().toUpperCase() : "";
        String keyIp = dev.ip != null ? dev.ip.trim().toUpperCase() : "";

        ManualLabel found = labels.get(keyMac);
        if (found == null) found = labels.get(keyIp);

        if (found != null && !found.label.isBlank()) {
            dev.customLabel = found.label;
            dev.customType = DeviceType.fromString(found.type);
            dev.humanTitle = found.label;
            dev.type = dev.customType;
            dev.isManuallyLabeled = true;
        }
    }

    public DiagnosticResult toDiagnosticResult(MapScanResult scan) {
        boolean ok = scan.totalDevicesFound > 0;
        String badge = ok ? ToolStatus.OK.getDisplayText() : ToolStatus.ATENCION.getDisplayText();
        String summary = String.format("Mapeo de Red Local: Mi PC '%s', %d adaptadores, %d dispositivos detectados.",
                scan.localPcName, scan.adapters.size(), scan.totalDevicesFound);

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Equipo Local", scan.localPcName);
        tele.put("Adaptadores Activos", String.valueOf(scan.adapters.size()));
        tele.put("Total Dispositivos Hallados", String.valueOf(scan.totalDevicesFound));
        tele.put("BOSGAME", scan.bosgameFound ? "En red (Resaltada)" : "No responde (Registrada en gris)");
        if (scan.newDevicesCount > 0) {
            tele.put("Nuevos respecto al escaneo anterior", String.valueOf(scan.newDevicesCount));
        }

        List<String> audit = new ArrayList<>();
        for (AdapterNode a : scan.adapters) {
            audit.add(String.format("Adaptador: %s (%s) IP: %s - %d vecinos",
                    a.displayName, a.isWifi ? "Wi-Fi" : "Ethernet", a.ipAddress, a.devices.size()));
            for (DeviceNode d : a.devices) {
                audit.add(String.format("  └─ %s | IP: %s | MAC: %s | Host: %s (%s) | %s | %d ms",
                        d.humanTitle, d.ip, d.mac, d.hostName, d.manufacturer, d.probableOs, d.responseTimeMs));
            }
        }

        return new DiagnosticResult(ok, badge, summary, tele, audit);
    }
}
