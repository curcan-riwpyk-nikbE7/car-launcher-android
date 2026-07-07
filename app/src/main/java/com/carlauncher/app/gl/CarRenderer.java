package com.carlauncher.app.gl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Рендерер low-poly 3D-модели машины поверх OpenGL ES 2.0.
 *
 * ТРЕТЬЯ ВЕРСИЯ. Пропорции машины на этот раз НЕ подбирались вслепую
 * прямо в Java-коде (как в первых двух версиях, что приводило к
 * невидимой/неузнаваемой машине) - вместо этого точная копия этой же
 * геометрии была сначала отрендерена в Python (matplotlib 3D, тот же
 * набор координат боксов/колёс) и визуально проверена перед переносом
 * сюда. Итеративно исправлены:
 *  - колёса скрывались внутри кузова -> радиус увеличен, колёса вынесены
 *    ШИРЕ кузова по оси X (WHEEL_X=1.0 > BODY_HX=0.85), как у настоящей
 *    машины (шины видны сбоку из-под арок);
 *  - кузов был слишком высоким относительно длины -> снижен BODY_HY;
 *  - под колёсами теперь есть пол на реальной высоте (WHEEL_CY-WHEEL_R).
 */
public class CarRenderer implements GLSurfaceView.Renderer {

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec4 vColor;" +
            "varying vec4 fColor;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  fColor = vColor;" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "varying vec4 fColor;" +
            "void main() {" +
            "  gl_FragColor = fColor;" +
            "}";

    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private GeometryUtils.Mesh floorMesh;
    private GeometryUtils.Mesh bodyMesh;
    private GeometryUtils.Mesh hoodMesh;
    private GeometryUtils.Mesh cabinMesh;
    private GeometryUtils.Mesh spoilerMesh;
    private GeometryUtils.Mesh rimStripeMesh;
    private GeometryUtils.Mesh wheelMesh;
    private GeometryUtils.Mesh headlightLMesh;
    private GeometryUtils.Mesh headlightRMesh;
    private GeometryUtils.Mesh taillightLMesh;
    private GeometryUtils.Mesh taillightRMesh;

    private float cameraAngle = 0.6f;
    private float wheelRotation = 0f;
    private volatile float speed = 0f;
    private long lastFrameNanos = 0;

    // Визуально проверенные пропорции (см. комментарий класса)
    private static final float BODY_HX = 0.85f;
    private static final float BODY_HY = 0.22f;
    private static final float BODY_HZ = 2.0f;
    private static final float WHEEL_R = 0.40f;
    private static final float WHEEL_CY = -0.30f;
    private static final float WHEEL_X = 1.00f;
    private static final float WHEEL_Z = 1.32f;

    private static final float CAMERA_PITCH_RAD = (float) Math.toRadians(16);
    private static final float CAMERA_DISTANCE = 5.2f;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.02f, 0.02f, 0.06f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        colorHandle = GLES20.glGetAttribLocation(program, "vColor");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        buildScene();
        lastFrameNanos = System.nanoTime();
    }

    private void buildScene() {
        floorMesh = GeometryUtils.buildFloor(WHEEL_CY - WHEEL_R, 6f, 0.04f, 0.05f, 0.12f, 1f);

        bodyMesh = GeometryUtils.buildBox(0f, 0f, 0f, BODY_HX, BODY_HY, BODY_HZ,
                0.80f, 0.84f, 0.94f, 1f, false);

        hoodMesh = GeometryUtils.buildBox(0f, -0.02f, 1.55f, BODY_HX - 0.05f, 0.14f, 0.55f,
                0.72f, 0.77f, 0.88f, 1f, false);

        cabinMesh = GeometryUtils.buildBox(0f, 0.30f, -0.15f, BODY_HX - 0.2f, 0.20f, 1.0f,
                0.08f, 0.09f, 0.14f, 1f, false);

        spoilerMesh = GeometryUtils.buildBox(0f, 0.40f, -1.78f, BODY_HX - 0.05f, 0.035f, 0.12f,
                0.20f, 0.90f, 1.0f, 1f, true);

        rimStripeMesh = GeometryUtils.buildBox(0f, -0.10f, 0f, BODY_HX + 0.02f, 0.02f, BODY_HZ + 0.02f,
                0.24f, 0.88f, 1.0f, 1f, true);

        headlightLMesh = GeometryUtils.buildBox(0.6f, 0.02f, 1.95f, 0.16f, 0.07f, 0.05f,
                1f, 1f, 0.92f, 1f, true);
        headlightRMesh = GeometryUtils.buildBox(-0.6f, 0.02f, 1.95f, 0.16f, 0.07f, 0.05f,
                1f, 1f, 0.92f, 1f, true);

        taillightLMesh = GeometryUtils.buildBox(0.62f, 0.06f, -1.95f, 0.18f, 0.06f, 0.05f,
                1f, 0.15f, 0.15f, 1f, true);
        taillightRMesh = GeometryUtils.buildBox(-0.62f, 0.06f, -1.95f, 0.18f, 0.06f, 0.05f,
                1f, 0.15f, 0.15f, 1f, true);

        wheelMesh = GeometryUtils.buildWheel(0f, 0f, 0f, WHEEL_R, 0.22f, 20,
                0.05f, 0.05f, 0.07f, 1f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.frustumM(projectionMatrix, 0, -ratio * 0.55f, ratio * 0.55f, -0.55f, 0.55f, 1.5f, 16f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt > 0.1f) dt = 0.1f;

        float autoOrbitSpeed = 0.12f + Math.min(speed, 200f) * 0.0015f;
        cameraAngle += dt * autoOrbitSpeed;

        float horizontalDist = (float) (CAMERA_DISTANCE * Math.cos(CAMERA_PITCH_RAD));
        float camHeight = (float) (CAMERA_DISTANCE * Math.sin(CAMERA_PITCH_RAD)) + 0.1f;
        float camX = (float) (Math.sin(cameraAngle) * horizontalDist);
        float camZ = (float) (Math.cos(cameraAngle) * horizontalDist);

        Matrix.setLookAtM(viewMatrix, 0,
                camX, camHeight, camZ,
                0f, -0.1f, 0f,
                0f, 1f, 0f);
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        float wheelDegPerSec = speed * 48f;
        wheelRotation += wheelDegPerSec * dt;

        Matrix.setIdentityM(modelMatrix, 0);
        drawMesh(floorMesh, modelMatrix);
        drawMesh(bodyMesh, modelMatrix);
        drawMesh(hoodMesh, modelMatrix);
        drawMesh(cabinMesh, modelMatrix);
        drawMesh(spoilerMesh, modelMatrix);
        drawMesh(rimStripeMesh, modelMatrix);
        drawMesh(headlightLMesh, modelMatrix);
        drawMesh(headlightRMesh, modelMatrix);
        drawMesh(taillightLMesh, modelMatrix);
        drawMesh(taillightRMesh, modelMatrix);

        drawWheel(WHEEL_X, WHEEL_CY, WHEEL_Z);
        drawWheel(-WHEEL_X, WHEEL_CY, WHEEL_Z);
        drawWheel(WHEEL_X, WHEEL_CY, -WHEEL_Z);
        drawWheel(-WHEEL_X, WHEEL_CY, -WHEEL_Z);
    }

    private void drawWheel(float x, float y, float z) {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        Matrix.translateM(m, 0, x, y, z);
        Matrix.rotateM(m, 0, wheelRotation, 1f, 0f, 0f);
        drawMesh(wheelMesh, m);
    }

    private void drawMesh(GeometryUtils.Mesh mesh, float[] model) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0);

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, mesh.colorBuffer);
        GLES20.glEnableVertexAttribArray(colorHandle);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    private int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }
}
