package com.portsps2.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;

/** Acceso directo fijado en la pantalla de inicio, uno por juego (API 26+). */
public class ShortcutHelper {

    public static void createShortcut(Context ctx, Game game) {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(ctx, "Tu versión de Android no soporta fijar accesos directos",
                    Toast.LENGTH_LONG).show();
            return;
        }
        ShortcutManager sm = (ShortcutManager) ctx.getSystemService(Context.SHORTCUT_SERVICE);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(ctx, "Tu pantalla de inicio (launcher) no permite fijar accesos directos",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String label = game.name == null || game.name.trim().isEmpty() ? "Juego PS2" : game.name.trim();

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setAction(MainActivity.ACTION_OPEN_GAME);
        intent.putExtra(MainActivity.EXTRA_OPEN_GAME_ID, game.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        ShortcutInfo info = new ShortcutInfo.Builder(ctx, "game_" + game.id)
                .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                .setLongLabel(label)
                .setIcon(Icon.createWithBitmap(makeIcon(label)))
                .setIntent(intent)
                .build();
        try {
            boolean ok = sm.requestPinShortcut(info, null);
            Toast.makeText(ctx, ok
                    ? "Acceso directo creado: tocá el ícono de " + label + " en tu pantalla de inicio"
                    : "No se pudo crear el acceso directo", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Tu pantalla de inicio rechazó el acceso directo", Toast.LENGTH_LONG).show();
        }
    }

    /** Ícono circular generado por código: fondo violeta e inicial del juego. */
    private static Bitmap makeIcon(String label) {
        int size = 192;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.rgb(88, 60, 180));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(96f);
        text.setTextAlign(Paint.Align.CENTER);
        String initial = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
        float y = size / 2f - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(initial, size / 2f, y, text);
        return bmp;
    }
}
