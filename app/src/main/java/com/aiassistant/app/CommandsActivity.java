package com.aiassistant.app;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;

public class CommandsActivity extends AppCompatActivity {
    private LinearLayout llCommands;
    private Button btnAdd, btnSave;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_commands);

        llCommands = findViewById(R.id.llCommands);
        btnAdd  = findViewById(R.id.btnAdd);
        btnSave = findViewById(R.id.btnSave);
        prefs   = new Prefs(this);

        loadRows();
        btnAdd.setOnClickListener(v -> addRow("", 0));
        btnSave.setOnClickListener(v -> save());
    }

    private void loadRows() {
        llCommands.removeAllViews();
        try {
            JSONArray cmds = prefs.commands();
            for (int i = 0; i < cmds.length(); i++) {
                JSONObject c = cmds.getJSONObject(i);
                String phrase = c.optString("phrase","");
                String action = c.optString("action","listen");
                int idx = 0;
                for (int j = 0; j < Prefs.ACTIONS.length; j++) {
                    if (Prefs.ACTIONS[j].equals(action)) { idx = j; break; }
                }
                addRow(phrase, idx);
            }
        } catch (JSONException ignored) {}
    }

    private void addRow(String phrase, int actionIdx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 0);
        row.setLayoutParams(lp);

        EditText et = new EditText(this);
        et.setHint("фраза");
        et.setText(phrase);
        et.setTextColor(getResources().getColor(R.color.text));
        LinearLayout.LayoutParams etLp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        et.setLayoutParams(etLp);
        row.addView(et);

        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, Prefs.ACTION_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(actionIdx);
        LinearLayout.LayoutParams spLp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sp.setLayoutParams(spLp);
        row.addView(sp);

        Button del = new Button(this);
        del.setText("✕");
        del.setTextColor(getResources().getColor(R.color.accent));
        del.setBackground(null);
        del.setOnClickListener(v -> llCommands.removeView(row));
        row.addView(del);

        llCommands.addView(row);
    }

    private void save() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < llCommands.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) llCommands.getChildAt(i);
            EditText et = (EditText) row.getChildAt(0);
            Spinner sp = (Spinner) row.getChildAt(1);
            String phrase = et.getText().toString().trim();
            if (phrase.isEmpty()) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("phrase", phrase);
                o.put("action", Prefs.ACTIONS[sp.getSelectedItemPosition()]);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.saveCommands(arr);
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        finish();
    }
}
