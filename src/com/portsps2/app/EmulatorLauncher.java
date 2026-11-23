package com.portsps2.app;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Detecta los motores de PS2 instalados en el teléfono y abre el juego
 * porteado en ellos. Ports PS2 no incluye emulador: el juego carga de
 * verdad en el motor que el usuario ya tenga instalado (AetherSX2,
 * NetherSX2, ARMSX2, Play!...), siguiendo la convención de intents que
 * usan los frontends.
 *
 * Como la imagen porteada vive en el almacenamiento privado de la app,
 * al emulador se le pasa el content URI de GameFileProvider con permiso
 * de lectura temporal.
 */
public class EmulatorLauncher {

    /** Emuladores de la lista conocida que están instalados en el teléfono. */
    public static List<Emulators.Def> installed(Context ctx) {
        ArrayList<Emulators.Def> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (Emulators.Def def : Emulators.all()) {
            try {
                pm.getPackageInfo(def.packageName, 0);
                out.add(def);
            } catch (PackageManager.NameNotFoundException ignored) {
                // no instalado
            } catch (Exception ignored) {
                // visibilidad de paquetes u otro problema: se lo salta
            }
        }
        return out;
    }

    /** Content URI del archivo porteado del juego (null si no está porteado). */
    public static Uri gameUri(Context ctx, Game game) {
        if (game == null || game.localPath == null || game.localPath.isEmpty()) return null;
        File f = new File(game.localPath);
        if (!f.isFile()) return null;
        return GameFileProvider.uriForFile(ctx, f);
    }

    /**
     * Abre el juego en el emulador elegido. Devuelve true si el intent se
     * lanzó; ante cualquier fallo devuelve false para que la UI ofrezca
     * alternativas (nunca deja al usuario en una pantalla muerta).
     */
    public static boolean launch(Context ctx, Emulators.Def def, Game game) {
        Uri uri = gameUri(ctx, game);
        if (uri == null) return false;
        // Permiso de lectura explícito para ese paquete, además del flag.
        try {
            ctx.grantUriPermission(def.packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }

        try {
            switch (def.mode) {
                case Emulators.MODE_BOOTPATH: {
                    // AetherSX2 / NetherSX2 (y forks): ACTION_MAIN sobre
                    // EmulationActivity con extra "bootPath". Estas builds
                    // aceptan content URIs en bootPath (convención de
                    // frontends); también va como data por compatibilidad.
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.setComponent(new ComponentName(def.packageName, def.activityClass));
                    i.putExtra("bootPath", uri.toString());
                    i.setData(uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(i);
                    return true;
                }
                case Emulators.MODE_VIEW: {
                    // ARMSX2: ACTION_VIEW con data = Uri del juego.
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(uri);
                    if (def.activityClass != null && !def.activityClass.isEmpty()) {
                        i.setComponent(new ComponentName(def.packageName, def.activityClass));
                    } else {
                        i.setPackage(def.packageName);
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        ctx.startActivity(i);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        // Sin componente concreto: VIEW genérico al paquete.
                        Intent generic = new Intent(Intent.ACTION_VIEW);
                        generic.setData(uri);
                        generic.setPackage(def.packageName);
                        generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ctx.startActivity(generic);
                        return true;
                    }
                }
                case Emulators.MODE_LAUNCH:
                default: {
                    // Play!: VIEW con data al paquete; si no resuelve, su
                    // actividad principal a secas.
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(uri);
                    i.setPackage(def.packageName);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        ctx.startActivity(i);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        Intent main = ctx.getPackageManager()
                                .getLaunchIntentForPackage(def.packageName);
                        if (main == null) return false;
                        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(main);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre la búsqueda de emuladores de PS2 en la Play Store. */
    public static void openStoreSearch(Context ctx) {
        Intent market = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=PS2%20emulator"));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(market);
            return;
        } catch (Exception ignored) { }
        Intent web = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=PS2%20emulator&c=apps"));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(web);
        } catch (Exception ignored) { }
    }
}
