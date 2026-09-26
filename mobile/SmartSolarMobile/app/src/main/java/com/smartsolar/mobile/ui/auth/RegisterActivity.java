package com.smartsolar.mobile.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.RegisterProsumerRequest;
import java.io.IOException;
import java.util.regex.Pattern;
import retrofit2.Response;

/** Provides anonymous Prosumer registration; activation remains a Backoffice decision. */
public final class RegisterActivity extends AppCompatActivity {
    private static final Pattern NIC = Pattern.compile("(?:[0-9]{9}[VvXx]|[0-9]{12})");
    private EditText nic, fullName, email, phone, password;
    private TextView error, success;
    private ProgressBar progress;
    private Button register;
    private Thread worker;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_register);
        nic = findViewById(R.id.editNic); fullName = findViewById(R.id.editFullName);
        email = findViewById(R.id.editEmail); phone = findViewById(R.id.editPhone); password = findViewById(R.id.editPassword);
        error = findViewById(R.id.textError); success = findViewById(R.id.textSuccess);
        progress = findViewById(R.id.progress); register = findViewById(R.id.buttonRegister);
        findViewById(R.id.buttonBackToLogin).setOnClickListener(v -> finish());
        register.setOnClickListener(v -> submit());
    }

    private void submit() {
        String nicValue = nic.getText().toString().trim();
        String nameValue = fullName.getText().toString().trim();
        String emailValue = email.getText().toString().trim();
        String phoneValue = phone.getText().toString().trim();
        String passwordValue = password.getText().toString();
        if (!NIC.matcher(nicValue).matches()) { showError("Enter a valid NIC: 12 digits or 9 digits followed by V/X."); return; }
        if (nameValue.length() < 2 || nameValue.length() > 120) { showError("Enter your full name."); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) { showError("Enter a valid email address."); return; }
        if (phoneValue.length() < 7 || phoneValue.length() > 20) { showError("Enter a valid phone number."); return; }
        if (passwordValue.length() < 8 || passwordValue.length() > 100) { showError("Password must be 8 to 100 characters."); return; }
        setBusy(true);
        ApiService api;
        try { api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG); }
        catch (IllegalArgumentException exception) { setBusy(false); showError(getString(R.string.api_not_configured)); return; }
        worker = new Thread(() -> {
            try {
                Response<com.smartsolar.mobile.data.remote.dto.UserResponse> response = api.registerProsumer(new RegisterProsumerRequest(nicValue, nameValue, emailValue, phoneValue, passwordValue)).execute();
                runOnUiThread(() -> {
                    setBusy(false);
                    if (response.isSuccessful()) {
                        success.setText(R.string.registration_pending); success.setVisibility(View.VISIBLE); error.setVisibility(View.GONE); register.setEnabled(false);
                        ((TextView) findViewById(R.id.buttonBackToLogin)).setText(R.string.return_to_sign_in); findViewById(R.id.buttonBackToLogin).setVisibility(View.VISIBLE);
                    } else if (response.code() == 409) showError("An account with that NIC or email already exists.");
                    else showError(R.string.registration_failed);
                });
            } catch (IOException exception) { runOnUiThread(() -> { setBusy(false); showError(getString(R.string.connection_failed)); }); }
        });
        worker.start();
    }

    private void showError(String message) { error.setText(message); error.setVisibility(View.VISIBLE); success.setVisibility(View.GONE); }
    private void showError(int resource) { showError(getString(resource)); }
    private void setBusy(boolean busy) { progress.setVisibility(busy ? View.VISIBLE : View.GONE); register.setEnabled(!busy); }
    @Override protected void onDestroy() { if (worker != null) worker.interrupt(); super.onDestroy(); }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.title_create_account), null);
    }
}
