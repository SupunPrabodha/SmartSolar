package com.smartsolar.mobile.ui.common;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.smartsolar.mobile.R;

/** Toolbar/insets for focused Activities. Contains no session logic or bottom navigation. */
public final class DeepScreenChrome {
    private DeepScreenChrome() { }
    public static void attach(AppCompatActivity activity, String title) {
        androidx.activity.EdgeToEdge.enable(activity);
        ViewGroup host = activity.findViewById(android.R.id.content);
        View original = host.getChildAt(0);
        if (original == null) return;
        host.removeView(original); ViewCompat.setOnApplyWindowInsetsListener(original, null);
        View shell = activity.getLayoutInflater().inflate(R.layout.view_deep_screen, host, false);
        ((FrameLayout) shell.findViewById(R.id.deepContent)).addView(original, new FrameLayout.LayoutParams(-1, -1));
        host.addView(shell);
        MaterialToolbar toolbar = shell.findViewById(R.id.deepToolbar);
        toolbar.setTitle(title); toolbar.setNavigationIcon(R.drawable.ic_nav_back);
        toolbar.setNavigationContentDescription(R.string.navigate_back);
        toolbar.setNavigationOnClickListener(v -> activity.getOnBackPressedDispatcher().onBackPressed());
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(shell);
    }
}
