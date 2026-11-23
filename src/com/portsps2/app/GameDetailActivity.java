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

import java.util.ArrayList;

public class GameDetailActivity extends Activity {
    private Game game;
    private TextView historyView;
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
        data.setText("Origen: " + game.origin + "  ·  Archivo: " + game.fileName
                + "\nISO: " + game.isoName + "  ·  " + Store.fmtSize(game.isoSize > 0 ? game.isoSize : game.fileSize)
                + "\nImportado: " + Store.fmtDate(game.addedAt));
        data.setTextSize(14);
        data.setTextColor(Color.rgb(180, 180, 190));
        data.setGravity(Gravity.CENTER);
        root.addView(data);

        Button play = new Button(this);
        play.setText("▶  Jugar — pantalla completa");
        play.setTextSize(19);
        play.setPadding(0, 24, 0, 24);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.topMargin = 28;
        play.setLayoutParams(playLp);
        play.setOnClickListener(v -> {
            game.playCount++;
            game.lastPlayedAt = System.currentTimeMillis();
            Store.upsert(this, game);
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra("gameId", game.id);
            startActivity(i);
        });
        root.addView(play);

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

    private void refreshDynamic() {
        if (historyView == null) return;
        historyView.setText("Historial: abierto " + game.playCount + " veces · Última vez: "
                + Store.fmtDate(game.lastPlayedAt));
        ArrayList<String> rows = new ArrayList<>();
        if (game.saves.isEmpty()) rows.add("(sin guardados todavía)");
        else rows.addAll(game.saves);
        savesList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }
}
