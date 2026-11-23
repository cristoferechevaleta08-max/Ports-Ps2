package com.portsps2.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * ContentProvider propio que sirve SOLO los archivos de los juegos ya
 * porteados (getFilesDir()/games/) para que un emulador externo pueda
 * leerlos con un content URI.
 *
 * Los juegos viven en el almacenamiento privado de la app, así que el
 * emulador no puede abrir la ruta directa: se le pasa un content URI y un
 * permiso de lectura temporal (grantUriPermission). El provider NO está
 * exportado y valida cada ruta con SafePaths (anti path-traversal):
 * nada fuera de games/ es accesible.
 *
 * Formato de la Uri: content://com.portsps2.app.files/games/<carpeta>/<archivo>
 */
public class GameFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.portsps2.app.files";

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Carpeta raíz de los juegos porteados. */
    public static File gamesRoot(Context ctx) {
        return new File(ctx.getFilesDir(), "games");
    }

    /**
     * Arma el content URI para un archivo local de un juego porteado.
     * Devuelve null si el archivo no está dentro de games/.
     */
    public static Uri uriForFile(Context ctx, File file) {
        if (file == null) return null;
        File root = gamesRoot(ctx);
        String rel;
        try {
            String rootCanon = root.getCanonicalPath();
            String fileCanon = file.getCanonicalPath();
            if (!fileCanon.startsWith(rootCanon + File.separator)) return null;
            rel = fileCanon.substring(rootCanon.length() + 1);
        } catch (Exception e) {
            return null;
        }
        Uri.Builder b = new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("games");
        for (String seg : rel.split(java.util.regex.Pattern.quote(File.separator))) {
            if (!seg.isEmpty()) b.appendPath(seg);
        }
        return b.build();
    }

    /** Resuelve la Uri a un archivo local validado dentro de games/. */
    private File fileForUri(Uri uri) {
        Context ctx = getContext();
        if (ctx == null) return null;
        List<String> segs = uri.getPathSegments();
        // Esperado: ["games", <carpeta>, <archivo>...]
        if (segs.size() < 2 || !"games".equals(segs.get(0))) return null;
        StringBuilder rel = new StringBuilder();
        for (int i = 1; i < segs.size(); i++) {
            if (rel.length() > 0) rel.append(File.separator);
            rel.append(segs.get(i));
        }
        try {
            return SafePaths.resolveInside(gamesRoot(ctx), rel.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.contains("r")) {
            throw new FileNotFoundException("solo lectura");
        }
        File f = fileForUri(uri);
        if (f == null || !f.isFile()) {
            throw new FileNotFoundException("archivo no disponible: " + uri);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".iso")) return "application/x-iso9660-image";
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
