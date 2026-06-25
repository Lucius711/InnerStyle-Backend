package com.innerstyle.meshy.util;

import com.innerstyle.meshy.entity.enums.ModelOrigin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resizes a downloadable 3D model to a target physical height and repositions its origin.
 *
 * <p>Resizing bakes a uniform scale + translation directly into the mesh's vertex coordinates,
 * which is the only way to give a unit-less printing format (STL/OBJ) a real-world size. The
 * vertical (Y) axis is treated as the model's height (MeshyAI exports Y-up); the scale is
 * uniform so the model's proportions are preserved. Only formats whose geometry can be safely
 * rewritten without a full 3D toolchain are supported — see {@link #supportsResize(String)}.</p>
 */
public final class MeshTransformer {

    private MeshTransformer() {
    }

    private static final int STL_HEADER_BYTES = 80;
    private static final int STL_COUNT_BYTES = 4;
    private static final int STL_TRIANGLE_BYTES = 50; // normal(12) + 3 verts(36) + attr(2)
    private static final int FLOAT_BYTES = 4;
    private static final int VERTICES_PER_TRIANGLE = 3;
    private static final double EPSILON = 1e-9;

    /** Formats whose vertices can be transformed server-side without a 3D library. */
    public static boolean supportsResize(String format) {
        if (format == null) {
            return false;
        }
        String f = format.toLowerCase(Locale.ROOT);
        return f.equals("stl") || f.equals("obj");
    }

    /**
     * Returns a copy of {@code data} scaled so its height equals {@code targetHeightMm} and
     * translated so its origin matches {@code origin}. If the format is not resizable the input
     * is returned unchanged.
     */
    public static byte[] resize(byte[] data, String format, double targetHeightMm, ModelOrigin origin) {
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "stl" -> resizeStl(data, targetHeightMm, origin);
            case "obj" -> resizeObj(data, targetHeightMm, origin);
            default -> data;
        };
    }

    // ----------------------------------------------------------------- shared transform math

    /** Mutable axis-aligned bounding box accumulator. */
    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        void add(double x, double y, double z) {
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        boolean isEmpty() {
            return minX > maxX;
        }
    }

    /** Uniform scale + per-axis translation, applied as {@code v' = v * scale + t}. */
    private record Transform(double scale, double tx, double ty, double tz) {
        double ax(double v) {
            return v * scale + tx;
        }

        double ay(double v) {
            return v * scale + ty;
        }

        double az(double v) {
            return v * scale + tz;
        }
    }

    private static Transform computeTransform(Bounds b, double targetHeightMm, ModelOrigin origin) {
        double sizeY = b.maxY - b.minY;
        // Height is measured along the up-axis (Y). Fall back to the largest dimension when the
        // model is flat on Y so we never divide by (near) zero.
        double basis = sizeY;
        if (basis <= EPSILON) {
            basis = Math.max(b.maxX - b.minX, Math.max(b.maxY - b.minY, b.maxZ - b.minZ));
        }
        double scale = basis <= EPSILON ? 1.0 : targetHeightMm / basis;

        double minXs = b.minX * scale;
        double maxXs = b.maxX * scale;
        double minYs = b.minY * scale;
        double maxYs = b.maxY * scale;
        double minZs = b.minZ * scale;
        double maxZs = b.maxZ * scale;

        double tx = -(minXs + maxXs) / 2.0;
        double tz = -(minZs + maxZs) / 2.0;
        double ty = origin == ModelOrigin.CENTER ? -(minYs + maxYs) / 2.0 : -minYs;
        return new Transform(scale, tx, ty, tz);
    }

    private static double[] normal(double[] v) {
        double ax = v[3] - v[0];
        double ay = v[4] - v[1];
        double az = v[5] - v[2];
        double bx = v[6] - v[0];
        double by = v[7] - v[1];
        double bz = v[8] - v[2];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < EPSILON) {
            return new double[] {0.0, 0.0, 0.0};
        }
        return new double[] {nx / len, ny / len, nz / len};
    }

    // ----------------------------------------------------------------------------------- STL

    private static byte[] resizeStl(byte[] data, double targetHeightMm, ModelOrigin origin) {
        return isBinaryStl(data)
            ? resizeBinaryStl(data, targetHeightMm, origin)
            : resizeAsciiStl(data, targetHeightMm, origin);
    }

    private static boolean isBinaryStl(byte[] data) {
        if (data.length < STL_HEADER_BYTES + STL_COUNT_BYTES) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(data, STL_HEADER_BYTES, STL_COUNT_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        long count = bb.getInt() & 0xFFFFFFFFL;
        long expected = (long) STL_HEADER_BYTES + STL_COUNT_BYTES + count * STL_TRIANGLE_BYTES;
        return expected == data.length;
    }

    private static byte[] resizeBinaryStl(byte[] data, double targetHeightMm, ModelOrigin origin) {
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int count = in.getInt(STL_HEADER_BYTES);
        int triStart = STL_HEADER_BYTES + STL_COUNT_BYTES;

        Bounds b = new Bounds();
        for (int i = 0; i < count; i++) {
            int base = triStart + i * STL_TRIANGLE_BYTES + 3 * FLOAT_BYTES; // skip normal
            for (int v = 0; v < VERTICES_PER_TRIANGLE; v++) {
                int p = base + v * 3 * FLOAT_BYTES;
                b.add(in.getFloat(p), in.getFloat(p + FLOAT_BYTES), in.getFloat(p + 2 * FLOAT_BYTES));
            }
        }

        Transform t = computeTransform(b, targetHeightMm, origin);
        ByteBuffer out = ByteBuffer.allocate(data.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(data, 0, STL_HEADER_BYTES);
        out.putInt(count);
        for (int i = 0; i < count; i++) {
            int base = triStart + i * STL_TRIANGLE_BYTES + 3 * FLOAT_BYTES;
            double[] vs = new double[9];
            for (int v = 0; v < VERTICES_PER_TRIANGLE; v++) {
                int p = base + v * 3 * FLOAT_BYTES;
                vs[v * 3] = t.ax(in.getFloat(p));
                vs[v * 3 + 1] = t.ay(in.getFloat(p + FLOAT_BYTES));
                vs[v * 3 + 2] = t.az(in.getFloat(p + 2 * FLOAT_BYTES));
            }
            double[] n = normal(vs);
            out.putFloat((float) n[0]);
            out.putFloat((float) n[1]);
            out.putFloat((float) n[2]);
            for (int k = 0; k < 9; k++) {
                out.putFloat((float) vs[k]);
            }
            out.putShort((short) 0); // attribute byte count
        }
        return out.array();
    }

    private static byte[] resizeAsciiStl(byte[] data, double targetHeightMm, ModelOrigin origin) {
        String text = new String(data, StandardCharsets.UTF_8);
        List<double[]> verts = new ArrayList<>();
        Bounds b = new Bounds();
        for (String line : text.split("\\r?\\n")) {
            String s = line.trim();
            if (s.length() >= 6 && s.regionMatches(true, 0, "vertex", 0, 6)) {
                String[] parts = s.split("\\s+");
                if (parts.length >= 4) {
                    try {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double z = Double.parseDouble(parts[3]);
                        verts.add(new double[] {x, y, z});
                        b.add(x, y, z);
                    } catch (NumberFormatException ignored) {
                        // skip malformed vertex line
                    }
                }
            }
        }
        if (verts.size() < VERTICES_PER_TRIANGLE || b.isEmpty()) {
            return data;
        }

        Transform t = computeTransform(b, targetHeightMm, origin);
        StringBuilder sb = new StringBuilder(data.length);
        sb.append("solid model\n");
        for (int i = 0; i + 2 < verts.size(); i += VERTICES_PER_TRIANGLE) {
            double[] p0 = transformVertex(t, verts.get(i));
            double[] p1 = transformVertex(t, verts.get(i + 1));
            double[] p2 = transformVertex(t, verts.get(i + 2));
            double[] n = normal(new double[] {
                p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]
            });
            sb.append(String.format(Locale.ROOT, "  facet normal %e %e %e%n", n[0], n[1], n[2]));
            sb.append("    outer loop\n");
            appendStlVertex(sb, p0);
            appendStlVertex(sb, p1);
            appendStlVertex(sb, p2);
            sb.append("    endloop\n  endfacet\n");
        }
        sb.append("endsolid model\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendStlVertex(StringBuilder sb, double[] p) {
        sb.append(String.format(Locale.ROOT, "      vertex %e %e %e%n", p[0], p[1], p[2]));
    }

    // ----------------------------------------------------------------------------------- OBJ

    private static byte[] resizeObj(byte[] data, double targetHeightMm, ModelOrigin origin) {
        String text = new String(data, StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n", -1);

        Bounds b = new Bounds();
        for (String line : lines) {
            if (isObjVertex(line)) {
                double[] v = parseObjVertex(line);
                if (v != null) {
                    b.add(v[0], v[1], v[2]);
                }
            }
        }
        if (b.isEmpty()) {
            return data;
        }

        Transform t = computeTransform(b, targetHeightMm, origin);
        StringBuilder sb = new StringBuilder(data.length + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            double[] v = isObjVertex(line) ? parseObjVertex(line) : null;
            if (v != null) {
                sb.append(String.format(Locale.ROOT, "v %.6f %.6f %.6f",
                    t.ax(v[0]), t.ay(v[1]), t.az(v[2])));
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isObjVertex(String line) {
        // A geometric vertex line is "v" followed by whitespace (excludes "vn", "vt", "vp").
        return line.length() >= 2 && line.charAt(0) == 'v'
            && (line.charAt(1) == ' ' || line.charAt(1) == '\t');
    }

    private static double[] parseObjVertex(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 4) {
            return null;
        }
        try {
            return new double[] {
                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double[] transformVertex(Transform t, double[] v) {
        return new double[] {t.ax(v[0]), t.ay(v[1]), t.az(v[2])};
    }
}
