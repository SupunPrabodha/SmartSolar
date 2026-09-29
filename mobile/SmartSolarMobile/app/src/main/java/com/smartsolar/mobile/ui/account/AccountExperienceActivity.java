package com.smartsolar.mobile.ui.account;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.NotificationInbox;
import com.smartsolar.mobile.data.remote.dto.UpdateProfileRequest;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.ui.common.DeepScreenChrome;
import com.smartsolar.mobile.util.EnterpriseFeedback;
import com.smartsolar.mobile.util.ReservationUiUtils;
import com.smartsolar.mobile.util.SessionManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

/** Focused account tasks; the role-specific five-destination workspace remains untouched. */
public final class AccountExperienceActivity extends AppCompatActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final EnterpriseFeedback feedback = new EnterpriseFeedback();
    private LinearLayout content;
    private TextView status;
    private ApiService api;
    private SessionManager sessions;
    private AuthRepository auth;
    private String mode;
    private boolean active, busy, verified;
    private int generation;
    private String owner;
    private String expectedToken;
    private Uri pendingPhoto;
    private volatile boolean passwordChanged;
    private boolean retryAvailable;
    private Runnable retryAction;
    private UserResponse profile;
    private TextInputEditText fullName, email, phone;
    private String draftName, draftEmail, draftPhone;
    private final Runnable expire = this::signOut;
    private NotificationInbox inbox;
    private boolean unreadOnly;
    private String priority = "";
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
        new ActivityResultContracts.PickVisualMedia(), uri -> { if (uri != null) { pendingPhoto = uri; if (verified && active) { pendingPhoto = null; upload(uri); } } });

    public static void open(Activity source, String mode) {
        source.startActivity(new Intent(source, AccountExperienceActivity.class).putExtra("mode", mode));
    }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        mode = getIntent().getStringExtra("mode");
        if (!"recovery".equals(mode) && !"inbox".equals(mode)) mode = "profile";
        setContentView(R.layout.activity_account_experience);
        DeepScreenChrome.attach(this, "recovery".equals(mode) ? "Recover account" : "inbox".equals(mode) ? "Notifications" : "My Profile");
        content = findViewById(R.id.experienceContent); status = findViewById(R.id.experienceStatus);
        sessions = new SessionManager(this);
        try { api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG); }
        catch (IllegalArgumentException e) { status.setText(R.string.api_not_configured); return; }
        auth = new AuthRepository(api, sessions);
        expectedToken = getSharedPreferences("smart_solar_session",MODE_PRIVATE).getString("access_token",null);
        if (saved != null) {
            owner=saved.getString("owner"); draftName=saved.getString("draftName");draftEmail=saved.getString("draftEmail");draftPhone=saved.getString("draftPhone");
        }
        if ("recovery".equals(mode)) recovery();
    }
    @Override protected void onStart() {
        super.onStart(); active = true;
        if (auth == null || "recovery".equals(mode)) return;
        verified = false; content.setVisibility(View.INVISIBLE);
        final int request = ++generation;
        auth.restore((user, expiry, error) -> {
            if (!active || request != generation || isFinishing()) return;
            content.setVisibility(View.VISIBLE);
            if (user == null) {
                clear();
                if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) { login(); return; }
                text("Service unavailable. Reconnect to verify your account."); button("Retry", this::recreate); return;
            }
            if (owner != null && !owner.equals(user.getNic())) { login(); return; }
            owner = user.getNic(); profile = user; verified = true;
            main.postDelayed(expire, Math.max(0,expiry-System.currentTimeMillis()));
            if ("inbox".equals(mode)) loadInbox(); else profile();
            if (pendingPhoto != null) { Uri selectedPhoto = pendingPhoto; pendingPhoto = null; upload(selectedPhoto); }
        });
    }
    private void clear() {
        content.removeAllViews(); retryAvailable=false; status = text(""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        view.setPadding(0,12,0,12); content.addView(view); return view;
    }
    private MaterialButton button(String label, Runnable action) {
        MaterialButton view = new MaterialButton(this); view.setText(label); view.setOnClickListener(v -> { if(!busy) action.run(); });
        content.addView(view,new LinearLayout.LayoutParams(-1,-2)); return view;
    }
    private TextInputEditText field(String label, String value, int type, int max) {
        TextInputLayout wrapper = new TextInputLayout(this); wrapper.setHint(label);
        TextInputEditText edit = new TextInputEditText(wrapper.getContext()); edit.setInputType(type); edit.setText(value);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            wrapper.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE); edit.setSaveEnabled(false);
        }
        wrapper.addView(edit,new LinearLayout.LayoutParams(-1,-2)); content.addView(wrapper); return edit;
    }
    private void recovery() {
        clear(); text("Enter the email or NIC for your account. Reset your password using the link sent by email.");
        TextInputEditText identifier=field("Email or NIC","",InputType.TYPE_CLASS_TEXT,254);
        button("Send reset instructions",()->{
            String value=identifier.getText().toString().trim();
            if(value.isEmpty()){identifier.setError("Enter your email or NIC.");return;}
            run(()->checked(api.forgotPassword(Map.of("identifier",value)).execute()),result->{
                clear();text("If an eligible account exists, reset instructions have been sent. Check your email, including spam.");
                button("Back to Sign In",this::finish);
            });
        });
    }
    private void profile() {
        clear();
        text(profile.getFullName()+"\n"+profile.getNic()+" · "+profile.getRole()+" · "+profile.getStatus());
        TextView initials = text(profile.getFullName().isEmpty() ? "?" : profile.getFullName().substring(0,1));
        initials.setTextSize(40);
        ImageView picture=new ImageView(this); picture.setContentDescription("Your profile photo");picture.setAdjustViewBounds(true);
        int size=(int)(120*getResources().getDisplayMetrics().density);
        content.addView(picture,new LinearLayout.LayoutParams(size,size));
        if(profile.getAvatarVersion()!=null) {
            final int request=generation;
            worker.execute(()->{
                try {
                    Response<okhttp3.ResponseBody> response=api.avatar().execute();
                    if(response.body()!=null)try(okhttp3.ResponseBody body=response.body()){
                        byte[] bytes=body.bytes();
                        main.post(()->{if(active&&request==generation){ picture.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length)); initials.setVisibility(View.GONE); }});
                    }
                    if(response.errorBody()!=null)response.errorBody().close();
                }catch(Exception ignored){}
            });
        }
        text("JPEG, PNG or WebP up to 2 MB. Your photo stays on the server and is not stored in SQLite.");
        button("Choose photo",()->{saveDraft();picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());});
        if(profile.getAvatarVersion()!=null)button("Remove photo",()->run(()->{checked(api.removeAvatar().execute());return true;},result->{profileFeedback("Photo removed.");recreate();}));
        fullName=field("Full name",draftName==null?profile.getFullName():draftName,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME,120);
        email=field("Email",draftEmail==null?profile.getEmail():draftEmail,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,254);
        phone=field("Phone number",draftPhone==null?profile.getPhoneNumber():draftPhone,InputType.TYPE_CLASS_PHONE,20);
        text("NIC, role and account state cannot be edited here. Changing a Prosumer email requires new approval and verification.");
        button("Save profile",()->{
            saveDraft();
            if(draftName.trim().length()<2 || !android.util.Patterns.EMAIL_ADDRESS.matcher(draftEmail).matches() || draftPhone.trim().length()<7){
                status.setText("Enter a name, valid email and phone number.");return;
            }
            run(()->{
                UserResponse user=checked(api.updateMyProfile(new UpdateProfileRequest(draftName.trim(),draftEmail.trim(),draftPhone.trim())).execute());
                if(!"Active".equals(user.getStatus())) sessions.clear(); else sessions.cacheProfile(user);
                return user;
            },user->{
                if(!"Active".equals(user.getStatus())){login();return;}
                profile=user; draftName=draftEmail=draftPhone=null; profile(); profileFeedback("Profile saved.");
            });
        });
        text("Account security\nUse 8–100 characters. Changing your password signs out all your current sessions.");
        TextInputEditText current=field("Current password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,100);
        TextInputEditText next=field("New password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,100);
        TextInputEditText confirm=field("Confirm new password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,100);
        button("Change password",()->{
            String old=current.getText().toString(),password=next.getText().toString();
            if(old.isEmpty()){current.setError("Enter your current password.");return;}
            if(password.length()<8){next.setError("Use 8–100 characters.");return;}
            if(!password.equals(confirm.getText().toString())){confirm.setError("Passwords do not match.");return;}
            current.setText("");next.setText("");confirm.setText("");
            run(()->{checked(api.changePassword(Map.of("currentPassword",old,"newPassword",password)).execute());passwordChanged=true;sessions.clear();return true;},
                result->login());
        });
        button("View security history",()->run(()->checked(api.profileAudit().execute()),items->{
            clear();text("Account audit history");
            for(com.google.gson.JsonObject item:items)text(item.get("event").getAsString()+"\n"+ReservationUiUtils.formatTime(item.get("atUtc").getAsString())+"\nReference: "+item.get("correlationId").getAsString());
            button("Back to profile",this::profile);
        }));
    }
    private void upload(Uri uri) {
        run(()->{
            try(InputStream stream=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                if(stream==null)throw new IllegalArgumentException("The selected image is unavailable.");
                byte[] buffer=new byte[8192];int count;
                while((count=stream.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>2000000)throw new IllegalArgumentException("Choose an image under 2 MB.");}
                okhttp3.RequestBody body=okhttp3.RequestBody.create(bytes.toByteArray(),okhttp3.MediaType.parse("application/octet-stream"));
                checked(api.uploadAvatar(okhttp3.MultipartBody.Part.createFormData("file","avatar",body)).execute());return true;
            }
        },result->{profileFeedback("Photo updated. Save your profile to finish.");recreate();});
    }
    private void loadInbox() {
        run(()->checked(api.notifications().execute()),result->{inbox=result;renderInbox();});
    }
    private void renderInbox() {
        clear();text("Notifications · "+inbox.unreadCount+" unread");
        button("Refresh",this::loadInbox);
        button("Mark all read",()->run(()->{checked(api.readNotifications().execute());return true;},result->loadInbox()));
        button(unreadOnly?"Show all notifications":"Show unread only",()->{unreadOnly=!unreadOnly;renderInbox();});
        button("Priority: "+(priority.isEmpty()?"All":priority),()->{
            priority=priority.isEmpty()?"High":priority.equals("High")?"Medium":priority.equals("Medium")?"Low":"";renderInbox();
        });
        int shown=0;
        for(NotificationInbox.Item item:inbox.items) {
            if(unreadOnly&&item.readAtUtc!=null || !priority.isEmpty()&&!priority.equals(item.priority))continue;
            shown++;
            text(item.priority+" · "+item.category+(item.readAtUtc==null?" · Unread":"")+"\n"+item.message+"\n"+ReservationUiUtils.formatTime(item.atUtc));
            button("Open",()->run(()->{checked(api.readNotification(item.id).execute());return true;},result->openItem(item)));
            if(item.readAtUtc==null)button("Mark read",()->run(()->{checked(api.readNotification(item.id).execute());return true;},result->loadInbox()));
        }
        if(shown==0)text("No notifications match these filters.");
    }
    private void openItem(NotificationInbox.Item item) {
        // Allowlisted destinations; the API rechecks ownership and current role on the target screen.
        if("Reservation".equals(item.action)&&item.resourceId!=null) {

            run(() -> checked(api.getReservation(item.resourceId).execute()), reservation -> {
                clear(); text("Reservation details");
                text(reservation.getReservationId()+"\n"+reservation.getStatus()+" · "+reservation.getEnergyAmountKwh()+" kWh");
                text("Starts: "+ReservationUiUtils.formatTime(reservation.getScheduledStartAtUtc())+"\nEnds: "+ReservationUiUtils.formatTime(reservation.getScheduledEndAtUtc()));
                button("Back to notifications",this::loadInbox);
            });
        }else if("Station".equals(item.action)&&item.resourceId!=null){
            Intent intent=new Intent(this,com.smartsolar.mobile.ui.stations.StationDetailActivity.class);
            intent.putExtra("stationId",item.resourceId);startActivity(intent);
        }else open(this,"profile");
    }
    private void saveDraft(){if(fullName!=null){draftName=fullName.getText().toString();draftEmail=email.getText().toString();draftPhone=phone.getText().toString();}}
    private void profileFeedback(String value){feedback.show(content,value,false);}
    private interface Work<T>{T execute()throws Exception;}
    private interface Result<T>{void accept(T value);}
    private <T> void run(Work<T> work,Result<T> result){
        if(busy || api==null || (!"recovery".equals(mode)&&!verified))return;
        busy=true;status.setText("Working…");
        retryAction = () -> run(work,result);
        final int request=generation;
        worker.execute(()->{
            T data=null;String error=null;
            try{
                if (!"recovery".equals(mode) && (expectedToken == null || !expectedToken.equals(sessions.getAccessToken())))
                    throw new IllegalArgumentException("Your session ended. Sign in again.");
                data=work.execute();
            }catch(IllegalArgumentException e){error=e.getMessage();}
            catch(Exception e){error="Service unavailable. Check your connection, then retry.";}
            final T value=data;final String problem=error;
            main.post(()->{
                busy=false;if(!active||request!=generation||isFinishing())return;
                if (!"recovery".equals(mode) && !java.util.Objects.equals(expectedToken,getSharedPreferences("smart_solar_session",MODE_PRIVATE).getString("access_token",null))) { login(); return; }
                if(problem==null){status.setText("");retryAction=null;result.accept(value);}
                else{status.setText(problem); if(!retryAvailable){retryAvailable=true;button("Retry last request",()->{if(retryAction!=null)retryAction.run();});}}
            });
        });
    }
    private <T>T checked(Response<T> response)throws Exception{
        if(response.code()==401){main.post(this::login);throw new IllegalArgumentException("Your session ended. Sign in again.");}
        if(!response.isSuccessful())throw new IllegalArgumentException(EnterpriseFeedback.problem(response));
        return response.body();
    }
    private void signOut(){worker.execute(()->{sessions.clear();main.post(this::login);});}
    private void login(){startActivity(new Intent(this,LoginActivity.class).putExtra("passwordChanged",passwordChanged).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
    @Override protected void onSaveInstanceState(Bundle state){saveDraft();state.putString("owner",owner); state.putString("draftName",draftName);state.putString("draftEmail",draftEmail);state.putString("draftPhone",draftPhone);super.onSaveInstanceState(state);}
    @Override protected void onStop(){saveDraft();retryAction=null;active=false;verified=false;generation++;main.removeCallbacks(expire);feedback.dismiss();if(!"recovery".equals(mode))content.setVisibility(View.INVISIBLE);super.onStop();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();main.removeCallbacksAndMessages(null);if(auth!=null)auth.close();super.onDestroy();}
}
