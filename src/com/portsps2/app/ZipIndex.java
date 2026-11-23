package com.portsps2.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser del directorio central de un ZIP (clase pura, sin dependencias de Android).
 *
 * Lee SOLO el índice (EOCD / ZIP64 EOCD + directorio central) usando lecturas
 * posicionadas sobre un FileChannel: nunca descomprime las entradas, así que
 * importar un ZIP con una ISO de varios GB es casi instantáneo.
 * Soporta data descriptors (los tamaños siempre están en el directorio central)
 * y ZIP64 (EOCD64 + locator + extra field 0x0001).
 */
public class ZipIndex {

    public static class Entry {
        public String name;
        public long compressedSize;
        public long uncompressedSize;
        public long localHeaderOffset;
        public boolean directory;

        public String extension() {
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length() - 1) return "";
            return name.substring(dot + 1).toLowerCase();
        }
    }

    private static final long SIG_EOCD = 0x06054b50L;
    private static final long SIG_ZIP64_EOCD = 0x06064b50L;
    private static final long SIG_ZIP64_LOCATOR = 0x07064b50L;
    private static final long SIG_CENTRAL = 0x02014b50L;
    private static final int EOCD_MIN = 22;
    private static final int MAX_COMMENT = 65535;

    /** Extensiones consideradas imagen de juego, en orden de preferencia. */
    public static final String[] GAME_EXTS = {"iso", "chd", "img", "bin"};

    public static List<Entry> parse(FileChannel ch, long fileSize) throws IOException {
        if (fileSize < EOCD_MIN) throw new IOException("archivo demasiado chico para ser ZIP");

        // 1) Buscar el EOCD escaneando la cola del archivo (hasta 64 KB + 22).
        int tailLen = (int) Math.min(fileSize, (long) EOCD_MIN + MAX_COMMENT);
        byte[] tail = readAt(ch, fileSize - tailLen, tailLen);
        int eocdPos = -1;
        for (int i = tailLen - EOCD_MIN; i >= 0; i--) {
            if (u32(tail, i) == SIG_EOCD) { eocdPos = i; break; }
        }
        if (eocdPos < 0) throw new IOException("no se encontró el fin del directorio central (EOCD)");

        long eocdOffset = fileSize - tailLen + eocdPos;
        long totalEntries = u16(tail, eocdPos + 10);
        long cdSize = u32(tail, eocdPos + 12);
        long cdOffset = u32(tail, eocdPos + 16);

        // 2) ZIP64: si algún campo está saturado, leer locator + EOCD64.
        boolean zip64 = totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL;
        if (zip64) {
            long locOffset = eocdOffset - 20;
            if (locOffset < 0) throw new IOException("ZIP64 sin locator");
            byte[] loc = readAt(ch, locOffset, 20);
            if (u32(loc, 0) != SIG_ZIP64_LOCATOR) throw new IOException("ZIP64: locator inválido");
            long z64Offset = u64(loc, 8);
            byte[] z64 = readAt(ch, z64Offset, 56);
            if (u32(z64, 0) != SIG_ZIP64_EOCD) throw new IOException("ZIP64: registro EOCD64 inválido");
            totalEntries = u64(z64, 32);
            cdSize = u64(z64, 40);
            cdOffset = u64(z64, 48);
        }

        if (cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > fileSize) {
            throw new IOException("directorio central fuera de rango");
        }
        if (totalEntries > 1_000_000) throw new IOException("ZIP con demasiadas entradas");

        // 3) Recorrer el directorio central.
        byte[] cd = readAt(ch, cdOffset, (int) Math.min(cdSize, Integer.MAX_VALUE - 8));
        ArrayList<Entry> out = new ArrayList<>();
        int pos = 0;
        Charset utf8 = Charset.forName("UTF-8");
        Charset cp437;
        try { cp437 = Charset.forName("IBM437"); }
        catch (Exception e) { cp437 = Charset.forName("ISO-8859-1"); } // Android no trae CP437
        while (pos + 46 <= cd.length) {
            if (u32(cd, pos) != SIG_CENTRAL) break;
            int flags = u16(cd, pos + 8);
            long comp = u32(cd, pos + 20);
            long uncomp = u32(cd, pos + 24);
            int nameLen = u16(cd, pos + 28);
            int extraLen = u16(cd, pos + 30);
            int commentLen = u16(cd, pos + 32);
            long localOff = u32(cd, pos + 42);
            if (pos + 46 + nameLen + extraLen > cd.length) break;
            byte[] nameBytes = new byte[nameLen];
            System.arraycopy(cd, pos + 46, nameBytes, 0, nameLen);
            boolean utf8Flag = (flags & 0x800) != 0;
            String name = new String(nameBytes, utf8Flag ? utf8 : cp437);

            // Extra fields: el ZIP64 (0x0001) trae los valores reales, en orden,
            // solo para los campos base que estén saturados (0xFFFFFFFF).
            int ex = pos + 46 + nameLen;
            int exEnd = ex + extraLen;
            while (ex + 4 <= exEnd) {
                int tag = u16(cd, ex);
                int size = u16(cd, ex + 2);
                int p = ex + 4;
                if (tag == 0x0001) {
                    if (uncomp == 0xFFFFFFFFL) { uncomp = u64(cd, p); p += 8; }
                    if (comp == 0xFFFFFFFFL) { comp = u64(cd, p); p += 8; }
                    if (localOff == 0xFFFFFFFFL) { localOff = u64(cd, p); p += 8; }
                }
                ex += 4 + size;
            }

            Entry e = new Entry();
            e.name = name;
            e.compressedSize = comp;
            e.uncompressedSize = uncomp;
            e.localHeaderOffset = localOff;
            e.directory = name.endsWith("/");
            if (!e.directory) out.add(e);
            pos += 46 + nameLen + extraLen + commentLen;
        }
        return out;
    }

    /** Mejor entrada de imagen de juego según GAME_EXTS; entre iguales, la más grande. */
    public static Entry findGameImage(List<Entry> entries) {
        Entry best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Entry e : entries) {
            int rank = -1;
            for (int i = 0; i < GAME_EXTS.length; i++) {
                if (GAME_EXTS[i].equals(e.extension())) { rank = i; break; }
            }
            if (rank < 0) continue;
            if (best == null || rank < bestRank
                    || (rank == bestRank && e.uncompressedSize > best.uncompressedSize)) {
                best = e;
                bestRank = rank;
            }
        }
        return best;
    }

    private static byte[] readAt(FileChannel ch, long pos, int len) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(len);
        long p = pos;
        while (buf.hasRemaining()) {
            int n = ch.read(buf, p);
            if (n < 0) break;
            p += n;
        }
        if (buf.position() < len) {
            byte[] partial = new byte[buf.position()];
            System.arraycopy(buf.array(), 0, partial, 0, partial.length);
            return partial;
        }
        return buf.array();
    }

    static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    static long u32(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    static long u64(byte[] b, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[off + i] & 0xFFL);
        return v;
    }

    // Mantener ByteOrder referenciado para lectores futuros (lectura manual LE arriba).
    private static final ByteOrder LE = ByteOrder.LITTLE_ENDIAN;
}
