package com.fixtool.service;

import com.fixtool.model.AppConfig;
import com.fixtool.model.ConnectionConfig;
import com.fixtool.model.DiagnosticAction;
import com.fixtool.model.DiagnosticResult;
import com.fixtool.model.SessionState;
import com.fixtool.model.ToolStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio de generación de informes de diagnóstico en formato HTML profesional (Herramienta 10).
 * Diseñado para entrega técnica y sustentación del proyecto.
 * Garantiza cero persistencia de contraseñas y reporte visual limpio con semáforo global.
 */
public class ReportService {

    public static class ReportSummary {
        public Path filePath;
        public int toolsIncluded;
        public int okCount;
        public int warnCount;
        public int errorCount;
        public int pendingCount;
        public String generatedAt;
    }

    /**
     * Genera el archivo HTML del informe con las herramientas seleccionadas.
     */
    public ReportSummary generateHtmlReport(List<DiagnosticAction> actions, Set<Integer> selectedToolIds, ConnectionConfig config) throws IOException {
        ReportSummary summary = new ReportSummary();
        LocalDateTime now = LocalDateTime.now();
        summary.generatedAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Preparar carpeta de destino <user.home>/FixTool_Willy/Informes/
        String userHome = System.getProperty("user.home", ".");
        Path reportsDir = Paths.get(userHome, AppConfig.WORK_FOLDER_NAME, "Informes");
        Files.createDirectories(reportsDir);

        String fileName = "Synchro_Willy_informe_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".html";
        Path reportFile = reportsDir.resolve(fileName);
        summary.filePath = reportFile;

        // Recopilar estados y conteos
        List<ToolReportItem> reportItems = new ArrayList<>();
        SessionState state = SessionState.getInstance();

        for (DiagnosticAction action : actions) {
            if (action.getId() == 10) continue; // No incluir la herramienta de informe dentro de sí misma
            if (selectedToolIds.contains(action.getId())) {
                summary.toolsIncluded++;
                DiagnosticResult res = state.getResult(action.getId());

                String statusLabel = (res != null) ? res.getBadge() : ToolStatus.PENDIENTE.getDisplayText();
                if (res == null) {
                    summary.pendingCount++;
                } else if (res.isOk()) {
                    if (statusLabel.equalsIgnoreCase(ToolStatus.ATENCION.getDisplayText())) {
                        summary.warnCount++;
                    } else {
                        summary.okCount++;
                    }
                } else {
                    summary.errorCount++;
                }

                reportItems.add(new ToolReportItem(action, res));
            }
        }

        // Construir HTML completo y autocontenido
        String html = buildHtmlDocument(summary, reportItems, config);
        Files.writeString(reportFile, html, StandardCharsets.UTF_8);

        return summary;
    }

    private static class ToolReportItem {
        public DiagnosticAction action;
        public DiagnosticResult result;

        public ToolReportItem(DiagnosticAction action, DiagnosticResult result) {
            this.action = action;
            this.result = result;
        }
    }

    private String buildHtmlDocument(ReportSummary summary, List<ToolReportItem> items, ConnectionConfig config) {
        StringBuilder sb = new StringBuilder();

        String hostName = "PC-LOCAL";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}

        String targetIp = config != null ? config.getIpTarget() : AppConfig.DEFAULT_IP;
        String userFast = config != null ? config.getUserFast() : AppConfig.DEFAULT_USER;

        // Cargar logo en Base64 para embeberlo directamente en el HTML
        String base64Logo = "";
        try (var in = getClass().getResourceAsStream(AppConfig.LOGO_RESOURCE)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                base64Logo = Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception ignored) {}

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"es\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Informe de Diagnóstico — ").append(AppConfig.APP_NAME).append("</title>\n");
        sb.append("<style>\n");
        sb.append("  :root {\n");
        sb.append("    --bg-dark: #0D1117; --card-bg: #161B22; --border: #30363D;\n");
        sb.append("    --accent: #00B4D8; --text-main: #F1F5F9; --text-muted: #94A3B8;\n");
        sb.append("    --ok: #22C55E; --warn: #F59E0B; --err: #EF4444; --pend: #64748B;\n");
        sb.append("  }\n");
        sb.append("  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }\n");
        sb.append("  body { background-color: var(--bg-dark); color: var(--text-main); padding: 36px 20px; line-height: 1.5; }\n");
        sb.append("  .container { max-width: 900px; margin: 0 auto; }\n");
        sb.append("  .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid var(--border); padding-bottom: 20px; margin-bottom: 28px; }\n");
        sb.append("  .header h1 { font-size: 24px; color: var(--accent); }\n");
        sb.append("  .header .meta { text-align: right; font-size: 13px; color: var(--text-muted); }\n");
        sb.append("  .semaphore-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 24px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; text-align: center; }\n");
        sb.append("  .sem-box { padding: 12px; border-radius: 6px; }\n");
        sb.append("  .sem-box.ok { background: rgba(34,197,94,0.1); border: 1px solid var(--ok); }\n");
        sb.append("  .sem-box.warn { background: rgba(245,158,11,0.1); border: 1px solid var(--warn); }\n");
        sb.append("  .sem-box.err { background: rgba(239,68,68,0.1); border: 1px solid var(--err); }\n");
        sb.append("  .sem-box.pend { background: rgba(100,116,139,0.1); border: 1px solid var(--pend); }\n");
        sb.append("  .sem-val { font-size: 26px; font-weight: bold; margin-bottom: 4px; }\n");
        sb.append("  .sem-lbl { font-size: 12px; text-transform: uppercase; color: var(--text-muted); font-weight: 600; }\n");
        sb.append("  .tool-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 20px; }\n");
        sb.append("  .tool-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; border-bottom: 1px solid rgba(255,255,255,0.06); padding-bottom: 10px; }\n");
        sb.append("  .tool-title { font-size: 16px; font-weight: bold; color: var(--text-main); }\n");
        sb.append("  .badge { font-size: 11px; font-weight: bold; padding: 3px 10px; border-radius: 4px; }\n");
        sb.append("  .badge-ok { background: var(--ok); color: #0D1117; }\n");
        sb.append("  .badge-warn { background: var(--warn); color: #0D1117; }\n");
        sb.append("  .badge-err { background: var(--err); color: #FFFFFF; }\n");
        sb.append("  .badge-pend { background: var(--pend); color: #FFFFFF; }\n");
        sb.append("  .summary-text { font-size: 14px; margin-bottom: 14px; color: #CBD5E1; }\n");
        sb.append("  table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 13px; }\n");
        sb.append("  th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid rgba(255,255,255,0.07); }\n");
        sb.append("  th { color: var(--text-muted); font-weight: 600; background: rgba(0,0,0,0.2); }\n");
        sb.append("  td.val { font-weight: 600; color: #E2E8F0; }\n");
        sb.append("  details { margin-top: 12px; font-size: 12px; }\n");
        sb.append("  summary { cursor: pointer; color: var(--accent); margin-bottom: 8px; }\n");
        sb.append("  .audit-log { background: #0D1117; padding: 12px; border-radius: 6px; font-family: monospace; font-size: 11px; color: #94A3B8; max-height: 200px; overflow-y: auto; }\n");
        sb.append("  .footer { text-align: center; margin-top: 40px; padding-top: 20px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); }\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");

        // Encabezado
        sb.append("  <div class=\"header\">\n");
        sb.append("    <div style=\"display: flex; align-items: center; gap: 16px;\">\n");
        if (!base64Logo.isEmpty()) {
            sb.append("      <img src=\"data:image/png;base64,").append(base64Logo)
              .append("\" width=\"64\" height=\"64\" style=\"border-radius: 50%; box-shadow: 0 0 10px rgba(0,180,216,0.3);\" alt=\"").append(AppConfig.APP_NAME).append("\" />\n");
        } else {
            sb.append("      <div style=\"width: 64px; height: 64px; border-radius: 50%; background: #161F2E; border: 2px solid #00B4D8; display: flex; align-items: center; justify-content: center; font-size: 28px;\">&#129451;</div>\n");
        }
        sb.append("      <div>\n");
        sb.append("        <h1>").append(AppConfig.APP_NAME).append("</h1>\n");
        sb.append("        <p style=\"font-size: 13px; color: var(--text-muted);\">Herramienta de Diagnóstico y Mantenimiento Técnico</p>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"meta\">\n");
        sb.append("      <p><strong>Fecha:</strong> ").append(summary.generatedAt).append("</p>\n");
        sb.append("      <p><strong>Equipo:</strong> ").append(hostName).append(" | <strong>Usuario:</strong> ").append(System.getProperty("user.name", "Windows")).append("</p>\n");
        sb.append("      <p><strong>Objetivo BOSGAME:</strong> ").append(targetIp).append(" (Usuario: ").append(userFast).append(")</p>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // Semáforo Global
        sb.append("  <div class=\"semaphore-card\">\n");
        sb.append("    <div class=\"sem-box ok\"><div class=\"sem-val\" style=\"color: var(--ok);\">").append(summary.okCount).append("</div><div class=\"sem-lbl\">Correcto</div></div>\n");
        sb.append("    <div class=\"sem-box warn\"><div class=\"sem-val\" style=\"color: var(--warn);\">").append(summary.warnCount).append("</div><div class=\"sem-lbl\">Atención</div></div>\n");
        sb.append("    <div class=\"sem-box err\"><div class=\"sem-val\" style=\"color: var(--err);\">").append(summary.errorCount).append("</div><div class=\"sem-lbl\">Errores</div></div>\n");
        sb.append("    <div class=\"sem-box pend\"><div class=\"sem-val\" style=\"color: var(--pend);\">").append(summary.pendingCount).append("</div><div class=\"sem-lbl\">Pendientes</div></div>\n");
        sb.append("  </div>\n");

        // Detalle de cada herramienta
        for (ToolReportItem it : items) {
            sb.append("  <div class=\"tool-card\">\n");
            sb.append("    <div class=\"tool-head\">\n");
            sb.append("      <div class=\"tool-title\">").append(it.action.getId()).append(". ").append(it.action.getTitle()).append("</div>\n");

            String badgeClass = "badge-pend";
            String badgeText = "Pendiente";
            if (it.result != null) {
                badgeText = it.result.getBadge();
                if (it.result.isOk()) {
                    badgeClass = badgeText.equalsIgnoreCase(ToolStatus.ATENCION.getDisplayText()) ? "badge-warn" : "badge-ok";
                } else {
                    badgeClass = "badge-err";
                }
            }
            sb.append("      <span class=\"badge ").append(badgeClass).append("\">").append(badgeText).append("</span>\n");
            sb.append("    </div>\n");

            if (it.result != null) {
                sb.append("    <p class=\"summary-text\">").append(it.result.getSummary()).append("</p>\n");

                // Telemetría / Métricas clave
                Map<String, String> tele = it.result.getTelemetry();
                if (tele != null && !tele.isEmpty()) {
                    sb.append("    <table>\n");
                    sb.append("      <thead><tr><th style=\"width: 40%;\">Parámetro</th><th>Valor Registrado</th></tr></thead>\n");
                    sb.append("      <tbody>\n");
                    for (Map.Entry<String, String> entry : tele.entrySet()) {
                        sb.append("        <tr><td>").append(entry.getKey()).append("</td><td class=\"val\">").append(entry.getValue()).append("</td></tr>\n");
                    }
                    sb.append("      </tbody>\n");
                    sb.append("    </table>\n");
                }

                // Registro de auditoría colapsable
                List<String> audit = it.result.getAuditLog();
                if (audit != null && !audit.isEmpty()) {
                    sb.append("    <details>\n");
                    sb.append("      <summary>Ver registro detallado de auditoría (").append(audit.size()).append(" eventos)</summary>\n");
                    sb.append("      <div class=\"audit-log\">\n");
                    for (String line : audit) {
                        sb.append("        <div>").append(line.replace("<", "&lt;").replace(">", "&gt;")).append("</div>\n");
                    }
                    sb.append("      </div>\n");
                    sb.append("    </details>\n");
                }
            } else {
                sb.append("    <p class=\"summary-text\" style=\"color: var(--text-muted);\">Esta prueba no fue ejecutada durante la sesión actual de diagnóstico.</p>\n");
            }

            sb.append("  </div>\n");
        }

        // Pie de página
        sb.append("  <div class=\"footer\">\n");
        sb.append("    <p>Desarrollado por <strong>").append(AppConfig.DEVELOPER).append("</strong> | ").append(AppConfig.APP_NAME).append(" ").append(AppConfig.VERSION).append("</p>\n");
        sb.append("    <p style=\"margin-top: 4px;\">Informe confidencial generado de forma local. Ninguna credencial o contraseña fue almacenada.</p>\n");
        sb.append("  </div>\n");

        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    public DiagnosticResult toDiagnosticResult(ReportSummary summary) {
        boolean ok = summary.errorCount == 0;
        String badge = ok ? ToolStatus.OK.getDisplayText() : ToolStatus.ATENCION.getDisplayText();
        String sumText = String.format("Informe generado con éxito en '%s'. %d pruebas incluidas (%d OK, %d Atención, %d Error).",
                summary.filePath.getFileName(), summary.toolsIncluded, summary.okCount, summary.warnCount, summary.errorCount);

        Map<String, String> tele = new LinkedHashMap<>();
        tele.put("Archivo del Informe", summary.filePath.toString());
        tele.put("Pruebas Incluidas", String.valueOf(summary.toolsIncluded));
        tele.put("Resultados Correctos", String.valueOf(summary.okCount));
        tele.put("Advertencias / Atención", String.valueOf(summary.warnCount));
        tele.put("Errores Registrados", String.valueOf(summary.errorCount));
        tele.put("Pruebas Pendientes", String.valueOf(summary.pendingCount));

        List<String> audit = new ArrayList<>();
        audit.add("Informe guardado en: " + summary.filePath.toAbsolutePath());
        audit.add("Fecha de generación: " + summary.generatedAt);

        return new DiagnosticResult(ok, badge, sumText, tele, audit);
    }
}
