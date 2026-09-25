package com.smartsolar.mobile.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.UpdateProfileRequest;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.util.SessionManager;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

/** Lets an authenticated Prosumer update contact details or self-deactivate through the API. */
public final class AccountActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText name;
    private EditText email;
    private EditText phone;
    private TextView result;
    private SessionManager sessions;
    private ApiService api;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_account);
        name = findViewById(R.id.inputName);
        email = findViewById(R.id.inputEmail);
        phone = findViewById(R.id.inputPhone);
        result = findViewById(R.id.textResult);
        sessions = new SessionManager(this);
        api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        name.setText(getIntent().getStringExtra("name"));
        email.setText(getIntent().getStringExtra("email"));
        phone.setText(getIntent().getStringExtra("phone"));
        findViewById(R.id.buttonSave).setOnClickListener(view -> save());
        findViewById(R.id.buttonDeactivate).setOnClickListener(view -> confirmDeactivation());
    }

    private void save() {
        // Read controls on the UI thread, then make the network and SQLite work off the UI thread.
        String fullName = name.getText().toString().trim();
        String emailAddress = email.getText().toString().trim();
        String phoneNumber = phone.getText().toString().trim();
        setBusy(true);
        worker.execute(() -> {
            try {
                Response<UserResponse> response = api.updateMyProfile(
                        new UpdateProfileRequest(fullName, emailAddress, phoneNumber)).execute();
                if (response.isSuccessful() && response.body() != null) {
                    sessions.cacheProfile(response.body());
                    runOnUiThread(() -> finishSave(R.string.profile_saved));
                } else runOnUiThread(() -> finishSave(R.string.profile_update_failed));
            } catch (IOException | IllegalStateException exception) {
                runOnUiThread(() -> finishSave(R.string.connection_failed));
            }
        });
    }

    private void deactivate() {
        // Clear the token and cached profile only after the server accepts Prosumer self-deactivation.
        setBusy(true);
        worker.execute(() -> {
            try {
                Response<Void> response = api.requestDeactivation().execute();
                if (response.isSuccessful()) {
                    sessions.clear();
                    runOnUiThread(this::openLogin);
                } else runOnUiThread(() -> finishSave(R.string.profile_update_failed));
            } catch (IOException exception) {
                runOnUiThread(() -> finishSave(R.string.connection_failed));
            }
        });
    }

    private void confirmDeactivation() {
        // Require an explicit confirmation before the irreversible account-access action reaches the service.
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.deactivate_account)
                .setMessage(R.string.deactivation_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.deactivate_account, (dialog, which) -> deactivate())
                .show();
    }

    private void finishSave(int message) {
        // Restore the controls and show an accessible result after the asynchronous request completes.
        setBusy(false);
        result.setText(message);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void setBusy(boolean value) {
        findViewById(R.id.buttonSave).setEnabled(!value);
        findViewById(R.id.buttonDeactivate).setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
