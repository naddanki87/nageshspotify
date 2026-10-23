package com.nagesh.spotify;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * The first screen. On first launch it asks for the listener's name (only once; it's saved on the
 * device). After that it greets them with "Welcome back, name!" and opens the player.
 */
public class WelcomeActivity extends Activity {

    private Profile profile;
    private View registerForm;
    private View welcomeGroup;
    private EditText nameInput;
    private TextView welcomeTitle;
    private TextView welcomeSubtitle;
    private TextView changeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        profile = new Profile(this);
        registerForm = findViewById(R.id.register_form);
        welcomeGroup = findViewById(R.id.welcome_group);
        nameInput = findViewById(R.id.name_input);
        welcomeTitle = findViewById(R.id.welcome_title);
        welcomeSubtitle = findViewById(R.id.welcome_subtitle);
        changeName = findViewById(R.id.change_name);

        findViewById(R.id.register_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                register();
            }
        });
        nameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    register();
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            }
        });
        changeName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRegisterForm(profile.name());
            }
        });

        String name = profile.name();
        if (name == null) {
            showRegisterForm(null);
        } else {
            showWelcome(name, false);
        }
    }

    private void register() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError(getString(R.string.name_required));
            nameInput.requestFocus();
            return;
        }
        profile.setName(name);
        hideKeyboard();
        showWelcome(name, true);
    }

    private void showRegisterForm(String currentName) {
        welcomeGroup.setVisibility(View.GONE);
        registerForm.setVisibility(View.VISIBLE);
        if (currentName != null) {
            nameInput.setText(currentName);
            nameInput.setSelection(currentName.length());
        }
        nameInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void showWelcome(String name, boolean firstTime) {
        registerForm.setVisibility(View.GONE);
        welcomeGroup.setVisibility(View.VISIBLE);
        welcomeTitle.setText(getString(firstTime ? R.string.hello_name : R.string.welcome_back_name, name));
        welcomeSubtitle.setText(firstTime ? R.string.hello_subtitle : R.string.welcome_back_subtitle);
        changeName.setText(getString(R.string.not_you, name));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(nameInput.getWindowToken(), 0);
        nameInput.clearFocus();
    }
}
