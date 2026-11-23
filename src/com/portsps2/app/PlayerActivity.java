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
 * mando de PS2 COMPLETO en pantalla (L1/L2, R1/R2, cruceta, △ □ ○ ✕ con sus
 * colores clásicos, START/SELECT y dos sticks analógicos táctiles con L3/R3),
 * cronómetro de sesión y guardado rápido.
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

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Centro: nombre del juego + aviso honesto + cronómetro + último input
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText(game.name);
        name.setTextSize(28);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        center.addView(name);
        TextView note = new TextView(this);
        note.setText("Vista del juego tipo nativo — el motor de emulación de PS2 no está incluido en esta app.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(160, 160, 170));
        note.setGravity(Gravity.CENTER);
        center.addView(note);
        timerView = new TextView(this);
        timerView.setText("Sesión 00:00");
        timerView.setTextSize(15);
        timerView.setTextColor(Color.rgb(140, 220, 140));
        timerView.setGravity(Gravity.CENTER);
        timerView.setPadding(0, 8, 0, 0);
        center.addView(timerView);
        pressedView = new TextView(this);
        pressedView.setText("Tocá el mando 👇");
        pressedView.setTextColor(Color.rgb(200, 200, 210));
        pressedView.setGravity(Gravity.CENTER);
        pressedView.setPadding(0, 6, 0, 0);
        center.addView(pressedView);
        root.addView(center, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Fila superior: L2 L1 | guardado / salir | R1 R2
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        top.addView(padButton("L2", 18));
        top.addView(padButton("L1", 18));
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
        top.addView(padButton("R1", 18));
        top.addView(padButton("R2", 18));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        root.addView(top, topLp);

        // Zona inferior: [cruceta + stick izq.] | SELECT/START | [acción + stick der.]
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.BOTTOM);
        bottom.setPadding(16, 0, 16, 12);

        LinearLayout leftCluster = new LinearLayout(this);
        leftCluster.setOrientation(LinearLayout.VERTICAL);
        leftCluster.setGravity(Gravity.CENTER);
        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);
        dpad.addView(centered(padButton("▲", 18)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.addView(padButton("◀", 18));
        mid.addView(padButton("●", 18));
        mid.addView(padButton("▶", 18));
        dpad.addView(mid);
        dpad.addView(centered(padButton("▼", 18)));
        leftCluster.addView(dpad);
        JoystickView leftStick = new JoystickView(this, "Stick izquierdo", "L3");
        leftStick.setListener(stickListener());
        leftStick.setLayoutParams(new LinearLayout.LayoutParams(300, 300));
        leftCluster.addView(leftStick);
        bottom.addView(leftCluster);

        LinearLayout startSel = new LinearLayout(this);
        startSel.setOrientation(LinearLayout.HORIZONTAL);
        startSel.setGravity(Gravity.CENTER);
        startSel.addView(padButton("SELECT", 12));
        startSel.addView(padButton("START", 12));
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startSel.setLayoutParams(ssLp);
        bottom.addView(startSel);

        LinearLayout rightCluster = new LinearLayout(this);
        rightCluster.setOrientation(LinearLayout.VERTICAL);
        rightCluster.setGravity(Gravity.CENTER);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(centered(actionButton("△", Color.rgb(46, 204, 113))));   // verde
        LinearLayout mid2 = new LinearLayout(this);
        mid2.setOrientation(LinearLayout.HORIZONTAL);
        mid2.addView(actionButton("□", Color.rgb(255, 105, 180)));                 // rosa
        mid2.addView(actionButton("○", Color.rgb(231, 76, 60)));                  // rojo
        actions.addView(mid2);
        actions.addView(centered(actionButton("✕", Color.rgb(52, 152, 219))));    // azul
        rightCluster.addView(actions);
        JoystickView rightStick = new JoystickView(this, "Stick derecho", "R3");
        rightStick.setListener(stickListener());
        rightStick.setLayoutParams(new LinearLayout.LayoutParams(300, 300));
        rightCluster.addView(rightStick);
        bottom.addView(rightCluster);

        FrameLayout.LayoutParams botLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        botLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, botLp);

        setContentView(root);
        startMs = System.currentTimeMillis();
        handler.post(tick);
    }

    private JoystickView.Listener stickListener() {
        return new JoystickView.Listener() {
            @Override public void onMove(String stickName, float x, float y) {
                if (x == 0f && y == 0f) {
                    pressedView.setText(stickName + " centrado");
                } else {
                    pressedView.setText(String.format("%s: x=%+.2f  y=%+.2f", stickName, x, y));
                }
            }
            @Override public void onCenterClick(String buttonName) {
                vibrate();
                pressedView.setText("Botón: " + buttonName);
            }
        };
    }

    private LinearLayout centered(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        l.addView(v);
        return l;
    }

    private Button actionButton(String label, int color) {
        Button b = padButton(label, 20);
        b.setTextColor(color);
        return b;
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
