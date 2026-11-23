package com.portsps2.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Vista del juego a pantalla completa, estilo "tipo nativo":
 * mando de PS2 en pantalla, cronómetro de sesión y guardado rápido.
 * Alcance honesto: es la vista/launcher del juego; no ejecuta emulación de PS2.
 */
public class PlayerActivity extends Activity {
    private Game game;
    private TextView timerView;
    private TextView pressedView;
    private final Handler handler = new Handler();
    private long startMs;
    private boolean running = true;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long s = (System.currentTimeMillis() - startMs) / 1000;
            timerView.setText(String.format("Sesión %02d:%02d", s / 60, s % 60));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long id = getIntent().getLongExtra("gameId", 0);
        game = Store.find(this, id);
        if (game == null) { finish(); return; }

        // Inmersivo: pantalla completa sin barras
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Centro: nombre del juego + aviso honesto + cronómetro
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText(game.name);
        name.setTextSize(30);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        center.addView(name);
        TextView note = new TextView(this);
        note.setText("Vista del juego tipo nativo — el motor de emulación de PS2 no está incluido en esta app.");
        note.setTextSize(13);
        note.setTextColor(Color.rgb(160, 160, 170));
        note.setGravity(Gravity.CENTER);
        center.addView(note);
        timerView = new TextView(this);
        timerView.setText("Sesión 00:00");
        timerView.setTextSize(16);
        timerView.setTextColor(Color.rgb(140, 220, 140));
        timerView.setGravity(Gravity.CENTER);
        timerView.setPadding(0, 10, 0, 0);
        center.addView(timerView);
        pressedView = new TextView(this);
        pressedView.setText("Tocá el mando 👇");
        pressedView.setTextColor(Color.rgb(200, 200, 210));
        pressedView.setGravity(Gravity.CENTER);
        pressedView.setPadding(0, 8, 0, 0);
        center.addView(pressedView);
        root.addView(center, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Fila superior: L1 / R1 + salir / guardado
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        top.addView(padButton("L1", 30));
        Button save = new Button(this);
        save.setText("💾 Guardado rápido");
        save.setOnClickListener(v -> {
            Game fresh = Store.find(this, game.id);
            if (fresh != null) {
                fresh.saves.add(0, "Guardado rápido · " + Store.fmtDate(System.currentTimeMillis()));
                Store.upsert(this, fresh);
                game = fresh;
            }
            Toast.makeText(this, "Guardado rápido creado", Toast.LENGTH_SHORT).show();
        });
        top.addView(save);
        Button exit = new Button(this);
        exit.setText("Salir");
        exit.setOnClickListener(v -> finish());
        top.addView(exit);
        top.addView(padButton("R1", 30));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        root.addView(top, topLp);

        // Fila inferior: cruceta | START/SELECT | botones de acción
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.BOTTOM);
        bottom.setPadding(24, 0, 24, 24);

        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);
        dpad.addView(centered(padButton("▲", 22)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.addView(padButton("◀", 22));
        mid.addView(padButton("●", 22));
        mid.addView(padButton("▶", 22));
        dpad.addView(mid);
        dpad.addView(centered(padButton("▼", 22)));
        bottom.addView(dpad);

        LinearLayout startSel = new LinearLayout(this);
        startSel.setOrientation(LinearLayout.HORIZONTAL);
        startSel.setGravity(Gravity.CENTER);
        startSel.addView(padButton("SELECT", 13));
        startSel.addView(padButton("START", 13));
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startSel.setLayoutParams(ssLp);
        bottom.addView(startSel);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(centered(padButton("△", 22)));
        LinearLayout mid2 = new LinearLayout(this);
        mid2.setOrientation(LinearLayout.HORIZONTAL);
        mid2.addView(padButton("□", 22));
        mid2.addView(padButton("○", 22));
        actions.addView(mid2);
        actions.addView(centered(padButton("✕", 22)));
        bottom.addView(actions);

        FrameLayout.LayoutParams botLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        botLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, botLp);

        setContentView(root);
        startMs = System.currentTimeMillis();
        handler.post(tick);
    }

    private LinearLayout centered(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        l.addView(v);
        return l;
    }

    private Button padButton(String label, int textSize) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(textSize);
        b.setAlpha(0.85f);
        b.setOnTouchListener((view, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                view.setAlpha(0.5f);
                vibrate();
                pressedView.setText("Botón: " + label);
            } else if (ev.getAction() == MotionEvent.ACTION_UP
                    || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setAlpha(0.85f);
            }
            return false;
        });
        return b;
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(25);
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
