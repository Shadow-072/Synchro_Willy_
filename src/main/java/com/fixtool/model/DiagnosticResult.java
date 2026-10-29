package com.fixtool.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado estructurado de una ejecución de diagnóstico en Synchro Willy.
 * Reemplaza consolas o terminales de texto por telemetría limpia, insignias y métricas clave.
 */
public class DiagnosticResult {

    private final boolean success;
    private final String statusBadge;
    private final String summary;
    private final Map<String, String> telemetry;
    private final List<String> auditPoints;
    private final String timestamp;

    public DiagnosticResult(boolean success,
                            String statusBadge,
                            String summary,
                            Map<String, String> telemetry,
                            List<String> auditPoints) {
        this.success = success;
        this.statusBadge = statusBadge;
        this.summary = summary;
        this.telemetry = telemetry != null ? new LinkedHashMap<>(telemetry) : Collections.emptyMap();
        this.auditPoints = auditPoints != null ? List.copyOf(auditPoints) : Collections.emptyList();
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isOk() {
        return success;
    }

    public String getStatusBadge() {
        return statusBadge;
    }

    public String getBadge() {
        return statusBadge;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, String> getTelemetry() {
        return Collections.unmodifiableMap(telemetry);
    }

    public List<String> getAuditPoints() {
        return auditPoints;
    }

    public List<String> getAuditLog() {
        return auditPoints;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
