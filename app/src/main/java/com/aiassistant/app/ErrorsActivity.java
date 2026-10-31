package com.aiassistant.app;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ErrorsActivity extends AppCompatActivity {
    private TextView tvErrors;
    private Button btnRefresh, btnClear;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_errors);

        tvErrors   = findViewById(R.id.tvErrors);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnClear   = findViewById(R.id.btnClear);
        prefs      = new Prefs(this);

        refresh();
        btnRefresh.setOnClickListener(v -> refresh());
        btnClear.setOnClickListener(v -> {
            prefs.clearErrors();
            refresh();
        });
    }

    private void refresh() {
        String e = prefs.errors();
        tvErrors.setText(e.isEmpty() ? "Ошибок нет" : e);
    }
}
