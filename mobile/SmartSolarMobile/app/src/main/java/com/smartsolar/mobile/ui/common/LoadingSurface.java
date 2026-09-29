package com.smartsolar.mobile.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import com.smartsolar.mobile.R;

/** Static, theme-aware placeholders. The caller owns visibility and request state. */
public final class LoadingSurface extends LinearLayout {
    private TextView status;
    public void setLabel(int label) { status.setText(label); }
    public LoadingSurface(Context context) { this(context, null); }
    public LoadingSurface(Context context, AttributeSet attributes) {
        super(context, attributes);
        CharSequence label = getContentDescription();
        setContentDescription(null);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(16), dp(16), dp(16));
        setBackgroundResource(R.drawable.bg_visual_brand);
        status = new TextView(context);
        status.setText(label == null ? context.getString(R.string.visual_loading) : label);
        status.setTextSize(14);
        status.setTextColor(context.getColor(R.color.solar_secondary));
        status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(status, new LayoutParams(-1, -2));
        if ("metrics".equals(getTag())) {
            LinearLayout metrics = new LinearLayout(context); metrics.setOrientation(HORIZONTAL);
            addView(metrics,new LayoutParams(-1,-2));
            for (int labelId : new int[]{R.string.nav_pending,R.string.approved_future_short}) {
                LinearLayout metric = new LinearLayout(context); metric.setOrientation(VERTICAL);
                metric.setPadding(0,dp(16),dp(12),0);metrics.addView(metric,new LayoutParams(0,-2,1));
                TextView labelView=new TextView(context);labelView.setText(labelId);labelView.setTextSize(14);
                labelView.setTextColor(context.getColor(R.color.solar_secondary));metric.addView(labelView);
                block(metric,64,36);
            }
            return;
        }
        for (int row = 0; row < 2; row++) {
            LinearLayout blocks = new LinearLayout(context);
            blocks.setOrientation(VERTICAL);
            blocks.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            LayoutParams rowSpace = new LayoutParams(-1, -2); rowSpace.topMargin = dp(16);
            addView(blocks, rowSpace);
            block(blocks, 120, 18);
            block(blocks, -1, 12);
            block(blocks, 88, 12);
        }
    }
    private int dp(int size) { return SurfaceUi.dp(getContext(), size); }
    private void block(LinearLayout parent, int width, int height) {
        View block = new View(getContext());
        block.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(6));
        shape.setColor(getContext().getColor(R.color.solar_skeleton));
        block.setBackground(shape);
        LayoutParams space = new LayoutParams(width == -1 ? -1 : dp(width), dp(height));
        space.bottomMargin = dp(8);
        parent.addView(block, space);
    }
}
