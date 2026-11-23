package com.portsps2.app;

import java.io.UnsupportedEncodingException;

/**
 * Verificación ISO 9660 sobre los primeros bytes de un archivo (clase pura).
 * La firma "CD001" está en el offset 32769 (sector 16, byte 1) y el
 * identificador de volumen en el offset 32808 (32 bytes).
 * Verificar es informativo: si no verifica, el juego se importa igual,
 * marcado como "sin verificar" (nunca se bloquea la importación por esto).
 */
public class IsoCheck {
    public static final int HEADER_SIZE = 32840;
    public static final int CD001_OFFSET = 32769;
    public static final int VOLUME_ID_OFFSET = 32808;

    public static class Result {
        public boolean verified;
        public String volumeId = "";
    }

    public static Result check(byte[] head, int read) {
        Result r = new Result();
        r.verified = read > CD001_OFFSET + 5
                && head[CD001_OFFSET] == 'C' && head[CD001_OFFSET + 1] == 'D'
                && head[CD001_OFFSET + 2] == '0' && head[CD001_OFFSET + 3] == '0'
                && head[CD001_OFFSET + 4] == '1';
        if (r.verified && read >= VOLUME_ID_OFFSET + 32) {
            try {
                r.volumeId = new String(head, VOLUME_ID_OFFSET, 32, "ASCII").trim();
            } catch (UnsupportedEncodingException ignored) { }
        }
        return r;
    }
}
