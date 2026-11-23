# Ports PS2

App de Android (APK) que funciona como **launcher tipo nativo por juego** para tus juegos de PlayStation 2:

- **Importar desde ZIP**: elegís el archivo `.zip` y la app lo lee, busca la ISO de PS2 que trae adentro y registra su nombre y su tamaño real.
- **Importar desde ISO directa**: elegís el `.iso`, la app verifica la firma ISO 9660 (`CD001`) y lee el nombre del volumen.
- **Biblioteca persistente**: los juegos quedan guardados en el teléfono (nombre, origen ZIP/ISO, tamaños, fecha de importación).
- **Pantalla propia por juego (tipo nativo)**: cada juego tiene su ícono/inicial, sus datos, un botón grande **“Jugar — pantalla completa”**, su historial (veces abierto, última vez) y sus **guardados propios** (crear, listar y borrar por juego).
- **Vista del juego a pantalla completa**: mando de PS2 en pantalla (cruceta, △ □ ○ ✕, L1/R1, START/SELECT) con feedback háptico y visual, cronómetro de sesión y botón de guardado rápido.

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

También hay un workflow de GitHub Actions (`.github/workflows/build-apk.yml`) que compila el APK en cada push y lo deja como artifact descargable.

## Datos técnicos

- Package: `com.portsps2.app`
- Nombre de la app: **Ports PS2**
- minSdk 26 (Android 8.0) · targetSdk 34 (Android 14)
- Java puro, sin dependencias externas ni AndroidX.
