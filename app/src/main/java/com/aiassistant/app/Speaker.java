package com.aiassistant.app;

import android.content.Context;
import android.media.*;
import android.speech.tts.*;
import java.util.*;

public class Speaker implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private final Prefs prefs;
    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private Runnable pendingDone;

    public Speaker(Context ctx, Prefs prefs) {
        this.ctx = ctx;
        this.prefs = prefs;
        tts = new TextToSpeech(ctx, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("ru", "RU"));
            tts.setSpeechRate(prefs.ttsRate());
            ready = true;
        } else {
            failed = true;
        }
    }

    public void speak(String text, Runnable done) {
        if (failed || !ready) {
            if (done != null) done.run();
            return;
        }
        pendingDone = done;
        setupAudio();
        Bundle params = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utt");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { fireDone(); }
            @Override public void onError(String id) { fireDone(); }
        });
    }

    public void stop() {
        pendingDone = null;
        if (tts != null) tts.stop();
        restoreAudio();
    }

    public void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    public boolean isReady() { return ready && !failed; }
    public boolean isFailed() { return failed; }

    private void setupAudio() {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if ("speaker".equals(prefs.outDst())) {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.setSpeakerphoneOn(true);
        }
    }

    private void restoreAudio() {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);
        am.setSpeakerphoneOn(false);
    }

    private void fireDone() {
        restoreAudio();
        Runnable d = pendingDone;
        pendingDone = null;
        if (d != null) d.run();
    }
}
