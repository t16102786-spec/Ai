package com.aiassistant.app;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private EditText etAsm, etGroq, etModel, etPrompt, etRate;
    private Button btnSave;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_settings);

        etAsm   = findViewById(R.id.etAsm);
        etGroq  = findViewById(R.id.etGroq);
        etModel = findViewById(R.id.etModel);
        etPrompt = findViewById(R.id.etPrompt);
        etRate  = findViewById(R.id.etRate);
        btnSave = findViewById(R.id.btnSave);

        prefs = new Prefs(this);
        etAsm.setText(prefs.assemblyKey());
        etGroq.setText(prefs.groqKey());
        etModel.setText(prefs.model());
        etPrompt.setText(prefs.prompt());
        etRate.setText(String.valueOf(prefs.ttsRate()));

        btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        float rate = 1.0f;
        try { rate = Float.parseFloat(etRate.getText().toString()); }
        catch (NumberFormatException ignored) {}
        prefs.saveSettings(
            etAsm.getText().toString().trim(),
            etGroq.getText().toString().trim(),
            etModel.getText().toString().trim(),
            etPrompt.getText().toString().trim(),
            rate
        );
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        finish();
    }
}
