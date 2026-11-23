import com.portsps2.app.IsoCheck;
import com.portsps2.app.ZipIndex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tests JVM (sin Android) del parser de directorio central ZIP y de IsoCheck. */
public class TestZipIso {
    static int passed = 0, failed = 0;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("PASS  " + name + "  " + detail); }
        else { failed++; System.out.println("FAIL  " + name + "  " + detail); }
    }

    static byte[] fakeIso(int size, String volumeId) {
        byte[] b = new byte[size];
        if (size >= IsoCheck.HEADER_SIZE) {
            byte[] sig = {'C', 'D', '0', '0', '1'};
            System.arraycopy(sig, 0, b, IsoCheck.CD001_OFFSET, 5);
            byte[] vol = volumeId.getBytes();
            System.arraycopy(vol, 0, b, IsoCheck.VOLUME_ID_OFFSET, Math.min(vol.length, 32));
        }
        return b;
    }

    static List<ZipIndex.Entry> parse(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r");
             FileChannel ch = raf.getChannel()) {
            return ZipIndex.parse(ch, raf.length());
        }
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/tmp/ports-tests");
        dir.mkdirs();

        // --- Test 1: ZIP normal STORED con ISO falsa + txt ---
        File f1 = new File(dir, "normal.zip");
        byte[] iso = fakeIso(40000, "JUEGO_UNO");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f1))) {
            CRC32 crc = new CRC32(); crc.update(iso);
            ZipEntry e = new ZipEntry("juego.iso");
            e.setMethod(ZipEntry.STORED);
            e.setSize(iso.length); e.setCompressedSize(iso.length); e.setCrc(crc.getValue());
            zos.putNextEntry(e); zos.write(iso); zos.closeEntry();
            zos.putNextEntry(new ZipEntry("leeme.txt"));
            zos.write("hola".getBytes("UTF-8")); zos.closeEntry();
        }
        List<ZipIndex.Entry> l1 = parse(f1);
        ZipIndex.Entry img1 = ZipIndex.findGameImage(l1);
        check("zip-normal", img1 != null && img1.name.equals("juego.iso") && img1.uncompressedSize == 40000,
                "entradas=" + l1.size() + " img=" + (img1 == null ? "null" : img1.name + "/" + img1.uncompressedSize));

        // --- Test 2: ZIP DEFLATED sin tamaños previos => data descriptors ---
        File f2 = new File(dir, "descriptor.zip");
        byte[] payload = new byte[12345];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f2))) {
            ZipEntry e = new ZipEntry("subdir/mi-juego.iso");
            e.setMethod(ZipEntry.DEFLATED); // sin setSize => ZipOutputStream usa data descriptor
            zos.putNextEntry(e); zos.write(payload); zos.closeEntry();
        }
        List<ZipIndex.Entry> l2 = parse(f2);
        ZipIndex.Entry img2 = ZipIndex.findGameImage(l2);
        check("zip-data-descriptor", img2 != null && img2.uncompressedSize == 12345,
                "img=" + (img2 == null ? "null" : img2.name + "/" + img2.uncompressedSize));

        // --- Test 2b: ZIP sin ISO pero con .bin => se acepta como imagen ---
        File f2b = new File(dir, "bin.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f2b))) {
            zos.putNextEntry(new ZipEntry("game.bin"));
            zos.write(new byte[2048]); zos.closeEntry();
            zos.putNextEntry(new ZipEntry("cover.jpg"));
            zos.write(new byte[10]); zos.closeEntry();
        }
        ZipIndex.Entry img2b = ZipIndex.findGameImage(parse(f2b));
        check("zip-bin-aceptado", img2b != null && img2b.name.equals("game.bin") && img2b.uncompressedSize == 2048,
                "img=" + (img2b == null ? "null" : img2b.name));

        // --- Test 3: ZIP64 construido a mano (EOCD saturado + EOCD64 + locator + extra) ---
        File f3 = new File(dir, "zip64.zip");
        byte[] data = new byte[100];
        byte[] name = "juego64.iso".getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Local header con extra ZIP64
        ByteArrayOutputStream extraLocal = new ByteArrayOutputStream();
        put16(extraLocal, 0x0001); put16(extraLocal, 16); put64(extraLocal, data.length); put64(extraLocal, data.length);
        long localOffset = 0;
        put32(out, 0x04034b50L); put16(out, 45); put16(out, 0); put16(out, 0);
        put16(out, 0); put16(out, 0); put32(out, 0);
        put32(out, 0xFFFFFFFFL); put32(out, 0xFFFFFFFFL);
        put16(out, name.length); put16(out, extraLocal.size());
        out.write(name); out.write(extraLocal.toByteArray()); out.write(data);
        long cdOffset = out.size();
        // Central directory con extra ZIP64 (uncomp, comp, offset)
        ByteArrayOutputStream extraCd = new ByteArrayOutputStream();
        put16(extraCd, 0x0001); put16(extraCd, 24);
        put64(extraCd, data.length); put64(extraCd, data.length); put64(extraCd, localOffset);
        put32(out, 0x02014b50L); put16(out, 45); put16(out, 45); put16(out, 0); put16(out, 0);
        put16(out, 0); put16(out, 0); put32(out, 0);
        put32(out, 0xFFFFFFFFL); put32(out, 0xFFFFFFFFL);
        put16(out, name.length); put16(out, extraCd.size()); put16(out, 0);
        put16(out, 0); put16(out, 0); put32(out, 0); put32(out, 0);
        out.write(name); out.write(extraCd.toByteArray());
        long cdSize = out.size() - cdOffset;
        long z64Offset = out.size();
        // ZIP64 EOCD (56 bytes)
        put32(out, 0x06064b50L); put64(out, 44); put16(out, 45); put16(out, 45);
        put32(out, 0); put32(out, 0); put64(out, 1); put64(out, 1); put64(out, cdSize); put64(out, cdOffset);
        // Locator (20 bytes)
        put32(out, 0x07064b50L); put32(out, 0); put64(out, z64Offset); put32(out, 1);
        // EOCD saturado
        put32(out, 0x06054b50L); put16(out, 0); put16(out, 0); put16(out, 0xFFFF); put16(out, 0xFFFF);
        put32(out, 0xFFFFFFFFL); put32(out, 0xFFFFFFFFL); put16(out, 0);
        try (FileOutputStream fos = new FileOutputStream(f3)) { fos.write(out.toByteArray()); }
        List<ZipIndex.Entry> l3 = parse(f3);
        ZipIndex.Entry img3 = ZipIndex.findGameImage(l3);
        check("zip64", img3 != null && img3.name.equals("juego64.iso") && img3.uncompressedSize == data.length,
                "entradas=" + l3.size() + " img=" + (img3 == null ? "null" : img3.name + "/" + img3.uncompressedSize));

        // --- Test 4: IsoCheck con ISO sintética válida ---
        IsoCheck.Result ok = IsoCheck.check(iso, iso.length);
        check("iso-cd001-ok", ok.verified && ok.volumeId.equals("JUEGO_UNO"),
                "verified=" + ok.verified + " vol=" + ok.volumeId);

        // --- Test 5: archivo que NO verifica => no verificado (se importa igual, no error) ---
        byte[] junk = new byte[40000];
        for (int i = 0; i < junk.length; i++) junk[i] = (byte) (i % 97);
        IsoCheck.Result bad = IsoCheck.check(junk, junk.length);
        check("iso-sin-verificar-no-bloquea", !bad.verified, "verified=" + bad.verified);
        IsoCheck.Result trunc = IsoCheck.check(new byte[100], 100);
        check("iso-truncada-no-bloquea", !trunc.verified, "verified=" + trunc.verified);

        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void put16(ByteArrayOutputStream o, int v) { o.write(v & 0xFF); o.write((v >> 8) & 0xFF); }
    static void put32(ByteArrayOutputStream o, long v) { for (int i = 0; i < 4; i++) o.write((int) (v >> (8 * i)) & 0xFF); }
    static void put64(ByteArrayOutputStream o, long v) { for (int i = 0; i < 8; i++) o.write((int) (v >> (8 * i)) & 0xFF); }
}
