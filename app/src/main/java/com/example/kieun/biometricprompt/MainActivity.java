package com.example.kieun.biometricprompt;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.Executor;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometrics.BiometricPrompt;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import static android.os.Build.VERSION.SDK_INT;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = MainActivity.class.getName();

    private BiometricPrompt mBiometricPrompt;
    private String mToBeSignedMessage;

    // Unique identifier of a key pair
    private static final String KEY_NAME = "test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_register) {
            if (isSupportBiometricPrompt()) {
                Log.i(TAG, "Try registration");
                // Generate keypair and init signature
                Signature signature;
                try {
                    // Before generating a key pair, we have to check enrollment of biometrics on the device
                    // But, there is no such method on new biometric prompt API

                    // Note that this method will throw an exception if there is no enrolled biometric on the device
                    // This issue is reported to Android issue tracker
                    // https://issuetracker.google.com/issues/112495828
                    KeyPair keyPair = generateKeyPair(KEY_NAME, true);
                    // Send public key part of key pair to the server, this public key will be used for authentication
                    mToBeSignedMessage = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.URL_SAFE) +
                            ":" +
                            KEY_NAME +
                            ":" +
                            // Generated by the server to protect against replay attack
                            "12345";

                    signature = initSignature(KEY_NAME);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                ShowBiometricPrompt(signature);
            }
        } else if (id == R.id.nav_authenticate) {
            if (isSupportBiometricPrompt()) {
                Log.i(TAG, "Try authentication");

                // Init signature
                Signature signature;
                try {
                    // Send key name and challenge to the server, this message will be verified with registered public key on the server
                    mToBeSignedMessage = KEY_NAME +
                            ":" +
                            // Generated by the server to protect against replay attack
                            "12345";
                    signature = initSignature(KEY_NAME);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                ShowBiometricPrompt(signature);
            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void ShowBiometricPrompt(Signature signature) {
        // Create biometricPrompt
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setDescription("Description")
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setNegativeButtonText("Cancel Button")
                .build();
        CancellationSignal cancellationSignal = getCancellationSignal();
        BiometricPrompt.AuthenticationCallback authenticationCallback = getAuthenticationCallback();

        // Show biometric prompt
        if (signature != null) {
            Log.i(TAG, "Show biometric prompt");
            mBiometricPrompt = new BiometricPrompt(this, defaultCallbackExecutor(), authenticationCallback);
            mBiometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(signature));
        }
    }

    private CancellationSignal getCancellationSignal() {
        // With this cancel signal, we can cancel biometric prompt operation
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
            @Override
            public void onCancel() {
                //handle cancel result
                Log.i(TAG, "Canceled");
            }
        });
        return cancellationSignal;
    }

    private BiometricPrompt.AuthenticationCallback getAuthenticationCallback() {
        // Callback for biometric authentication result
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
            }


            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                Log.i(TAG, "onAuthenticationSucceeded");
                super.onAuthenticationSucceeded(result);
                Signature signature = result.getCryptoObject().getSignature();
                try {
                    signature.update(mToBeSignedMessage.getBytes());
                    String signatureString = Base64.encodeToString(signature.sign(), Base64.URL_SAFE);
                    // Normally, ToBeSignedMessage and Signature are sent to the server and then verified
                    Log.i(TAG, "Message: " + mToBeSignedMessage);
                    Log.i(TAG, "Signature (Base64 EncodeD): " + signatureString);
                    Toast.makeText(getApplicationContext(), mToBeSignedMessage + ":" + signatureString, Toast.LENGTH_SHORT).show();
                } catch (SignatureException e) {
                    throw new RuntimeException();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        };
    }

    /**
     * Before generating a key pair with biometric prompt, we need to check system feature to ensure that the device supports fingerprint, iris, or face.
     * Currently, there is no FEATURE_IRIS and FEATURE_FACE constant on PackageManager
     * So, only check FEATURE_FINGERPRINT
     * @return
     */
    private boolean isSupportBiometricPrompt() {
        PackageManager packageManager = this.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return true;
        }
        return false;
    }

    /**
     * Generate NIST P-256 EC Key pair for signing and verification
     * @param keyName
     * @param invalidatedByBiometricEnrollment
     * @return
     * @throws Exception
     */
    private KeyPair generateKeyPair(String keyName, boolean invalidatedByBiometricEnrollment) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512)
                // Require the user to authenticate with a biometric to authorize every use of the key
                .setUserAuthenticationRequired(true);

        if (SDK_INT >= Build.VERSION_CODES.N) {
            // Generated keys will be invalidated if the biometric templates are added more to user device
            builder.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment);
        }
        keyPairGenerator.initialize(builder.build());

        return keyPairGenerator.generateKeyPair();
    }

    @Nullable
    private KeyPair getKeyPair(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyName)) {
            // Get public key
            PublicKey publicKey = keyStore.getCertificate(keyName).getPublicKey();
            // Get private key
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyName, null);
            // Return a key pair
            return new KeyPair(publicKey, privateKey);
        }
        return null;
    }

    @Nullable
    private Signature initSignature (String keyName) throws Exception {
        KeyPair keyPair = getKeyPair(keyName);

        if (keyPair != null) {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            return signature;
        }
        return null;
    }

    public Executor defaultCallbackExecutor() {
        return new MainThreadExecutor();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable r) {
            handler.post(r);
        }
    }
}
