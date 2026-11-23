package com.portsps2.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
 * Vista de controles a pantalla completa: el mando de PS2 de referencia
 * (L1/L2, R1/R2, cruceta en cruz, △ □ ○ ✕ circulares con sus colores
 * clásicos, START/SELECT y dos sticks analógicos táctiles con L3/R3),
 * cronómetro de sesión y guardado rápido.
 *
 * Layout v1.3: FrameLayout con posicionamiento absoluto y tamaños FIJOS
 * en dp (antes era un LinearLayout que estiraba los botones y cortaba
 * los de los bordes). Todo entra en una pantalla landscape típica de
 * celular (~800x360 dp): cruceta arriba-izquierda, stick izquierdo
 * abajo-izquierda, rombo de acciones arriba-derecha, stick derecho
 * abajo-derecha, sin superposiciones.
 *
 * Alcance honesto: esta es la vista del mando de referencia. El juego se
 * abre en el motor de PS2 instalado, desde el botón Jugar de su ficha.
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

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private FrameLayout.LayoutParams flp(int wDp, int hDp, int gravity,
                                         int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(wDp), dp(hDp));
        lp.gravity = gravity;
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

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

        // ---- Centro: nombre + aviso + cronómetro + último input ------------
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText(game.name);
        name.setTextSize(22);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        center.addView(name);
        TextView note = new TextView(this);
        note.setText("Vista de controles — este es el mando de referencia.\n"
                + "El juego se abre en el motor de PS2 que tengas instalado, "
                + "desde el botón Jugar de la ficha del juego.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(160, 160, 170));
        note.setGravity(Gravity.CENTER);
        center.addView(note);
        timerView = new TextView(this);
        timerView.setText("Sesión 00:00");
        timerView.setTextSize(15);
        timerView.setTextColor(Color.rgb(140, 220, 140));
        timerView.setGravity(Gravity.CENTER);
        timerView.setPadding(0, dp(8), 0, 0);
        center.addView(timerView);
        pressedView = new TextView(this);
        pressedView.setText("Tocá el mando 👇");
        pressedView.setTextColor(Color.rgb(200, 200, 210));
        pressedView.setGravity(Gravity.CENTER);
        pressedView.setPadding(0, dp(6), 0, 0);
        center.addView(pressedView);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(300),
                FrameLayout.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        root.addView(center, centerLp);

        // ---- Gatillos: L2 L1 (izquierda) y R1 R2 (derecha), píldoras chicas -
        root.addView(pillButton("L2"), flp(62, 32, Gravity.TOP | Gravity.LEFT, 12, 10, 0, 0));
        root.addView(pillButton("L1"), flp(62, 32, Gravity.TOP | Gravity.LEFT, 82, 10, 0, 0));
        root.addView(pillButton("R1"), flp(62, 32, Gravity.TOP | Gravity.RIGHT, 0, 10, 82, 0));
        root.addView(pillButton("R2"), flp(62, 32, Gravity.TOP | Gravity.RIGHT, 0, 10, 12, 0));

        // ---- Arriba-centro: guardado rápido y salir, botones chicos --------
        LinearLayout topCenter = new LinearLayout(this);
        topCenter.setOrientation(LinearLayout.HORIZONTAL);
        topCenter.setGravity(Gravity.CENTER);
        Button save = new Button(this);
        save.setText("💾 Guardado rápido");
        save.setTextSize(12);
        save.setOnClickListener(v -> {
            Game fresh = Store.find(this, game.id);
            if (fresh != null) {
                fresh.saves.add(0, "Guardado rápido · " + Store.fmtDate(System.currentTimeMillis()));
                Store.upsert(this, fresh);
                game = fresh;
            }
            Toast.makeText(this, "Guardado rápido creado", Toast.LENGTH_SHORT).show();
        });
        topCenter.addView(save, new LinearLayout.LayoutParams(dp(150), dp(38)));
        Button exit = new Button(this);
        exit.setText("Salir");
        exit.setTextSize(12);
        exit.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(dp(70), dp(38));
        exitLp.leftMargin = dp(8);
        topCenter.addView(exit, exitLp);
        FrameLayout.LayoutParams tcLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tcLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tcLp.topMargin = dp(8);
        root.addView(topCenter, tcLp);

        // ---- Cruceta en cruz (izquierda): 4 botones fijos + centro ---------
        FrameLayout dpad = new FrameLayout(this);
        View hub = new View(this);
        GradientDrawable hubBg = new GradientDrawable();
        hubBg.setShape(GradientDrawable.RECTANGLE);
        hubBg.setCornerRadius(dp(10));
        hubBg.setColor(Color.argb(220, 38, 38, 46));
        hub.setBackground(hubBg);
        dpad.addView(hub, flp(50, 50, Gravity.CENTER, 0, 0, 0, 0));
        dpad.addView(dpadButton("▲"), flp(50, 50, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        dpad.addView(dpadButton("◀"), flp(50, 50, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        dpad.addView(dpadButton("▶"), flp(50, 50, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        dpad.addView(dpadButton("▼"), flp(50, 50, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        root.addView(dpad, flp(150, 150, Gravity.TOP | Gravity.LEFT, 12, 52, 0, 0));

        // ---- Rombo de acciones (derecha): botones CIRCULARES ---------------
        FrameLayout diamond = new FrameLayout(this);
        diamond.addView(actionButton("△", Color.rgb(46, 204, 113)),
                flp(60, 60, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));   // verde
        diamond.addView(actionButton("□", Color.rgb(255, 105, 180)),
                flp(60, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));     // rosa
        diamond.addView(actionButton("○", Color.rgb(231, 76, 60)),
                flp(60, 60, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));    // rojo
        diamond.addView(actionButton("✕", Color.rgb(52, 152, 219)),
                flp(60, 60, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0)); // azul
        root.addView(diamond, flp(180, 180, Gravity.TOP | Gravity.RIGHT, 0, 52, 12, 0));

        // ---- Sticks analógicos: esquinas inferiores, tamaño fijo -----------
        JoystickView leftStick = new JoystickView(this, "Stick izquierdo", "L3");
        leftStick.setListener(stickListener());
        root.addView(leftStick, flp(112, 112, Gravity.BOTTOM | Gravity.LEFT, 24, 0, 0, 10));

        JoystickView rightStick = new JoystickView(this, "Stick derecho", "R3");
        rightStick.setListener(stickListener());
        root.addView(rightStick, flp(112, 112, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 24, 10));

        // ---- SELECT / START: chicos, abajo-centro ---------------------------
        LinearLayout startSel = new LinearLayout(this);
        startSel.setOrientation(LinearLayout.HORIZONTAL);
        startSel.setGravity(Gravity.CENTER);
        startSel.addView(pillButton("SELECT"), new LinearLayout.LayoutParams(dp(66), dp(30)));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(dp(66), dp(30));
        startLp.leftMargin = dp(10);
        startSel.addView(pillButton("START"), startLp);
        FrameLayout.LayoutParams ssLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ssLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ssLp.bottomMargin = dp(12);
        root.addView(startSel, ssLp);

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

    /** Comportamiento común: feedback visual + háptico + texto de estado. */
    private Button baseButton(String label, int textSize) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(textSize);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setAlpha(0.9f);
        b.setOnTouchListener((view, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                view.setAlpha(0.55f);
                vibrate();
                pressedView.setText("Botón: " + label);
            } else if (ev.getAction() == MotionEvent.ACTION_UP
                    || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setAlpha(0.9f);
            }
            return false;
        });
        return b;
    }

    /** Botón de acción circular con el color clásico de PlayStation. */
    private Button actionButton(String label, int color) {
        Button b = baseButton(label, 24);
        b.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(225, 42, 42, 52));
        bg.setStroke(dp(2), color);
        b.setBackground(bg);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    /** Botón de cruceta cuadrado con esquinas apenas redondeadas. */
    private Button dpadButton(String label) {
        Button b = baseButton(label, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.argb(225, 52, 52, 62));
        bg.setStroke(dp(1), Color.argb(160, 255, 255, 255));
        b.setBackground(bg);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    /** Píldora chica para L1/L2/R1/R2/SELECT/START. */
    private Button pillButton(String label) {
        Button b = baseButton(label, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.argb(225, 58, 58, 70));
        bg.setStroke(dp(1), Color.argb(140, 255, 255, 255));
        b.setBackground(bg);
        b.setPadding(0, 0, 0, 0);
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
