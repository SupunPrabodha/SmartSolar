package com.smartsolar.mobile.ui.common;

import android.content.res.ColorStateList;
import androidx.appcompat.content.res.AppCompatResources;
import com.google.android.material.button.MaterialButton;
import com.smartsolar.mobile.R;

/** Explicit Material button colors; widget styles must not be used as theme overlays. */
public final class ButtonAppearance {
    private ButtonAppearance() { }
    public static void outlined(MaterialButton button) {
        colors(button, R.color.solar_action_tonal, R.color.solar_action_text);
        button.setStrokeWidth(SurfaceUi.dp(button.getContext(),1));
        button.setStrokeColor(AppCompatResources.getColorStateList(button.getContext(),R.color.solar_action_text));
    }
    public static void primary(MaterialButton button) {
        colors(button, R.color.solar_action_fill, R.color.solar_action_on_primary);
        button.setStrokeWidth(0);
    }
    public static void menuRow(MaterialButton button, boolean danger) {
        colors(button, danger?R.color.solar_action_danger_fill:R.color.solar_action_tonal,
                danger?R.color.solar_action_danger_text:R.color.solar_action_text);
        button.setStrokeWidth(0);
        button.setCornerRadius(SurfaceUi.dp(button.getContext(),14));
    }
    private static void colors(MaterialButton button, int background, int foreground) {
        button.setBackgroundTintList(AppCompatResources.getColorStateList(button.getContext(),background));
        ColorStateList label=AppCompatResources.getColorStateList(button.getContext(),foreground);
        button.setTextColor(label);button.setIconTint(label);
        button.setRippleColor(ColorStateList.valueOf(button.getContext().getColor(foreground==R.color.solar_action_on_primary?R.color.solar_action_primary_ripple:R.color.solar_action_ripple)));
        button.setIconSize(SurfaceUi.dp(button.getContext(),24));
    }
}
