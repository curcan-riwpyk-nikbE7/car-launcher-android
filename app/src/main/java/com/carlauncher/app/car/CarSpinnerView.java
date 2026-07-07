package com.carlauncher.app.car;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
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
 *  v1-v3: процедурная геометрия из боксов в OpenGL ES 2.0 - три раза
 *  провалилась на реальном устройстве (нечитаемый силуэт).
 *  v4: статичное готовое изображение машины (car_neon.png) + анимация
 *  покачивания/наклона. Подтверждено пользователем реальным скриншотом.
 *  v5: добавлена плоская псевдо-3D дорога с перспективой и разметкой +
 *  вращающиеся векторные диски колёс. Подтверждено пользователем, но
 *  затем обнаружен и исправлен баг с пустыми углами карточки.
 *  v6 (текущая): пользователь прислал референс другого лаунчера
 *  (skyline synthwave/outrun: яркое сияние на горизонте, расходящиеся
 *  неоновые лучи, перспективная сетка) и попросил сделать "как здесь".
 *  Полностью заменён фон карточки на synthwave-стиль:
 *   - статический (закэшированный в отдельный software Bitmap, чтобы
 *     можно было использовать BlurMaskFilter для мягкого свечения -
 *     BlurMaskFilter не поддерживается на hardware-accelerated Canvas,
 *     но полностью работает на канвасе поверх обычного Bitmap)
 *     слой: вертикальный + радиальный градиент под яркое сияние
 *     горизонта в фиолетовых тонах, веер расходящихся неоновых лучей,
 *     статичные сходящиеся в точку схода линии сетки;
 *   - анимированный слой поверх кэша: горизонтальные линии сетки едут
 *     на зрителя (перспективное сжатие), скорость зависит от реальной
 *     скорости с GPS - как и раньше с дорожной разметкой;
 *   - машина и вращающиеся колёсные диски оставлены как есть (уже
 *     подтверждены пользователем ранее), только чуть уменьшены и
 *     подняты, чтобы не перекрывать лучи и не обрезаться.
 *  Вся композиция была заново собрана и лично просмотрена в Python/
 *  Pillow (сравнение с присланным пользователем референсом бок о бок)
 *  перед переносом на Canvas Android, см. car_geometry_check/.
 */
public class CarSpinnerView extends View {

    private Bitmap carBitmap;
    private Bitmap bgCache;
    private int bgCacheW = -1, bgCacheH = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    private float gridOffset = 0f;    // [0,1) фаза бегущей сетки
    private float wheelAngleDeg = 0f; // угол поворота дисков

    private ValueAnimator animator;
    private long lastFrameNanos = 0L;

    private static final float HORIZON_FRAC = 0.30f;
    private static final float VP_X_FRAC = 0.5f;

    // Относительные координаты колёс на car_neon.png, измерены программно
    // по цвету неоновой обводки диска (car_geometry_check/measure_wheels.py).
    private static final float REAR_CX_FRAC = 222.0f / 1621f;
    private static final float REAR_CY_FRAC = 337.0f / 604f;
    private static final float REAR_RX_FRAC = 132.0f / 1621f;
    private static final float REAR_RY_FRAC = 128.0f / 604f;

    private static final float FRONT_CX_FRAC = 847.0f / 1621f;
    private static final float FRONT_CY_FRAC = 342.0f / 604f;
    private static final float FRONT_RX_FRAC = 148.0f / 1621f;
    private static final float FRONT_RY_FRAC = 148.0f / 604f;

    private static final float WHEEL_BOTTOM_FRAC = 0.962f;

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
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(1.5f);

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
        animator.setDuration(1_000_000_000);
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

            float speedFactor = Math.min(speedKmh, 180f) / 180f;
            float gridSpeed = 0.12f + speedFactor * 1.6f;
            gridOffset += dt * gridSpeed;
            gridOffset -= Math.floor(gridOffset);

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

        if (bgCache == null || bgCacheW != w || bgCacheH != h) {
            buildBackgroundCache(w, h);
        }
        canvas.drawBitmap(bgCache, 0f, 0f, null);
        drawAnimatedGrid(canvas, w, h);

        float bob = (float) Math.sin(bobPhase) * (h * 0.010f);
        float breath = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathPhase));

        float padding = w * 0.10f;
        float availW = w - padding * 2f;
        float availH = h * 0.44f;
        float bw = carBitmap.getWidth();
        float bh = carBitmap.getHeight();
        float scale = Math.min(availW / bw, availH / bh);

        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (w - drawW) / 2f;
        float groundY = h * 0.63f;
        float top = groundY - drawH * WHEEL_BOTTOM_FRAC + bob;

        float glowCx = w * 0.5f;
        float glowCy = groundY + bob;
        float glowR = w * 0.38f * (0.9f + 0.15f * breath) * (1f + Math.min(speedKmh, 160f) / 800f);
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

        drawWheel(canvas, matrix,
                REAR_CX_FRAC * bw, REAR_CY_FRAC * bh, REAR_RX_FRAC * bw, REAR_RY_FRAC * bh, scale);
        drawWheel(canvas, matrix,
                FRONT_CX_FRAC * bw, FRONT_CY_FRAC * bh, FRONT_RX_FRAC * bw, FRONT_RY_FRAC * bh, scale);
    }

    /**
     * Статичная (не меняется каждый кадр) часть synthwave-фона: собирается
     * один раз в отдельный software Bitmap через свой Canvas - это позволяет
     * использовать BlurMaskFilter для мягкого свечения лучей, чего нельзя
     * сделать на hardware-accelerated Canvas самого View. Пересобирается
     * только при изменении размера карточки.
     */
    private void buildBackgroundCache(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        float horizonY = h * HORIZON_FRAC;
        float vpX = w * VP_X_FRAC;

        int deepColor = Color.rgb(16, 13, 36);
        int horizonColor = Color.rgb(70, 52, 130);

        Paint vGrad = new Paint(Paint.ANTI_ALIAS_FLAG);
        vGrad.setShader(new LinearGradient(0f, 0f, 0f, h,
                new int[]{deepColor, horizonColor, deepColor},
                new float[]{0f, horizonY / h, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, vGrad);

        Paint hGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        RadialGradient rg = new RadialGradient(vpX, horizonY, w * 0.85f,
                new int[]{withAlpha(0x9668FF, 190), withAlpha(0x9668FF, 60), withAlpha(0x9668FF, 0)},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
        Matrix flatten = new Matrix();
        flatten.setScale(1f, 0.32f, vpX, horizonY);
        rg.setLocalMatrix(flatten);
        hGlow.setShader(rg);
        c.drawRect(0f, 0f, w, h, hGlow);

        // веер расходящихся неоновых лучей (мягкое свечение через BlurMaskFilter -
        // работает, т.к. этот Canvas создан поверх обычного software Bitmap)
        Paint rayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rayPaint.setStrokeWidth(Math.max(2f, w * 0.0035f));
        rayPaint.setMaskFilter(new BlurMaskFilter(Math.max(3f, w * 0.006f), BlurMaskFilter.Blur.NORMAL));

        float maxLen = h * 1.5f;
        int rayCount = 13;
        for (int i = 0; i < rayCount; i++) {
            float t = i / (float) (rayCount - 1);
            float angleDeg = 90f + (t - 0.5f) * 150f;
            double rad = Math.toRadians(angleDeg);
            float x2 = (float) (vpX + maxLen * Math.cos(rad));
            float y2 = (float) (horizonY + maxLen * Math.sin(rad));
            int color = (i % 2 == 0) ? withAlpha(0xAA78FF, 130) : withAlpha(0x6496FF, 110);
            rayPaint.setColor(color);
            c.drawLine(vpX, horizonY, x2, y2, rayPaint);
        }
        int upCount = 5;
        for (int i = 0; i < upCount; i++) {
            float t = i / (float) (upCount - 1);
            float angleDeg = -90f + (t - 0.5f) * 90f;
            double rad = Math.toRadians(angleDeg);
            float length = h * 0.55f;
            float x2 = (float) (vpX + length * Math.cos(rad));
            float y2 = (float) (horizonY + length * Math.sin(rad));
            rayPaint.setColor(withAlpha(0xAA78FF, 70));
            c.drawLine(vpX, horizonY, x2, y2, rayPaint);
        }

        // статичные сходящиеся линии перспективной сетки (не анимируются -
        // едут только горизонтальные линии, как в референсе)
        Paint staticGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        staticGridPaint.setStyle(Paint.Style.STROKE);
        staticGridPaint.setStrokeWidth(1f);
        staticGridPaint.setColor(withAlpha(0x8C6EDC, 90));

        float bottomY = h * 1.08f;
        float groundHalfWBottom = w * 0.9f;
        int nVLines = 12;
        for (int i = 0; i <= nVLines; i++) {
            float t = i / (float) nVLines;
            float xBottom = vpX - groundHalfWBottom + t * (2f * groundHalfWBottom);
            c.drawLine(xBottom, bottomY, vpX, horizonY, staticGridPaint);
        }

        // яркая линия-ядро точно на горизонте
        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        core.setColor(withAlpha(0xFFFFFF, 210));
        float coreH = Math.max(1.5f, h * 0.008f);
        c.drawRect(0f, horizonY - coreH, w, horizonY + coreH, core);

        bgCache = bmp;
        bgCacheW = w;
        bgCacheH = h;
    }

    /** Горизонтальные линии сетки, "едущие" на зрителя - единственная анимированная часть фона. */
    private void drawAnimatedGrid(Canvas canvas, int w, int h) {
        float horizonY = h * HORIZON_FRAC;
        float vpX = w * VP_X_FRAC;
        float bottomY = h * 1.08f;
        float groundHalfWBottom = w * 0.9f;
        float groundHalfWTop = w * 0.02f;

        int nHLines = 10;
        for (int i = 0; i < nHLines; i++) {
            float tt = (i + gridOffset) / nHLines;
            tt = tt - (float) Math.floor(tt);
            float te = tt * tt;
            float y = bottomY + (horizonY - bottomY) * te;
            float halfW = groundHalfWBottom + (groundHalfWTop - groundHalfWBottom) * te;
            int alpha = (int) (150 * (1f - te));
            if (alpha <= 2) continue;
            gridLinePaint.setColor(withAlpha(0x8C6EDC, Math.min(alpha, 150)));
            canvas.drawLine(vpX - halfW, y, vpX + halfW, y, gridLinePaint);
        }
    }

    private final RectF wheelRect = new RectF();
    private final RectF ringRect = new RectF();
    private final RectF hubRect = new RectF();
    private final RectF hubCenterRect = new RectF();
    private final Path spokePath = new Path();

    private void drawWheel(Canvas canvas, Matrix carMatrix,
                            float cxLocal, float cyLocal, float rxLocal, float ryLocal, float scale) {
        float[] pts = new float[]{cxLocal, cyLocal};
        carMatrix.mapPoints(pts);
        float cx = pts[0];
        float cy = pts[1];
        float rx = rxLocal * scale;
        float ry = ryLocal * scale;

        canvas.save();
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
        if (bgCache != null) {
            bgCache.recycle();
            bgCache = null;
        }
    }
}
