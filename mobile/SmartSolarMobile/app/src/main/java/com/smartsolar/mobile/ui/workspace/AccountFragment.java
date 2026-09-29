package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.UpdateProfileRequest;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.util.SessionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

/** Unsaved fields live in the retained view; hierarchy state also survives configuration recreation. */
public final class AccountFragment extends WorkspaceFragment {
    private final com.smartsolar.mobile.util.EnterpriseFeedback feedback = new com.smartsolar.mobile.util.EnterpriseFeedback();
    private EditText name, email, phone;
    private SessionManager sessions;
    private ApiService api;
    private ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    @Override protected boolean bookingData() { return false; }
    @Override protected int layout() { return R.layout.fragment_account; }
    @Override protected void bind(Bundle saved) {
        worker = Executors.newSingleThreadExecutor();
        sessions = new SessionManager(requireContext()); api = RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        name = findViewById(R.id.inputName); email = findViewById(R.id.inputEmail); phone = findViewById(R.id.inputPhone);
        findViewById(R.id.buttonPhotoSecurity).setOnClickListener(v -> com.smartsolar.mobile.ui.account.AccountExperienceActivity.open(requireActivity(), "profile"));
        populate();
        if (saved != null && saved.containsKey("draftName")) {
            name.setText(saved.getString("draftName")); email.setText(saved.getString("draftEmail")); phone.setText(saved.getString("draftPhone"));
            memory.values.putBoolean("draft", true);
        }
        findViewById(R.id.buttonSave).setOnClickListener(v -> save());
        findViewById(R.id.buttonDeactivate).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_nav_account).setTitle(R.string.deactivate_account).setMessage(R.string.deactivation_confirmation)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.deactivate_account, (dialog, which) -> deactivate()).show());
    }
    private void populate() {
        UserResponse user = workspace().state().profile;
        if (user == null) return;
        name.setText(user.getFullName()); email.setText(user.getEmail()); phone.setText(user.getPhoneNumber());
        ((TextView) findViewById(R.id.profileName)).setText(user.getFullName());
        ((TextView) findViewById(R.id.profileNic)).setText(user.getNic());
    }
    @Override protected void load() {
        if (memory.hierarchy == null && !memory.values.getBoolean("draft")) populate();
        // Profile was already read by WorkspaceActivity. No duplicate /users/me here.
    }
    private void save() {
        if (!workspace().isVerified()) return;
        String fullName = name.getText().toString().trim(), emailValue = email.getText().toString().trim(), phoneValue = phone.getText().toString().trim();
        busy(true); final int request = ++generation;
        worker.execute(() -> {
            int message = R.string.profile_update_failed; UserResponse updated = null; boolean expired = false;
            try {
                String token = sessions.getAccessToken();
                Response<UserResponse> response = api.updateMyProfile(new UpdateProfileRequest(fullName,emailValue,phoneValue)).execute();
                if (response.code() == 401) { sessions.clearIfMatches(token); expired = true; }
                else if (response.isSuccessful() && response.body() != null && token != null && token.equals(sessions.getAccessToken())) {
                    updated = response.body();
                    if ("Active".equals(updated.getStatus())) sessions.cacheProfile(updated);
                    else sessions.clearIfMatches(token);
                    message = R.string.profile_saved;
                }
                if (response.errorBody() != null) response.errorBody().close();
            } catch (Exception failure) { message = R.string.connection_failed; }
            final int result = message; final UserResponse user = updated; final boolean invalid = expired;
            main.post(() -> {
                if (!alive() || request != generation) return;
                busy(false);
                if (invalid) { workspace().openLogin(); return; }
                if (user != null) {
                    if (!"Active".equals(user.getStatus())) {
                        new MaterialAlertDialogBuilder(requireContext()).setTitle("Email changed")
                            .setMessage("Your new email needs Backoffice approval and verification before you can sign in again.")
                            .setCancelable(false).setPositiveButton("Return to sign in", (dialog, which) -> workspace().openLogin()).show();
                        return;
                    }
                    workspace().state().profile = user;
                    memory.values.remove("draft"); memory.hierarchy = null;
                    populate();
                }
                ((TextView)findViewById(R.id.textResult)).setText(user == null ? getString(result) : "");
                if (user != null) feedback.show(root, getString(result), false);
            });
        });
    }
    private void deactivate() {
        if (!workspace().isVerified()) return;
        busy(true); final int request = ++generation;
        worker.execute(() -> {
            boolean signedOut = false;
            try {
                String token = sessions.getAccessToken();
                Response<Void> response = api.requestDeactivation().execute();
                if (response.code() == 401) { sessions.clearIfMatches(token); signedOut = true; }
                else if (response.isSuccessful() && token != null && token.equals(sessions.getAccessToken())) { sessions.clear(); signedOut = true; }
                if (response.errorBody() != null) response.errorBody().close();
            } catch (Exception ignored) { }
            final boolean invalid = signedOut;
            main.post(() -> {
                if (!alive() || request != generation) return;
                busy(false); if (invalid) workspace().openLogin();
                else ((TextView)findViewById(R.id.textResult)).setText(R.string.profile_update_failed);
            });
        });
    }
    private void busy(boolean value) {
        ((com.google.android.material.button.MaterialButton)findViewById(R.id.buttonSave)).setText(value ? R.string.working : R.string.save_profile);
        name.setEnabled(!value); email.setEnabled(!value); phone.setEnabled(!value);
        findViewById(R.id.buttonSave).setEnabled(!value); findViewById(R.id.buttonDeactivate).setEnabled(!value);
    }
    @Override public void onSaveInstanceState(Bundle out) {
        if (root != null) { out.putString("draftName", name.getText().toString()); out.putString("draftEmail", email.getText().toString()); out.putString("draftPhone", phone.getText().toString()); }
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() { feedback.dismiss(); generation++; if (worker != null) worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroyView(); }
}
