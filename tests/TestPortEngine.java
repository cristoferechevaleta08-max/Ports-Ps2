import com.portsps2.app.IsoCheck;
import com.portsps2.app.PortEngine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tests JVM (sin Android) del motor de port real v1.2 (PortEngine). */
public class TestPortEngine {
    static int passed = 0, failed = 0;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("PASS  " + name + "  " + detail); }
        else { failed++; System.out.println("FAIL  " + name + "  " + detail); }
    }

    static class Listener implements PortEngine.Listener {
        boolean cancelAfterFirst = false;
        int progressCalls = 0;
        long lastCopied = -1, lastExpected = -1;
        @Override public void onProgress(long copied, long expected) {
            progressCalls++;
            lastCopied = copied;
            lastExpected = expected;
        }
        @Override public boolean isCancelled() {
            return cancelAfterFirst && progressCalls > 0;
        }
    }

    /** ISO sintética: relleno determinístico + firma CD001 + volume id. */
    static byte[] fakeIso(int size, String volumeId) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = (byte) (i % 251);
        if (size >= IsoCheck.HEADER_SIZE) {
            byte[] sig = {'C', 'D', '0', '0', '1'};
            System.arraycopy(sig, 0, b, IsoCheck.CD001_OFFSET, 5);
            byte[] vol = volumeId.getBytes();
            System.arraycopy(vol, 0, b, IsoCheck.VOLUME_ID_OFFSET, Math.min(vol.length, 32));
        }
        return b;
    }

    static byte[] readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            return in.readAllBytes();
        }
    }

    public static void main(String[] args) throws Exception {
        File base = new File(args.length > 0 ? args[0] : "/tmp/ports-port-tests");
        PortEngine.deleteRecursively(base);
        base.mkdirs();

        // --- Test 1: port desde ZIP STORED ---
        byte[] iso1 = fakeIso(40000, "JUEGO_PORT");
        File zip1 = new File(base, "stored.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip1))) {
            CRC32 crc = new CRC32(); crc.update(iso1);
            ZipEntry e = new ZipEntry("juego.iso");
            e.setMethod(ZipEntry.STORED);
            e.setSize(iso1.length); e.setCompressedSize(iso1.length); e.setCrc(crc.getValue());
            zos.putNextEntry(e); zos.write(iso1); zos.closeEntry();
            zos.putNextEntry(new ZipEntry("leeme.txt"));
            zos.write("hola".getBytes("UTF-8")); zos.closeEntry();
        }
        File dir1 = new File(base, "g1");
        Listener l1 = new Listener();
        PortEngine.Result r1;
        try (InputStream in = new FileInputStream(zip1)) {
            r1 = PortEngine.portFromZip(in, dir1, "juego.iso", iso1.length, l1);
        }
        check("port-zip-stored", r1.size == iso1.length && r1.file.getName().equals("game.iso")
                        && Arrays.equals(readAll(r1.file), iso1) && l1.progressCalls > 0,
                "size=" + r1.size + " file=" + r1.file.getName() + " progress=" + l1.progressCalls);

        // --- Test 2: port desde ZIP DEFLATED (con data descriptor, entrada en subcarpeta) ---
        byte[] iso2 = fakeIso(123456, "DEFLATED");
        File zip2 = new File(base, "deflated.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip2))) {
            zos.putNextEntry(new ZipEntry("portada.txt"));
            zos.write(new byte[100]); zos.closeEntry();
            ZipEntry e = new ZipEntry("disco/game.iso");
            e.setMethod(ZipEntry.DEFLATED); // sin tamaños previos => data descriptor
            zos.putNextEntry(e); zos.write(iso2); zos.closeEntry();
        }
        File dir2 = new File(base, "g2");
        PortEngine.Result r2;
        try (InputStream in = new FileInputStream(zip2)) {
            r2 = PortEngine.portFromZip(in, dir2, "disco/game.iso", iso2.length, new Listener());
        }
        check("port-zip-deflated", r2.size == iso2.length && Arrays.equals(readAll(r2.file), iso2),
                "size=" + r2.size + " entry=" + r2.entryName);

        // --- Test 3: port de ISO directa ---
        File dir3 = new File(base, "g3");
        PortEngine.Result r3 = PortEngine.portFromImage(
                new ByteArrayInputStream(iso1), dir3, "iso", iso1.length, new Listener());
        check("port-iso-directa", r3.size == iso1.length && Arrays.equals(readAll(r3.file), iso1),
                "size=" + r3.size);

        // --- Test 4: ZIP sin la imagen pedida => falla limpio, sin archivo final ---
        File zip4 = new File(base, "sinimagen.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip4))) {
            zos.putNextEntry(new ZipEntry("leeme.txt"));
            zos.write("nada".getBytes("UTF-8")); zos.closeEntry();
        }
        File dir4 = new File(base, "g4");
        boolean threw4 = false;
        try (InputStream in = new FileInputStream(zip4)) {
            PortEngine.portFromZip(in, dir4, "juego.iso", 0, new Listener());
        } catch (PortEngine.PortException e) {
            threw4 = true;
        }
        check("port-zip-sin-imagen-falla-limpio", threw4 && !new File(dir4, "game.iso").exists(),
                "excepcion=" + threw4);

        // --- Test 5: tamaño que no coincide => falla y no deja archivo válido ni parcial ---
        File dir5 = new File(base, "g5");
        boolean threw5 = false;
        try {
            PortEngine.portFromImage(new ByteArrayInputStream(new byte[1000]),
                    dir5, "iso", 1500, new Listener());
        } catch (PortEngine.PortException e) {
            threw5 = true;
        }
        check("port-size-mismatch-falla-limpio", threw5
                        && !new File(dir5, "game.iso").exists()
                        && !new File(dir5, "game.iso.partial").exists(),
                "excepcion=" + threw5);

        // --- Test 6: cancelación => CancelledException y sin archivo final/parcial ---
        File dir6 = new File(base, "g6");
        Listener l6 = new Listener();
        l6.cancelAfterFirst = true;
        boolean cancelled = false;
        try {
            PortEngine.portFromImage(new ByteArrayInputStream(fakeIso(600000, "CANCEL")),
                    dir6, "iso", 600000, l6);
        } catch (PortEngine.CancelledException e) {
            cancelled = true;
        }
        check("port-cancelado-limpio", cancelled
                        && !new File(dir6, "game.iso").exists()
                        && !new File(dir6, "game.iso.partial").exists(),
                "cancelled=" + cancelled + " progressCalls=" + l6.progressCalls);

        // --- Test 7: chequeo de espacio ---
        boolean spaceThrew = false;
        try {
            PortEngine.checkSpace(base, Long.MAX_VALUE / 4);
        } catch (PortEngine.PortException e) {
            spaceThrew = e.getMessage() != null && e.getMessage().contains("espacio");
        }
        boolean spaceOk = true;
        try {
            PortEngine.checkSpace(base, 1);
        } catch (PortEngine.PortException e) {
            spaceOk = false;
        }
        check("port-chequeo-espacio", spaceThrew && spaceOk,
                "sinEspacioDetectado=" + spaceThrew + " conEspacioOk=" + spaceOk);

        // --- Test 8: port.json y nombre de carpeta seguro ---
        PortEngine.writePortJson(dir1, "Juego \"X\"", "ZIP", r1.file.getName(), r1.size, 123456789L);
        File pj = new File(dir1, "port.json");
        String json = pj.exists() ? new String(Files.readAllBytes(pj.toPath()), "UTF-8") : "";
        String sane = PortEngine.sanitizeDirName("Mi Juego: Edición!", 7);
        check("port-json-y-sanitize", json.contains("\"size\": " + r1.size)
                        && json.contains("\\\"X\\\"")
                        && !sane.contains(" ") && !sane.contains(":") && !sane.contains("!"),
                "jsonOk=" + json.contains("\"size\": " + r1.size) + " sane=" + sane);

        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
