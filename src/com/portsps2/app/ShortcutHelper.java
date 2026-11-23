package com.portsps2.app;

import android.app.AlertDialog;
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

import java.util.Collections;

/**
 * Acceso directo de un juego en la pantalla de inicio, con doble método:
 *
 * 1) Moderno: ShortcutManager.requestPinShortcut (API 26+).
 * 2) Legacy de respaldo: broadcast com.android.launcher.action.INSTALL_SHORTCUT,
 *    que usan muchos launchers que no soportan bien el método moderno.
 *
 * Además registra un dynamic shortcut para que el juego aparezca al
 * mantener apretado el ícono de la app.
 *
 * Honestidad: que el acceso aparezca depende del launcher del teléfono;
 * algunos piden tocar «Agregar» o arrastrar el ícono, y otros bloquean los
 * accesos creados por apps de terceros hasta permitirlo en sus ajustes.
 * Por eso, tras pedirlo, la app lo explica en un diálogo.
 */
public class ShortcutHelper {

    private static final String GUIDANCE =
            "Según tu pantalla de inicio (launcher), puede aparecer un aviso: "
            + "tocá «Agregar» / «Añadir a pantalla de inicio» o arrastrá el ícono "
            + "hasta donde lo quieras.\n\n"
            + "Si no aparece nada, tu launcher puede estar bloqueando los accesos "
            + "directos creados por otras apps: permitilo en los ajustes de tu "
            + "pantalla de inicio y probá de nuevo.";

    public static void createShortcut(Context ctx, Game game) {
        String label = game.name == null || game.name.trim().isEmpty()
                ? "Juego PS2" : game.name.trim();
        Intent intent = openGameIntent(ctx, game);
        Bitmap icon = makeIcon(label);

        boolean modernOk = false;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                ShortcutManager sm = (ShortcutManager)
                        ctx.getSystemService(Context.SHORTCUT_SERVICE);
                if (sm != null) {
                    // Dynamic shortcut: aparece al mantener apretado el ícono de la app.
                    registerDynamic(sm, ctx, game, label, icon, intent);
                    if (sm.isRequestPinShortcutSupported()) {
                        ShortcutInfo info = new ShortcutInfo.Builder(ctx, "game_" + game.id)
                                .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                                .setLongLabel(label)
                                .setIcon(Icon.createWithBitmap(icon))
                                .setIntent(intent)
                                .build();
                        modernOk = sm.requestPinShortcut(info, null);
                    }
                }
            } catch (Exception e) {
                modernOk = false;
            }
        }

        if (modernOk) {
            showGuidance(ctx, "Acceso directo de «" + label + "»",
                    "Se pidió crear el acceso directo.\n\n" + GUIDANCE);
            return;
        }

        // Respaldo legacy: broadcast INSTALL_SHORTCUT.
        if (sendLegacyShortcut(ctx, label, icon, intent)) {
            showGuidance(ctx, "Acceso directo de «" + label + "»",
                    "Se envió el acceso directo con el método clásico.\n\n" + GUIDANCE);
        } else {
            Toast.makeText(ctx, "No se pudo crear el acceso directo: tu pantalla "
                    + "de inicio no aceptó ninguno de los dos métodos. Revisá los "
                    + "ajustes de tu launcher.", Toast.LENGTH_LONG).show();
        }
    }

    /** Intent que abre directo la pantalla del juego (vía MainActivity). */
    private static Intent openGameIntent(Context ctx, Game game) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setAction(MainActivity.ACTION_OPEN_GAME);
        intent.putExtra(MainActivity.EXTRA_OPEN_GAME_ID, game.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /** Dynamic shortcut (best effort: si se llega al máximo, se ignora). */
    private static void registerDynamic(ShortcutManager sm, Context ctx, Game game,
                                        String label, Bitmap icon, Intent intent) {
        try {
            ShortcutInfo dyn = new ShortcutInfo.Builder(ctx, "game_" + game.id)
                    .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                    .setLongLabel(label)
                    .setIcon(Icon.createWithBitmap(icon))
                    .setIntent(intent)
                    .build();
            try {
                sm.addDynamicShortcuts(Collections.singletonList(dyn));
            } catch (IllegalArgumentException maxed) {
                // Ya hay demasiados: actualizar el existente si está.
                sm.updateShortcuts(Collections.singletonList(dyn));
            }
        } catch (Exception ignored) { }
    }

    /** Broadcast clásico de instalación de accesos directos. */
    private static boolean sendLegacyShortcut(Context ctx, String label,
                                              Bitmap icon, Intent target) {
        try {
            Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, target);
            install.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
            install.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
            install.putExtra("duplicate", false);
            ctx.sendBroadcast(install);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void showGuidance(Context ctx, String title, String msg) {
        try {
            new AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton("Entendido", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
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
