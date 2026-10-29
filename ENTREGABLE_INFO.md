# Synchro Willy – Extracción de Información del Código Real

> Documento técnico con datos exactos extraídos del código fuente de **Synchro Willy** para la asignatura de **Sistemas Operativos**.

---

### 1. Datos para la guía de ejecución

#### Identificación y Tecnologías
- **Nombre de la aplicación:** `Synchro Willy` (`src/main/java/com/fixtool/model/AppConfig.java:14`)
- **Versión:** `2.0.0` (`src/main/java/com/fixtool/model/AppConfig.java:17`, `pom.xml:8`)
- **Desarrollador:** `Bryan Espinoza` (`src/main/java/com/fixtool/model/AppConfig.java:15`)
- **Lenguaje base:** Java (Nivel de compatibilidad de código: Java 17+, compatible y compilado con JDK 17 hasta JDK 25) (`pom.xml:16-17`)
- **Framework de interfaz gráfica:** JavaFX 21.0.2 (`pom.xml:18, 24-38`)
  - `org.openjfx:javafx-controls:21.0.2`
  - `org.openjfx:javafx-fxml:21.0.2`
  - `org.openjfx:javafx-graphics:21.0.2`
- **Librerías adicionales:**
  - `com.formdev:flatlaf:3.5.4` (`pom.xml:19, 42-45`)
- **Plugins de construcción (Build Plugins):**
  - `org.apache.maven.plugins:maven-compiler-plugin:3.11.0` (`pom.xml:53`)
  - `org.openjfx:javafx-maven-plugin:0.0.8` (`pom.xml:65`)
  - `org.apache.maven.plugins:maven-shade-plugin:3.5.1` (`pom.xml:75`)

#### Requisitos del Sistema
- **Sistema Operativo:** Windows 10 (versión 1809 o superior) o Windows 11 (64 bits, arquitectura x86_64).
- **Dependencias de ejecución:** Java Runtime Environment (JRE) o Java Development Kit (JDK) 17 o superior. No requiere instalación de bases de datos ni servicios externos.
- **Conectividad:** 100 % autónomo y sin conexión a Internet. Todos los recursos visuales (iconos SVG de trazo vectorial, paleta de colores CSS y logo de Willy) y dependencias están integrados localmente.
- **Espacio en disco requerido:**
  - ~45 MB para dependencias JavaFX/FlatLaf y binarios compilados.
  - ~10 MB para la carpeta de trabajo del usuario (`FixTool_Willy`).

#### Pasos Exactos de Ejecución desde Cero en un PC sin Internet

| Método | Instrucciones paso a paso | Dónde queda el resultado |
|---|---|---|
| **Opción A: Script automático `run.bat` (Recomendado)** | 1. Abrir la carpeta del proyecto en el Explorador de Windows.<br>2. Hacer doble clic sobre `run.bat` (o ejecutar `.\run.bat` en CMD/PowerShell).<br>3. El script detecta si Maven está disponible para ejecutar `mvn compile javafx:run`; si no hay Maven instalado, enlaza automáticamente el motor Java local con las librerías en caché (`%USERPROFILE%\.m2\repository`) e inicia `com.fixtool.Launcher`. | Clases en `target\classes`. |
| **Opción B: Mediante Maven** | 1. Abrir PowerShell en la raíz del proyecto.<br>2. Ejecutar: `mvn compile javafx:run` | Clases en `target\classes`. |
| **Opción C: Empaquetar y ejecutar como Fat JAR autónomo** | 1. Generar el paquete con: `mvn clean package`<br>2. Ejecutar el JAR resultante con: `java -jar target\fixtool-2.0.0.jar` | Archivo ejecutable único en `target\fixtool-2.0.0.jar` (`pom.xml:75-100`). |
| **Opción D: Compilación y ejecución directa sin Maven (CLI puro)** | 1. Compilar fuentes Java:<br>`javac -encoding UTF-8 -cp "libs\*" -d target\classes (Get-ChildItem src\main\java -Recurse -Filter *.java)`<br>2. Copiar recursos:<br>`Copy-Item src\main\resources\* target\classes -Recurse -Force`<br>3. Iniciar aplicación:<br>`java -cp "target\classes;libs\*" com.fixtool.Launcher` | Clases compiladas en `target\classes\`. |

#### Cómo se Garantiza en el Código que no Pide Administrador
1. **Sin manifiesto UAC de elevación:** El empaquetado no define directivas `requireAdministrator` ni `highestAvailable`; corre como proceso de usuario estándar (`asInvoker`).
2. **Uso exclusivo de APIs y comandos sin privilegios:** Las herramientas utilizan comandos que cualquier usuario estándar puede ejecutar en Windows: `ping`, `netstat -ano`, `tasklist`, `route print`, `arp -a`, `wevtutil qe`, y llamadas estándar Java (`java.net.Socket`, `java.nio.file`, `java.lang.management`).
3. **Aislamiento en el perfil de usuario:** Todas las escrituras de archivos (cuarentena, informes, archivos de prueba) se confinan estrictamente dentro de `System.getProperty("user.home")`. La cuarentena rechaza de forma explícita cualquier intento de interactuar con carpetas del sistema (`src/main/java/com/fixtool/service/DiskScanService.java:443-455`).
4. **Laboratorio de permisos restringido a archivo temporal propio:** Las modificaciones de ACLs mediante `icacls` operan únicamente sobre `<user.home>\FixTool_Willy\prueba_permisos_willy.tmp`, donde el usuario estándar es dueño legítimo del archivo (`src/main/java/com/fixtool/service/PermissionLabService.java:80, 290`).
5. **Manejo defensivo de excepciones:** En caso de que un comando devuelva código de error por permisos (por ejemplo, `taskkill` intentando finalizar un proceso de otro usuario o de `SYSTEM`), el código captura el fallo con mensajes descriptivos sin intentar elevar privilegios ni bloquear la aplicación (`src/main/java/com/fixtool/service/OpenPortsService.java:378-388`).

#### Primer Arranque: Qué se ve y qué pasa
- **Comportamiento inicial:** No hay pantalla de bienvenida ni asistentes intermedios. Se inicializa directamente la ventana principal de `1280×840` px (`src/main/java/com/fixtool/FixToolApp.java:51`).
- **Título de ventana:** `Synchro Willy 2.0.0` (`src/main/java/com/fixtool/FixToolApp.java:34`).
- **Cabecera superior (TopBar):**
  - Título `Synchro Willy` con subtítulo `Desarrollado por Bryan Espinoza`.
  - Chip de estado de la BOSGAME: muestra `Desconectada` con borde tenue. Al hacer clic, abre el `ConnectionDialog`.
  - Botón `Salir` con icono de apagado.
- **Barra lateral izquierda (Sidebar):**
  - Arriba: logo de Willy en contenedor circular con borde cian y datos del desarrollador.
  - Botón `Inicio`.
  - Botones de las 10 herramientas numeradas del 1 al 10 con sus chips en estado inicial `Sin ejecutar`.
  - Línea divisoria fina estructural.
  - Botón `Acerca de` al final de la barra, con icono de información y sin número.
- **Área principal central:** Abre la vista `Inicio` (`HomeView`), presentando:
  - Tarjeta de bienvenida con Willy y explicación breve.
  - Cuadrícula con 10 tarjetas interactivas correspondientes a las 10 herramientas (número, icono, título oficial, subtítulo y chip de estado).
  - Barra inferior flotante con botones para: *"Ejecutar todas las pruebas"* (dispara la suite completa en segundo plano sin congelar la interfaz) y *"Generar informe"* (navega a la herramienta 10).

#### Diálogo de Conexión con la BOSGAME (`ConnectionDialog.java`)
- **Campos del formulario y valores por defecto:**
  - `IP de la BOSGAME`: `192.168.100.10` (`src/main/java/com/fixtool/model/AppConfig.java:20`)
  - `Carpeta compartida`: `""` (campo vacío, con sugerencia `Nombre de la carpeta (ej. compartida)`) (`src/main/java/com/fixtool/model/ConnectionConfig.java:24`)
  - `Usuario`: `FAST` (`src/main/java/com/fixtool/model/AppConfig.java:21`)
  - `Contraseña`: campo oculto `PasswordField` (vacío por defecto, con casilla `Mostrar contraseña`). **La contraseña nunca se guarda en disco ni en archivos de configuración; vive únicamente en memoria RAM durante la sesión** (`src/main/java/com/fixtool/model/ConnectionConfig.java:12-14, 42`).
- **Qué ocurre al hacer clic en "Conectar" (`ConnectionDialog.java:203-263`):**
  1. Valida el formato IPv4 del campo IP (`^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$`).
  2. Lanza una tarea en segundo plano (`Task<String>`) para no bloquear la interfaz.
  3. **Paso 1:** Verifica si el equipo remoto responde mediante `InetAddress.getByName(ip).isReachable(2000)`. Si no responde, aborta con mensaje descriptivo.
  4. **Paso 2:** Verifica si el puerto TCP 445 (SMB) está abierto mediante `new Socket().connect(..., 445, 2000)`.
  5. **Paso 3:** Al tener éxito, actualiza el modelo reactivo `ConnectionConfig`, almacena temporalmente la credencial en memoria, cambia el chip de la cabecera a `Conectada ✔` en color verde y habilita las operaciones remotas en las herramientas 4 y 5.

#### Carpetas y Archivos Creados en el Directorio Personal del Usuario
Todas las rutas parten de `AppConfig.getWorkDir()`, ubicada en:  
`C:\Users\<Usuario>\FixTool_Willy\` (`src/main/java/com/fixtool/model/AppConfig.java:30-32`):

| Ruta exacta relativa a `<user.home>` | Tipo | Para qué sirve |
|---|:---:|---|
| `FixTool_Willy\` | Carpeta | Raíz de trabajo de la aplicación para aislar todos los artefactos locales sin tocar carpetas del sistema. |
| `FixTool_Willy\Cuarentena\` | Carpeta | Directorio raíz del almacén de cuarentena segura. |
| `FixTool_Willy\Cuarentena\<timestamp>\` | Carpeta | Subcarpeta de lote por fecha/hora (ej. `20261028_120000`) donde se mueven los archivos seleccionados para cuarentena. |
| `FixTool_Willy\Cuarentena\<timestamp>\manifiesto.txt` | Archivo | Registro con formato `ruta_original\|timestamp\|tamano` para permitir la restauración exacta a su ubicación de origen. |
| `FixTool_Willy\Informes\` | Carpeta | Directorio de salida de los informes generados. |
| `FixTool_Willy\Informes\informe_diagnostico_YYYYMMDD_HHmmss.html` | Archivo | Informe final HTML autocontenido para entregar o imprimir. |
| `FixTool_Willy\prueba_permisos_willy.tmp` | Archivo | Archivo temporal de prueba de 4 líneas creado por el Laboratorio de permisos para ensayar lecturas, escrituras y cambios de ACLs con `icacls`. |
| `FixTool_Willy\estabilidad_historico.txt` | Archivo | Registro histórico de pruebas de estabilidad (timestamp, latencia media, jitter y pérdida de paquetes). |
| `FixTool_Willy\Descargas_BOSGAME\` | Carpeta | Carpeta de destino local cuando se bajan archivos desde la BOSGAME en la herramienta Puente de archivos. |

#### Salida Controlada: Qué Hace Paso a Paso
Implementada en `FixToolApp.java:78-145` (al presionar el botón `Salir` o la 'X' de la ventana):
1. **Intercepción del evento:** Se ejecuta `e.consume()` para evitar que el sistema operativo cierre bruscamente la aplicación.
2. **Evaluación del estado de la sesión:** Comprueba si existen diagnósticos completados pero sin informe HTML generado.
   - Si hay resultados pendientes de informe, muestra un diálogo con las opciones: *"Generar informe y salir"*, *"Salir sin generar"* y *"Cancelar"*.
   - Si no hay pendientes, muestra diálogo de confirmación estándar (*"Salir"* / *"Cancelar"*).
3. **Limpieza de seguridad (`limpiarAlSalir()`):**
   - **Restaura permisos:** Invoca `PermissionLabService.resetFilePermissions()`, ejecutando `icacls /remove:d` y `icacls /grant :(F)` sobre el archivo de prueba para garantizar que no queden bloqueos en el disco.
   - **Borrado de contraseña:** Invoca `connectionConfig.clearPassword()`, sobrescribiendo la contraseña en memoria con cadena vacía.
   - **Cierre de conexión:** Invoca `connectionConfig.setConnected(false)`.
4. **Terminación del proceso:** Cierra el `Stage` principal y finaliza la máquina virtual limpiamente con `System.exit(0)`.

#### Los 5 Mensajes de Error Más Probables al Ejecutar o Conectar

| # | Mensaje real en el código | Causa exacta en el sistema | Archivo:línea |
|---|---|---|---|
| **1** | `"El equipo <IP> no responde. Verifica que está encendido y conectado por cable."` | Fallo de respuesta ICMP en `verificarAlcanzable(ip, 2000)`. El cable Ethernet está desconectado, la BOSGAME está apagada o la IP configurada no coincide con el segmento físico. | `src/main/java/com/fixtool/ui/components/ConnectionDialog.java:222` |
| **2** | `"El equipo responde pero el servicio de carpetas compartidas no está disponible en <IP>."` | El equipo responde al ping pero el socket TCP al puerto 445 expira o es rechazado. El servicio Samba/SMB está detenido o bloqueado por firewall en la BOSGAME. | `src/main/java/com/fixtool/ui/components/ConnectionDialog.java:229` |
| **3** | `"Escribe una dirección IP válida (ej. 192.168.100.10)"` | La entrada en el campo de texto no coincide con la expresión regular IPv4 `^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$`. | `src/main/java/com/fixtool/ui/components/ConnectionDialog.java:322` |
| **4** | `"No se puede alcanzar el equipo remoto en <host> (puerto 445 SMB no responde). Verifique que esté encendido y conectado a la red."` | La herramienta Puente de archivos intentó explorar el recurso UNC (`\\192.168.100.10\compartida`), pero la comprobación previa de socket al puerto 445 falló tras 1500 ms de timeout. | `src/main/java/com/fixtool/service/FileBridgeService.java:127` |
| **5** | `"Solo se pueden mover elementos de tu carpeta personal (<nombre> está fuera)."` | El usuario intentó enviar a cuarentena un archivo fuera de `user.home` (por ejemplo en `C:\Windows` o raíz del sistema), bloqueado por el filtro de seguridad de la app. | `src/main/java/com/fixtool/service/DiskScanService.java:444` |

---

### 2. Tabla de comandos y llamadas del sistema

| Herramienta | Comando o llamada exacta (con parámetros) | Función, en lenguaje claro | Qué dato obtiene o qué modifica | ¿Solo lectura o modifica? | ¿Requiere administrador? | Archivo:línea |
|---|---|---|---|:---:|:---:|---|
| **1. Chequeo de Estado** | `cmd.exe /c ver` | Ejecuta el comando nativo de Windows para conocer la versión exacta del sistema operativo. | Obtiene la cadena de versión y número de compilación (build) del kernel. | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:220` |
| **1. Chequeo de Estado** | `cmd.exe /c powershell -NoProfile -Command "Get-CimInstance Win32_Battery \| Select-Object -ExpandProperty EstimatedChargeRemaining"` | Consulta a Windows por medio de WMI si hay batería y cuánta carga le queda. | Obtiene el porcentaje numérico de batería restante (o determina que es un PC de escritorio). | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:238` |
| **1. Chequeo de Estado** | `cmd.exe /c powershell -NoProfile -Command "Get-CimInstance Win32_OperatingSystem \| ForEach-Object { '' + $_.TotalVisibleMemorySize + '\|' + $_.FreePhysicalMemory }"` | Consulta alternativa a Windows para obtener la memoria RAM si el monitor interno no responde. | Obtiene la cantidad total y libre de memoria RAM en kilobytes. | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:294` |
| **1. Chequeo de Estado** | `OperatingSystemMXBean.getTotalMemorySize()`, `getFreeMemorySize()`, `getCpuLoad()` | Consulta las métricas de rendimiento del procesador y memoria directamente a la máquina virtual Java. *(Equivalente: `wmic OS get FreePhysicalMemory,TotalVisibleMemorySize`)* | Obtiene los bytes de memoria RAM física total, libre y el porcentaje de uso actual del procesador. | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:105-107` |
| **1. Chequeo de Estado** | `File.listRoots()`, `getTotalSpace()`, `getUsableSpace()` | Pregunta al sistema operativo cuáles discos están conectados y cuánto espacio tienen. *(Equivalente: `wmic logicaldisk get Caption,Size,FreeSpace`)* | Obtiene la lista de unidades montadas (`C:\`, etc.), su tamaño total y el espacio disponible en bytes. | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:132-136` |
| **1. Chequeo de Estado** | `ManagementFactory.getRuntimeMXBean().getUptime()` | Consulta el tiempo de funcionamiento continuo del sistema. *(Equivalente: `wmic os get lastbootuptime`)* | Obtiene los milisegundos que lleva el equipo encendido sin reiniciarse. | Solo lectura | No | `src/main/java/com/fixtool/service/SystemInfoService.java:151` |
| **2. Espacio y limpieza** | `Files.newDirectoryStream(dir)`, `File.listFiles()`, `File.length()` | Recorre las carpetas del usuario para analizar qué archivos ocupan más espacio. *(Equivalente: `dir /s /b`)* | Obtiene los nombres, tamaños y fechas de modificación de archivos en el perfil del usuario. | Solo lectura | No | `src/main/java/com/fixtool/service/DiskScanService.java:180-210, 392` |
| **2. Espacio y limpieza** | `Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING)` | Mueve un archivo a la carpeta de cuarentena o lo regresa a su lugar de origen sin borrarlo. *(Equivalente: `move <origen> <destino>`)* | Modifica la ubicación física del archivo en el sistema de archivos del disco. | Modifica | No | `src/main/java/com/fixtool/service/DiskScanService.java:350, 470, 571, 619` |
| **2. Espacio y limpieza** | `Files.write(manifestPath, manifestLines, StandardOpenOption.CREATE, ...)` | Guarda una lista de control con la ruta original de los archivos puestos en cuarentena. *(Equivalente: `echo ... >> manifiesto.txt`)* | Crea o actualiza el archivo `manifiesto.txt` dentro del lote de cuarentena. | Modifica | No | `src/main/java/com/fixtool/service/DiskScanService.java:361, 482, 594` |
| **2. Espacio y limpieza** | `File.delete()` | Borra de forma permanente archivos huérfanos solo si el usuario pide vaciar la cuarentena. *(Equivalente: `del /f /q`)* | Elimina físicamente el archivo seleccionado del disco duro. | Modifica | No | `src/main/java/com/fixtool/service/DiskScanService.java:650` |
| **3. Análisis de Conexión** | `NetworkInterface.getNetworkInterfaces()`, `ni.isUp()`, `ni.isLoopback()` | Revisa si el cable de red o el adaptador Wi-Fi de la computadora están encendidos y conectados. *(Equivalente: `Get-NetAdapter` o `ipconfig /all`)* | Obtiene el estado físico y nombre del adaptador de red activo. | Solo lectura | No | `src/main/java/com/fixtool/service/NetworkDoctorService.java:83-115` |
| **3. Análisis de Conexión** | `InterfaceAddress.getNetworkPrefixLength()` y cálculo `(localInt & mask) == (targetInt & mask)` | Calcula matemáticamente si la computadora y la BOSGAME comparten el mismo segmento de red mediante máscara CIDR. | Compara las direcciones IP bit a bit para saber si pueden comunicarse directamente sin router. | Solo lectura | No | `src/main/java/com/fixtool/service/NetworkDoctorService.java:170-184` |
| **3. Análisis de Conexión** | `cmd.exe /c "ping -n 1 -w <timeoutMs> <ip>"` | Envía un paquete de prueba a la BOSGAME para ver si contesta y con qué velocidad. | Obtiene el tiempo de respuesta en milisegundos y el valor TTL del paquete devuelto. | Solo lectura | No | `src/main/java/com/fixtool/service/NetworkDoctorService.java:343` |
| **3. Análisis de Conexión** | `Socket.connect(new InetSocketAddress(targetIp, 445), 1200)` | Intenta una conexión rápida al puerto 445 para comprobar si el servicio de archivos compartidos está encendido. *(Equivalente: `Test-NetConnection -Port 445`)* | Obtiene el estado del puerto SMB (abierto o cerrado). | Solo lectura | No | `src/main/java/com/fixtool/service/NetworkDoctorService.java:261` |
| **4. Estabilidad y velocidad** | `cmd.exe /c "ping -n 1 -w 700 <targetIp>"` | Realiza pings continuos para medir qué tan estable es el cable de red y si se pierden datos. | Obtiene la latencia individual de cada intento, el cálculo de variación (jitter) y pérdida de paquetes. | Solo lectura | No | `src/main/java/com/fixtool/service/StabilitySpeedService.java:92` |
| **4. Estabilidad y velocidad** | `cmd.exe /c powershell -NoProfile -Command "Get-NetAdapter \| Where-Object { $_.Status -eq 'Up' -and $_.InterfaceDescription -notlike '*Virtual*' } \| Select-Object -ExpandProperty LinkSpeed -First 1"` | Pregunta a Windows la velocidad máxima negociada por la tarjeta de red física. | Obtiene la velocidad teórica del cable (ejemplo: "1 Gbps" o "100 Mbps"). | Solo lectura | No | `src/main/java/com/fixtool/service/StabilitySpeedService.java:303` |
| **4. Estabilidad y velocidad** | `Files.newOutputStream(testFile)`, `Files.newInputStream(testFile)` | Escribe y lee un archivo temporal en la BOSGAME para medir la velocidad real de subida y bajada en MB/s. *(Equivalente: `robocopy /z`)* | Mide la tasa de transferencia en Megabytes por segundo (MB/s). | Modifica (temporal) | No | `src/main/java/com/fixtool/service/StabilitySpeedService.java:239, 257` |
| **4. Estabilidad y velocidad** | `Files.deleteIfExists(testFile)` | Elimina el archivo de prueba creado durante el test de velocidad una vez finalizado. *(Equivalente: `del <archivo>`)* | Borra el archivo temporal `speedtest_*.tmp` en la carpeta compartida. | Modifica | No | `src/main/java/com/fixtool/service/StabilitySpeedService.java:273` |
| **4. Estabilidad y velocidad** | `Files.writeString(histFile, line, ...)` | Guarda el resultado de la prueba de estabilidad en un archivo de texto para comparar después. *(Equivalente: `echo ... >> estabilidad_historico.txt`)* | Escribe fecha, latencia y pérdida en `estabilidad_historico.txt`. | Modifica | No | `src/main/java/com/fixtool/service/StabilitySpeedService.java:207` |
| **5. Puente de archivos** | `Socket.connect(new InetSocketAddress(host, 445), timeoutMs)` | Comprueba rápidamente que el servidor de archivos esté respondiendo antes de abrir carpetas remotas. *(Equivalente: `Test-NetConnection -Port 445`)* | Determina si el puerto 445 está listo para recibir peticiones de transferencia. | Solo lectura | No | `src/main/java/com/fixtool/service/FileBridgeService.java:101` |
| **5. Puente de archivos** | `Files.newDirectoryStream(dir)` | Muestra la lista de archivos tanto de la computadora local como de la BOSGAME compartida (`\\IP\carpeta`). *(Equivalente: `dir \\<host>\<share>`)* | Obtiene los nombres, tamaños y fechas de los archivos disponibles. | Solo lectura | No | `src/main/java/com/fixtool/service/FileBridgeService.java:139` |
| **5. Puente de archivos** | `Files.newInputStream(source)`, `Files.newOutputStream(destFile, ...)` | Copia un archivo desde la PC hacia la BOSGAME o viceversa en bloques de memoria de 64 KB. *(Equivalente: `copy /b <origen> <destino>`)* | Escribe el archivo transferido en el destino seleccionado. | Modifica | No | `src/main/java/com/fixtool/service/FileBridgeService.java:191-192` |
| **5. Puente de archivos** | `MessageDigest.getInstance("SHA-256")` y `md.update(buffer)` | Calcula la firma digital única SHA-256 del archivo en ambos lados para verificar que no se corrompió. *(Equivalente: `CertUtil -hashfile <archivo> SHA256`)* | Obtiene la huella criptográfica de 64 caracteres de los dos archivos para compararlos. | Solo lectura | No | `src/main/java/com/fixtool/service/FileBridgeService.java:184-185, 203, 230` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /deny "<username>:(W)"` | Bloquea explícitamente el permiso de escritura del usuario sobre el archivo de prueba mediante la lista de seguridad de Windows. | Modifica la lista de control de acceso (ACL) NTFS del archivo temporal para impedir escribir. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:126` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /grant "<username>:(W)"` | Devuelve el permiso de escritura al usuario sobre el archivo de prueba. | Modifica la lista de control de acceso (ACL) NTFS para permitir guardar cambios. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:129` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /deny "<username>:(R)"` | Bloquea el permiso de lectura en la lista de seguridad de Windows para que el archivo no se pueda abrir. | Modifica la ACL NTFS del archivo para denegar la lectura al usuario. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:135` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /grant "<username>:(R)"` | Concede nuevamente permiso de lectura al usuario sobre el archivo de prueba. | Modifica la ACL NTFS para volver a permitir leer el archivo. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:138` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /remove:d "<username>"` | Quita todas las reglas de bloqueo que se hayan colocado sobre el archivo de prueba. | Elimina las entradas de denegación en la lista de seguridad NTFS. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:279` |
| **6. Laboratorio de permisos** | `cmd.exe /c icacls "<file>" /grant "<username>:(F)"` | Otorga control total al usuario sobre el archivo de prueba al reiniciar la herramienta o salir de la app. | Restablece todos los permisos normales sobre el archivo. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:280` |
| **6. Laboratorio de permisos** | `File.setReadOnly()`, `File.setWritable()`, `File.setReadable()` | Modifica el atributo de solo lectura del archivo de prueba en el sistema de archivos. *(Equivalente: `attrib +r <archivo>` o `attrib -r <archivo>`)* | Activa o desactiva la bandera de solo lectura en los atributos básicos de Windows. | Modifica | No | `src/main/java/com/fixtool/service/PermissionLabService.java:125, 128, 134, 137, 275-276` |
| **6. Laboratorio de permisos** | `Files.readString(path)`, `Files.writeString(path, ...)` | Intenta leer o escribir en el archivo de prueba para ver en la práctica si Windows lo permite o arroja error de acceso denegado. | Comprueba en tiempo real si el sistema operativo genera la excepción `AccessDeniedException`. | Modifica / Lee | No | `src/main/java/com/fixtool/service/PermissionLabService.java:152, 180, 204` |
| **7. Mapeo de Red Local** | `cmd.exe /c "route print 0.0.0.0"` | Lee la tabla interna de rutas de Windows para saber cuál es la IP del router o puerta de enlace predeterminada. | Obtiene la dirección IP del router o gateway local. | Solo lectura | No | `src/main/java/com/fixtool/service/PcMapService.java:477` |
| **7. Mapeo de Red Local** | `cmd.exe /c "arp -a"` | Lee la tabla ARP de Windows para saber qué otros dispositivos están conectados en la misma red local. | Obtiene la lista de direcciones IP y sus direcciones físicas MAC en la subred. | Solo lectura | No | `src/main/java/com/fixtool/service/PcMapService.java:497` |
| **7. Mapeo de Red Local** | `cmd.exe /c "ping -n 1 -w 350 <ip>"` | Envía un paquete rápido al dispositivo detectado para examinar su valor TTL. | Obtiene el TTL para deducir qué sistema operativo tiene el equipo (Linux, Windows o router). | Solo lectura | No | `src/main/java/com/fixtool/service/PcMapService.java:348` |
| **7. Mapeo de Red Local** | `Socket.connect(new InetSocketAddress(dev.ip, port), 90)` | Prueba si el dispositivo tiene abiertos puertos comunes (80, 443, 445, 139, 3389, 22) sin requerir privilegios. *(Equivalente: `Test-NetConnection -Port <puerto>`)* | Identifica si el dispositivo ofrece página web, carpetas compartidas o acceso remoto. | Solo lectura | No | `src/main/java/com/fixtool/service/PcMapService.java:393` |
| **7. Mapeo de Red Local** | `NetworkInterface.getHardwareAddress()` | Consulta a la tarjeta de red su identificador físico de fábrica. *(Equivalente: `getmac /fo csv`)* | Obtiene la dirección física MAC de la computadora local. | Solo lectura | No | `src/main/java/com/fixtool/service/PcMapService.java:509` |
| **8. Monitor de Eventos** | `cmd.exe /c "wevtutil qe System /q:\"*[System[(EventID=41 or EventID=1074 or EventID=6008 or EventID=6006)]]\" /c:1 /rd:true /f:text"` | Consulta el registro de eventos de Windows para averiguar por qué se reinició o apagó el equipo la última vez. *(Equivalente: `Get-WinEvent` en PowerShell)* | Obtiene el evento de apagado y su causa: corte de luz/apagado forzado (ID 41/6008) o apagado limpio (ID 1074/6006). | Solo lectura | No | `src/main/java/com/fixtool/service/EventDetectiveService.java:271` |
| **8. Monitor de Eventos** | `cmd.exe /c "wevtutil qe <logName> /c:<count> /rd:true /f:text"` | Lee los últimos eventos guardados en los registros del sistema o de las aplicaciones en orden cronológico inverso. *(Equivalente: `Get-WinEvent -LogName <log>`)* | Obtiene fecha, origen, nivel de gravedad y mensaje de los eventos recientes. | Solo lectura | No | `src/main/java/com/fixtool/service/EventDetectiveService.java:319` |
| **9. Puertos Abiertos** | `cmd.exe /c "netstat -ano"` | Consulta todas las conexiones y puertos de red abiertos en la computadora junto con el número de programa (PID) que los usa. | Obtiene protocolo, puerto local, estado de conexión y el identificador de proceso (PID). | Solo lectura | No | `src/main/java/com/fixtool/service/OpenPortsService.java:239` |
| **9. Puertos Abiertos** | `tasklist /FO CSV /NH` | Obtiene la lista de programas en ejecución para saber el nombre de la aplicación que corresponde a cada puerto abierto. | Relaciona cada número de PID con el nombre del ejecutable (ejemplo: `node.exe`, `python.exe`). | Solo lectura | No | `src/main/java/com/fixtool/service/OpenPortsService.java:405` |
| **9. Puertos Abiertos** | `taskkill /PID <pid> /F` | Cierra a la fuerza un programa que esté usando un puerto cuando el usuario confirma su cierre (protegiendo procesos críticos del sistema). | Detiene el proceso correspondiente para liberar el puerto de red. | Modifica | Solo si el proceso es de SYSTEM u otro usuario; para programas del usuario actual no requiere admin | `src/main/java/com/fixtool/service/OpenPortsService.java:378` |
| **10. Informe de diagnóstico** | `Files.writeString(reportFile, html, StandardCharsets.UTF_8)` | Crea un archivo HTML independiente con todos los datos y gráficas recolectados durante la sesión de diagnóstico. *(Equivalente: `echo ... > informe.html`)* | Guarda en disco el archivo `informe_diagnostico_YYYYMMDD_HHmmss.html`. | Modifica | No | `src/main/java/com/fixtool/service/ReportService.java:83` |
| **10. Informe de diagnóstico** | `Desktop.getDesktop().open(reportFile)` / `Desktop.open(reportsFolder)` | Abre el informe generado en el navegador web predeterminado o abre la carpeta de informes en el Explorador de Windows. *(Equivalente: `cmd /c start <archivo>` o `explorer.exe <carpeta>`)* | Muestra el informe en pantalla para que el usuario pueda revisarlo, guardarlo o imprimirlo en PDF. | Solo lectura | No | `src/main/java/com/fixtool/ui/InformeView.java:414, 436` |

