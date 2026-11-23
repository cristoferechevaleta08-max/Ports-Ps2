package com.portsps2.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class GameDetailActivity extends Activity {
    private Game game;
    private TextView historyView;
    private TextView portView;
    private Button deletePortBtn;
    private ListView savesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long id = getIntent().getLongExtra("gameId", 0);
        game = Store.find(this, id);
        if (game == null) { finish(); return; }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Game fresh = Store.find(this, game.id);
        if (fresh != null) { game = fresh; refreshDynamic(); }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 32);
        root.setBackgroundColor(Color.rgb(18, 18, 24));

        // Ícono propio del juego (tipo nativo): círculo con la inicial
        TextView icon = new TextView(this);
        icon.setText(game.name.isEmpty() ? "?" : game.name.substring(0, 1).toUpperCase());
        icon.setTextSize(44);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.rgb(88, 60, 180));
        icon.setBackground(circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(220, 220);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconLp);
        root.addView(icon);

        TextView name = new TextView(this);
        name.setText(game.name);
        name.setTextSize(26);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 20, 0, 4);
        root.addView(name);

        TextView data = new TextView(this);
        String verifiedLine = "ISO".equals(game.origin)
                ? (game.verified ? "\nVerificación: ISO 9660 verificada ✓"
                                  : "\n⚠ Sin verificar: no se encontró la firma CD001, pero se importó igual.")
                : "";
        data.setText("Origen: " + game.origin + "  ·  Archivo: " + game.fileName
                + "\nISO: " + game.isoName + "  ·  " + Store.fmtSize(game.isoSize > 0 ? game.isoSize : game.fileSize)
                + "\nImportado: " + Store.fmtDate(game.addedAt) + verifiedLine);
        data.setTextSize(14);
        data.setTextColor(Color.rgb(180, 180, 190));
        data.setGravity(Gravity.CENTER);
        root.addView(data);

        portView = new TextView(this);
        portView.setTextSize(14);
        portView.setGravity(Gravity.CENTER);
        portView.setPadding(0, 12, 0, 0);
        root.addView(portView);

        Button play = new Button(this);
        play.setText("▶  Jugar — abrir en el emulador");
        play.setTextSize(19);
        play.setPadding(0, 24, 0, 24);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.topMargin = 28;
        play.setLayoutParams(playLp);
        play.setOnClickListener(v -> playGame());
        root.addView(play);

        Button controls = new Button(this);
        controls.setText("🎮 Vista de controles (mando de referencia)");
        controls.setTextSize(15);
        LinearLayout.LayoutParams ctLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ctLp.topMargin = 12;
        controls.setLayoutParams(ctLp);
        controls.setOnClickListener(v -> openControlsView());
        root.addView(controls);

        Button shortcut = new Button(this);
        shortcut.setText("📌 Crear acceso directo en la pantalla de inicio");
        shortcut.setTextSize(15);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scLp.topMargin = 12;
        shortcut.setLayoutParams(scLp);
        shortcut.setOnClickListener(v -> ShortcutHelper.createShortcut(this, game));
        root.addView(shortcut);

        historyView = new TextView(this);
        historyView.setTextColor(Color.rgb(170, 170, 180));
        historyView.setPadding(0, 20, 0, 8);
        root.addView(historyView);

        Button saveBtn = new Button(this);
        saveBtn.setText("Crear guardado rápido");
        saveBtn.setOnClickListener(v -> {
            game.saves.add(0, "Guardado rápido · " + Store.fmtDate(System.currentTimeMillis()));
            Store.upsert(this, game);
            refreshDynamic();
            Toast.makeText(this, "Guardado creado para este juego", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveBtn);

        TextView savesTitle = new TextView(this);
        savesTitle.setText("Guardados de este juego (tocá uno para borrarlo):");
        savesTitle.setTextColor(Color.rgb(170, 170, 180));
        savesTitle.setPadding(0, 16, 0, 4);
        root.addView(savesTitle);

        savesList = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        savesList.setLayoutParams(lp);
        savesList.setOnItemClickListener((parent, view, pos, id) ->
                new AlertDialog.Builder(this)
                        .setTitle("Borrar guardado")
                        .setMessage(game.saves.get(pos))
                        .setPositiveButton("Borrar", (d, w) -> {
                            game.saves.remove(pos);
                            Store.upsert(this, game);
                            refreshDynamic();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show());
        root.addView(savesList);

        deletePortBtn = new Button(this);
        deletePortBtn.setText("Borrar archivos del port");
        deletePortBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Borrar archivos del port")
                .setMessage("Se borra la copia instalada dentro de la app ("
                        + Store.fmtSize(game.installedSize) + "). Tu archivo ZIP/ISO original "
                        + "no se toca, y el juego sigue en la biblioteca como «sin portear».")
                .setPositiveButton("Borrar", (d, w) -> {
                    if (!game.localPath.isEmpty()) {
                        File f = new File(game.localPath);
                        File dir = f.getParentFile();
                        PortEngine.deleteRecursively(dir != null ? dir : f);
                    }
                    game.installed = false;
                    game.localPath = "";
                    game.installedSize = 0;
                    Store.upsert(this, game);
                    refreshDynamic();
                    Toast.makeText(this, "Archivos del port borrados", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show());
        root.addView(deletePortBtn);

        Button del = new Button(this);
        del.setText("Quitar juego de la biblioteca");
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Quitar juego")
                .setMessage("Se quita de la biblioteca (no se borra tu archivo ZIP/ISO).")
                .setPositiveButton("Quitar", (d, w) -> { Store.remove(this, game.id); finish(); })
                .setNegativeButton("Cancelar", null)
                .show());
        root.addView(del);

        setContentView(root);
        refreshDynamic();
    }

    /** Abre la vista de controles (PlayerActivity) como pantalla secundaria. */
    private void openControlsView() {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("gameId", game.id);
        startActivity(i);
    }

    /**
     * Jugar de verdad: el juego se abre en el motor de PS2 instalado en el
     * teléfono (AetherSX2/NetherSX2/ARMSX2/Play!). Ports PS2 no incluye
     * emulador propio.
     */
    private void playGame() {
        boolean ported = game.installed && game.localPath != null && !game.localPath.isEmpty()
                && new File(game.localPath).isFile();
        if (!ported) {
            new AlertDialog.Builder(this)
                    .setTitle("Este juego todavía no está porteado")
                    .setMessage("«" + game.name + "» está en la biblioteca pero su imagen "
                            + "todavía no quedó instalada dentro de la app, así que no hay "
                            + "nada para abrir en el emulador.\n\n"
                            + "Volvé a la pantalla principal e importá de nuevo su archivo "
                            + "ZIP o ISO: al terminar el port, el botón Jugar lo va a abrir "
                            + "en tu emulador.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        java.util.List<Emulators.Def> installed = EmulatorLauncher.installed(this);
        if (installed.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Falta un motor de PS2")
                    .setMessage("El juego está porteado y listo, pero para cargarlo hace "
                            + "falta un emulador de PS2 instalado en el teléfono "
                            + "(AetherSX2, NetherSX2, ARMSX2 o Play!). Ports PS2 no "
                            + "incluye el motor: organiza tus juegos y los abre en el "
                            + "emulador que tengas.\n\n"
                            + "Instalá uno, configurá en él la BIOS de tu propia PS2, "
                            + "y volvé a tocar Jugar.")
                    .setPositiveButton("Buscar emulador",
                            (d, w) -> EmulatorLauncher.openStoreSearch(this))
                    .setNeutralButton("Ver controles igual", (d, w) -> openControlsView())
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (installed.size() == 1) {
            launchIn(installed.get(0));
            return;
        }
        String[] names = new String[installed.size()];
        for (int i = 0; i < installed.size(); i++) names[i] = installed.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("¿En qué emulador querés abrirlo?")
                .setItems(names, (d, which) -> launchIn(installed.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Lanza el juego en el emulador elegido y registra el historial. */
    private void launchIn(Emulators.Def def) {
        Toast.makeText(this, "Abriendo «" + game.name + "» en " + def.name + "…",
                Toast.LENGTH_SHORT).show();
        boolean ok = EmulatorLauncher.launch(this, def, game);
        if (ok) {
            game.playCount++;
            game.lastPlayedAt = System.currentTimeMillis();
            Store.upsert(this, game);
            refreshDynamic();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("El emulador no aceptó el juego")
                    .setMessage(def.name + " no pudo abrir «" + game.name + "» desde "
                            + "Ports PS2. Probá abriendo el emulador y cargando el "
                            + "juego desde ahí, o con otro emulador si tenés más de uno.")
                    .setPositiveButton("Entendido", null)
                    .setNeutralButton("Ver controles", (d, w) -> openControlsView())
                    .show();
        }
    }

    private void refreshDynamic() {
        if (historyView == null) return;
        if (portView != null) {
            if (game.installed) {
                portView.setText("Estado: ✓ Porteado / instalado dentro de la app\n"
                        + "Archivo local: " + game.localPath + "\n"
                        + "Tamaño instalado: " + Store.fmtSize(game.installedSize));
                portView.setTextColor(Color.rgb(140, 220, 140));
            } else {
                portView.setText("Estado: solo registrado, sin portear.\n"
                        + "Este juego se importó con una versión anterior: para portearlo "
                        + "de verdad, volvé a importarlo desde la pantalla principal.");
                portView.setTextColor(Color.rgb(230, 190, 120));
            }
        }
        if (deletePortBtn != null) {
            deletePortBtn.setVisibility(game.installed ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        historyView.setText("Historial: abierto " + game.playCount + " veces · Última vez: "
                + Store.fmtDate(game.lastPlayedAt));
        ArrayList<String> rows = new ArrayList<>();
        if (game.saves.isEmpty()) rows.add("(sin guardados todavía)");
        else rows.addAll(game.saves);
        savesList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }
}
