package com.portsps2.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Motor de port REAL (clase pura, sin dependencias de Android).
 *
 * Hasta la v1.1 el "port" solo registraba metadatos: el juego NO quedaba
 * instalado dentro de la app. Este motor copia de verdad la imagen del juego
 * (la ISO interna de un ZIP, o una ISO directa) a la carpeta propia del juego,
 * en streaming, con progreso, cancelación, chequeo de espacio previo y
 * verificación de tamaño al final. Escribe primero a un archivo ".partial"
 * y solo lo renombra al nombre final cuando el port terminó bien: un port
 * cancelado o fallido nunca deja un archivo válido a medias.
 */
public class PortEngine {

    /** Callbacks de progreso y cancelación (se invocan desde el hilo del port). */
    public interface Listener {
        /** copied = bytes ya escritos; expected = tamaño esperado (<=0 si se desconoce). */
        void onProgress(long copied, long expected);
        boolean isCancelled();
    }

    /** Error de port con mensaje en español listo para mostrar al usuario. */
    public static class PortException extends IOException {
        public PortException(String msg) { super(msg); }
    }

    /** El usuario canceló el port. */
    public static class CancelledException extends IOException {
        public CancelledException() { super("Port cancelado por el usuario."); }
    }

    public static class Result {
        public File file;          // archivo final instalado (game.<ext>)
        public long size;          // bytes reales instalados
        public String entryName;   // entrada origen dentro del ZIP ("" si fue copia directa)
    }

    private static final int BUFFER = 256 * 1024;

    // ------------------------------------------------------------------ API

    /**
     * Portea desde un ZIP: busca entryName dentro del stream ZIP y extrae SOLO
     * esa entrada a destDir/game.<ext>. expectedSize viene del directorio
     * central (ZipIndex) y se usa para el chequeo de espacio y la verificación.
     */
    public static Result portFromZip(InputStream zipStream, File destDir, String entryName,
                                     long expectedSize, Listener listener) throws IOException {
        checkSpace(destDir, expectedSize);
        String ext = extensionOf(entryName);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipStream));
        try {
            ZipEntry entry;
            boolean found = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) { found = true; break; }
                // getNextEntry() saltea solo el contenido de las demás entradas.
            }
            if (!found) {
                throw new PortException("No se encontró la imagen «" + entryName
                        + "» dentro del ZIP. El archivo puede estar dañado o haber cambiado.");
            }
            long expected = expectedSize > 0 ? expectedSize : entry.getSize();
            Result r = copyToGameFile(zis, destDir, ext, expected, listener);
            r.entryName = entryName;
            return r;
        } catch (PortException | CancelledException e) {
            throw e;
        } catch (IOException e) {
            throw new PortException("No se pudo extraer la imagen del ZIP. El archivo puede "
                    + "estar dañado o usar una compresión no soportada. (Detalle técnico: "
                    + shortDetail(e) + ")");
        } finally {
            try { zis.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Portea una imagen directa (ISO/BIN/IMG/CHD): copia el stream a
     * destDir/game.<ext> con progreso, cancelación y verificación de tamaño.
     */
    public static Result portFromImage(InputStream in, File destDir, String ext,
                                       long expectedSize, Listener listener) throws IOException {
        checkSpace(destDir, expectedSize);
        Result r = copyToGameFile(new BufferedInputStream(in), destDir,
                ext == null || ext.isEmpty() ? "iso" : ext, expectedSize, listener);
        r.entryName = "";
        return r;
    }

    /**
     * Chequeo de espacio ANTES de portear: si el tamaño esperado no entra en el
     * espacio usable del volumen destino, falla con un mensaje que dice cuánto
     * falta, sin crear nada.
     */
    public static void checkSpace(File destDir, long expectedSize) throws PortException {
        if (expectedSize <= 0) return; // tamaño desconocido: no se puede chequear
        File volume = destDir;
        while (volume != null && !volume.exists()) volume = volume.getParentFile();
        if (volume == null) return;
        long free = volume.getUsableSpace();
        if (free > 0 && free < expectedSize) {
            throw new PortException("No hay espacio suficiente para hacer el port. "
                    + "El juego necesita " + fmtSize(expectedSize)
                    + " y en el teléfono quedan " + fmtSize(free)
                    + " libres (faltan " + fmtSize(expectedSize - free) + "). "
                    + "Liberá espacio y probá de nuevo.");
        }
    }

    /** Escribe port.json (JSON simple armado a mano) en la carpeta del juego. */
    public static void writePortJson(File gameDir, String name, String origin,
                                     String imageFileName, long size, long dateMs) throws IOException {
        String json = "{\n"
                + "  \"name\": \"" + jsonEscape(name) + "\",\n"
                + "  \"origin\": \"" + jsonEscape(origin) + "\",\n"
                + "  \"file\": \"" + jsonEscape(imageFileName) + "\",\n"
                + "  \"size\": " + size + ",\n"
                + "  \"portedAt\": " + dateMs + "\n"
                + "}\n";
        File f = new File(gameDir, "port.json");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            out.write(json.getBytes("UTF-8"));
        }
    }

    /** Borra un archivo o carpeta completa (para limpiar ports parciales o borrados). */
    public static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Nombre de carpeta seguro para un juego: solo [a-zA-Z0-9._-], sin espacios. */
    public static String sanitizeDirName(String raw, long fallbackId) {
        String s = raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "game_" + fallbackId;
        if (s.length() > 60) s = s.substring(0, 60);
        return s;
    }

    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "tamaño desconocido";
        String[] u = {"B", "KB", "MB", "GB"};
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    // -------------------------------------------------------------- internos

    private static Result copyToGameFile(InputStream src, File destDir, String ext,
                                         long expected, Listener listener) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs() && !destDir.isDirectory()) {
            throw new PortException("No se pudo crear la carpeta del juego en el "
                    + "almacenamiento de la app.");
        }
        File partial = new File(destDir, "game." + ext + ".partial");
        File target = new File(destDir, "game." + ext);
        long copied = 0;
        boolean ok = false;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(partial))) {
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = src.read(buf)) >= 0) {
                if (listener != null && listener.isCancelled()) throw new CancelledException();
                if (n == 0) continue;
                out.write(buf, 0, n);
                copied += n;
                if (listener != null) listener.onProgress(copied, expected);
            }
            out.flush();
            ok = true;
        } catch (CancelledException e) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw e;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            if (e instanceof PortException) throw e;
            throw new PortException("Se interrumpió la copia del juego. Revisá que el archivo "
                    + "de origen siga disponible y que haya espacio libre. (Detalle técnico: "
                    + shortDetail(e) + ")");
        }
        if (ok && expected > 0 && copied != expected) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new PortException("El port quedó incompleto: se copiaron " + fmtSize(copied)
                    + " de " + fmtSize(expected) + " esperados. El archivo de origen puede "
                    + "estar dañado. No se instaló nada.");
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new PortException("No se pudo reemplazar el archivo del port anterior.");
        }
        if (!partial.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new PortException("No se pudo finalizar el port (no se pudo guardar el archivo).");
        }
        Result r = new Result();
        r.file = target;
        r.size = copied;
        return r;
    }

    private static String extensionOf(String name) {
        int slash = name.lastIndexOf('/');
        int dot = name.lastIndexOf('.');
        if (dot > slash && dot < name.length() - 1) return name.substring(dot + 1).toLowerCase();
        return "iso";
    }

    private static String shortDetail(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
