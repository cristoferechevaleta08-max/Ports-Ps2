package com.portsps2.app;

import java.io.File;
import java.io.IOException;

/**
 * Resuelve rutas relativas garantizando que el resultado quede DENTRO de la
 * carpeta raíz (anti path-traversal: "..", rutas absolutas y trucos con
 * separadores quedan rechazados comparando rutas canónicas).
 *
 * Clase SIN dependencias de Android a propósito: es testeable en JVM y la
 * usa GameFileProvider para servir solo archivos de games/.
 */
public class SafePaths {

    /**
     * Devuelve el File de root/relativePath si queda dentro de root.
     * Lanza IllegalArgumentException si relativePath es vacío, absoluto o
     * intenta escapar de root.
     */
    public static File resolveInside(File root, String relativePath) {
        if (root == null) throw new IllegalArgumentException("raíz nula");
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("ruta vacía");
        }
        if (new File(relativePath).isAbsolute()) {
            throw new IllegalArgumentException("ruta absoluta no permitida: " + relativePath);
        }
        try {
            String rootCanon = root.getCanonicalPath();
            File target = new File(root, relativePath);
            String targetCanon = target.getCanonicalPath();
            if (!targetCanon.equals(rootCanon)
                    && !targetCanon.startsWith(rootCanon + File.separator)) {
                throw new IllegalArgumentException("la ruta escapa de la raíz: " + relativePath);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalArgumentException("ruta inválida: " + relativePath, e);
        }
    }

    /** true si relativePath se puede resolver dentro de root sin escapar. */
    public static boolean isInside(File root, String relativePath) {
        try {
            resolveInside(root, relativePath);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
