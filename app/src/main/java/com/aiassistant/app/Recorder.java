package com.aiassistant.app;

import android.bluetooth.*;
import android.content.Context;
import android.media.*;
import java.io.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

public class Recorder {
    private static final int RATE = 16000;
    private static final int CH   = AudioFormat.CHANNEL_IN_MONO;
    private static final int FMT  = AudioFormat.ENCODING_PCM_16BIT;

    private final Context ctx;
    private volatile boolean running = false;
    private AudioRecord ar;
    private BluetoothAdapter btAdapter;
    private BluetoothHeadset btHeadset;
    private boolean scoStarted = false;

    public Recorder(Context ctx) {
        this.ctx = ctx;
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void start(Prefs prefs) {
        running = true;
        if ("bt".equals(prefs.inSrc())) {
            startSco();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        }
        int minBuf = AudioRecord.getMinBufferSize(RATE, CH, FMT);
        ar = new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RATE, CH, FMT, Math.max(minBuf, 4096)
        );
        ar.startRecording();
    }

    public byte[] stop() {
        running = false;
        if (ar == null) return null;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = ar.read(buf, 0, buf.length)) > 0) {
            pcm.write(buf, 0, n);
        }
        ar.stop();
        ar.release();
        ar = null;
        stopSco();
        byte[] pcmBytes = pcm.toByteArray();
        return buildWav(pcmBytes);
    }

    public boolean isRunning() { return running; }

    private void startSco() {
        if (btAdapter == null) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.startBluetoothSco();
        am.setBluetoothScoOn(true);
        scoStarted = true;
    }

    private void stopSco() {
        if (!scoStarted) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.stopBluetoothSco();
        am.setBluetoothScoOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
        scoStarted = false;
    }

    private byte[] buildWav(byte[] pcm) {
        int dataLen = pcm.length;
        int totalLen = dataLen + 36;
        ByteBuffer bb = ByteBuffer.allocate(44 + dataLen);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes());
        bb.putInt(totalLen);
        bb.put("WAVE".getBytes());
        bb.put("fmt ".getBytes());
        bb.putInt(16);
        bb.putShort((short)1);
        bb.putShort((short)1);
        bb.putInt(RATE);
        bb.putInt(RATE * 2);
        bb.putShort((short)2);
        bb.putShort((short)16);
        bb.put("data".getBytes());
        bb.putInt(dataLen);
        bb.put(pcm);
        return bb.array();
    }
}
