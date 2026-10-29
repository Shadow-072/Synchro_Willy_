package com.fixtool.service;

import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.ToolStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio de detección de puertos abiertos y programas en escucha (Herramienta 9: Puertos Abiertos).
 * Clasifica puertos entre 'Solo local' y 'Expuesto a la red', identifica procesos y ofrece cierre seguro.
 * Responde a las 6 secciones requeridas para cada puerto.
 */
public class OpenPortsService {

    public enum PortScope {
        LOCAL_ONLY("Solo este PC", com.fixtool.ui.theme.ThemeTokens.ACCENT),
        NETWORK_EXPOSED("Expuesto a la red", com.fixtool.ui.theme.ThemeTokens.COLOR_WARN);

        private final String label;
        private final String colorHex;

        PortScope(String label, String colorHex) {
            this.label = label;
            this.colorHex = colorHex;
        }

        public String getLabel() { return label; }
        public String getColorHex() { return colorHex; }
    }

    /**
     * Modelo de conocimiento de puerto con las 6 secciones requeridas por el estándar de diseño.
     */
    public static class PortKnowledge {
        public final String serviceFriendlyName; // 1. Qué es
        public final String everydayPurpose;     // 2. Para qué sirve (ejemplo cotidiano)
        public final String isNormal;            // 3. ¿Es normal?
        public final String scopeDescription;    // 5. Alcance
        public final String riskAndAction;       // 6. Riesgo y qué hacer

        public PortKnowledge(String serviceFriendlyName, String everydayPurpose, String isNormal, String scopeDescription, String riskAndAction) {
            this.serviceFriendlyName = serviceFriendlyName;
            this.everydayPurpose = everydayPurpose;
            this.isNormal = isNormal;
            this.scopeDescription = scopeDescription;
            this.riskAndAction = riskAndAction;
        }
    }

    public static class OpenPortItem {
        public String protocol; // TCP o UDP
        public int port;
        public String localAddress;
        public String state;
        public int pid;
        public String processName = "Desconocido";
        public PortScope scope;
        public String serviceFriendlyName; // 1. Qué es
        public String everydayPurpose;     // 2. Para qué sirve
        public String isNormal;            // 3. ¿Es normal?
        public String scopeDescription;    // 5. Alcance
        public String riskAndAction;       // 6. Riesgo y qué hacer
        public String explanation;         // Resumen amigable
        public boolean isSystemCritical = false;
        public boolean closedByUser = false;

        public OpenPortItem(String protocol, int port, String localAddress, String state, int pid) {
            this.protocol = protocol;
            this.port = port;
            this.localAddress = localAddress;
            this.state = state;
            this.pid = pid;
        }
    }

    public static class PortsScanResult {
        public List<OpenPortItem> ports = new ArrayList<>();
        public int totalListening = 0;
        public int totalExposed = 0;
        public int totalLocalOnly = 0;
    }

    private static final Set<String> CRITICAL_PROCESSES = new HashSet<>(Arrays.asList(
            "system", "system idle process", "registry", "smss.exe", "csrss.exe",
            "wininit.exe", "services.exe", "lsass.exe", "svchost.exe", "explorer.exe",
            "dwm.exe", "fontdrvhost.exe", "spoolsv.exe"
    ));

    private static final Map<String, PortKnowledge> PORT_DICTIONARY = new HashMap<>();

    static {
        // 135: llamadas a procedimientos remotos de Windows
        registerPort("TCP", 135, new PortKnowledge(
                "Llamadas a Procedimientos Remotos de Windows (RPC Endpoint Mapper)",
                "Como la centralita telefónica interna de un edificio: cuando una aplicación o servicio de Windows necesita comunicarse con otro proceso, llama aquí para saber a qué extensión interna dirigirse.",
                "Sí, es totalmente normal y fundamental en todas las versiones de Windows.",
                "Generalmente escucha en todas las interfaces de red para coordinar servicios locales.",
                "Riesgo nulo en redes privadas. El Firewall de Windows bloquea por defecto accesos no autorizados desde internet. No intentes cerrarlo, es crítico para el sistema."
        ));

        // 139 & 445: compartir archivos e impresoras
        registerPort("TCP", 139, new PortKnowledge(
                "Sesiones NetBIOS para Compartir Archivos e Impresoras",
                "Como la bandeja de correo compartido para equipos antiguos: permite compartir carpetas e impresoras con dispositivos que usan versiones heredadas de red.",
                "Sí, es normal en redes Windows que comparten recursos.",
                "Visible en la red local (LAN) para equipos en el mismo segmento.",
                "Bajo en tu red local. En redes Wi-Fi públicas, Windows activa el perfil de red Pública que lo aísla automáticamente."
        ));
        registerPort("TCP", 445, new PortKnowledge(
                "Servidor de Archivos e Impresoras de Windows (SMB)",
                "Como la biblioteca compartida de tu red: permite que tu PC comparta carpetas, documentos y discos con otros equipos (como la BOSGAME o tu router).",
                "Sí, completamente normal si tienes carpetas compartidas o el servicio de uso compartido activo.",
                "Visible para todos los equipos autorizados dentro de tu red local.",
                "Seguro en redes privadas domésticas. Requiere contraseñas robustas si compartes carpetas con permisos de escritura."
        ));

        // 3389: escritorio remoto
        registerPort("TCP", 3389, new PortKnowledge(
                "Escritorio Remoto de Windows (RDP)",
                "Como un mando a distancia con pantalla: permite que te conectes desde otro ordenador y utilices este equipo como si estuvieras sentado frente a él.",
                "Normal si has habilitado el Escritorio Remoto en la Configuración de Windows.",
                "Escucha peticiones de inicio de sesión remoto desde la red.",
                "Seguro dentro de tu red doméstica. No debe exponerse directamente a internet sin una VPN o pasarela segura."
        ));

        // 5040: plataforma de dispositivos conectados de Windows
        registerPort("TCP", 5040, new PortKnowledge(
                "Plataforma de Dispositivos Conectados de Windows (CDP)",
                "Como el enlace inalámbrico de tu móvil: sincroniza notificaciones, portapapeles en la nube y la función 'Continuar en la PC' entre Windows y tus dispositivos móviles.",
                "Sí, activo por defecto en Windows 10 y Windows 11.",
                "Comunicaciones del sistema y dispositivos vinculados de confianza.",
                "Completamente inofensivo. Es un componente oficial de Microsoft firmado por el sistema operativo."
        ));

        // 5357: descubrimiento de dispositivos de red
        registerPort("TCP", 5357, new PortKnowledge(
                "Descubrimiento de Servicios Web en Red (WSDAPI)",
                "Como el radar de impresoras inteligentes: ayuda a Windows a encontrar automáticamente impresoras, escáneres y televisores conectados a la red Wi-Fi sin instalar software adicional.",
                "Sí, muy común en hogares y oficinas con impresoras de red.",
                "Limitado a la red local (LAN).",
                "Seguro y necesario para que tus dispositivos periféricos se detecten entre sí."
        ));

        // 7680: optimización de entrega de actualizaciones de Windows
        registerPort("TCP", 7680, new PortKnowledge(
                "Optimización de Distribución de Windows Update (P2P)",
                "Como prestarle un apunte a tu compañero: si tienes varios PC en tu casa, permite que descarguen las actualizaciones entre ellos ahorrando datos de internet.",
                "Sí, función estándar de Windows para acelerar descargas de parches.",
                "Red local doméstica (solo comparte trozos de actualización ya verificados).",
                "Inofensivo. Si deseas desactivar el intercambio en red, puedes hacerlo en Configuración > Windows Update > Optimización de distribución."
        ));

        // 1900 (UDP): descubrimiento de dispositivos (UPnP/SSDP)
        registerPort("UDP", 1900, new PortKnowledge(
                "Descubrimiento Universal Plug and Play (UPnP / SSDP)",
                "Como levantar la mano en una reunión: permite que Smart TVs, consolas, altavoces inteligentes y routers anuncien su presencia para transmitir multimedia o juegos.",
                "Sí, sumamente habitual para streaming de audio, vídeo y detección de consolas.",
                "Difusión en red local (multicast local).",
                "Normal en red doméstica. En routers o redes públicas no de confianza conviene mantener activo el firewall."
        ));

        // 5353 (UDP): descubrimiento de nombres en red local (mDNS)
        registerPort("UDP", 5353, new PortKnowledge(
                "Descubrimiento de Nombres en Red Local (mDNS / Bonjour)",
                "Como la libreta de contactos rápida de tu hogar: traduce nombres amigables (ej: 'impresora.local' o 'bosgame.local') sin requerir un servidor DNS dedicado.",
                "Sí, muy habitual; utilizado por Windows, Apple Bonjour, Chromecast e impresoras modernas.",
                "Red local exclusivamente.",
                "Inofensivo y muy práctico para comunicarse entre equipos sin memorizar números de IP."
        ));

        // 137 y 138 (UDP): servicio de nombres de Windows (NetBIOS)
        registerPort("UDP", 137, new PortKnowledge(
                "Servicio de Nombres NetBIOS de Windows",
                "Como el altavoz que vocea el nombre de tu PC en la red para que computadoras y dispositivos antiguos lo reconozcan.",
                "Sí, forma parte del soporte heredado de red de Windows.",
                "Red local (LAN).",
                "Seguro en redes privadas domésticas. El perfil de red Pública lo bloquea por defecto."
        ));
        registerPort("UDP", 138, new PortKnowledge(
                "Servicio de Datagramas NetBIOS de Windows",
                "Como el servicio de mensajes cortos entre equipos de la misma red local para coordinar grupos de trabajo y recursos compartidos.",
                "Sí, habitual en redes de oficina doméstica y grupos de trabajo Windows.",
                "Red local (LAN).",
                "Inofensivo dentro de redes de confianza."
        ));

        // 5985: administración remota de Windows
        registerPort("TCP", 5985, new PortKnowledge(
                "Administración Remota de Windows (WinRM / WS-Management)",
                "Como un túnel de mantenimiento para técnicos: permite administrar este ordenador mediante comandos de PowerShell desde otro equipo de la red.",
                "Normal si has activado PowerShell Remoto o soporte de TI corporativo.",
                "Red local.",
                "Requiere autenticación obligatoria de administrador. Si no utilizas administración remota, se puede deshabilitar con 'Disable-PSRemoting'."
        ));

        // 22: acceso remoto seguro (SSH)
        registerPort("TCP", 22, new PortKnowledge(
                "Acceso Remoto Seguro por Consola (SSH)",
                "Como una cerradura digital blindada: permite acceder a la línea de comandos de forma cifrada para administrar servidores o transferir archivos vía SFTP.",
                "Normal si tienes instalado el servidor OpenSSH de Windows o herramientas como Git/WSL.",
                "Escucha conexiones entrantes en el equipo.",
                "Muy seguro si se emplean contraseñas robustas o llaves criptográficas. No se debe exponer a internet sin medidas anti fuerza bruta."
        ));

        // 80 y 443: servidor web
        registerPort("TCP", 80, new PortKnowledge(
                "Servidor Web HTTP Estándar",
                "Como el mostrador principal de una tienda: sirve páginas web, paneles de configuración o interfaces de usuario sin cifrado.",
                "Normal si ejecutas un servidor web local de desarrollo (Apache, Nginx, Node.js, IIS o Docker).",
                "Accesible según la interfaz configurada (solo este equipo o toda la red).",
                "El tráfico por puerto 80 no está encriptado. Si no desarrollas sitios web ni tienes paneles locales, revisa qué programa lo inició."
        ));
        registerPort("TCP", 443, new PortKnowledge(
                "Servidor Web HTTPS Cifrado",
                "Como una sucursal bancaria digital: entrega páginas web y APIs protegidas mediante certificados SSL/TLS para que nadie pueda interceptar la comunicación.",
                "Normal si desarrollas aplicaciones con SSL o tienes herramientas como Docker/Kubernetes/VMware.",
                "Accesible según la interfaz (local o red).",
                "Seguro y cifrado. Revisa el programa que lo mantiene abierto para confirmar su propósito."
        ));
    }

    private static void registerPort(String protocol, int port, PortKnowledge pk) {
        PORT_DICTIONARY.put(protocol.toUpperCase() + ":" + port, pk);
    }

    /**
     * Escanea los puertos en escucha y obtiene los procesos asociados.
     */
    public PortsScanResult scanOpenPorts(AtomicBoolean cancelFlag) {
        PortsScanResult result = new PortsScanResult();
        Map<Integer, String> processMap = buildProcessMap();

        try {
            // netstat -ano para capturar tanto TCP en LISTENING como UDP activos
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "netstat -ano");
            Process p = pb.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (cancelFlag != null && cancelFlag.get()) break;

                    line = line.trim();
                    if (!line.startsWith("TCP") && !line.startsWith("UDP")) continue;

                    String[] parts = line.split("\\s+");
                    String proto = parts[0].toUpperCase();
                    String local = "";
                    String state = "";
                    String pidStr = "";

                    if ("TCP".equals(proto) && parts.length >= 5) {
                        local = parts[1];
                        state = parts[3];
                        pidStr = parts[4];
                        if (!"LISTENING".equalsIgnoreCase(state) && !"ESCUCHANDO".equalsIgnoreCase(state)) {
                            continue; // Solo puertos TCP en escucha
                        }
                    } else if ("UDP".equals(proto) && parts.length >= 4) {
                        local = parts[1];
                        state = "ABIERTO (UDP)";
                        pidStr = parts[parts.length - 1]; // Última columna es PID
                    } else {
                        continue;
                    }

                    int colon = local.lastIndexOf(':');
                    if (colon > 0) {
                        String ipPart = local.substring(0, colon);
                        String portStr = local.substring(colon + 1);

                        try {
                            int port = Integer.parseInt(portStr);
                            int pid = Integer.parseInt(pidStr);

                            OpenPortItem item = new OpenPortItem(proto, port, local, state, pid);

                            // Determinar si es local o expuesto
                            if (ipPart.equals("127.0.0.1") || ipPart.equals("[::1]") || ipPart.equalsIgnoreCase("localhost")) {
                                item.scope = PortScope.LOCAL_ONLY;
                                result.totalLocalOnly++;
                            } else {
                                item.scope = PortScope.NETWORK_EXPOSED;
                                result.totalExposed++;
                            }

                            // Resolver nombre de proceso
                            String pName = processMap.getOrDefault(pid, "PID " + pid);
                            item.processName = pName;
                            item.isSystemCritical = isCritical(pName);

                            // Resolver conocimiento detallado del puerto
                            resolvePortKnowledge(item);

                            result.ports.add(item);
                            result.totalListening++;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        // Ordenar primero los expuestos a la red, luego por número de puerto
        result.ports.sort((a, b) -> {
            if (a.scope != b.scope) {
                return a.scope == PortScope.NETWORK_EXPOSED ? -1 : 1;
            }
            return Integer.compare(a.port, b.port);
        });

        return result;
    }

    private void resolvePortKnowledge(OpenPortItem item) {
        String key = item.protocol.toUpperCase() + ":" + item.port;
        PortKnowledge pk = PORT_DICTIONARY.get(key);

        if (pk != null) {
            item.serviceFriendlyName = pk.serviceFriendlyName;
            item.everydayPurpose = pk.everydayPurpose;
            item.isNormal = pk.isNormal;
            item.scopeDescription = pk.scopeDescription;
            item.riskAndAction = pk.riskAndAction;
        } else if (item.port >= 49664 && item.port <= 49679) {
            // Rango dinámico específico de Windows RPC
            item.serviceFriendlyName = "Puerto Dinámico RPC de Windows (" + item.port + ")";
            item.everydayPurpose = "Como ventanillas provisionales que abre un banco en horas pico: Windows asigna temporalmente estos puertos a servicios internos como DCOM, spooler de impresión o replicación para dialogar entre sí.";
            item.isNormal = "Completamente normal; reservado oficialmente por Microsoft para llamadas de procedimiento remoto.";
            item.scopeDescription = item.scope == PortScope.LOCAL_ONLY
                    ? "Uso exclusivo dentro de este PC (127.0.0.1)."
                    : "Interfaces locales del sistema; el Firewall de Windows filtra accesos externos no autorizados.";
            item.riskAndAction = "Inofensivo. Es administrado y liberado automáticamente por el subsistema RPC de Windows.";
        } else {
            // Respaldo para puertos desconocidos según rango oficial IANA
            String rangeName;
            String rangeUsage;
            if (item.port >= 0 && item.port <= 1023) {
                rangeName = "Rango de Sistema (Puertos Bien Conocidos 0–1023)";
                rangeUsage = "Reservado para servicios fundamentales de red y protocolos reconocidos de internet.";
            } else if (item.port >= 1024 && item.port <= 49151) {
                rangeName = "Rango Registrado (1024–49151)";
                rangeUsage = "Asignado habitualmente a aplicaciones de software específicas, bases de datos o servicios instalados por el usuario.";
            } else {
                rangeName = "Rango Dinámico o Efímero (49152–65535)";
                rangeUsage = "Puerto asignado temporalmente por un programa o por Windows para una sesión puntual de comunicación.";
            }

            item.serviceFriendlyName = "Servicio en puerto " + item.port + " (" + item.processName + ")";
            item.everydayPurpose = "Un programa en este equipo (" + item.processName + ") reservó este puerto para escuchar mensajes entrantes. " + rangeUsage;
            item.isNormal = item.isSystemCritical
                    ? "Sí, es un proceso oficial del sistema operativo."
                    : "Depende del programa '" + item.processName + "'; si reconoces la aplicación, es su comportamiento previsto.";
            item.scopeDescription = item.scope == PortScope.LOCAL_ONLY
                    ? "Solo dentro de este PC: accesible exclusivamente desde este mismo ordenador (127.0.0.1)."
                    : "Visible en la red local: otros equipos conectados a tu router podrían enviar peticiones si el firewall lo autoriza.";
            item.riskAndAction = "Tener un puerto en escucha no significa que sea vulnerable ni accesible desde internet. El Firewall de Windows inspecciona y filtra el tráfico entrante. Si no reconoces el programa '" + item.processName + "', puedes cerrarlo de forma segura con el botón inferior.";
        }

        item.explanation = item.everydayPurpose;
    }

    /**
     * Termina de forma segura un proceso si no está en la lista de exclusión del sistema.
     */
    public boolean killProcess(int pid, String processName, StringBuilder errorOut) {
        if (isCritical(processName)) {
            if (errorOut != null) {
                errorOut.append("El proceso '").append(processName).append("' es crítico para Windows y no puede ser finalizado.");
            }
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/F");
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                return true;
            } else {
                if (errorOut != null) {
                    errorOut.append("No se pudo finalizar el proceso (Código ").append(exit).append("). Puede requerir permisos adicionales.");
                }
                return false;
            }
        } catch (Exception e) {
            if (errorOut != null) {
                errorOut.append("Error al detener proceso: ").append(e.getMessage());
            }
            return false;
        }
    }

    private boolean isCritical(String name) {
        if (name == null) return false;
        return CRITICAL_PROCESSES.contains(name.trim().toLowerCase());
    }

    private Map<Integer, String> buildProcessMap() {
        Map<Integer, String> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FO", "CSV", "/NH");
            Process p = pb.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("\"")) {
                        String[] cols = line.split("\",\"");
                        if (cols.length >= 2) {
                            String name = cols[0].replace("\"", "").trim();
                            String pidStr = cols[1].replace("\"", "").trim();
                            try {
                                int pid = Integer.parseInt(pidStr);
                                map.put(pid, name);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    public DiagnosticResult toDiagnosticResult(PortsScanResult scan) {
        boolean ok = scan.totalListening > 0;
        String badge = scan.totalExposed > 25 ? ToolStatus.ATENCION.getDisplayText() : ToolStatus.OK.getDisplayText();
        String summary = String.format("Puertos Abiertos: %d en escucha (%d puertos visibles en la red, %d solo locales).",
                scan.totalListening, scan.totalExposed, scan.totalLocalOnly);

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Total Puertos en Escucha", String.valueOf(scan.totalListening));
        tele.put("Puertos visibles en la red", String.valueOf(scan.totalExposed));
        tele.put("Solo Locales (127.0.0.1)", String.valueOf(scan.totalLocalOnly));

        List<String> audit = new ArrayList<>();
        for (OpenPortItem it : scan.ports) {
            audit.add(String.format("[%s] Puerto %d/%s - %s (PID %d: %s)",
                    it.scope.getLabel(), it.port, it.protocol, it.serviceFriendlyName, it.pid, it.processName));
        }

        return new DiagnosticResult(ok, badge, summary, tele, audit);
    }
}
