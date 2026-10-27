package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Random;

/**
 * A little yellow buddy (config.xml buddy_minutes) who walks along the top of the dock band now and then: he
 * fades in at one end, stops somewhere along it to look at you and wave, then walks on and fades out at the other
 * end. {@link #hop()} makes him jump. He dresses for the day: a scarf at night, a diya in his hand around Diwali,
 * a Santa hat at Christmas and a kite at Sankranti.
 *
 * Everything is drawn with a few paths and no bitmaps, in a view only {@link #SIZE_DP} square that is moved with
 * its translation, so a walk costs one small redraw per frame for about twenty seconds, and nothing at all between
 * walks: the view is gone and holds no timers then.
 */
public final class BuddyView extends View {

    /** The view's side, in dp; the buddy stands at the bottom centre and the kite flies in the space above him. */
    static final int SIZE_DP = 150;
    private static final float WALK_DP_PER_S = 62f;
    private static final long FADE_MS = 450, LOOK_MS = 700, WAVE_MS = 1800, HOP_MS = 480, FRAME_MS = 33;

    /** Lakshmi Puja day; the diya shows from two days before (Dhanteras) to two days after (Bhai Dooj). */
    private static final LocalDate[] DIWALI = {
            LocalDate.of(2026, 11, 8), LocalDate.of(2027, 10, 29), LocalDate.of(2028, 10, 17),
            LocalDate.of(2029, 11, 5), LocalDate.of(2030, 10, 26),
    };

    /** What he wears and carries on a given day and hour; see {@link #outfit}. */
    static final int SCARF = 1, DIYA = 2, SANTA_HAT = 4, KITE = 8;

    private static final int YELLOW = 0xFFFFCE3A, YELLOW_DARK = 0xFFE0A410, OUTLINE = 0xFF7A4E00;

    private final float density;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Runnable frame = this::step;
    /** Made once: the body's shine (body at y 0 to 54, centred on x 75) and the diya's glow (centred on 0, 0). */
    private final Shader bodyShine = new RadialGradient(SIZE_DP / 2f - 8f, 14f, 48f, YELLOW, YELLOW_DARK, Shader.TileMode.CLAMP);
    private final Shader diyaGlow = new RadialGradient(0f, 0f, 20f, 0x66FFB23C, 0x00FFB23C, Shader.TileMode.CLAMP);

    private boolean walking;
    private int outfit;
    private int dir;                       // +1 walks right, -1 walks left
    private float fromX, stopX, toX;       // translationX at the band's ends and where he stops
    private long startMs, arriveMs, leaveMs, endMs, hopMs = -1;
    private long blinkAtMs;
    private Runnable onDone;

    // the pose of the current frame, set in step() and read in onDraw()
    private float phase;                   // walk cycle, radians
    private boolean facingFront, waving;
    private float waveT, hopLift, flicker;

    public BuddyView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** The view's side in px, for its layout params. */
    static int sizePx(float density) {
        return (int) (SIZE_DP * density);
    }

    /** The outfit for {@code day} at {@code hour} (0-23), with the theme's festival name ("" for none). */
    static int outfit(LocalDate day, int hour, String festival) {
        int o = 0;
        if (hour >= 19 || hour < 6) o |= SCARF;
        boolean diwali = "diwali".equals(festival);
        for (LocalDate d : DIWALI) {
            if (!day.isBefore(d.minusDays(2)) && !day.isAfter(d.plusDays(2))) diwali = true;
        }
        if (diwali) o |= DIYA;
        MonthDay md = MonthDay.from(day);
        if (!md.isBefore(MonthDay.of(12, 20)) && !md.isAfter(MonthDay.of(12, 31))) o |= SANTA_HAT;
        if (!md.isBefore(MonthDay.of(1, 13)) && !md.isAfter(MonthDay.of(1, 15))) o |= KITE;
        return o;
    }

    boolean isWalking() {
        return walking;
    }

    /**
     * One walk along the band from {@code left} to {@code right} (px in the parent), his feet on {@code ground},
     * wearing {@code outfit}. {@code done} runs on the main thread when he has gone, or when {@link #stop()} ends
     * the walk early.
     */
    void walk(float left, float right, float ground, int outfit, Runnable done) {
        stop();
        float size = sizePx(density);
        float half = size / 2f;
        if (right - left < size * 2) return;
        this.outfit = outfit;
        onDone = done;
        dir = rnd.nextBoolean() ? 1 : -1;
        float a = left + 10 * density - half, b = right - 10 * density - half;
        fromX = dir > 0 ? a : b;
        toX = dir > 0 ? b : a;
        stopX = a + (b - a) * (0.3f + 0.4f * rnd.nextFloat());
        float pxPerMs = WALK_DP_PER_S * density / 1000f;
        startMs = SystemClock.uptimeMillis();
        arriveMs = startMs + (long) (Math.abs(stopX - fromX) / pxPerMs);
        leaveMs = arriveMs + LOOK_MS + WAVE_MS + LOOK_MS / 2;
        endMs = leaveMs + (long) (Math.abs(toX - stopX) / pxPerMs);
        blinkAtMs = startMs + 1500 + rnd.nextInt(2500);
        hopMs = -1;
        setTranslationY(ground - size);
        setTranslationX(fromX);
        setAlpha(0f);
        setVisibility(VISIBLE);
        walking = true;
        step();
    }

    /** A jump, if he is out; the key that asked for it carries on to Home as usual. */
    void hop() {
        if (!walking) return;
        long now = SystemClock.uptimeMillis();
        if (hopMs < 0 || now - hopMs > HOP_MS) hopMs = now;
    }

    /** Ends the walk at once (Home going to the background); the callback still runs. */
    void stop() {
        removeCallbacks(frame);
        if (!walking) return;
        walking = false;
        setVisibility(GONE);
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void step() {
        if (!walking) return;
        long now = SystemClock.uptimeMillis();
        if (now >= endMs) {
            stop();
            return;
        }
        float x;
        facingFront = false;
        waving = false;
        if (now < arriveMs) {
            x = fromX + (stopX - fromX) * (now - startMs) / (float) Math.max(1, arriveMs - startMs);
            phase = (now - startMs) / 1000f * 2f * (float) Math.PI * 1.6f;
        } else if (now < leaveMs) {
            x = stopX;
            phase = 0f;
            facingFront = true;
            long t = now - arriveMs;
            waving = t >= LOOK_MS && t < LOOK_MS + WAVE_MS;
            waveT = (t - LOOK_MS) / 1000f;
        } else {
            x = stopX + (toX - stopX) * (now - leaveMs) / (float) Math.max(1, endMs - leaveMs);
            phase = (now - leaveMs) / 1000f * 2f * (float) Math.PI * 1.6f;
        }
        setTranslationX(x);
        long sinceStart = now - startMs, untilEnd = endMs - now;
        setAlpha(Math.min(1f, Math.min(sinceStart, untilEnd) / (float) FADE_MS));
        if (hopMs >= 0 && now - hopMs < HOP_MS) {
            float p = (now - hopMs) / (float) HOP_MS;
            hopLift = 4f * p * (1f - p) * 24f;
        } else {
            hopLift = 0f;
            hopMs = -1;
        }
        if (now > blinkAtMs + 140) blinkAtMs = now + 2200 + rnd.nextInt(2600);
        flicker = rnd.nextFloat();
        invalidate();
        postDelayed(frame, FRAME_MS);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!walking) return;
        long now = SystemClock.uptimeMillis();
        boolean blink = now >= blinkAtMs && now < blinkAtMs + 140;
        c.save();
        c.scale(density, density);
        // facing right in his own coordinates: feet on y = SIZE_DP, centred on x = 75; mirrored to walk left
        if (dir < 0) c.scale(-1f, 1f, SIZE_DP / 2f, 0f);
        float cx = SIZE_DP / 2f, ground = SIZE_DP - 2f;
        float swing = (float) Math.sin(phase);
        float bob = facingFront ? 0f : Math.abs((float) Math.cos(phase)) * 2f;
        float lift = hopLift;
        float top = ground - 60f - bob - lift, bottom = ground - 6f - bob - lift;

        if ((outfit & KITE) != 0) drawKite(c, cx, top, now);

        // feet: lifted in turn while walking, both down when he stands
        fill.setColor(0xFF3A2A1A);
        float liftA = facingFront ? 0f : Math.max(0f, swing) * 4f, liftB = facingFront ? 0f : Math.max(0f, -swing) * 4f;
        oval.set(cx - 15f + swing * 3f, ground - 6f - liftA - lift, cx - 2f + swing * 3f, ground - lift - liftA);
        c.drawOval(oval, fill);
        oval.set(cx + 2f - swing * 3f, ground - 6f - liftB - lift, cx + 15f - swing * 3f, ground - lift - liftB);
        c.drawOval(oval, fill);

        // back arm, behind the body
        float shoulderY = top + 36f;
        drawArm(c, cx - 20f, shoulderY, 180f - 90f + swing * 28f, false);

        // body: a soft yellow bean with a shine on its upper left
        c.save();
        c.translate(0f, top);
        oval.set(cx - 22f, 0f, cx + 22f, bottom - top);
        fill.setShader(bodyShine);
        c.drawRoundRect(oval, 22f, 22f, fill);
        fill.setShader(null);
        stroke.setColor(OUTLINE);
        stroke.setStrokeWidth(1.4f);
        c.drawRoundRect(oval, 22f, 22f, stroke);
        c.restore();

        // sprout, or the Christmas hat over it
        if ((outfit & SANTA_HAT) != 0) {
            drawHat(c, cx, top);
        } else {
            stroke.setColor(0xFF3E8E2E);
            stroke.setStrokeWidth(2f);
            path.reset();
            path.moveTo(cx, top + 1f);
            path.quadTo(cx + 1f, top - 6f, cx + 3f, top - 11f + swing);
            c.drawPath(path, stroke);
            fill.setColor(0xFF5CBF3A);
            drawLeaf(c, cx + 3f, top - 11f + swing, -35f);
            drawLeaf(c, cx + 3f, top - 11f + swing, 205f);
        }

        // eyes: big, looking where he walks, or at you when he stops; a blink now and then
        float eyeY = top + 19f, look = facingFront ? 0f : 2.2f;
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 8.5f + (facingFront ? 0f : 2f);
            fill.setColor(0xFFFFFFFF);
            oval.set(ex - 7f, eyeY - (blink ? 1f : 7.5f), ex + 7f, eyeY + (blink ? 1f : 7.5f));
            c.drawOval(oval, fill);
            stroke.setColor(OUTLINE);
            stroke.setStrokeWidth(1.2f);
            c.drawOval(oval, stroke);
            if (!blink) {
                fill.setColor(0xFF2B1B10);
                c.drawCircle(ex + look, eyeY + 0.5f, 3.6f, fill);
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(ex + look + 1.2f, eyeY - 0.8f, 1.1f, fill);
            }
        }

        // cheeks and a smile, opening into a laugh while he waves
        fill.setColor(0x66FF7A7A);
        c.drawCircle(cx - 15f + (facingFront ? 0f : 2f), top + 30f, 3.4f, fill);
        c.drawCircle(cx + 15f + (facingFront ? 0f : 2f), top + 30f, 3.4f, fill);
        float mx = cx + (facingFront ? 0f : 2f), my = top + 30f;
        if (waving) {
            fill.setColor(0xFF7A2A1A);
            oval.set(mx - 5f, my - 3f, mx + 5f, my + 5f);
            c.drawArc(oval, 0f, 180f, true, fill);
        } else {
            stroke.setColor(0xFF7A2A1A);
            stroke.setStrokeWidth(1.6f);
            oval.set(mx - 5f, my - 4f, mx + 5f, my + 3f);
            c.drawArc(oval, 20f, 140f, false, stroke);
        }

        if ((outfit & SCARF) != 0) drawScarf(c, cx, shoulderY - 1f, now);

        // front arm: waves when he stops, holds the diya out, or swings with the walk
        if (waving) {
            float angle = -60f + 22f * (float) Math.sin(waveT * 2f * Math.PI * 2.2f);
            drawArm(c, cx + 20f, shoulderY, angle, false);
        } else if ((outfit & DIYA) != 0) {
            drawArm(c, cx + 20f, shoulderY, 20f, true);
        } else if ((outfit & KITE) != 0) {
            drawArm(c, cx + 20f, shoulderY, -20f, false);
        } else {
            drawArm(c, cx + 20f, shoulderY, 90f - swing * 28f, false);
        }
        c.restore();
    }

    /** An arm from the shoulder at {@code degrees} (0 points forward, 90 straight down), with a round hand. */
    private void drawArm(Canvas c, float sx, float sy, float degrees, boolean diya) {
        double r = Math.toRadians(degrees);
        float hx = sx + 12f * (float) Math.cos(r), hy = sy + 12f * (float) Math.sin(r);
        stroke.setColor(YELLOW_DARK);
        stroke.setStrokeWidth(4.5f);
        c.drawLine(sx, sy, hx, hy, stroke);
        fill.setColor(YELLOW);
        c.drawCircle(hx, hy, 3.2f, fill);
        if (diya) drawDiya(c, hx + 3f, hy - 2f);
    }

    private void drawLeaf(Canvas c, float x, float y, float degrees) {
        c.save();
        c.rotate(degrees, x, y);
        oval.set(x, y - 2.5f, x + 9f, y + 2.5f);
        c.drawOval(oval, fill);
        c.restore();
    }

    /** A clay diya on his hand with a flickering flame and a warm glow around it. */
    private void drawDiya(Canvas c, float x, float y) {
        float h = 9f + flicker * 2.5f;
        c.save();
        c.translate(x, y - 7f);
        fill.setShader(diyaGlow);
        c.drawCircle(0f, 0f, 20f, fill);
        fill.setShader(null);
        c.restore();
        fill.setColor(0xFFB5541C);
        oval.set(x - 8f, y - 4f, x + 8f, y + 4f);
        c.drawArc(oval, 0f, 180f, true, fill);
        fill.setColor(0xFFD9772E);
        oval.set(x - 8f, y - 2f, x + 8f, y + 1f);
        c.drawOval(oval, fill);
        path.reset();
        path.moveTo(x, y - 1f - h);
        path.quadTo(x + 4f, y - 4f, x, y - 1f);
        path.quadTo(x - 4f, y - 4f, x, y - 1f - h);
        fill.setColor(0xFFFFB627);
        c.drawPath(path, fill);
        fill.setColor(0xFFFFF1B0);
        c.drawCircle(x, y - 4f, 1.6f, fill);
    }

    /** A red scarf round his middle, its loose end fluttering behind him. */
    private void drawScarf(Canvas c, float cx, float y, long now) {
        float flutter = (float) Math.sin(now / 160.0) * 3f;
        fill.setColor(0xFFD7263D);
        path.reset();
        path.moveTo(cx - 12f, y - 2f);
        path.lineTo(cx - 24f + flutter, y + 3f);
        path.lineTo(cx - 26f - flutter, y + 13f);
        path.lineTo(cx - 14f, y + 6f);
        path.close();
        c.drawPath(path, fill);
        oval.set(cx - 22.5f, y - 4f, cx + 22.5f, y + 5f);
        c.drawRoundRect(oval, 4.5f, 4.5f, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(1.5f);
        for (float sx = cx - 14f; sx <= cx + 16f; sx += 10f) c.drawLine(sx, y - 3f, sx - 3f, y + 4f, stroke);
    }

    /** A Santa hat leaning back, with a white brim and pompom. */
    private void drawHat(Canvas c, float cx, float top) {
        fill.setColor(0xFFD7263D);
        path.reset();
        path.moveTo(cx - 18f, top + 7f);
        path.quadTo(cx - 6f, top - 22f, cx - 22f, top - 18f);
        path.lineTo(cx + 18f, top + 7f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx - 22f, top - 18f, 4.2f, fill);
        oval.set(cx - 20f, top + 2f, cx + 20f, top + 10f);
        c.drawRoundRect(oval, 4f, 4f, fill);
    }

    /** A paper kite high behind him on a string from his raised hand, its tail waving. */
    private void drawKite(Canvas c, float cx, float top, long now) {
        float sway = (float) Math.sin(now / 420.0) * 5f;
        float kx = cx - 34f + sway, ky = top - 40f + (float) Math.cos(now / 530.0) * 3f;
        double r = Math.toRadians(-20);
        float hx = cx + 20f + 12f * (float) Math.cos(r), hy = top + 36f + 12f * (float) Math.sin(r);
        stroke.setColor(0xCCFFFFFF);
        stroke.setStrokeWidth(0.8f);
        path.reset();
        path.moveTo(hx, hy);
        path.quadTo(kx + 20f, ky + 34f, kx, ky + 12f);
        c.drawPath(path, stroke);
        path.reset();
        path.moveTo(kx, ky - 13f);
        path.lineTo(kx + 11f, ky);
        path.lineTo(kx, ky + 12f);
        path.lineTo(kx - 11f, ky);
        path.close();
        fill.setColor(0xFFFF6F3C);
        c.drawPath(path, fill);
        fill.setColor(0xFF2E86DE);
        path.reset();
        path.moveTo(kx, ky - 13f);
        path.lineTo(kx + 11f, ky);
        path.lineTo(kx, ky);
        path.close();
        c.drawPath(path, fill);
        stroke.setColor(0xFFFFD23F);
        stroke.setStrokeWidth(1.2f);
        path.reset();
        path.moveTo(kx, ky + 12f);
        path.quadTo(kx - 6f + sway, ky + 20f, kx + 1f, ky + 28f);
        path.quadTo(kx + 6f - sway, ky + 34f, kx - 1f, ky + 40f);
        c.drawPath(path, stroke);
    }
}
