package com.carlauncher.app.car;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.carlauncher.app.R;

/**
 * Карточка "машина". История подхода (важно для будущих правок):
 *  v1-v3: процедурная геометрия из боксов в OpenGL ES 2.0 - провалилась
 *  на реальном устройстве (нечитаемый силуэт).
 *  v4: готовое изображение машины сбоку/3-4 + покачивание. Подтверждено
 *  пользователем как выглядящее правильно.
 *  v5: добавлена плоская псевдо-3D дорога с разметкой + вращающиеся
 *  векторные диски колёс поверх картинки машины сбоку.
 *  v6: пользователь прислал референс другого лаунчера (synthwave: яркое
 *  сияние на горизонте, расходящиеся лучи, перспективная сетка) -
 *  реализовано поверх той же машины сбоку.
 *  v7 (текущая): пользователь прислал ДРУГОЙ референс - вид на машину
 *  СЗАДИ по центру карточки, с мягким ярким бело-фиолетовым сиянием-
 *  нимбом позади силуэта, светящейся горизонтальной полосой стоп-сигнала,
 *  тёмной поверхностью с едва заметной текстурой под колёсами - без
 *  лучей, без сетки, без бегущей разметки. Реализовано с нуля под этот
 *  референс:
 *   - новое сгенерированное изображение машины СЗАДИ (car_rear.png) со
 *     встроенным свечением-нимбом и текстурой пола, максимально похожее
 *     на референс, обрезанное под пропорции карточки;
 *   - т.к. колёс сбоку не видно (вид строго сзади), вращающиеся диски
 *     убраны - вместо них "ощущение движения" передаётся через
 *     пульсирующее свечение нимба позади машины (реализовано через
 *     ColorMatrix, увеличивающую яркость по синусоиде - работает
 *     одинаково что в Pillow (numpy-умножение), что в Android
 *     ColorMatrixColorFilter, т.к. оба линейно масштабируют RGB) и лёгкое
 *     покачивание корпуса на "подвеске", как раньше;
 *   - наклон корпуса пропорционально реальной скорости с GPS сохранён.
 *  Композиция и эффект пульсации свечения заранее проверены в Python/
 *  Pillow (наложение картинки на несколько "кадров" яркости) перед
 *  переносом в Canvas Android, см. car_geometry_check/.
 */
public class CarSpinnerView extends View {

    private Bitmap carBitmap;
    private final Paint carPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private float bobPhase = 0f;
    private float breathPhase = 0f;
    private float speedKmh = 0f;
    private float tiltDeg = 0f;
    private float targetTiltDeg = 0f;

    private ValueAnimator animator;
    private long lastFrameNanos = 0L;

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
            carBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_rear);
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

            bobPhase += dt * 1.4f;
            // пульсация свечения ускоряется с ростом скорости, но не исчезает на 0
            float speedFactor = Math.min(speedKmh, 180f) / 180f;
            breathPhase += dt * (1.8f + speedFactor * 2.5f);
            tiltDeg += (targetTiltDeg - tiltDeg) * 0.06f;

            invalidate();
        });
        animator.start();
    }

    public void setSpeed(float speed) {
        this.speedKmh = Math.max(0f, speed);
        float clamped = Math.max(0f, Math.min(speed, 160f));
        targetTiltDeg = -clamped * 0.02f; // здесь наклон едва заметен - вид строго сзади, сильный наклон выглядел бы неестественно
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

        float bob = (float) Math.sin(bobPhase) * (h * 0.008f);

        // яркость свечения пульсирует в диапазоне [0.9x .. 1.25x], сильнее на
        // скорости - имитация "нарастающего" эффекта энергии/движения
        float speedFactor = Math.min(speedKmh, 180f) / 180f;
        float breathRange = 0.16f + speedFactor * 0.10f;
        float brightness = 1.02f + breathRange * (float) Math.sin(breathPhase);

        ColorMatrix cm = new ColorMatrix(new float[]{
                brightness, 0, 0, 0, 0,
                0, brightness, 0, 0, 0,
                0, 0, brightness, 0, 0,
                0, 0, 0, 1, 0
        });
        carPaint.setColorFilter(new ColorMatrixColorFilter(cm));

        float bw = carBitmap.getWidth();
        float bh = carBitmap.getHeight();
        // картинка уже обрезана под пропорции карточки - заполняем весь View,
        // с небольшим избытком (center-crop), чтобы не было пустых полос
        float scale = Math.max((float) w / bw, (float) h / bh);
        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (w - drawW) / 2f;
        float top = (h - drawH) / 2f + bob;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(tiltDeg, w / 2f, h * 0.7f);

        canvas.drawBitmap(carBitmap, matrix, carPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
