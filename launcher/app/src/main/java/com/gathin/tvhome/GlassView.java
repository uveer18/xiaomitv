package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 *
 * The slab is sized so that its inner rect lands exactly on the focused (scaled) tile and its
 * corner radius matches the tile's, which is what keeps the highlight from looking misaligned.
 */
public class GlassView extends View {

    /** Space kept around the slab so the outer glow is never clipped. */
    private static final float INSET_DP = 7f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private final float inset;
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        inset = INSET_DP * density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
    }

    /** Room reserved around the slab for the glow; callers must add it to the size. */
    float getInset() {
        return inset;
    }

    void setRadius(float px) {
        if (Math.abs(radius - px) < 0.5f) return;
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x33FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x5CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (rect.width() <= 0 || rect.height() <= 0) return;

        // Outer glow: three widening, fading strokes that stay inside the reserved inset.
        for (int i = 3; i >= 1; i--) {
            float o = i * 0.85f * density;
            glow.setStrokeWidth(i * 1.6f * density);
            glow.setColor((0x2C / i) << 24 | 0xFFFFFF);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o,
                    radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f, specular);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
