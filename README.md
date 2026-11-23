# Ports PS2

App de Android (APK) que funciona como **launcher tipo nativo por juego** para tus juegos de PlayStation 2. Versión actual: **1.2** (versionCode 3).

- **El port es REAL (v1.2)**: al importar, la imagen del juego **se copia dentro de la app** y queda instalada en su carpeta propia (`files/games/<juego>/game.<ext>`, con su `port.json`). Antes (hasta la v1.1) la app solo registraba los datos del archivo en la biblioteca y el juego **no** quedaba instalado: eso era el “port que no se hacía”. Ahora el juego se suma a la biblioteca **solo cuando el port terminó bien**; si se cancela o falla, no queda ninguna entrada ni archivo a medias.
- **Importar desde ZIP**: elegís el archivo `.zip` y la app lee el **directorio central** del ZIP (con soporte ZIP64) para encontrar la imagen del juego (`.iso`, y si no hay ISO acepta `.chd`, `.img` o `.bin`) con su tamaño real, y después **extrae solo esa imagen** a la carpeta del juego. Si el ZIP no trae ninguna imagen, la app te dice qué tipos de archivo había adentro.
- **Importar desde ISO directa**: elegís el `.iso` y la app lo **copia** a la carpeta del juego. Además verifica la firma ISO 9660 (`CD001`) y lee el nombre del volumen. **Si no verifica, el juego se portea igual**, marcado como **“sin verificar”**.
- **Durante el port**: barra de progreso con porcentaje y bytes, botón **Cancelar**, y **chequeo de espacio libre antes de empezar** (si no alcanza, la app dice cuánto falta y no crea nada). Al final se verifica que el tamaño instalado coincida con el esperado.
- **Acceso persistente**: la app toma permiso persistente sobre el archivo elegido y guarda su Uri, para que el acceso siga válido tras reiniciar.
- **Biblioteca persistente**: los juegos quedan guardados en el teléfono (nombre, origen ZIP/ISO, tamaños, fecha de importación, Uri de origen, estado de verificación). Los datos guardados por la v1.0 se cargan sin problema.
- **Pantalla propia por juego (tipo nativo)**: cada juego tiene su ícono/inicial, sus datos, un botón grande **“Jugar — pantalla completa”**, su historial (veces abierto, última vez) y sus **guardados propios** (crear, listar y borrar por juego).
- **Acceso directo por juego**: botón **“Crear acceso directo”** en la pantalla del juego (o pulsación larga en la lista principal) que fija un ícono con la inicial del juego en la pantalla de inicio del teléfono (`ShortcutManager.requestPinShortcut`). Al tocarlo, la app abre **directo la pantalla de ese juego**.
- **Vista del juego a pantalla completa**: mando de PS2 **completo** en pantalla: cruceta, **△ □ ○ ✕ con los colores clásicos de PlayStation**, **L1/L2 y R1/R2**, **START/SELECT**, y **dos sticks analógicos táctiles** con arrastre multitáctil (la perilla vuelve al centro; tocar el centro de cada stick es **L3/R3**), con feedback háptico y visual, cronómetro de sesión y botón de guardado rápido.

## Qué cambia en la v1.2

1. **Port real**: nuevo motor `PortEngine` que copia/extrae la imagen del juego al almacenamiento interno de la app (carpeta por juego + `port.json`), en streaming, con progreso, cancelación, chequeo de espacio previo y verificación de tamaño final. El registro en la biblioteca ocurre recién cuando el port termina bien.
2. **Estado del port visible**: en la pantalla de cada juego se ve si está **“Porteado / instalado”** (con ruta local y tamaño instalado) o **“solo registrado, sin portear”** (juegos importados con la v1.1: hay que reimportarlos para portearlos; sus datos viejos se cargan sin romperse).
3. **Borrar archivos del port**: botón separado de “Quitar de la biblioteca”, con confirmación, que borra la carpeta instalada del juego y actualiza su estado.

## Qué se corrigió en la v1.1

1. **El “port” ya no da error ni se cuelga con ZIP grandes**: la v1.0 descomprimía entera la ISO interna solo para medirla (con ISOs de GB tardaba muchísimo o parecía un error, y `ZipInputStream` falla con algunos métodos de compresión, data descriptors y ZIP64). Ahora se parsea el directorio central con seek, sin descomprimir nada.
2. **Una ISO que no verifica ya no bloquea la importación**: se importa marcada “sin verificar”, con aviso.
3. **Mensajes claros en español y estado de progreso** durante la importación (antes podía mostrarse solo el texto crudo de la excepción), y botón deshabilitado mientras importa.
4. **Controles completos**: se agregaron L2/R2, L3/R3 y los dos sticks analógicos (la v1.0 solo tenía L1/R1).
5. **Acceso directo por juego** en la pantalla de inicio (nuevo).

## Alcance honesto

Esto **no es un emulador de PS2** y no convierte una ISO en un juego nativo real. Un port nativo verdadero requiere el código fuente del juego (la ISO está compilada para el procesador de la PS2, incompatible con ARM). Ports PS2 es el **importador y launcher**: organiza tus juegos y les da una experiencia “como nativa” (ícono propio, apertura directa, guardados por juego), pero no ejecuta el código del juego de PS2.

## Compilar el APK

Sin Gradle: el build es manual y reproducible con `aapt2 + javac + d8 + zipalign + apksigner`.

Requisitos: JDK 17 y Android SDK con `platforms;android-34` y `build-tools;34.0.0`.

```bash
export JAVA_HOME=/ruta/al/jdk17
export ANDROID_HOME=/ruta/al/android-sdk
./build-apk.sh
```

Eso genera **`app-debug.apk`** en la raíz del repo, firmado con un keystore debug generado automáticamente (solo para instalar y probar; no es una firma de publicación).

Tests JVM (sin Android) del parser ZIP, la verificación ISO y el motor de port: compilar `ZipIndex.java`, `IsoCheck.java`, `PortEngine.java`, `tests/TestZipIso.java` y `tests/TestPortEngine.java`, y ejecutar `TestZipIso` y `TestPortEngine`.

Está previsto un workflow de GitHub Actions (`.github/workflows/build-apk.yml`) que compila el APK en cada push y lo deja como artifact descargable. Nota: ese archivo todavía no está en el repo porque subir workflows requiere el scope `workflow` en el token de GitHub (`gh auth refresh -s workflow`); el resto del proyecto ya compila localmente con `./build-apk.sh`.

## Datos técnicos

- Package: `com.portsps2.app`
- Nombre de la app: **Ports PS2**
- Versión: **1.2** (versionCode 3)
- minSdk 26 (Android 8.0) · targetSdk 34 (Android 14)
- Java puro, sin dependencias externas ni AndroidX.
