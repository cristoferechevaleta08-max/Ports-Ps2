package com.portsps2.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 42;
    private ListView listView;
    private TextView emptyView;
    private ArrayList<Game> games = new ArrayList<>();

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
                + "(detecta la ISO de adentro) o desde una ISO directa.\n"
                + "Nota honesta: esta app es el importador/launcher. No incluye un emulador de PS2.");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(180, 180, 190));
        sub.setPadding(0, 12, 0, 20);
        root.addView(sub);

        Button importBtn = new Button(this);
        importBtn.setText("Importar juego (ZIP o ISO)");
        importBtn.setTextSize(17);
        importBtn.setOnClickListener(v -> pickFile());
        root.addView(importBtn);

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
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            Game g = games.get(pos);
            Intent i = new Intent(this, GameDetailActivity.class);
            i.putExtra("gameId", g.id);
            startActivity(i);
        });
        root.addView(listView);

        setContentView(root);
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
            rows.add("🎮  " + g.name + "\nDesde " + g.origin + " · " + Store.fmtSize(
                    g.isoSize > 0 ? g.isoSize : g.fileSize));
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, rows));
        emptyView.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed",
                "application/x-iso9660-image", "application/octet-stream", "*/*"});
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No se encontró un selector de archivos", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Toast.makeText(this, "Leyendo el archivo…", Toast.LENGTH_SHORT).show();
        new Thread(() -> importFile(uri)).start();
    }

    private void importFile(Uri uri) {
        String fileName = queryName(uri);
        long fileSize = querySize(uri);
        String lower = fileName.toLowerCase();
        try {
            if (lower.endsWith(".zip")) {
                importZip(uri, fileName, fileSize);
            } else if (lower.endsWith(".iso")) {
                importIso(uri, fileName, fileSize);
            } else {
                // Sin extensión clara: probar por magia (PK = ZIP, si no, ISO)
                InputStream probe = getContentResolver().openInputStream(uri);
                int b1 = probe.read(), b2 = probe.read();
                probe.close();
                if (b1 == 'P' && b2 == 'K') importZip(uri, fileName, fileSize);
                else importIso(uri, fileName, fileSize);
            }
        } catch (Exception e) {
            toast("No se pudo importar: " + e.getMessage());
        }
    }

    private void importZip(Uri uri, String fileName, long fileSize) throws Exception {
        String isoName = null;
        long isoSize = 0;
        int entries = 0;
        InputStream raw = getContentResolver().openInputStream(uri);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw));
        ZipEntry entry;
        byte[] buf = new byte[65536];
        while ((entry = zis.getNextEntry()) != null) {
            entries++;
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".iso")) {
                isoName = entry.getName();
                long counted = 0;
                int n;
                while ((n = zis.read(buf)) > 0) counted += n;   // tamaño real de la ISO
                isoSize = entry.getSize() > 0 ? entry.getSize() : counted;
                break;
            }
        }
        zis.close();
        if (isoName == null) {
            toast("Ese ZIP no trae ninguna ISO de PS2 adentro (" + entries + " archivos revisados)");
            return;
        }
        Game g = new Game();
        g.id = System.currentTimeMillis();
        String base = isoName.substring(isoName.lastIndexOf('/') + 1);
        g.name = base.toLowerCase().endsWith(".iso") ? base.substring(0, base.length() - 4) : base;
        g.origin = "ZIP";
        g.fileName = fileName;
        g.fileSize = fileSize;
        g.isoName = isoName;
        g.isoSize = isoSize;
        g.addedAt = System.currentTimeMillis();
        Store.upsert(this, g);
        toast("Juego importado desde ZIP: " + g.name);
        runOnUiThread(this::refresh);
    }

    private void importIso(Uri uri, String fileName, long fileSize) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        byte[] head = new byte[32840];
        int read = 0;
        while (read < head.length) {
            int n = in.read(head, read, head.length - read);
            if (n < 0) break;
            read += n;
        }
        in.close();
        boolean ok = read > 32774
                && head[32769] == 'C' && head[32770] == 'D' && head[32771] == '0'
                && head[32772] == '0' && head[32773] == '1';
        if (!ok) {
            toast("Ese archivo no verifica como ISO 9660 de PS2 (falta la firma CD001)");
            return;
        }
        String volumeId = new String(head, 32808, 32, "ASCII").trim();
        Game g = new Game();
        g.id = System.currentTimeMillis();
        g.name = !volumeId.isEmpty() ? volumeId
                : (fileName.toLowerCase().endsWith(".iso")
                    ? fileName.substring(0, fileName.length() - 4) : fileName);
        g.origin = "ISO";
        g.fileName = fileName;
        g.fileSize = fileSize;
        g.isoName = fileName;
        g.isoSize = fileSize;
        g.addedAt = System.currentTimeMillis();
        Store.upsert(this, g);
        toast("Juego importado desde ISO: " + g.name);
        runOnUiThread(this::refresh);
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
