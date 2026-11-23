package com.portsps2.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 42;
    public static final String ACTION_OPEN_GAME = "com.portsps2.app.OPEN_GAME";
    public static final String EXTRA_OPEN_GAME_ID = "openGameId";

    private ListView listView;
    private TextView emptyView;
    private TextView statusView;
    private Button importBtn;
    private ArrayList<Game> games = new ArrayList<>();
    private volatile boolean importing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 32);
        root.setBackgroundColor(Color.rgb(18, 18, 24));

        TextView title = new TextView(this);
        title.setText("Ports PS2");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tu pantalla de juegos tipo nativo. Importá juegos de PS2 desde un ZIP "
                + "(detecta la imagen de adentro sin descomprimirla) o desde una ISO directa.\n"
                + "Nota honesta: esta app es el importador/launcher. No incluye un emulador de PS2.");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(180, 180, 190));
        sub.setPadding(0, 12, 0, 20);
        root.addView(sub);

        importBtn = new Button(this);
        importBtn.setText("Importar juego (ZIP o ISO)");
        importBtn.setTextSize(17);
        importBtn.setOnClickListener(v -> pickFile());
        root.addView(importBtn);

        statusView = new TextView(this);
        statusView.setText("");
        statusView.setTextSize(14);
        statusView.setTextColor(Color.rgb(140, 220, 140));
        statusView.setPadding(0, 14, 0, 0);
        root.addView(statusView);

        TextView hint = new TextView(this);
        hint.setText("Mantené apretado un juego para crear su acceso directo en la pantalla de inicio.");
        hint.setTextSize(12);
        hint.setTextColor(Color.rgb(140, 140, 150));
        hint.setPadding(0, 6, 0, 0);
        root.addView(hint);

        emptyView = new TextView(this);
        emptyView.setText("Todavía no hay juegos. Importá tu primer ZIP o ISO.");
        emptyView.setTextColor(Color.rgb(150, 150, 160));
        emptyView.setPadding(0, 32, 0, 0);
        root.addView(emptyView);

        listView = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = 16;
        listView.setLayoutParams(lp);
        listView.setOnItemClickListener((parent, view, pos, id) -> openGame(games.get(pos).id));
        listView.setOnItemLongClickListener((parent, view, pos, id) -> {
            ShortcutHelper.createShortcut(this, games.get(pos));
            return true;
        });
        root.addView(listView);

        setContentView(root);
        handleOpenGameIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenGameIntent(intent);
    }

    /** Apertura directa desde el acceso directo fijado: va derecho al juego. */
    private void handleOpenGameIntent(Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra(EXTRA_OPEN_GAME_ID, 0);
        if (id != 0) openGame(id);
    }

    private void openGame(long id) {
        Intent i = new Intent(this, GameDetailActivity.class);
        i.putExtra("gameId", id);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        games = Store.load(this);
        ArrayList<String> rows = new ArrayList<>();
        for (Game g : games) {
            String row = "🎮  " + g.name + "\nDesde " + g.origin + " · " + Store.fmtSize(
                    g.isoSize > 0 ? g.isoSize : g.fileSize);
            if ("ISO".equals(g.origin) && !g.verified) row += " · sin verificar";
            rows.add(row);
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, rows));
        emptyView.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void pickFile() {
        if (importing) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed",
                "application/x-iso9660-image", "application/octet-stream", "*/*"});
        // Pedimos permiso de lectura persistente para conservar el acceso al archivo.
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            setStatus("No se encontró un selector de archivos en este teléfono. "
                    + "Probá con la app Archivos de Android.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        // Permiso persistente: así el acceso directo y la biblioteca siguen
        // apuntando a un archivo que la app puede volver a leer tras reiniciar.
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // Algunos proveedores no dan permiso persistente; la importación sigue igual.
        }
        setImporting(true, "Leyendo el archivo… no cierres la app.");
        new Thread(() -> importFile(uri)).start();
    }

    private void setImporting(boolean on, String msg) {
        importing = on;
        runOnUiThread(() -> {
            importBtn.setEnabled(!on);
            statusView.setText(msg == null ? "" : msg);
        });
    }

    private void finishImport(String msg) {
        setImporting(false, msg);
        if (msg != null) toast(msg);
        runOnUiThread(this::refresh);
    }

    private void importFile(Uri uri) {
        String fileName = queryName(uri);
        long fileSize = querySize(uri);
        String lower = fileName.toLowerCase();
        try {
            if (lower.endsWith(".zip")) {
                importZip(uri, fileName, fileSize);
            } else if (isImageName(lower)) {
                importIso(uri, fileName, fileSize);
            } else {
                // Sin extensión clara: probar por magia (PK = ZIP, si no, imagen)
                InputStream probe = getContentResolver().openInputStream(uri);
                if (probe == null) throw new FileNotFoundException("sin stream");
                int b1 = probe.read(), b2 = probe.read();
                probe.close();
                if (b1 == 'P' && b2 == 'K') importZip(uri, fileName, fileSize);
                else importIso(uri, fileName, fileSize);
            }
        } catch (SecurityException e) {
            finishImport("Android no nos dejó leer ese archivo (permiso denegado). "
                    + "Volvé a importarlo eligiéndolo desde la app Archivos.");
        } catch (FileNotFoundException e) {
            finishImport("No se encontró el archivo. Puede que se haya movido o borrado: "
                    + "elegilo de nuevo desde donde esté ahora.");
        } catch (Exception e) {
            finishImport("No se pudo importar el archivo. Revisá que no esté dañado y que haya "
                    + "espacio libre, y probá de nuevo. (Detalle técnico: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    private static boolean isImageName(String lower) {
        return lower.endsWith(".iso") || lower.endsWith(".bin")
                || lower.endsWith(".img") || lower.endsWith(".chd");
    }

    /**
     * ZIP robusto y rápido: se lee SOLO el directorio central (con ZIP64 si hace
     * falta) por canal con seek. La imagen interna NO se descomprime.
     */
    private void importZip(Uri uri, String fileName, long fileSize) throws Exception {
        List<ZipIndex.Entry> entries;
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new FileNotFoundException("sin descriptor");
        try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
            FileChannel ch = fis.getChannel();
            long size = fileSize > 0 ? fileSize : ch.size();
            entries = ZipIndex.parse(ch, size);
        } finally {
            pfd.close();
        }
        ZipIndex.Entry image = ZipIndex.findGameImage(entries);
        if (image == null) {
            LinkedHashSet<String> exts = new LinkedHashSet<>();
            for (ZipIndex.Entry e : entries) {
                String ext = e.extension();
                exts.add(ext.isEmpty() ? "(sin extensión)" : "." + ext);
            }
            finishImport("Ese ZIP no trae ninguna imagen de juego (.iso, .chd, .img o .bin). "
                    + "Adentro había: " + (exts.isEmpty() ? "ningún archivo" : String.join(", ", exts))
                    + ". Revisá que el ZIP sea el del juego y no otro archivo.");
            return;
        }
        Game g = new Game();
        g.id = System.currentTimeMillis();
        String base = image.name.substring(image.name.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        g.name = dot > 0 ? base.substring(0, dot) : base;
        g.origin = "ZIP";
        g.fileName = fileName;
        g.fileSize = fileSize;
        g.isoName = image.name;
        g.isoSize = image.uncompressedSize;
        g.addedAt = System.currentTimeMillis();
        g.sourceUri = uri.toString();
        g.verified = true;
        Store.upsert(this, g);
        finishImport("Juego importado desde ZIP: " + g.name
                + " (imagen " + image.name + ", " + Store.fmtSize(image.uncompressedSize) + ")");
    }

    /**
     * ISO que nunca bloquea: si la firma CD001 verifica, se lee el nombre del
     * volumen; si no verifica (o el archivo es chico/truncado), se importa igual
     * marcado como "sin verificar", con aviso.
     */
    private void importIso(Uri uri, String fileName, long fileSize) {
        byte[] head = new byte[IsoCheck.HEADER_SIZE];
        int read = 0;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new FileNotFoundException("sin stream");
            try {
                while (read < head.length) {
                    int n = in.read(head, read, head.length - read);
                    if (n < 0) break;
                    read += n;
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            finishImport("No se pudo leer ese archivo. Revisá que siga estando en el teléfono "
                    + "y volvé a elegirlo.");
            return;
        }
        IsoCheck.Result check = IsoCheck.check(head, read);
        Game g = new Game();
        g.id = System.currentTimeMillis();
        String fallback = fileName;
        int dot = fallback.lastIndexOf('.');
        if (dot > 0) fallback = fallback.substring(0, dot);
        g.name = check.verified && !check.volumeId.isEmpty() ? check.volumeId : fallback;
        g.origin = "ISO";
        g.fileName = fileName;
        g.fileSize = fileSize;
        g.isoName = fileName;
        g.isoSize = fileSize;
        g.addedAt = System.currentTimeMillis();
        g.sourceUri = uri.toString();
        g.verified = check.verified;
        Store.upsert(this, g);
        if (check.verified) {
            finishImport("Juego importado desde ISO: " + g.name);
        } else {
            finishImport("Juego importado SIN VERIFICAR: " + g.name
                    + ". No se encontró la firma de una ISO estándar, pero lo agregamos igual a tu biblioteca.");
        }
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> statusView.setText(msg));
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last != null ? last : "juego";
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}
