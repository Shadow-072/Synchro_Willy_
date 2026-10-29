package com.fixtool;

/**
 * Bootstrap Launcher para ejecución independiente y empaquetado en JAR.
 * Al no extender directamente de Application, permite iniciar la máquina virtual
 * sin requerir módulos de JavaFX en el classpath previo.
 */
public class Launcher {

    public static void main(String[] args) {
        FixToolApp.main(args);
    }
}
