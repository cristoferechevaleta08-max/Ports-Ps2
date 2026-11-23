import com.portsps2.app.Emulators;
import com.portsps2.app.SafePaths;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Tests JVM (sin Android) de las piezas puras de la v1.3:
 *  validación de rutas del GameFileProvider (SafePaths) y la lista de
 *  emuladores conocidos (Emulators). */
public class TestV13 {
    static int passed = 0, failed = 0;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("PASS  " + name + "  " + detail); }
        else { failed++; System.out.println("FAIL  " + name + "  " + detail); }
    }

    public static void main(String[] args) throws Exception {
        // ---------- SafePaths ----------
        File root = Files.createTempDirectory("games-root").toFile();
        File sub = new File(root, "kenichi_123");
        sub.mkdirs();
        File iso = new File(sub, "game.iso");
        iso.createNewFile();

        File resolved = SafePaths.resolveInside(root, "kenichi_123/game.iso");
        check("SafePaths resuelve ruta interna", resolved.getCanonicalPath()
                .equals(iso.getCanonicalPath()), resolved.getPath());

        check("SafePaths acepta subcarpeta", SafePaths.isInside(root, "a/b/c.iso"), "");
        check("SafePaths rechaza .. directo", !SafePaths.isInside(root, "../fuera.iso"), "");
        check("SafePaths rechaza escape en el medio",
                !SafePaths.isInside(root, "kenichi_123/../../fuera.iso"), "");
        check("SafePaths rechaza ruta absoluta",
                !SafePaths.isInside(root, "/etc/passwd"), "");
        check("SafePaths rechaza vacío", !SafePaths.isInside(root, "  "), "");

        boolean threw = false;
        try {
            SafePaths.resolveInside(root, "../../etc/passwd");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("SafePaths lanza ante traversal", threw, "");

        // ---------- Emulators ----------
        List<Emulators.Def> all = Emulators.all();
        check("Hay 5 emuladores conocidos", all.size() == 5, "size=" + all.size());

        Emulators.Def aether = Emulators.byPackage("xyz.aethersx2.android");
        check("AetherSX2/NetherSX2 definido", aether != null, "");
        check("AetherSX2 usa bootPath", aether != null
                && aether.mode == Emulators.MODE_BOOTPATH, "");
        check("AetherSX2 actividad EmulationActivity", aether != null
                && "xyz.aethersx2.android.EmulationActivity".equals(aether.activityClass),
                aether == null ? "" : aether.activityClass);

        Emulators.Def turnip = Emulators.byPackage("xyz.aethersx2.tturnip");
        check("NetherSX2-Turnip comparte actividad y bootPath", turnip != null
                && turnip.mode == Emulators.MODE_BOOTPATH
                && "xyz.aethersx2.android.EmulationActivity".equals(turnip.activityClass), "");

        Emulators.Def trizle = Emulators.byPackage("xyz.trizle.nethersx2");
        check("NetherSX2 (Trizle) usa bootPath", trizle != null
                && trizle.mode == Emulators.MODE_BOOTPATH, "");

        Emulators.Def armsx2 = Emulators.byPackage("com.armsx2");
        check("ARMSX2 usa VIEW", armsx2 != null
                && armsx2.mode == Emulators.MODE_VIEW, "");

        Emulators.Def play = Emulators.byPackage("com.virtualapplications.play");
        check("Play! usa LAUNCH", play != null
                && play.mode == Emulators.MODE_LAUNCH, "");

        check("Package desconocido devuelve null",
                Emulators.byPackage("com.ejemplo.nada") == null, "");

        boolean packagesOk = true;
        for (Emulators.Def d : all) {
            if (d.packageName == null || d.packageName.isEmpty()
                    || d.name == null || d.name.isEmpty()) packagesOk = false;
            if (d.mode == Emulators.MODE_BOOTPATH
                    && (d.activityClass == null || d.activityClass.isEmpty())) packagesOk = false;
        }
        check("Todas las definiciones son consistentes", packagesOk, "");

        System.out.println();
        System.out.println("Resultado: " + passed + " PASS, " + failed + " FAIL");
        if (failed > 0) System.exit(1);
    }
}
