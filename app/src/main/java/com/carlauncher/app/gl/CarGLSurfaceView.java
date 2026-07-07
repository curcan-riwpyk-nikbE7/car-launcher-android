package com.carlauncher.app.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * GLSurfaceView-обёртка для 3D-виджета машины. OpenGL ES 2.0.
 * Поверхность непрозрачная - и фон/пол, и машина рисуются внутри
 * одной GL-сцены (см. CarRenderer).
 */
public class CarGLSurfaceView extends GLSurfaceView {

    private CarRenderer renderer;

    public CarGLSurfaceView(Context context) {
        super(context);
        init();
    }

    public CarGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        renderer = new CarRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void setSpeed(float speed) {
        if (renderer != null) renderer.setSpeed(speed);
    }
}
