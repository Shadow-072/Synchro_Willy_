package com.fixtool.model;

import java.util.function.Function;

/**
 * Representa una de las 10 operaciones de diagnóstico de Synchro Willy.
 */
public class DiagnosticAction {

    private final int id;
    private final String title;
    private final String shortDescription;
    private final String detailedDescription;
    private final String iconSymbol;
    private final String category;
    private final Function<ConnectionConfig, DiagnosticResult> executor;

    public DiagnosticAction(int id,
                            String title,
                            String shortDescription,
                            String detailedDescription,
                            String iconSymbol,
                            String category,
                            Function<ConnectionConfig, DiagnosticResult> executor) {
        this.id = id;
        this.title = title;
        this.shortDescription = shortDescription;
        this.detailedDescription = detailedDescription;
        this.iconSymbol = iconSymbol;
        this.category = category;
        this.executor = executor;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getSubtitle() {
        return shortDescription;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public String getIconSymbol() {
        return iconSymbol;
    }

    public String getCategory() {
        return category;
    }

    public DiagnosticResult execute(ConnectionConfig config) {
        if (executor != null) {
            return executor.apply(config);
        }
        return null;
    }
}
