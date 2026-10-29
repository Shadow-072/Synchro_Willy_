package com.fixtool.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Estado de la sesión actual de Synchro Willy.
 * Rastrea el estado y hora de última ejecución de cada una de las 10 herramientas,
 * y guarda sus resultados para alimentar el informe final.
 *
 * Patrón Singleton: una única instancia durante toda la ejecución de la app.
 */
public class SessionState {

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static final SessionState INSTANCE = new SessionState();

    public static SessionState getInstance() {
        return INSTANCE;
    }

    // ── Datos por herramienta (id 1-10) ──────────────────────────────────────
    private final Map<Integer, ObjectProperty<ToolStatus>> statuses = new HashMap<>();
    private final Map<Integer, StringProperty>             lastRun  = new HashMap<>();
    private final Map<Integer, DiagnosticResult>           results  = new HashMap<>();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // Constructor privado: inicializa las 10 herramientas como "Sin ejecutar"
    private SessionState() {
        for (int i = 1; i <= 10; i++) {
            statuses.put(i, new SimpleObjectProperty<>(ToolStatus.SIN_EJECUTAR));
            lastRun .put(i, new SimpleStringProperty(""));
        }
    }

    // ── Acceso a propiedades observables (para bindings en la UI) ─────────────

    /** Propiedad observable del estado de la herramienta indicada. */
    public ObjectProperty<ToolStatus> statusProperty(int id) {
        return statuses.get(id);
    }

    /** Estado actual de la herramienta. */
    public ToolStatus getStatus(int id) {
        return statuses.get(id).get();
    }

    /** Propiedad observable de la hora de última ejecución. */
    public StringProperty lastRunProperty(int id) {
        return lastRun.get(id);
    }

    /** Resultado guardado (puede ser null si la herramienta no se ha ejecutado aún). */
    public DiagnosticResult getResult(int id) {
        return results.get(id);
    }

    // ── Actualización del estado ──────────────────────────────────────────────

    /**
     * Registra el resultado de una herramienta y actualiza su estado y hora.
     * Llama desde la UI después de ejecutar una herramienta.
     */
    public void setStatus(int id, ToolStatus status, DiagnosticResult result) {
        statuses.get(id).set(status);
        lastRun .get(id).set(LocalDateTime.now().format(TIME_FMT));
        if (result != null) {
            results.put(id, result);
        }
    }

    /**
     * Registra un resultado directamente, infiriendo el estado de la herramienta.
     */
    public void setResult(int id, DiagnosticResult result) {
        ToolStatus st = ToolStatus.fromResult(result);
        if (result != null && result.getStatusBadge() != null && result.getStatusBadge().equalsIgnoreCase(ToolStatus.ATENCION.getDisplayText())) {
            st = ToolStatus.ATENCION;
        }
        setStatus(id, st, result);
    }

    /** Comprueba si ya existen resultados registrados en la sesión. */
    public boolean hasResults() {
        return !results.isEmpty();
    }

    // ── Estadísticas globales ─────────────────────────────────────────────────

    /** Cuántas herramientas ya se han ejecutado en esta sesión. */
    public long getExecutedCount() {
        return statuses.values().stream()
                .filter(p -> p.get() != ToolStatus.SIN_EJECUTAR)
                .count();
    }

    /** Estado global de la sesión: el peor estado entre todas las herramientas ejecutadas. */
    public ToolStatus getGlobalStatus() {
        boolean anyProblem   = statuses.values().stream()
                .anyMatch(p -> p.get() == ToolStatus.PROBLEMA);
        boolean anyAtencion  = statuses.values().stream()
                .anyMatch(p -> p.get() == ToolStatus.ATENCION);

        if (anyProblem)  return ToolStatus.PROBLEMA;
        if (anyAtencion) return ToolStatus.ATENCION;

        long executed = getExecutedCount();
        return executed > 0 ? ToolStatus.OK : ToolStatus.SIN_EJECUTAR;
    }

    /** Limpia todos los estados (reinicio de sesión). */
    public void reset() {
        for (int i = 1; i <= 10; i++) {
            statuses.get(i).set(ToolStatus.SIN_EJECUTAR);
            lastRun .get(i).set("");
            results.remove(i);
        }
    }
}
