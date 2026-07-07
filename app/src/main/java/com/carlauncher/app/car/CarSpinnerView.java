package com.carlauncher.app.car;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.carlauncher.app.R;

/**
 * Замена OpenGL-рендера "3D-машины". Три попытки процедурной генерации
 * low-poly геометрии на боксах в реальном OpenGL ES провалились -
 * силуэт получался нечитаемым (это подтверждено скриншотами из
 * реального приложения, не догадкой). Вместо хрупкой ручной геометрии
 * используется готовое, визуально проверенное изображение машины
 * (car_neon.png, drawable-nodpi, прозрачный фон) с наложенной
 * анимацией: лёгкое покачивание на подвеске, "дыхание" неонового
 * свечения под колёсами и наклон/сдвиг в зависимости от скорости
 * (имитация инерции при разгоне). Это гарантированно выглядит как
 * настоящая машина на любом устройстве, т.к. не зависит от рендеринга
 * треугольников на конкретном GPU/драйвере.
 */
public class CarSpinnerView extends View {

    private Bitmap carBitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float bobPhase = 0f;      // лёгкое покачивание вверх-вниз
    private float breathPhase = 0f;   // пульсация свечения
    private float speedKmh = 0f;
    private float tiltDeg = 0f;       // наклон корпуса при "разгоне"
    private float targetTiltDeg = 0f;

    private ValueAnimator animator;

    public CarSpinnerView(Context context) {
        super(context);
        init();
    }

    public CarSpinnerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        loadBitmap();
        startAnimator();
    }

    private void loadBitmap() {
        try {
            carBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_neon);
        } catch (Exception e) {
            carBitmap = null;
        }
    }

    private void startAnimator() {
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(4200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            bobPhase = t;
            breathPhase = t * 1.7f;
            // плавно подтягиваем текущий наклон к целевому (инерция)
            tiltDeg += (targetTiltDeg - tiltDeg) * 0.06f;
            invalidate();
        });
        animator.start();
    }

    public void setSpeed(float speed) {
        this.speedKmh = speed;
        // чем выше скорость, тем сильнее "приседает" перед и лёгкий наклон назад
        float clamped = Math.max(0f, Math.min(speed, 160f));
        targetTiltDeg = -clamped * 0.035f;
    }

    public void onResume() {
        if (animator != null && !animator.isRunning()) animator.start();
    }

    public void onPause() {
        if (animator != null) animator.pause();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (carBitmap == null) {
            loadBitmap();
            if (carBitmap == null) return;
        }

        float bob = (float) Math.sin(bobPhase) * (h * 0.012f);
        float breath = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathPhase));

        // область под машиной для эффекта свечения (реагирует на скорость и дыхание)
        float glowCx = w * 0.5f;
        float glowCy = h * 0.80f + bob;
        float glowR = w * 0.42f * (0.9f + 0.15f * breath) * (1f + Math.min(speedKmh, 160f) / 800f);
        int glowAlpha = (int) (70 * breath) + (int) Math.min(speedKmh, 60);
        glowPaint.setShader(new RadialGradient(
                glowCx, glowCy, Math.max(glowR, 1f),
                new int[]{
                        withAlpha(0x3FE0FF, Math.min(glowAlpha, 160)),
                        withAlpha(0x3FE0FF, 0)
                },
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(glowCx, glowCy, Math.max(glowR, 1f), glowPaint);

        // масштабируем машину, сохраняя пропорции, вписывая в контейнер с отступами
        float padding = w * 0.04f;
        float availW = w - padding * 2f;
        float availH = h - padding * 2f;
        float bw = carBitmap.getWidth();
        float bh = carBitmap.getHeight();
        float scale = Math.min(availW / bw, availH / bh);

        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (w - drawW) / 2f;
        float top = (h - drawH) / 2f + bob;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(left, top);
        // лёгкий наклон-имитация инерции разгона/торможения вокруг центра машины
        matrix.postRotate(tiltDeg, w / 2f, top + drawH * 0.75f);

        canvas.drawBitmap(carBitmap, matrix, paint);
    }

    private static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
