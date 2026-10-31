package com.aiassistant.app;

import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Prefs {
    public static final String DEFAULT_MODEL = "llama-3.1-8b-instant";
    public static final String DEFAULT_PROMPT = "Отвечай кратко и по делу, без markdown.";
    public static final String[] ACTIONS = {
        "print","new_branch","next_branch","prev_branch","repeat","listen"
    };
    public static final String[] ACTION_LABELS = {
        "Печать","Новая ветка","Следующая ветка","Предыдущая ветка","Повтори","Слушать"
    };

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE);
    }

    public String assemblyKey() { return sp.getString("assembly_key",""); }
    public String groqKey()     { return sp.getString("groq_key",""); }
    public String prompt()      { return sp.getString("system_prompt", DEFAULT_PROMPT); }
    public float  ttsRate()     { return sp.getFloat("tts_rate", 1.0f); }
    public String inSrc()       { return sp.getString("in_src","phone"); }
    public String outDst()      { return sp.getString("out_dst","speaker"); }

    public String model() {
        String m = sp.getString("groq_model","");
        if (m.isEmpty() || m.startsWith("llama3-")) return DEFAULT_MODEL;
        return m;
    }

    public void saveSettings(String asmKey, String groqKey, String model,
            String prompt, float rate) {
        sp.edit()
            .putString("assembly_key", asmKey)
            .putString("groq_key", groqKey)
            .putString("groq_model", model)
            .putString("system_prompt", prompt)
            .putFloat("tts_rate", rate)
            .apply();
    }

    public void saveAudio(String inSrc, String outDst) {
        sp.edit().putString("in_src", inSrc).putString("out_dst", outDst).apply();
    }

    // --- Branches ---
    public int branchCount() {
        try {
            return new JSONArray(sp.getString("branches","[]")).length();
        } catch (JSONException e) { return 0; }
    }

    public String branchName(int idx) {
        try {
            JSONArray a = new JSONArray(sp.getString("branches","[]"));
            if (idx < a.length()) return a.getString(idx);
        } catch (JSONException ignored) {}
        return "Ветка " + (idx + 1);
    }

    public void setBranchName(int idx, String name) {
        try {
            JSONArray a = getBranches();
            while (a.length() <= idx) a.put("Ветка " + (a.length() + 1));
            a.put(idx, name);
            sp.edit().putString("branches", a.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public void addBranch() {
        try {
            JSONArray a = getBranches();
            a.put("Ветка " + (a.length() + 1));
            sp.edit().putString("branches", a.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public int currentBranch() { return sp.getInt("cur_branch", 0); }

    public void setCurrentBranch(int idx) {
        sp.edit().putInt("cur_branch", idx).apply();
    }

    private JSONArray getBranches() {
        try { return new JSONArray(sp.getString("branches","[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    // --- History ---
    public JSONArray history(int branch) {
        try { return new JSONArray(sp.getString("hist_"+branch,"[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public void appendHistory(int branch, String role, String content) {
        try {
            JSONArray a = history(branch);
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            a.put(o);
            while (a.length() > 20) { a.remove(0); }
            sp.edit().putString("hist_"+branch, a.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public void clearHistory(int branch) {
        sp.edit().remove("hist_"+branch).apply();
    }

    // --- Errors ---
    public void logError(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = ts + " " + msg;
        String old = sp.getString("errors","");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        int start = Math.max(0, lines.length - 39);
        for (int i = start; i < lines.length; i++) sb.append('\n').append(lines[i]);
        sp.edit().putString("errors", sb.toString()).apply();
    }

    public String errors() { return sp.getString("errors",""); }

    public void clearErrors() { sp.edit().remove("errors").apply(); }

    // --- Commands ---
    public JSONArray commands() {
        String def = defaultCommandsJson();
        try { return new JSONArray(sp.getString("commands", def)); }
        catch (JSONException e) { try { return new JSONArray(def); } catch (JSONException e2) { return new JSONArray(); } }
    }

    public void saveCommands(JSONArray arr) {
        sp.edit().putString("commands", arr.toString()).apply();
    }

    public String[] matchCommand(String text) {
        if (text == null) return null;
        String t = text.replaceAll("^[\\s.,!?]+","").toLowerCase(Locale.getDefault());
        try {
            JSONArray cmds = commands();
            for (int i = 0; i < cmds.length(); i++) {
                JSONObject c = cmds.getJSONObject(i);
                String phrase = c.getString("phrase").toLowerCase(Locale.getDefault());
                if (t.startsWith(phrase)) {
                    String rest = text.substring(phrase.length()).trim();
                    return new String[]{c.getString("action"), rest};
                }
            }
        } catch (JSONException ignored) {}
        return null;
    }

    private String defaultCommandsJson() {
        try {
            JSONArray a = new JSONArray();
            String[][] defs = {
                {"печать","print"},
                {"новая ветка","new_branch"},
                {"следующая ветка","next_branch"},
                {"предыдущая ветка","prev_branch"},
                {"повтори","repeat"}
            };
            for (String[] d : defs) {
                JSONObject o = new JSONObject();
                o.put("phrase", d[0]);
                o.put("action", d[1]);
                a.put(o);
            }
            return a.toString();
        } catch (JSONException e) { return "[]"; }
    }
}
