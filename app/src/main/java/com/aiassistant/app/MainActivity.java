package com.aiassistant.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    static final int REQ_PERM = 1;

    Button btnMic;
    TextView tvStatus, tvAnswer, tvBranch;
    Prefs prefs;
    Recorder recorder;
    Speaker speaker;
    AiClient ai;
    java.util.concurrent.ExecutorService exec;
    Handler handler;
    private MainLogic logic;

    enum State { IDLE, RECORDING, PROCESSING, SPEAKING }
    volatile State state = State.IDLE;

    @Override
    protected void onCreate(android.os.Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        btnMic   = findViewById(R.id.btnMic);
        tvStatus = findViewById(R.id.tvStatus);
        tvAnswer = findViewById(R.id.tvAnswer);
        tvBranch = findViewById(R.id.tvBranch);

        prefs    = new Prefs(this);
        recorder = new Recorder(this);
        speaker  = new Speaker(this, prefs);
        ai       = new AiClient();
        exec     = java.util.concurrent.Executors.newSingleThreadExecutor();
        handler  = new Handler(Looper.getMainLooper());
        logic    = new MainLogic(this);

        btnMic.setOnClickListener(v -> onMicClick());
        findViewById(R.id.btnPrevBranch).setOnClickListener(v -> changeBranch(-1));
        findViewById(R.id.btnNextBranch).setOnClickListener(v -> changeBranch(+1));
        tvBranch.setOnLongClickListener(v -> { renameBranch(); return true; });
        findViewById(R.id.btnMenu).setOnClickListener(v -> showMenu());

        if (prefs.branchCount() == 0) prefs.addBranch();
        updateBranchUI();
        setState(State.IDLE);
    }

    private void onMicClick() {
        if (state == State.SPEAKING) {
            speaker.stop();
            setState(State.IDLE);
            return;
        }
        if (state == State.RECORDING) {
            setState(State.PROCESSING);
            exec.submit(logic::process);
            return;
        }
        if (state != State.IDLE) return;
        if (prefs.assemblyKey().isEmpty() || prefs.groqKey().isEmpty()) {
            toast("Задай ключи в Настройках");
            return;
        }
        checkPermissionsAndRecord();
    }

    private void checkPermissionsAndRecord() {
        boolean hasAudio = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean hasBt = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        if (!hasAudio || !hasBt) {
            String[] perms = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, perms, REQ_PERM);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERM) {
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    toast("Нужно разрешение на микрофон");
                    return;
                }
            }
            startRecording();
        }
    }

    private void startRecording() {
        setState(State.RECORDING);
        exec.submit(() -> recorder.start(prefs));
    }

    void changeBranch(int delta) {
        int count = Math.max(1, prefs.branchCount());
        int cur = ((prefs.currentBranch() + delta) % count + count) % count;
        prefs.setCurrentBranch(cur);
        updateBranchUI();
    }

    void updateBranchUI() {
        int cur = prefs.currentBranch();
        tvBranch.setText((cur + 1) + ". " + prefs.branchName(cur));
    }

    private void renameBranch() {
        int cur = prefs.currentBranch();
        EditText et = new EditText(this);
        et.setText(prefs.branchName(cur));
        new AlertDialog.Builder(this)
            .setTitle("Переименовать ветку")
            .setView(et)
            .setPositiveButton("OK", (d, w) -> {
                prefs.setBranchName(cur, et.getText().toString().trim());
                updateBranchUI();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showMenu() {
        Button btn = findViewById(R.id.btnMenu);
        PopupMenu pm = new PopupMenu(this, btn);
        pm.getMenu().add(0, 1, 0, "Настройки ИИ");
        pm.getMenu().add(0, 2, 0, "Bluetooth");
        pm.getMenu().add(0, 3, 0, "Команды");
        pm.getMenu().add(0, 4, 0, "Ошибки");
        pm.setOnMenuItemClickListener(item -> {
            Class<?> cls = null;
            switch (item.getItemId()) {
                case 1: cls = SettingsActivity.class; break;
                case 2: cls = BluetoothActivity.class; break;
                case 3: cls = CommandsActivity.class; break;
                case 4: cls = ErrorsActivity.class; break;
            }
            if (cls != null) startActivity(new Intent(this, cls));
            return true;
        });
        pm.show();
    }

    void setState(State s) {
        state = s;
        switch (s) {
            case IDLE:
                tvStatus.setText("Нажми и говори");
                tvStatus.setTextColor(getResources().getColor(R.color.status_idle));
                btnMic.setBackground(getDrawable(R.drawable.mic_button_bg));
                break;
            case RECORDING:
                tvStatus.setText("Слушаю... (нажми — закончить)");
                tvStatus.setTextColor(getResources().getColor(R.color.status_recording));
                btnMic.setBackground(getDrawable(R.drawable.mic_rec_bg));
                break;
            case PROCESSING:
                tvStatus.setText("Думаю...");
                tvStatus.setTextColor(getResources().getColor(R.color.status_processing));
                break;
            case SPEAKING:
                tvStatus.setText("Говорю... (нажми — остановить)");
                tvStatus.setTextColor(getResources().getColor(R.color.status_speaking));
                break;
        }
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speaker.shutdown();
        exec.shutdownNow();
    }
}
