package com.aiassistant.app;

import org.json.JSONArray;

/** Фоновая логика: запись → распознавание → ответ → озвучка. */
class MainLogic {
    private final MainActivity a;

    MainLogic(MainActivity a) { this.a = a; }

    void process() {
        try {
            byte[] wav = a.recorder.stop();
            if (wav == null || wav.length < 44 + 16000) {
                a.handler.post(() -> {
                    a.toast("Слишком короткая запись");
                    a.setState(MainActivity.State.IDLE);
                });
                return;
            }
            String text = a.ai.transcribe(wav, a.prefs.assemblyKey());
            if (text == null || text.trim().isEmpty()) {
                a.handler.post(() -> {
                    a.toast("Ничего не распознано");
                    a.setState(MainActivity.State.IDLE);
                });
                return;
            }
            String[] cmd = a.prefs.matchCommand(text);
            if (cmd != null) {
                a.handler.post(() -> runCommand(cmd[0], cmd[1]));
                return;
            }
            int branch = a.prefs.currentBranch();
            a.prefs.appendHistory(branch, "user", text);
            String answer = a.ai.chat(
                a.prefs.groqKey(), a.prefs.model(),
                a.prefs.prompt(), a.prefs.history(branch)
            );
            a.prefs.appendHistory(branch, "assistant", answer);
            final String t = text, ans = answer;
            a.handler.post(() -> {
                a.tvAnswer.setText(t + "\n\n" + ans);
                a.setState(MainActivity.State.SPEAKING);
                a.speaker.speak(ans, () ->
                    a.handler.post(() -> a.setState(MainActivity.State.IDLE)));
            });
        } catch (Exception e) {
            a.prefs.logError(e.getMessage());
            a.handler.post(() -> {
                a.toast("Ошибка: " + e.getMessage());
                a.setState(MainActivity.State.IDLE);
            });
        }
    }

    void runCommand(String action, String rest) {
        switch (action) {
            case "new_branch":
                a.prefs.addBranch();
                a.prefs.setCurrentBranch(a.prefs.branchCount() - 1);
                a.updateBranchUI();
                a.toast("Новая ветка");
                break;
            case "next_branch": a.changeBranch(+1); break;
            case "prev_branch": a.changeBranch(-1); break;
            case "repeat":
                JSONArray h = a.prefs.history(a.prefs.currentBranch());
                if (h.length() > 0) {
                    try {
                        String last = h.getJSONObject(h.length()-1)
                            .getString("content");
                        a.setState(MainActivity.State.SPEAKING);
                        a.speaker.speak(last, () ->
                            a.handler.post(() -> a.setState(MainActivity.State.IDLE)));
                    } catch (Exception ignored) {}
                }
                break;
            default: a.setState(MainActivity.State.IDLE);
        }
    }
}
