package com.carlauncher.app.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Утилиты для процедурной генерации low-poly геометрии: параллелепипед
 * (кузов/кабина/фары/спойлер), N-угольный цилиндр (колёса) и плоскость
 * (пол). Пропорции визуально проверены заранее через Python/matplotlib
 * рендер того же набора координат, прежде чем переноситься сюда -
 * поэтому силуэт машины (низкий широкий кузов, кабина сзади, 4 колеса,
 * торчащие по бокам шире кузова) гарантированно узнаваем.
 */
public class GeometryUtils {

    public static class Mesh {
        public final FloatBuffer vertexBuffer;
        public final FloatBuffer colorBuffer;
        public final int vertexCount;

        Mesh(float[] vertices, float[] colors) {
            vertexCount = vertices.length / 3;

            ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
            vbb.order(ByteOrder.nativeOrder());
            vertexBuffer = vbb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
            cbb.order(ByteOrder.nativeOrder());
            colorBuffer = cbb.asFloatBuffer();
            colorBuffer.put(colors);
            colorBuffer.position(0);
        }
    }

    /** Параллелепипед. X=ширина, Y=высота, Z=длина (положительный Z = перед). */
    public static Mesh buildBox(float cx, float cy, float cz,
                                 float hx, float hy, float hz,
                                 float r, float g, float b, float a,
                                 boolean emissive) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        float[][] faces = {
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z0, x1, y1, z1, x0, y1, z1}, // top
                {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z1, x1, y0, z0, x0, y0, z0}, // bottom
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y0, z1, x1, y1, z1, x0, y1, z1}, // front (+z)
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0}, // back (-z)
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1}, // right
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y0, z0, x0, y1, z1, x0, y1, z0}, // left
        };
        float[] shade = emissive
                ? new float[]{1f, 1f, 1f, 1f, 1f, 1f}
                : new float[]{1.25f, 0.55f, 0.95f, 0.75f, 1.05f, 0.85f};

        List<Float> verts = new ArrayList<>();
        List<Float> cols = new ArrayList<>();
        for (int f = 0; f < faces.length; f++) {
            for (float v : faces[f]) verts.add(v);
            float s = shade[f];
            for (int i = 0; i < 6; i++) {
                cols.add(clamp(r * s));
                cols.add(clamp(g * s));
                cols.add(clamp(b * s));
                cols.add(a);
            }
        }
        return toMesh(verts, cols);
    }

    /** Колесо: круг в плоскости высота/длина (Y-Z), ось вращения X (ширина). */
    public static Mesh buildWheel(float cx, float cy, float cz,
                                   float radius, float width, int sides,
                                   float r, float g, float b, float a) {
        List<Float> verts = new ArrayList<>();
        List<Float> cols = new ArrayList<>();

        float hw = width / 2f;
        double step = 2 * Math.PI / sides;

        for (int i = 0; i < sides; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            float y0 = (float) Math.sin(a0) * radius; // высота
            float z0 = (float) Math.cos(a0) * radius; // длина
            float y1 = (float) Math.sin(a1) * radius;
            float z1 = (float) Math.cos(a1) * radius;

            addTri(verts,
                    cx - hw, cy + y0, cz + z0,
                    cx - hw, cy + y1, cz + z1,
                    cx + hw, cy + y1, cz + z1);
            addTri(verts,
                    cx - hw, cy + y0, cz + z0,
                    cx + hw, cy + y1, cz + z1,
                    cx + hw, cy + y0, cz + z0);

            float shade = 0.55f + 0.45f * (float) Math.abs(Math.cos(a0));
            for (int k = 0; k < 6; k++) {
                cols.add(clamp(r * shade));
                cols.add(clamp(g * shade));
                cols.add(clamp(b * shade));
                cols.add(a);
            }

            addTri(verts, cx - hw, cy, cz, cx - hw, cy + y1, cz + z1, cx - hw, cy + y0, cz + z0);
            addTri(verts, cx + hw, cy, cz, cx + hw, cy + y0, cz + z0, cx + hw, cy + y1, cz + z1);
            for (int k = 0; k < 6; k++) {
                cols.add(clamp(r * 0.35f));
                cols.add(clamp(g * 0.35f));
                cols.add(clamp(b * 0.35f));
                cols.add(a);
            }
        }
        return toMesh(verts, cols);
    }

    /** Горизонтальная плоскость (пол) в плоскости Y=cy. */
    public static Mesh buildFloor(float cy, float halfSize, float r, float g, float b, float a) {
        List<Float> verts = new ArrayList<>();
        List<Float> cols = new ArrayList<>();
        addTri(verts,
                -halfSize, cy, -halfSize,
                halfSize, cy, -halfSize,
                halfSize, cy, halfSize);
        addTri(verts,
                -halfSize, cy, -halfSize,
                halfSize, cy, halfSize,
                -halfSize, cy, halfSize);
        for (int i = 0; i < 6; i++) {
            cols.add(r);
            cols.add(g);
            cols.add(b);
            cols.add(a);
        }
        return toMesh(verts, cols);
    }

    private static void addTri(List<Float> verts, float... xyz9) {
        for (float v : xyz9) verts.add(v);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static Mesh toMesh(List<Float> verts, List<Float> cols) {
        float[] v = new float[verts.size()];
        for (int i = 0; i < v.length; i++) v[i] = verts.get(i);
        float[] c = new float[cols.size()];
        for (int i = 0; i < c.length; i++) c[i] = cols.get(i);
        return new Mesh(v, c);
    }
}
