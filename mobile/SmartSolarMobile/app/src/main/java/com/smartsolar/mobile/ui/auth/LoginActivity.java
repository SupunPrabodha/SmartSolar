package com.smartsolar.mobile.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.home.HomeActivity;
import com.smartsolar.mobile.util.SessionManager;

/** Restores a server-verified mobile session before opening the home screen. */
public final class LoginActivity extends AppCompatActivity {
    private EditText editNic;
    private EditText editPassword;
    private Button buttonLogin;
    private TextView textError;
    private View progress;
    private AuthRepository repository;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        editNic = findViewById(R.id.editNic);
        editPassword = findViewById(R.id.editPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        textError = findViewById(R.id.textError);
        progress = findViewById(R.id.progress);
        findViewById(R.id.buttonCreateAccount).setOnClickListener(view -> startActivity(new Intent(this, RegisterActivity.class)));
        try {
            repository = new AuthRepository(
                    RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG),
                    new SessionManager(this));
        } catch (IllegalArgumentException exception) {
            showError(R.string.api_not_configured);
            buttonLogin.setEnabled(false);
            return;
        }
        buttonLogin.setOnClickListener(view -> login());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (repository != null && !busy) {
            setBusy(true);
            repository.restore(this::showResult);
        }
    }

    private void login() {
        String nic = editNic.getText().toString().trim();
        String password = editPassword.getText().toString();
        if (nic.isEmpty() || password.isEmpty()) {
            showError(R.string.credentials_required);
            return;
        }
        setBusy(true);
        repository.login(nic, password, this::showResult);
        editPassword.setText("");
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonLogin.setEnabled(!value);
        editNic.setEnabled(!value);
        editPassword.setEnabled(!value);
        if (value) textError.setVisibility(View.GONE);
    }

    private void showError(int error) {
        textError.setText(error);
        textError.setVisibility(View.VISIBLE);
    }

    private void showResult(UserResponse user, long expiry, int error) {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false);
        if (user != null) {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        if (error != 0) showError(error);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
