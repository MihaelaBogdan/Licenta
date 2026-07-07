package com.cityscape.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

public class CrystalBallView extends View {

    
    
    private static final float[][] SWIRL_CFG = {
        { 48f,  0.60f,  0.14f,  0.07f },   
        {-33f,  0.52f, -0.11f,  0.13f },   
        { 72f,  0.42f,  0.04f, -0.16f },   
        {-21f,  0.68f, -0.15f, -0.07f },   
        { 58f,  0.35f,  0.19f, -0.03f },   
        {-85f,  0.28f, -0.08f,  0.18f },   
    };

    
    private static final int[] SWIRL_COLORS = {
        0xCC10B981,  
        0xBB0891B2,  
        0xCC059669,  
        0xAA065F46,  
        0xEE34D399,  
        0x990E7490,  
    };

    private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalRect   = new RectF();

    private long startMs;
    private Choreographer.FrameCallback frameCallback;
    private boolean revealed = false;
    private float revealAlpha = 0f;  

    public CrystalBallView(Context context) {
        super(context); init();
    }
    public CrystalBallView(Context context, AttributeSet attrs) {
        super(context, attrs); init();
    }
    public CrystalBallView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle); init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        bgPaint.setStyle(Paint.Style.FILL);
        blobPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.FILL);
        specPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startMs = SystemClock.uptimeMillis();
        frameCallback = frameTimeNanos -> {
            invalidate();
            if (isAttachedToWindow()) {
                Choreographer.getInstance().postFrameCallback(frameCallback);
            }
        };
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    
    public void startReveal() {
        revealed = true;
    }

    
    public void resetBall() {
        revealed = false;
        revealAlpha = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        final float cx = w * 0.5f;
        final float cy = h * 0.5f;
        final float r  = Math.min(cx, cy);
        final float t  = (SystemClock.uptimeMillis() - startMs) / 1000f; 

        
        if (revealed && revealAlpha < 1f) {
            revealAlpha = Math.min(1f, revealAlpha + 0.018f);
        }
        final float nebulaAlpha = 1f - revealAlpha * 0.82f; 

        
        bgPaint.setShader(new RadialGradient(
            cx * 1.12f, cy * 1.18f, r * 1.65f,
            new int[]{ 0xFF102018, 0xFF030C06, 0xFF000000 },
            new float[]{ 0f, 0.45f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, bgPaint);

        
        for (int i = 0; i < SWIRL_CFG.length; i++) {
            drawNebulaBlobLayer(canvas, cx, cy, r, t, i, nebulaAlpha);
        }

        
        float corePulse = 0.72f + 0.28f * (float) Math.sin(t * 2.1f);
        float coreR = r * 0.38f * corePulse;
        int coreAlpha = (int)(nebulaAlpha * 0xAA);
        blobPaint.setShader(new RadialGradient(
            cx, cy, coreR * 1.5f,
            new int[]{
                (coreAlpha << 24) | 0x10B981,
                ((coreAlpha / 2) << 24) | 0x10B981,
                0x0010B981
            },
            new float[]{ 0f, 0.5f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r * 0.62f, blobPaint);

        
        rimPaint.setShader(new RadialGradient(
            cx, cy, r,
            new int[]{ 0x0010B981, 0x1510B981, 0x8810B981 },
            new float[]{ 0f, 0.62f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, rimPaint);

        
        float specX = cx * 0.62f;
        float specY = cy * 0.42f;
        specPaint.setShader(new RadialGradient(
            specX, specY, r * 0.46f,
            new int[]{ 0xCCFFFFFF, 0x55FFFFFF, 0x00FFFFFF },
            new float[]{ 0f, 0.36f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, specPaint);

        
        specPaint.setShader(new RadialGradient(
            specX * 0.93f, specY * 0.87f, r * 0.10f,
            0xFFFFFFFF, 0x00FFFFFF,
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, specPaint);

        
        specPaint.setShader(new RadialGradient(
            cx + r * 0.58f, cy + r * 0.54f, r * 0.13f,
            0x28FFFFFF, 0x00FFFFFF,
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, specPaint);
    }

    
    private void drawNebulaBlobLayer(Canvas canvas, float cx, float cy, float r,
                                      float t, int idx, float globalAlpha) {
        float speed   = SWIRL_CFG[idx][0];
        float blobFrac = SWIRL_CFG[idx][1];
        float offFx   = SWIRL_CFG[idx][2];
        float offFy   = SWIRL_CFG[idx][3];
        int   color   = SWIRL_COLORS[idx];

        
        double angleRad = Math.toRadians(speed * t);

        
        float ox = (float)(offFx * Math.cos(angleRad) - offFy * Math.sin(angleRad)) * r;
        float oy = (float)(offFx * Math.sin(angleRad) + offFy * Math.cos(angleRad)) * r;

        
        float pulse = 0.82f + 0.18f * (float) Math.sin(t * 1.3f + idx * 1.18f);
        int baseAlpha = (color >>> 24) & 0xFF;
        int a = (int)(baseAlpha * pulse * globalAlpha);

        float blobR = r * blobFrac;

        
        float stretchAngle = speed * t * 0.55f;
        int cMain = (a << 24) | (color & 0x00FFFFFF);
        int cFade = ((a / 3) << 24) | (color & 0x00FFFFFF);

        blobPaint.setShader(new RadialGradient(
            cx + ox, cy + oy, blobR,
            new int[]{ cMain, cFade, 0x00000000 },
            new float[]{ 0f, 0.48f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.save();
        canvas.rotate(stretchAngle, cx, cy);
        ovalRect.set(
            cx + ox - blobR * 1.38f,
            cy + oy - blobR * 0.72f,
            cx + ox + blobR * 1.18f,
            cy + oy + blobR * 0.90f
        );
        canvas.drawOval(ovalRect, blobPaint);
        canvas.restore();

        
        double angleRad2 = Math.toRadians(speed * t + 137.5);
        float ox2 = (float)(offFy  * Math.cos(angleRad2) + offFx * Math.sin(angleRad2)) * r * 0.72f;
        float oy2 = (float)(-offFx * Math.cos(angleRad2) + offFy * Math.sin(angleRad2)) * r * 0.72f;
        float blobR2 = blobR * 0.62f;
        int a2 = a * 2 / 3;
        int c2 = (a2 << 24) | (color & 0x00FFFFFF);

        blobPaint.setShader(new RadialGradient(
            cx + ox2, cy + oy2, blobR2,
            new int[]{ c2, 0x00000000 },
            null,
            Shader.TileMode.CLAMP
        ));
        canvas.save();
        canvas.rotate(-stretchAngle * 0.68f, cx, cy);
        ovalRect.set(
            cx + ox2 - blobR2 * 1.08f,
            cy + oy2 - blobR2 * 0.88f,
            cx + ox2 + blobR2 * 0.95f,
            cy + oy2 + blobR2 * 1.15f
        );
        canvas.drawOval(ovalRect, blobPaint);
        canvas.restore();
    }
}
