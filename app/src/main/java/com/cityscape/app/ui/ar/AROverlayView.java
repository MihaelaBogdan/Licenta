package com.cityscape.app.ui.ar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.cityscape.app.model.Place;
import java.util.ArrayList;
import java.util.List;

public class AROverlayView extends View {

    private List<Place> places = new ArrayList<>();
    private float userAzimuth = 0;
    private final float CAMERA_FOV = 60f; // Typical phone camera field of view
    private Paint pointPaint;
    private Paint textPaint;
    private Paint bgPaint;

    public AROverlayView(Context context) {
        super(context);
        init();
    }

    public AROverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(Color.parseColor("#4CAF50"));
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#80000000"));
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void updateData(List<Place> places, float azimuth) {
        this.places = places;
        this.userAzimuth = azimuth;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (places == null || places.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();

        for (Place place : places) {
            // This is a simplified bearing calculation for "Lite" version
            // In a real app we would use Location.bearingTo()
            float placeBearing = (float) Math.toDegrees(Math.atan2(place.longitude, place.latitude)); 
            // Note: properly computing bearing requires user location too. 
            // For now we assume the activity handles the bearing relative to user.

            float delta = placeBearing - userAzimuth;

            // Normalize delta to [-180, 180]
            if (delta > 180) delta -= 360;
            if (delta < -180) delta += 360;

            // Is it visible within the camera FOV?
            if (Math.abs(delta) < CAMERA_FOV / 2) {
                float x = (width / 2) + (delta / (CAMERA_FOV / 2)) * (width / 2);
                float y = height / 2; // Keep them centered vertically for simplicity

                // Draw floating card
                String label = place.name;
                float textWidth = textPaint.measureText(label);
                RectF rect = new RectF(x - textWidth/2 - 20, y - 60, x + textWidth/2 + 20, y + 20);
                canvas.drawRoundRect(rect, 10, 10, bgPaint);
                canvas.drawCircle(x, y + 40, 15, pointPaint);
                canvas.drawText(label, x, y - 10, textPaint);
            }
        }
    }
}
