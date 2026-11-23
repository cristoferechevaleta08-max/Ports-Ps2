package com.portsps2.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Stick analógico táctil: arrastrar mueve la perilla (ejes -1..1) y al soltar
 * vuelve al centro. Tocar el centro dispara el botón del stick (L3/R3).
 * Cada stick es una View propia, así que ambos pueden usarse a la vez
 * (multitáctil: el sistema reparte los punteros entre vistas).
 */
public class JoystickView extends View {
    public interface Listener {
        void onMove(String stickName, float x, float y);
        void onCenterClick(String buttonName);
    }

    private final String stickName;   // "Stick izquierdo" / "Stick derecho"
    private final String centerButton; // "L3" / "R3"
    private Listener listener;
    private float knobX = 0f, knobY = 0f; // -1..1
    private boolean downNearCenter = false;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JoystickView(Context ctx, String stickName, String centerButton) {
        super(ctx);
        this.stickName = stickName;
        this.centerButton = centerButton;
        basePaint.setColor(Color.argb(70, 255, 255, 255));
        basePaint.setStyle(Paint.Style.FILL);
        ringPaint.setColor(Color.argb(140, 255, 255, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        knobPaint.setColor(Color.argb(190, 90, 90, 120));
        knobPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(Color.argb(200, 255, 255, 255));
        labelPaint.setTextSize(30f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { this.listener = l; }
    public String stickName() { return stickName; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 6f;
        canvas.drawCircle(cx, cy, radius, basePaint);
        canvas.drawCircle(cx, cy, radius, ringPaint);
        float kx = cx + knobX * radius * 0.55f;
        float ky = cy + knobY * radius * 0.55f;
        canvas.drawCircle(kx, ky, radius * 0.42f, knobPaint);
        canvas.drawText(centerButton, kx, ky + 10f, labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 6f;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dx = ev.getX() - cx, dy = ev.getY() - cy;
                downNearCenter = Math.hypot(dx, dy) < radius * 0.30f;
                updateKnob(ev, cx, cy, radius);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                downNearCenter = false;
                updateKnob(ev, cx, cy, radius);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (downNearCenter && listener != null) listener.onCenterClick(centerButton);
                knobX = 0f; knobY = 0f;
                if (listener != null) listener.onMove(stickName, 0f, 0f);
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(ev);
        }
    }

    private void updateKnob(MotionEvent ev, float cx, float cy, float radius) {
        float dx = (ev.getX() - cx) / (radius * 0.55f);
        float dy = (ev.getY() - cy) / (radius * 0.55f);
        float len = (float) Math.hypot(dx, dy);
        if (len > 1f) { dx /= len; dy /= len; }
        knobX = dx; knobY = dy;
        if (listener != null) listener.onMove(stickName, dx, dy);
        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
