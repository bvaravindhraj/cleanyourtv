package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * The weather outside, over the wallpaper (config.xml weather, fed by WeatherNow): rain falling, with lightning in a
 * storm, snow drifting, a bright sun with turning rays, stars at a clear night, clouds passing and gusts of wind.
 *
 * It animates for {@link #RUN_MS} after Home comes up or a key is pressed, then holds the last frame, so an idle Home
 * on a 1 GB TV is not redrawing the whole screen all evening. Clouds are drawn once into small bitmaps; everything
 * else is a handful of lines and circles a frame.
 */
public class WeatherView extends View {

    /** What to show, as flags; see {@link #effects}. */
    static final int SUN = 1, STARS = 2, CLOUDS = 4, OVERCAST = 8, RAIN = 16, STORM = 32, SNOW = 64, WIND = 128;
    /** Wind from this speed on, in km/h, blows streaks across the screen. */
    static final int WINDY_KMH = 25;

    private static final long RUN_MS = 90_000, FRAME_MS = 40;
    /** Clouds stay see-through, so the wallpaper and anything behind them still reads. */
    private static final int CLOUD_ALPHA = 85;
    private static final int DROPS = 90, FLAKES = 60, STAR_COUNT = 45, STREAKS = 7;

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private final float density;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rays = new Path();

    private int effects;
    private long runUntil, lastFrame;
    private float seconds;                    // animation time, frozen while held
    private long flashAt = -1, nextFlash;

    // particles, in px; made for the view's size in onSizeChanged
    private final float[] px = new float[Math.max(DROPS, FLAKES)], py = new float[px.length],
            pv = new float[px.length], pl = new float[px.length];
    private final float[] starX = new float[STAR_COUNT], starY = new float[STAR_COUNT], starPhase = new float[STAR_COUNT];
    private final float[] streakX = new float[STREAKS], streakY = new float[STREAKS], streakLen = new float[STREAKS];
    private Bitmap[] clouds = new Bitmap[0];
    private float[] cloudX = new float[0], cloudY = new float[0], cloudV = new float[0];
    private Shader sunGlow, warmth, gloom;

    private final Runnable frame = this::invalidate;

    public WeatherView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        setVisibility(GONE);
    }

    /** The flags for a WMO weather code, day or night, and the wind in km/h. */
    static int effects(int code, boolean day, int windKmh) {
        int e;
        if (code <= 1) e = day ? SUN : STARS;
        else if (code == 2) e = CLOUDS | (day ? SUN : 0);
        else if (code == 3 || code == 45 || code == 48) e = CLOUDS | OVERCAST;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) e = CLOUDS | OVERCAST | SNOW;
        else if (code >= 95) e = CLOUDS | OVERCAST | RAIN | STORM;
        else if (code >= 51) e = CLOUDS | OVERCAST | RAIN;
        else e = CLOUDS;
        if (windKmh >= WINDY_KMH) e |= WIND;
        return e;
    }

    /** Shows {@code effects} (0 hides the view); the same flags again changes nothing. */
    void show(int effects) {
        if (effects == this.effects) return;
        this.effects = effects;
        setVisibility(effects == 0 ? GONE : VISIBLE);
        if (getWidth() > 0) build(getWidth(), getHeight());
        wake();
    }

    /** Animates again for RUN_MS (Home came up, or a key was pressed). */
    void wake() {
        if (effects == 0) return;
        boolean held = SystemClock.uptimeMillis() >= runUntil;
        runUntil = SystemClock.uptimeMillis() + RUN_MS;
        if (held) {
            lastFrame = 0;
            invalidate();
        }
    }

    /** Holds the current frame (Home went to the background). */
    void hold() {
        runUntil = 0;
        removeCallbacks(frame);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) build(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        hold();
        super.onDetachedFromWindow();
    }

    private void build(int w, int h) {
        for (int i = 0; i < px.length; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h;
            pv[i] = rnd.nextFloat();
            pl[i] = rnd.nextFloat();
        }
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rnd.nextFloat() * w;
            starY[i] = rnd.nextFloat() * h * 0.45f;
            starPhase[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < STREAKS; i++) newStreak(i, w, h, true);

        int n = (effects & CLOUDS) == 0 ? 0 : (effects & OVERCAST) != 0 ? 3 : 2;
        if (clouds.length != n) {   // 2 white or 3 grey: a new count is a new colour too
            for (Bitmap b : clouds) b.recycle();
            clouds = new Bitmap[n];
            cloudX = new float[n];
            cloudY = new float[n];
            cloudV = new float[n];
            Random shapes = new Random(7);
            for (int i = 0; i < n; i++) clouds[i] = cloud(shapes);
        }
        for (int i = 0; i < n; i++) {
            cloudX[i] = w * (i + rnd.nextFloat() * 0.6f) / n;   // spread out, never piled up
            cloudY[i] = h * (0.16f + 0.24f * rnd.nextFloat());   // below the clock and date, above the dock
            cloudV[i] = (5f + 5f * rnd.nextFloat()) * density;
        }

        float sx = w * 0.84f, sy = h * 0.06f;
        sunGlow = new RadialGradient(sx, sy, 150 * density,
                new int[]{0xFFFFF6D8, 0xCCFFE08A, 0x33FFC94D, 0x00FFC94D}, new float[]{0f, 0.18f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        warmth = new RadialGradient(sx, sy, w * 0.9f, 0x40FFB347, 0x00FFB347, Shader.TileMode.CLAMP);
        gloom = new RadialGradient(w / 2f, 0f, w, 0x66101820, 0x33101820, Shader.TileMode.CLAMP);
        rays.reset();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, spread = 0.07;
            rays.moveTo(0f, 0f);
            rays.lineTo((float) Math.cos(a - spread) * 260 * density, (float) Math.sin(a - spread) * 260 * density);
            rays.lineTo((float) Math.cos(a + spread) * 260 * density, (float) Math.sin(a + spread) * 260 * density);
            rays.close();
        }
        nextFlash = SystemClock.uptimeMillis() + 4000 + rnd.nextInt(6000);
    }

    /** One soft cloud, a few overlapping puffs blurred at the edges, about 220 dp wide. */
    private Bitmap cloud(Random r) {
        int w = (int) (240 * density), h = (int) (110 * density);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setMaskFilter(new BlurMaskFilter(10 * density, BlurMaskFilter.Blur.NORMAL));
        boolean grey = (effects & OVERCAST) != 0;
        p.setColor(grey ? 0xB0A9B1BC : 0xC8FFFFFF);
        float base = h * 0.72f;
        int puffs = 4 + r.nextInt(3);
        for (int i = 0; i < puffs; i++) {
            float cx = w * (0.2f + 0.6f * i / (puffs - 1f));
            float rad = h * (0.22f + 0.16f * r.nextFloat()) * (i == 0 || i == puffs - 1 ? 0.8f : 1.1f);
            c.drawCircle(cx, base - rad * 0.6f, rad, p);
        }
        c.drawRect(w * 0.15f, base - h * 0.15f, w * 0.85f, base, p);
        return b;
    }

    private void newStreak(int i, int w, int h, boolean anywhere) {
        streakLen[i] = (160 + rnd.nextFloat() * 220) * density;
        streakX[i] = anywhere ? rnd.nextFloat() * w : -streakLen[i] - rnd.nextFloat() * w * 0.6f;
        streakY[i] = h * (0.12f + 0.6f * rnd.nextFloat());
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (effects == 0 || w == 0) return;
        long now = SystemClock.uptimeMillis();
        boolean running = now < runUntil;
        float dt = running && lastFrame > 0 ? Math.min(0.1f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = running ? now : 0;
        seconds += dt;
        boolean windy = (effects & WIND) != 0;

        if ((effects & (RAIN | OVERCAST)) != 0) {
            paint.setShader(gloom);
            paint.setAlpha((effects & RAIN) != 0 ? 255 : 150);
            c.drawRect(0, 0, w, h, paint);
            paint.setAlpha(255);
        }
        if ((effects & SUN) != 0) drawSun(c, w, h);
        if ((effects & STARS) != 0) drawStars(c);
        for (int i = 0; i < clouds.length; i++) {
            cloudX[i] += cloudV[i] * dt * (windy ? 3f : 1f);
            if (cloudX[i] > w) cloudX[i] = -clouds[i].getWidth();
            paint.setShader(null);
            paint.setAlpha(CLOUD_ALPHA);
            c.drawBitmap(clouds[i], cloudX[i], cloudY[i], paint);
        }
        if ((effects & RAIN) != 0) drawRain(c, w, h, dt, windy);
        if ((effects & SNOW) != 0) drawSnow(c, w, h, dt, windy);
        if (windy) drawWind(c, w, h, dt);
        if ((effects & STORM) != 0) drawLightning(c, w, h, now, running);

        if (running) postDelayed(frame, FRAME_MS);
    }

    private void drawSun(Canvas c, int w, int h) {
        float sx = w * 0.84f, sy = h * 0.06f;
        paint.setShader(warmth);
        paint.setAlpha(255);
        c.drawRect(0, 0, w, h, paint);
        c.save();
        c.translate(sx, sy);
        c.rotate(seconds * 3f);
        paint.setShader(null);
        paint.setColor(0xFFFFE9A8);
        paint.setAlpha((int) (20 + 8 * Math.sin(seconds * 0.8)));
        c.drawPath(rays, paint);
        c.restore();
        paint.setShader(sunGlow);
        paint.setAlpha(255);
        c.drawCircle(sx, sy, 150 * density, paint);
        paint.setShader(null);
    }

    private void drawStars(Canvas c) {
        paint.setShader(null);
        for (int i = 0; i < STAR_COUNT; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(seconds * (0.8f + (i % 5) * 0.3f) + starPhase[i]);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (60 + 150 * tw));
            c.drawCircle(starX[i], starY[i], (0.8f + (i % 3) * 0.5f) * density, paint);
        }
    }

    private void drawRain(Canvas c, int w, int h, float dt, boolean windy) {
        float slant = windy ? 0.35f : 0.12f;
        line.setStrokeWidth(1.4f * density);
        for (int i = 0; i < DROPS; i++) {
            float speed = (700 + 500 * pv[i]) * density;
            float len = (14 + 16 * pl[i]) * density;
            py[i] += speed * dt;
            px[i] += speed * slant * dt;
            if (py[i] - len > h) {
                py[i] = -len;
                px[i] = rnd.nextFloat() * (w + h * slant) - h * slant;
            }
            if (px[i] > w) px[i] -= w;
            line.setColor(0x00C8DCFF | ((int) (70 + 70 * pl[i]) << 24));
            c.drawLine(px[i], py[i], px[i] - len * slant, py[i] - len, line);
        }
    }

    private void drawSnow(Canvas c, int w, int h, float dt, boolean windy) {
        paint.setShader(null);
        for (int i = 0; i < FLAKES; i++) {
            float speed = (30 + 40 * pv[i]) * density;
            py[i] += speed * dt;
            px[i] += ((float) Math.sin(seconds + i) * 12f + (windy ? 60f : 0f)) * density * dt;
            if (py[i] > h) {
                py[i] = -5 * density;
                px[i] = rnd.nextFloat() * w;
            }
            if (px[i] > w) px[i] -= w;
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (150 + 90 * pl[i]));
            c.drawCircle(px[i], py[i], (1.5f + 2f * pl[i]) * density, paint);
        }
    }

    /** Gusts: long wavy streaks that sweep across, brightest in the middle and fading at both ends. */
    private void drawWind(Canvas c, int w, int h, float dt) {
        line.setStrokeWidth(1.6f * density);
        for (int i = 0; i < STREAKS; i++) {
            streakX[i] += 520 * density * dt;
            if (streakX[i] > w) newStreak(i, w, h, false);
            float len = streakLen[i];
            int segs = 16;
            float prevX = streakX[i], prevY = streakY[i] + wave(i, 0f);
            for (int s = 1; s <= segs; s++) {
                float t = s / (float) segs;
                float x = streakX[i] + len * t, y = streakY[i] + wave(i, t);
                float a = (float) Math.sin(Math.PI * t);
                line.setColor(0x00FFFFFF | ((int) (110 * a) << 24));
                c.drawLine(prevX, prevY, x, y, line);
                prevX = x;
                prevY = y;
            }
        }
    }

    private float wave(int i, float t) {
        return (float) Math.sin(t * 5f + seconds * 3f + i) * 7f * density;
    }

    private void drawLightning(Canvas c, int w, int h, long now, boolean running) {
        if (!running) return;
        if (now >= nextFlash) {
            flashAt = now;
            nextFlash = now + 5000 + rnd.nextInt(9000);
        }
        if (flashAt < 0 || now - flashAt > 450) return;
        float p = (now - flashAt) / 450f;
        // two quick flickers, then fading
        float a = p < 0.15f ? 1f : p < 0.25f ? 0.3f : (1f - p) / 0.75f;
        paint.setShader(null);
        paint.setColor(0xFFE8F0FF);
        paint.setAlpha((int) (90 * a));
        c.drawRect(0, 0, w, h, paint);
    }
}
