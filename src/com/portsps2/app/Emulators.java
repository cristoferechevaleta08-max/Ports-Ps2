package com.portsps2.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Definiciones de los motores/emuladores de PS2 para Android que Ports PS2
 * sabe lanzar, con la convención de intent que usan los frontends
 * (Daijishou/EmuBridge y el código de frontends open source):
 *
 * - AetherSX2 y NetherSX2 comparten package y actividad
 *   (xyz.aethersx2.android / xyz.aethersx2.android.EmulationActivity):
 *   Intent ACTION_MAIN con extra String "bootPath" = ruta o content URI
 *   del juego. NetherSX2-Turnip y el fork de Trizle usan la misma actividad
 *   y la misma convención.
 * - ARMSX2 (com.armsx2): ACTION_VIEW con data = Uri del juego sobre su
 *   MainActivity.
 * - Play! (com.virtualapplications.play): se abre su actividad principal
 *   con la Uri del juego como data (fallback genérico).
 *
 * Clase SIN dependencias de Android a propósito: es testeable en JVM.
 */
public class Emulators {

    /** Intent ACTION_MAIN + extra "bootPath" (AetherSX2/NetherSX2 y forks). */
    public static final int MODE_BOOTPATH = 0;
    /** Intent ACTION_VIEW con data = Uri del juego (ARMSX2). */
    public static final int MODE_VIEW = 1;
    /** Abrir la actividad principal del paquete con la Uri como data (Play!). */
    public static final int MODE_LAUNCH = 2;

    public static class Def {
        public final String name;
        public final String packageName;
        /** Clase de la actividad de emulación; "" si se usa el launcher del paquete. */
        public final String activityClass;
        public final int mode;

        public Def(String name, String packageName, String activityClass, int mode) {
            this.name = name;
            this.packageName = packageName;
            this.activityClass = activityClass;
            this.mode = mode;
        }
    }

    private static final String AETHER_ACTIVITY = "xyz.aethersx2.android.EmulationActivity";

    /** Lista completa, en orden de preferencia. */
    public static List<Def> all() {
        ArrayList<Def> out = new ArrayList<>();
        out.add(new Def("AetherSX2 / NetherSX2",
                "xyz.aethersx2.android", AETHER_ACTIVITY, MODE_BOOTPATH));
        out.add(new Def("NetherSX2-Turnip",
                "xyz.aethersx2.tturnip", AETHER_ACTIVITY, MODE_BOOTPATH));
        out.add(new Def("NetherSX2 (Trizle)",
                "xyz.trizle.nethersx2", AETHER_ACTIVITY, MODE_BOOTPATH));
        out.add(new Def("ARMSX2",
                "com.armsx2", "com.armsx2.MainActivity", MODE_VIEW));
        out.add(new Def("Play!",
                "com.virtualapplications.play", "", MODE_LAUNCH));
        return out;
    }

    /** Busca una definición por package (null si no está en la lista). */
    public static Def byPackage(String packageName) {
        if (packageName == null) return null;
        for (Def d : all()) {
            if (d.packageName.equals(packageName)) return d;
        }
        return null;
    }
}
