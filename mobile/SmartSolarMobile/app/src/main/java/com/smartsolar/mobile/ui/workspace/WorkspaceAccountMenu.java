package com.smartsolar.mobile.ui.workspace;

import android.app.Activity;
import android.view.View;
import com.smartsolar.mobile.ui.common.ButtonAppearance;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.ui.common.SurfaceUi;

/** One native account surface; WorkspaceActivity supplies all existing actions. */
final class WorkspaceAccountMenu {
    private WorkspaceAccountMenu() { }
    static BottomSheetDialog show(Activity activity, UserResponse user, Runnable profile,
            Runnable security, Runnable refresh, Runnable signOut) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.Solar_AccountSheet);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int padding = SurfaceUi.dp(activity, 24); body.setPadding(padding,padding,padding,padding);
        // The dialog owns the rounded sheet background; content stays transparent.
        scroll.addView(body); dialog.setContentView(scroll);
        TextView initials = SurfaceUi.pill(body, user.getFullName().isEmpty() ? "?" : user.getFullName().substring(0,1),
                R.color.solar_primary, R.color.solar_approved_surface);
        initials.setTextSize(28); initials.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        SurfaceUi.heading(body,user.getFullName(),0);
        TextView identity = new TextView(activity); identity.setText(activity.getString(R.string.visual_identity,user.getNic(),user.getRole()));
        identity.setTextColor(activity.getColor(R.color.solar_secondary));identity.setTextSize(14);body.addView(identity);
        SurfaceUi.pill(body,user.getStatus(),R.color.solar_status_approved,R.color.solar_approved_surface);
        action(body, dialog, activity.getString(R.string.visual_my_profile),R.drawable.ic_nav_account,false,profile);
        action(body, dialog, activity.getString(R.string.visual_security),R.drawable.ic_ui_shield,false,security);
        action(body, dialog, activity.getString(R.string.refresh_profile),R.drawable.ic_ui_refresh,false,refresh);
        View divider = new View(activity); divider.setBackgroundColor(activity.getColor(R.color.solar_border));
        LinearLayout.LayoutParams dividerSpace = new LinearLayout.LayoutParams(-1,SurfaceUi.dp(activity,1));
        dividerSpace.topMargin=SurfaceUi.dp(activity,12);dividerSpace.bottomMargin=SurfaceUi.dp(activity,12);
        body.addView(divider,dividerSpace);
        action(body, dialog, activity.getString(R.string.sign_out),R.drawable.ic_ui_logout,true,signOut);
        dialog.show();
        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        return dialog;
    }
    private static void action(LinearLayout body, BottomSheetDialog dialog, String label, int icon, boolean danger, Runnable action) {
        MaterialButton button = new MaterialButton(body.getContext());
        button.setText(label);button.setIconResource(icon);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        button.setMinHeight(SurfaceUi.dp(body.getContext(),56));
        ButtonAppearance.menuRow(button,danger);
        button.setOnClickListener(v->{dialog.dismiss();action.run();});
        LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.topMargin=SurfaceUi.dp(body.getContext(),6);
        body.addView(button,spacing);
    }
}
