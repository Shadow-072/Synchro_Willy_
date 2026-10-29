# ⚡ Synchro Willy - Suite de Red y Diagnóstico para Windows

**Arquitectura:** Java 17+ | JavaFX 21 | Dark Pro Theme | Apache Maven  
**Diseño:** Dark Pro (Modo Oscuro Mate `#0D1117`, Acentos en Azul Cían `#00B4D8` y Tipografía Limpia Segoe UI)  
**Desarrollador:** Bryan Espinoza  
**Proyecto:** `com.fixtool:fixtool:2.0.0`

---

## 📋 Descripción General

**Synchro Willy** es una aplicación de escritorio de diagnóstico de red y sistema desarrollada en **Java** con **JavaFX** y gestionada mediante **Apache Maven**. Su diseño estético y ergonómico está fundamentado en el sistema de diseño **Dark Pro**: superficies oscuras mate (`#0D1117`, `#161B22`, `#1C2128`), contrastes nítidos en azul cían (`#00B4D8`), semáforo de estado (verde/amarillo/rojo), micro-animaciones fluidas de transición y un panel superior con diálogo modal de conexión para la BOSGAME.

---

## 🏗️ Arquitectura Modular del Proyecto

El código está estructurado bajo principios de separación de responsabilidades y diseño orientado a componentes:

```text
fixtool-willy/
├── pom.xml                                      # Configuración Maven con JavaFX y FlatLaf
├── run.bat                                      # Lanzador rápido para Windows
├── README.md                                    # Documentación técnica del proyecto
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── fixtool/
        │           ├── FixToolApp.java          # Aplicación principal JavaFX y gestor de ciclo de vida
        │           ├── Launcher.java            # Bootstrap launcher para arranque independiente y Fat JAR
        │           ├── model/
        │           │   ├── ConnectionConfig.java   # Modelo reactivo con StringProperty para enlace en vivo
        │           │   ├── DiagnosticAction.java   # Definición y metadatos de las 10 acciones del taller
        │           │   └── DiagnosticResult.java   # Modelo estructurado de resultados y telemetría visual
        │           ├── service/
        │           │   └── DiagnosticService.java  # Lógica de las 10 acciones (seguras, sin elevación UAC)
        │           ├── ui/
        │           │   ├── SplashView.java      # Pantalla de bienvenida geométrica y tipográfica minimalista
        │           │   ├── MainView.java        # Contenedor principal con TopBar y gestor de vistas
        │           │   ├── GridView.java        # Cuadrícula interactiva de las 10 herramientas
        │           │   ├── ActionDetailView.java # Pestaña / Panel en modo placeholder "Herramienta en desarrollo"
        │           │   └── components/
        │           │       └── DynamicTopBar.java   # Panel superior prominente con campos de red ampliados
        │           └── util/
        │               └── AnimationHelper.java # Utilidades de fundido (fade), deslizamiento y cambio suave
        └── resources/
            └── styles/
                └── dark-minimalist.css          # Hoja de estilos FlatLaf Minimalist Tech (#121212 / #00B4D8)
```

---

## ✨ Características Principales

### 1. 🌟 Pantalla de Bienvenida (Splash / Landing View)
- Estética **Minimalist Tech** con fondo mate (`#121212`) y tarjeta central elevada (`#1A1A1A`).
- **Diseño Puramente Tipográfico y Geométrico:** Emblema poligonal y vectorial moderno sin imágenes externas ni dependencias de archivos.
- Título oficial **Synchro Willy**.
- **Botón de Acceso Animado:** Botón *"🚀 Explorar Funciones"* con micro-animación de escala en hover y fundido suave que da paso al panel operativo.

### 2. 🎛️ Panel Superior de Configuración Dinámica (Ampliación Visual)
- **Mayor Amplitud y Contraste:** Diseñado con mayor espaciado interno (*padding*), fondo con contraste diferenciado (`#181C24`) y sombra perimetral suave.
- **Botón de Control Lateral (Hamburguesa `☰`):** Permite colapsar o expandir el menú lateral izquierdo con una animación suave, optimizando y maximizando el espacio de trabajo.
- **Campos de Texto con Iconos y Etiquetas Claras:**
  - `🌐 IP TARGET / BOSGAME:` `192.168.100.10` (predeterminado).
  - `👤 USUARIO:` `FAST` (predeterminado).
  - `🔑 CONTRASEÑA:` `12345` (predeterminado, enmascarada con `PasswordField`).
- **Reactividad Instantánea:** Utiliza propiedades observables `StringProperty` con enlace bidireccional (`bindBidirectional`). Al modificar cualquier carácter, la configuración se propaga al vuelo a todas las vistas.
- **Indicador en Vivo (*Live Chip*):** Muestra el estado activo de la conexión (`● CONECTADO: 192.168.100.10 (FAST)`).
- Botón rápido para restaurar valores de fábrica (`🔄 Restablecer`) y salida controlada (`✕`).

### 3. 📂 Panel Lateral Izquierdo Colapsable (Sidebar de Navegación)
- **Navegación Vertical por Herramientas:** Las 10 herramientas y la vista general están ubicadas en el panel lateral izquierdo con sus iconos distintivos.
- **Animación Fluida de Expansión/Colapso:** Con el botón `☰`, el panel se desliza y colapsa suavemente (0px de ancho) maximizando el área central.

### 4. 🚧 Modo Placeholder Ultra-Limpio (`ActionDetailView`)
- Al abrir cualquiera de las 10 herramientas, el área de contenido principal muestra de forma centrada, limpia y minimalista **únicamente** el texto:
  > **"Herramienta en desarrollo"**

### 5. ⚡ Catálogo de los 10 Módulos de Herramientas
1. `🖥️ 1. Chequeo de Estado`: Cómo está tu PC.
2. `🔍 2. Espacio y limpieza`: Libera espacio sin riesgo.
3. `📶 3. Análisis de Conexión`: Por qué conecta o no.
4. `🎯 4. Estabilidad y velocidad`: Qué tan buena es la conexión.
5. `🔌 5. Puente de archivos`: Pasa archivos a la BOSGAME.
6. `📝 6. Laboratorio de permisos`: Prueba leer y escribir.
7. `📖 7. Mapeo de Red Local`: Tus conexiones y dispositivos.
8. `✍️ 8. Monitor de Eventos`: Qué le pasó a tu PC.
9. `🧹 9. Puertos Abiertos`: Qué está expuesto en la red.
10. `📊 10. Informe de diagnóstico`: Resumen para entregar.

---

## 🚀 Compilación y Ejecución

```bash
# Opción 1: Ejecutar directamente con el plugin de JavaFX
mvn clean javafx:run

# Opción 2: Generar el Fat JAR empaquetado autónomo
mvn clean package
java -jar target/fixtool-2.0.0.jar

# Opción 3: Ejecutar con el script de Windows
run.bat
```
