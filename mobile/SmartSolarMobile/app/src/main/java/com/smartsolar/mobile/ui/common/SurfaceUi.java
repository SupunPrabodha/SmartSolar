package com.smartsolar.mobile.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.smartsolar.mobile.R;

/** Presentation-only surfaces shared by focused account screens. */
public final class SurfaceUi {
    private SurfaceUi() { }
    public static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public static LinearLayout card(LinearLayout parent) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context,20)); card.setCardElevation(dp(context,1));
        card.setCardBackgroundColor(ContextCompat.getColor(context,R.color.solar_content_surface));
        card.setStrokeColor(ContextCompat.getColor(context,R.color.solar_border));card.setStrokeWidth(dp(context,1));
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1,-2);space.bottomMargin=dp(context,16);
        parent.addView(card,space);
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        int padding=dp(context,16);body.setPadding(padding,padding,padding,padding);
        card.addView(body,new LinearLayout.LayoutParams(-1,-2));return body;
    }
    public static void tint(LinearLayout body, int color) {
        ((MaterialCardView)body.getParent()).setCardBackgroundColor(ContextCompat.getColor(body.getContext(),color));
    }
    public static TextView heading(LinearLayout parent,String title,int icon) {
        Context context=parent.getContext(); TextView view=new TextView(context);
        view.setText(title);view.setTextSize(18);view.setTypeface(view.getTypeface(),Typeface.BOLD);
        view.setTextColor(ContextCompat.getColor(context,R.color.solar_text));
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon,0,0,0);view.setCompoundDrawablePadding(dp(context,10));
        view.setPadding(0,0,0,dp(context,12));ViewCompat.setAccessibilityHeading(view,true);
        parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;
    }
    public static TextView pill(LinearLayout parent,String label,int foreground,int background) {
        Context context=parent.getContext();TextView view=new TextView(context);view.setText(label);view.setTextSize(12);
        view.setTypeface(view.getTypeface(),Typeface.BOLD);view.setTextColor(ContextCompat.getColor(context,foreground));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(context,24));shape.setColor(ContextCompat.getColor(context,background));view.setBackground(shape);
        view.setPadding(dp(context,12),dp(context,6),dp(context,12),dp(context,6));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-2,-2);space.bottomMargin=dp(context,8);parent.addView(view,space);return view;
    }
    public static void choices(LinearLayout parent,String[] labels,int selected,java.util.function.IntConsumer onSelected) {
        Context context=parent.getContext();ChipGroup group=new ChipGroup(context);group.setSingleSelection(true);group.setSelectionRequired(true);group.setChipSpacing(dp(context,8));
        for(int i=0;i<labels.length;i++){
            final int index=i;Chip chip=new Chip(context);chip.setId(View.generateViewId());chip.setText(labels[i]);chip.setCheckable(true);chip.setEnsureMinTouchTargetSize(true);chip.setMinHeight(dp(context,48));
            chip.setChecked(i==selected);chip.setOnClickListener(v->onSelected.accept(index));group.addView(chip);
        }
        parent.addView(group,new LinearLayout.LayoutParams(-1,-2));
    }
    public static void empty(LinearLayout parent,String title,String description,int icon) {
        LinearLayout body=card(parent);tint(body,R.color.solar_brand_surface);TextView heading=heading(body,title,icon);heading.setGravity(Gravity.CENTER);
        TextView detail=new TextView(parent.getContext());detail.setText(description);detail.setTextSize(14);detail.setGravity(Gravity.CENTER);
        detail.setTextColor(ContextCompat.getColor(parent.getContext(),R.color.solar_secondary));body.addView(detail,new LinearLayout.LayoutParams(-1,-2));
    }
}
