/*
 * SmartSolar Mobile - Prosumer & Operator Solar Energy Management Platform
 * SolarDonutChartView.java - Interactive custom Canvas Donut/Pie chart for energy summaries
 */

package com.smartsolar.mobile.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.smartsolar.mobile.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom hardware-accelerated interactive Donut/Pie chart widget with smooth sweep entry animation,
 * touch slice selection, center KPI value display, and SmartSolar branding.
 */
public final class SolarDonutChartView extends View {

    public static final class DonutEntry {
        public final String label;
        public final double value;
        public final int color;
        public final int count;

        public DonutEntry(String label, double value, int color, int count) {
            this.label = label;
            this.value = Math.max(0, value);
            this.color = color;
            this.count = Math.max(0, count);
        }
    }

    public interface OnSliceSelectedListener {
        void onSliceSelected(int position, @Nullable DonutEntry entry);
    }

    private final List<DonutEntry> entries = new ArrayList<>();
    private final RectF chartBounds = new RectF();
    private final RectF selectedBounds = new RectF();

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerCutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerSubtextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float animationProgress = 1.0f;
    private int selectedIndex = -1;
    private ValueAnimator animator;
    private OnSliceSelectedListener listener;

    public SolarDonutChartView(Context context) {
        super(context);
        init();
    }

    public SolarDonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SolarDonutChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paint properties and typography
        slicePaint.setStyle(Paint.Style.FILL);

        innerCutoutPaint.setStyle(Paint.Style.FILL);
        innerCutoutPaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_surface));

        emptyRingPaint.setStyle(Paint.Style.STROKE);
        emptyRingPaint.setStrokeWidth(dpToPx(16));
        emptyRingPaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_border));

        centerValuePaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_text));
        centerValuePaint.setTextAlign(Paint.Align.CENTER);
        centerValuePaint.setTextSize(spToPx(18));
        centerValuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        centerLabelPaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_secondary));
        centerLabelPaint.setTextAlign(Paint.Align.CENTER);
        centerLabelPaint.setTextSize(spToPx(12));

        centerSubtextPaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_emerald));
        centerSubtextPaint.setTextAlign(Paint.Align.CENTER);
        centerSubtextPaint.setTextSize(spToPx(11));
        centerSubtextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setOnSliceSelectedListener(OnSliceSelectedListener listener) {
        this.listener = listener;
    }

    public void setData(List<DonutEntry> newEntries, boolean animate) {
        // Populate chart entries and trigger animated sweep transition
        this.entries.clear();
        if (newEntries != null) {
            for (DonutEntry entry : newEntries) {
                if (entry != null && entry.value > 0) {
                    this.entries.add(entry);
                }
            }
        }
        this.selectedIndex = -1;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        if (animate) {
            animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.setDuration(650);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                animationProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            animationProgress = 1.0f;
            invalidate();
        }
    }

    public void selectSlice(int index) {
        // Select or unselect a slice programmatically
        if (index >= 0 && index < entries.size()) {
            selectedIndex = (selectedIndex == index) ? -1 : index;
        } else {
            selectedIndex = -1;
        }
        invalidate();
        if (listener != null) {
            listener.onSliceSelected(selectedIndex, selectedIndex >= 0 ? entries.get(selectedIndex) : null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w - getPaddingLeft() - getPaddingRight(), h - getPaddingTop() - getPaddingBottom());
        float cx = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight()) / 2f;
        float cy = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;
        float radius = (size / 2f) - dpToPx(10);

        chartBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float expandedRadius = radius + dpToPx(6);
        selectedBounds.set(cx - expandedRadius, cy - expandedRadius, cx + expandedRadius, cy + expandedRadius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = chartBounds.centerX();
        float cy = chartBounds.centerY();
        float outerRadius = chartBounds.width() / 2f;
        float innerRadius = outerRadius * 0.64f;

        double totalValue = 0;
        int totalCount = 0;
        for (DonutEntry entry : entries) {
            totalValue += entry.value;
            totalCount += entry.count;
        }

        if (entries.isEmpty() || totalValue <= 0) {
            // Draw empty ring placeholder
            canvas.drawCircle(cx, cy, outerRadius - dpToPx(8), emptyRingPaint);
            canvas.drawText("0.0 kWh", cx, cy - dpToPx(2), centerValuePaint);
            canvas.drawText(getContext().getString(R.string.summary_no_data), cx, cy + dpToPx(16), centerLabelPaint);
            return;
        }

        // Draw donut arc slices
        float currentAngle = -90f;
        for (int i = 0; i < entries.size(); i++) {
            DonutEntry entry = entries.get(i);
            float sweepAngle = (float) (entry.value / totalValue * 360f);
            float animatedSweep = sweepAngle * animationProgress;

            slicePaint.setColor(entry.color);
            RectF bounds = (i == selectedIndex) ? selectedBounds : chartBounds;

            // Small gap between slices if more than 1 slice
            float gap = entries.size() > 1 ? 1.5f : 0f;
            canvas.drawArc(bounds, currentAngle + (gap / 2f), Math.max(0.1f, animatedSweep - gap), true, slicePaint);

            currentAngle += sweepAngle;
        }

        // Cut out center to create donut
        canvas.drawCircle(cx, cy, innerRadius, innerCutoutPaint);

        // Draw center text
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            DonutEntry selected = entries.get(selectedIndex);
            centerValuePaint.setColor(selected.color);
            canvas.drawText(String.format(Locale.US, "%.1f kWh", selected.value), cx, cy - dpToPx(10), centerValuePaint);
            canvas.drawText(selected.label, cx, cy + dpToPx(8), centerLabelPaint);
            double pct = (selected.value / totalValue) * 100.0;
            canvas.drawText(String.format(Locale.US, "%.1f%% (%d)", pct, selected.count), cx, cy + dpToPx(24), centerSubtextPaint);
        } else {
            centerValuePaint.setColor(ContextCompat.getColor(getContext(), R.color.solar_text));
            canvas.drawText(String.format(Locale.US, "%.1f kWh", totalValue), cx, cy - dpToPx(8), centerValuePaint);
            canvas.drawText(getContext().getString(R.string.summary_total_energy), cx, cy + dpToPx(10), centerLabelPaint);
            String countText = totalCount == 1 ? getContext().getString(R.string.summary_booking_count_single) : getContext().getString(R.string.summary_bookings_count, totalCount);
            canvas.drawText(countText, cx, cy + dpToPx(24), centerSubtextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && !entries.isEmpty()) {
            float x = event.getX() - chartBounds.centerX();
            float y = event.getY() - chartBounds.centerY();
            float distance = (float) Math.sqrt(x * x + y * y);
            float outerRadius = chartBounds.width() / 2f;
            float innerRadius = outerRadius * 0.64f;

            if (distance < innerRadius) {
                // Tapped center: reset selection to overall total
                selectedIndex = -1;
                invalidate();
                if (listener != null) listener.onSliceSelected(-1, null);
                return true;
            } else if (distance <= outerRadius + dpToPx(16)) {
                // Tapped on slice ring: calculate angle
                double angleDeg = Math.toDegrees(Math.atan2(y, x));
                if (angleDeg < 0) angleDeg += 360; // 0 to 360 deg from 3 o'clock
                // Shift so that 12 o'clock (-90 deg) is 0
                angleDeg = (angleDeg + 90) % 360;

                double totalValue = 0;
                for (DonutEntry entry : entries) totalValue += entry.value;

                double accumulatedAngle = 0;
                for (int i = 0; i < entries.size(); i++) {
                    double sliceSpan = (entries.get(i).value / totalValue) * 360.0;
                    if (angleDeg >= accumulatedAngle && angleDeg < (accumulatedAngle + sliceSpan)) {
                        selectedIndex = (selectedIndex == i) ? -1 : i;
                        invalidate();
                        if (listener != null) {
                            listener.onSliceSelected(selectedIndex, selectedIndex >= 0 ? entries.get(selectedIndex) : null);
                        }
                        return true;
                    }
                    accumulatedAngle += sliceSpan;
                }
            }
        }
        return true;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
