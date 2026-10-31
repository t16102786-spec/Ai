package com.aiassistant.app;

import android.bluetooth.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.*;

public class BluetoothActivity extends AppCompatActivity {
    private TextView tvDevices;
    private RadioButton rbInPhone, rbInBt, rbOutBt, rbOutSpeaker;
    private Button btnRefresh, btnFind;
    private Prefs prefs;
    private BluetoothAdapter btAdapter;
    private BluetoothHeadset btHeadset;

    private final BroadcastReceiver aclReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) { refreshDevices(); }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_bluetooth);

        tvDevices    = findViewById(R.id.tvDevices);
        rbInPhone    = findViewById(R.id.rbInPhone);
        rbInBt       = findViewById(R.id.rbInBt);
        rbOutBt      = findViewById(R.id.rbOutBt);
        rbOutSpeaker = findViewById(R.id.rbOutSpeaker);
        btnRefresh   = findViewById(R.id.btnRefresh);
        btnFind      = findViewById(R.id.btnFind);

        prefs = new Prefs(this);
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if ("bt".equals(prefs.inSrc())) rbInBt.setChecked(true);
        else rbInPhone.setChecked(true);
        if ("bt".equals(prefs.outDst())) rbOutBt.setChecked(true);
        else rbOutSpeaker.setChecked(true);

        RadioGroup rgIn = findViewById(R.id.rgIn);
        RadioGroup rgOut = findViewById(R.id.rgOut);
        rgIn.setOnCheckedChangeListener((g, id) -> saveAudio());
        rgOut.setOnCheckedChangeListener((g, id) -> saveAudio());

        btnRefresh.setOnClickListener(v -> refreshDevices());
        btnFind.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        ContextCompat.registerReceiver(this, aclReceiver, f,
            ContextCompat.RECEIVER_NOT_EXPORTED);

        if (btAdapter != null) {
            btAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int p, BluetoothProfile proxy) {
                    btHeadset = (BluetoothHeadset) proxy;
                    refreshDevices();
                }
                @Override public void onServiceDisconnected(int p) { btHeadset = null; }
            }, BluetoothProfile.HEADSET);
        }

        refreshDevices();
    }

    private void saveAudio() {
        String inSrc  = rbInBt.isChecked() ? "bt" : "phone";
        String outDst = rbOutBt.isChecked() ? "bt" : "speaker";
        prefs.saveAudio(inSrc, outDst);
    }

    private void refreshDevices() {
        if (btAdapter == null || !btAdapter.isEnabled()) {
            tvDevices.setText("Bluetooth выключен");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (btHeadset != null) {
            List<BluetoothDevice> devs = btHeadset.getConnectedDevices();
            if (devs.isEmpty()) {
                sb.append("Нет подключённых гарнитур");
            } else {
                for (BluetoothDevice d : devs) sb.append(d.getName()).append('\n');
            }
        } else {
            Set<BluetoothDevice> bonded = btAdapter.getBondedDevices();
            if (bonded.isEmpty()) sb.append("Нет сопряжённых устройств");
            else for (BluetoothDevice d : bonded) sb.append(d.getName()).append('\n');
        }
        tvDevices.setText(sb.toString().trim());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(aclReceiver);
        if (btAdapter != null && btHeadset != null) {
            btAdapter.closeProfileProxy(BluetoothProfile.HEADSET, btHeadset);
        }
    }
}
