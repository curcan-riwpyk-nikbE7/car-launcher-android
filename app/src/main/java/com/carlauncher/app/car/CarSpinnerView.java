package com.carlauncher.app.car;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.carlauncher.app.R;

/**
 * Карточка "3D-машина". История подхода (важно для будущих правок):
 *  v1-v3: процедурная geometрия из боксов в OpenGL ES 2.0 - три раза
 *  провалилась на реальном устройстве (нечитаемый силуэт), несмотря на
 *  то что геометрия v3 была заранее визуально проверена в
 *  Python/matplotlib - matplotlib не отражает как GLES реально рисует
 *  грани/освещение/culling, поэтому расхождение было видно только на
 *  скриншотах из настоящего приложения.
 *  v4: статичное готовое изображение машины (car_neon.png) + анимация
 *  покачивания/наклона. Подтверждено пользователем как выглядящее
 *  правильно (реальный скриншот), но без ощущения движения/дороги.
 *  v5 (текущая): та же проверенная картинка машины, ПЛЮС:
 *   - процедурная псевдо-3D дорога с перспективой и дорожной разметкой,
 *     которая "едет" на зрителя со скоростью, пропорциональной реальной
 *     скорости с GPS (SpeedProvider) - на 0 км/ч анимация практически
 *     останавливается, на скорости растёт;
 *   - вращающиеся диски колёс поверх фото колёс на картинке машины -
 *     чистая векторная геометрия (эллипс + спицы), а не поворот самой
 *     фотографии (поворот фото давал видимые швы с кузовом - проверено
 *     и отвергнуто на этапе Python-прототипирования перед переносом
 *     сюда, см. car_geometry_check/road_and_wheels_check.py).
 *  Вся композиция (дорога + машина + положение/размер колёс) была
 *  заранее собрана и лично просмотрена в Python/Pillow перед переносом
 *  на Canvas Android, т.к. API рисования (заливки, эллипсы, полигоны,
 *  альфа-композитинг) идентичны между Pillow и android.graphics.Canvas -
 *  в отличие от OpenGL, здесь нет риска расхождения между "как это
 *  выглядит в проверке" и "как это выглядит на реальном устройстве".
 */
public class CarSpinnerView extends View {

    private Bitmap carBitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wheelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaintLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaintDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float bobPhase = 0f;
    private float breathPhase = 0f;
    private float speedKmh = 0f;
    private float tiltDeg = 0f;
    private float targetTiltDeg = 0f;

    private float roadOffset = 0f;    // [0,1) фаза бегущей разметки
    private float wheelAngleDeg = 0f; // угол поворота дисков

    private ValueAnimator animator;
    private long lastFrameNanos = 0L;

    // Относительные координаты колёс на car_neon.png (доля от ширины/высоты
    // битмапа), измерены детектором окружностей + анализом цвета неоновой
    // обводки диска (см. car_geometry_check/measure_wheels.py), НЕ подобраны
    // на глаз.
    private static final float REAR_CX_FRAC = 222.0f / 1621f;
    private static final float REAR_CY_FRAC = 337.0f / 604f;
    private static final float REAR_RX_FRAC = 132.0f / 1621f;
    private static final float REAR_RY_FRAC = 128.0f / 604f;

    private static final float FRONT_CX_FRAC = 847.0f / 1621f;
    private static final float FRONT_CY_FRAC = 342.0f / 604f;
    private static final float FRONT_RX_FRAC = 148.0f / 1621f;
    private static final float FRONT_RY_FRAC = 148.0f / 604f;

    private static final float WHEEL_BOTTOM_FRAC = 0.962f; // самая нижняя точка колёс на картинке (доля высоты)

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
        initPaints();
        startAnimator();
    }

    private void initPaints() {
        roadPaint.setColor(Color.rgb(9, 10, 24));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2.5f);
        edgePaint.setColor(withAlpha(0x3FE0FF, 90));
        dashPaint.setColor(withAlpha(0xE6EBFF, 195));

        wheelBgPaint.setColor(Color.rgb(16, 16, 22));
        spokePaintLight.setColor(Color.rgb(160, 165, 178));
        spokePaintDark.setColor(Color.rgb(64, 68, 82));
        rimRingPaint.setStyle(Paint.Style.STROKE);
        rimRingPaint.setStrokeJoin(Paint.Join.ROUND);
        rimRingPaint.setColor(0xFF3FE0FF);
        hubPaint.setColor(Color.rgb(10, 10, 14));
        hubCenterPaint.setColor(Color.rgb(100, 105, 120));
    }

    private void loadBitmap() {
        try {
            carBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_neon);
        } catch (Exception e) {
            carBitmap = null;
        }
    }

    private void startAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1_000_000_000); // тик через updateListener, длительность не важна т.к. используем dt
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        lastFrameNanos = System.nanoTime();
        animator.addUpdateListener(a -> {
            long now = System.nanoTime();
            float dt = (now - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = now;
            if (dt <= 0f || dt > 0.2f) dt = 0.016f;

            bobPhase += dt * 1.5f;
            breathPhase += dt * 2.5f;
            tiltDeg += (targetTiltDeg - tiltDeg) * 0.06f;

            // скорость разметки и вращения колёс пропорциональна реальной скорости,
            // но есть небольшая базовая "холостая" анимация даже на 0 км/ч,
            // чтобы карточка не выглядела мёртвой в статике
            float speedFactor = Math.min(speedKmh, 180f) / 180f; // 0..1
            float roadSpeed = 0.12f + speedFactor * 1.6f; // циклов дорожной разметки в секунду
            roadOffset += dt * roadSpeed;
            roadOffset -= Math.floor(roadOffset);

            float wheelDegPerSec = 24f + speedKmh * 9f;
            wheelAngleDeg += dt * wheelDegPerSec;
            wheelAngleDeg -= Math.floor(wheelAngleDeg / 360f) * 360f;

            invalidate();
        });
        animator.start();
    }

    public void setSpeed(float speed) {
        this.speedKmh = Math.max(0f, speed);
        float clamped = Math.max(0f, Math.min(speed, 160f));
        targetTiltDeg = -clamped * 0.035f;
    }

    public void onResume() {
        if (animator != null && !animator.isRunning()) {
            lastFrameNanos = System.nanoTime();
            animator.start();
        }
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

        drawRoad(canvas, w, h);

        float bob = (float) Math.sin(bobPhase) * (h * 0.010f);
        float breath = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathPhase));

        float padding = w * 0.08f;
        float availW = w - padding * 2f;
        float availH = h * 0.50f;
        float bw = carBitmap.getWidth();
        float bh = carBitmap.getHeight();
        float scale = Math.min(availW / bw, availH / bh);

        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (w - drawW) / 2f;
        float groundY = h * 0.60f;
        float top = groundY - drawH * WHEEL_BOTTOM_FRAC + bob;

        // свечение под машиной (реагирует на скорость и "дыхание")
        float glowCx = w * 0.5f;
        float glowCy = groundY + bob;
        float glowR = w * 0.40f * (0.9f + 0.15f * breath) * (1f + Math.min(speedKmh, 160f) / 800f);
        int glowAlpha = (int) (55 * breath) + (int) Math.min(speedKmh, 55);
        glowPaint.setShader(new RadialGradient(
                glowCx, glowCy, Math.max(glowR, 1f),
                new int[]{withAlpha(0x3FE0FF, Math.min(glowAlpha, 140)), withAlpha(0x3FE0FF, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(glowCx, glowCy, Math.max(glowR, 1f), glowPaint);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(tiltDeg, w / 2f, top + drawH * 0.75f);
        canvas.drawBitmap(carBitmap, matrix, paint);

        // вращающиеся диски поверх колёс на картинке (та же матрица трансформации,
        // чтобы координаты совпадали с наклоном/покачиванием кузова)
        drawWheel(canvas, matrix, drawW, drawH,
                REAR_CX_FRAC * bw, REAR_CY_FRAC * bh, REAR_RX_FRAC * bw, REAR_RY_FRAC * bh, scale);
        drawWheel(canvas, matrix, drawW, drawH,
                FRONT_CX_FRAC * bw, FRONT_CY_FRAC * bh, FRONT_RX_FRAC * bw, FRONT_RY_FRAC * bh, scale);
    }

    private void drawRoad(Canvas canvas, int w, int h) {
        float horizonY = h * 0.30f;
        float bottomY = h * 1.15f;
        float vpX = w * 0.5f;
        float roadBottomHalfW = w * 0.75f;
        float roadTopHalfW = w * 0.03f;

        Path road = new Path();
        road.moveTo(vpX - roadBottomHalfW, bottomY);
        road.lineTo(vpX + roadBottomHalfW, bottomY);
        road.lineTo(vpX + roadTopHalfW, horizonY);
        road.lineTo(vpX - roadTopHalfW, horizonY);
        road.close();
        canvas.drawPath(road, roadPaint);

        canvas.drawLine(vpX - roadBottomHalfW, bottomY, vpX - roadTopHalfW, horizonY, edgePaint);
        canvas.drawLine(vpX + roadBottomHalfW, bottomY, vpX + roadTopHalfW, horizonY, edgePaint);

        int nDashes = 16;
        float dashLenFrac = 0.45f;
        for (int i = 0; i < nDashes + 2; i++) {
            float t0 = (i + roadOffset) / nDashes;
            if (t0 >= 1f) continue;
            float t1 = Math.min(t0 + dashLenFrac / nDashes, 1f);

            float tt0 = t0 * t0;
            float y0 = bottomY + (horizonY - bottomY) * tt0;
            float hw0 = roadBottomHalfW + (roadTopHalfW - roadBottomHalfW) * tt0;
            float tt1 = t1 * t1;
            float y1 = bottomY + (horizonY - bottomY) * tt1;
            float hw1 = roadTopHalfW + (roadBottomHalfW - roadTopHalfW) * (1f - tt1);

            float dashW0 = hw0 * 0.035f;
            float dashW1 = hw1 * 0.035f;

            Path dash = new Path();
            dash.moveTo(vpX - dashW0, y0);
            dash.lineTo(vpX + dashW0, y0);
            dash.lineTo(vpX + dashW1, y1);
            dash.lineTo(vpX - dashW1, y1);
            dash.close();
            canvas.drawPath(dash, dashPaint);
        }
    }

    private final RectF wheelRect = new RectF();
    private final RectF innerRect = new RectF();
    private final RectF outerRect = new RectF();
    private final RectF ringRect = new RectF();
    private final RectF hubRect = new RectF();
    private final RectF hubCenterRect = new RectF();
    private final Path spokePath = new Path();

    private void drawWheel(Canvas canvas, Matrix carMatrix, float drawW, float drawH,
                            float cxLocal, float cyLocal, float rxLocal, float ryLocal, float scale) {
        // переводим локальные координаты колеса (в системе координат исходного
        // битмапа) в координаты канвы через ту же матрицу, что и сама машина,
        // чтобы диск точно совпадал с покачиванием/наклоном кузова
        float[] pts = new float[]{cxLocal, cyLocal};
        carMatrix.mapPoints(pts);
        float cx = pts[0];
        float cy = pts[1];
        float rx = rxLocal * scale;
        float ry = ryLocal * scale;

        canvas.save();
        // применяем тот же поворот тела (tiltDeg), что и матрица машины, чтобы диск
        // "приклеился" к кузову при наклоне
        canvas.rotate(tiltDeg, cx, cy);

        wheelRect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(wheelRect, wheelBgPaint);

        int nSpokes = 7;
        float innerRxF = rx * 0.14f, innerRyF = ry * 0.14f;
        float outerRxF = rx * 0.80f, outerRyF = ry * 0.80f;
        float spokeHalfWidthDeg = 6f;

        for (int i = 0; i < nSpokes; i++) {
            float baseAngle = wheelAngleDeg + i * (360f / nSpokes);
            double a0 = Math.toRadians(baseAngle - spokeHalfWidthDeg);
            double a1 = Math.toRadians(baseAngle + spokeHalfWidthDeg);

            spokePath.reset();
            spokePath.moveTo(cx + (float) (innerRxF * Math.cos(a0)), cy + (float) (innerRyF * Math.sin(a0)));
            spokePath.lineTo(cx + (float) (outerRxF * Math.cos(a0)), cy + (float) (outerRyF * Math.sin(a0)));
            spokePath.lineTo(cx + (float) (outerRxF * Math.cos(a1)), cy + (float) (outerRyF * Math.sin(a1)));
            spokePath.lineTo(cx + (float) (innerRxF * Math.cos(a1)), cy + (float) (innerRyF * Math.sin(a1)));
            spokePath.close();
            canvas.drawPath(spokePath, (i % 2 == 0) ? spokePaintLight : spokePaintDark);
        }

        rimRingPaint.setStrokeWidth(Math.max(2f, Math.min(rx, ry) * 0.07f));
        ringRect.set(cx - rx * 0.97f, cy - ry * 0.97f, cx + rx * 0.97f, cy + ry * 0.97f);
        canvas.drawOval(ringRect, rimRingPaint);

        hubRect.set(cx - rx * 0.17f, cy - ry * 0.17f, cx + rx * 0.17f, cy + ry * 0.17f);
        canvas.drawOval(hubRect, hubPaint);
        hubCenterRect.set(cx - rx * 0.08f, cy - ry * 0.08f, cx + rx * 0.08f, cy + ry * 0.08f);
        canvas.drawOval(hubCenterRect, hubCenterPaint);

        canvas.restore();
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
