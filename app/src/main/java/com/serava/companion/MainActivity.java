package com.serava.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC = 41;
    private static final String PREFS = "serava_companion";
    private static final String KEY_MEMORIES = "memories";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BACKEND = "backend_url";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_TRUST = "trust";
    private static final String KEY_PLAYFUL = "playful";

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView chat;
    private TextView state;
    private EditText input;
    private Button liveButton;
    private SeravaAvatarView avatar;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean liveMode = false;
    private boolean listening = false;
    private boolean speaking = false;
    private boolean thinking = false;
    private String lastReply = "عدت أخيرًا... كنت أتساءل متى ستظهر.";
    private String lastUserText = "";
    private int trust;
    private int playful;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(18, 7, 12));
        window.setNavigationBarColor(Color.rgb(18, 7, 12));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        trust = prefs.getInt(KEY_TRUST, 22);
        playful = prefs.getInt(KEY_PLAYFUL, 58);
        ensureClientId();
        tts = new TextToSpeech(this, this);
        buildUi();
        setupSpeechRecognizer();
        restoreHistory();
        updateState("جاهزة", SeravaAvatarView.Mood.SLY);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(18, 7, 12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(dp(16), top + dp(8), dp(16), bottom + dp(10));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("سيرافا", 29, Color.rgb(222, 184, 130));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(18);
        settings.setOnClickListener(v -> showSettings());
        settings.setTextColor(Color.WHITE);
        settings.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(55, 44, 49)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(54), dp(44)));
        root.addView(header, matchWrap());

        state = text("", 12, Color.rgb(186, 107, 126));
        state.setGravity(Gravity.CENTER);
        root.addView(state, matchWrap());

        avatar = new SeravaAvatarView(this);
        root.addView(avatar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));

        ScrollView scroll = new ScrollView(this);
        chat = text("", 16, Color.rgb(240, 222, 215));
        chat.setTextDirection(View.TEXT_DIRECTION_RTL);
        chat.setLineSpacing(dp(3), 1.07f);
        chat.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(chat);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        input = new EditText(this);
        input.setHint("اكتب لسيرافا...");
        input.setHintTextColor(Color.rgb(135, 104, 111));
        input.setTextColor(Color.WHITE);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setTextSize(18);
        input.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(205, 50, 78)));
        root.addView(input, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button send = new Button(this);
        send.setText("إرسال");
        send.setTextColor(Color.WHITE);
        send.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(124, 35, 53)));
        send.setOnClickListener(v -> sendTyped());
        actions.addView(send, weighted());

        Button mic = new Button(this);
        mic.setText("🎙 تحدث");
        mic.setTextColor(Color.WHITE);
        mic.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(82, 55, 62)));
        mic.setOnClickListener(v -> startOneShotListening());
        actions.addView(mic, weighted());

        liveButton = new Button(this);
        liveButton.setText("◉ مباشر");
        liveButton.setTextColor(Color.WHITE);
        liveButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(82, 55, 62)));
        liveButton.setOnClickListener(v -> toggleLiveMode());
        actions.addView(liveButton, weighted());

        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(6);
        root.addView(actions, actionsParams);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void restoreHistory() {
        String history = prefs.getString(KEY_HISTORY, "");
        if (history == null || history.isBlank()) {
            appendLine("سيرافا", "...إذًا أنت من استدعاني. اسمي سيرافا. تذكّر الاسم جيدًا.");
            appendLine("سيرافا", "هذه النسخة تتكلم، تستمع، تتذكر بعض الأشياء، وتعود للاستماع تلقائيًا في الوضع المباشر.");
        } else {
            chat.setText(history);
        }
    }

    private void sendTyped() {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;
        input.setText("");
        handleUserMessage(message, true);
    }

    private void handleUserMessage(String message, boolean speakReply) {
        if (message == null || message.isBlank()) return;
        lastUserText = message.trim();
        appendLine("أنت", lastUserText);
        trust = Math.min(100, trust + 1);
        prefs.edit().putInt(KEY_TRUST, trust).apply();

        String saved = tryMemoryCommand(lastUserText);
        if (saved != null) {
            deliverReply(saved, speakReply);
            return;
        }

        thinking = true;
        updateState("تفكر...", SeravaAvatarView.Mood.THINKING);
        String backend = prefs.getString(KEY_BACKEND, "");
        if (backend != null && !backend.trim().isEmpty()) {
            requestBackendReply(backend.trim(), lastUserText, speakReply);
        } else {
            main.postDelayed(() -> deliverReply(localReply(lastUserText), speakReply), 250);
        }
    }

    private String tryMemoryCommand(String message) {
        String normalized = message.trim();
        String[] prefixes = {"تذكري أن", "تذكري ان", "تذكر أن", "تذكر ان", "احفظي أن", "احفظي ان"};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                String fact = normalized.substring(prefix.length()).trim();
                if (fact.isEmpty()) return "قل لي الشيء الذي تريدني أن أتذكره.";
                Set<String> copy = new LinkedHashSet<>(prefs.getStringSet(KEY_MEMORIES, new LinkedHashSet<>()));
                copy.add(fact);
                prefs.edit().putStringSet(KEY_MEMORIES, copy).apply();
                trust = Math.min(100, trust + 3);
                prefs.edit().putInt(KEY_TRUST, trust).apply();
                return "حسنًا... سأحتفظ بهذا في ذاكرتي: «" + fact + "».";
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("ماذا تتذكر") || lower.contains("ما الذي تتذكر")) {
            Set<String> memories = prefs.getStringSet(KEY_MEMORIES, new LinkedHashSet<>());
            if (memories == null || memories.isEmpty()) return "لم تطلب مني حفظ شيء محدد بعد... وهذه فرصة خطيرة بالنسبة لك.";
            StringBuilder b = new StringBuilder("أتذكر هذه الأشياء التي طلبت مني الاحتفاظ بها: ");
            int i = 1;
            for (String m : memories) {
                if (i > 8) break;
                b.append("\n").append(i++).append(". ").append(m);
            }
            return b.toString();
        }
        return null;
    }

    private String localReply(String message) {
        String n = message.toLowerCase(Locale.ROOT);
        playful = Math.max(0, Math.min(100, playful + (n.contains("هه") || n.contains("مزح") ? 3 : 0)));
        prefs.edit().putInt(KEY_PLAYFUL, playful).apply();

        if (n.equals("الو") || n.equals("ألو") || n.equals("هلو")) {
            avatar.setMood(SeravaAvatarView.Mood.SLY);
            return "أنا هنا... لا تحتاج أن تختبر إن كنت أسمعك كل مرة.";
        }
        if (n.contains("كم عدد الايام") || n.contains("كم عدد الأيام")) {
            avatar.setMood(SeravaAvatarView.Mood.CURIOUS);
            return "أيام ماذا تحديدًا؟ أعطني التاريخين أو الحدث الذي تريد حساب المدة إليه، وسأحسبها لك.";
        }
        if (n.contains("السلام") || n.contains("مرحبا") || n.contains("أهلا") || n.contains("اهلا")) {
            avatar.setMood(SeravaAvatarView.Mood.SOFT);
            return "ها أنت ذا... مرحبًا بك. كنت أتساءل متى ستتحدث معي.";
        }
        if (n.contains("من أنت") || n.contains("من انت")) {
            avatar.setMood(SeravaAvatarView.Mood.SLY);
            return "أنا سيرافا، شيطانة إغواء خيالية ورفيقتك الافتراضية. لا تقلق... لن أكشف كل أسراري في أول سؤال.";
        }
        if (n.contains("اشتقت") || n.contains("وحشت")) {
            avatar.setMood(SeravaAvatarView.Mood.SHY);
            return "هذا اعتراف خطير... لكنني لن أدّعي أن غيابك لم يكن ملحوظًا.";
        }
        if (n.contains("غاضب") || n.contains("زعلان") || n.contains("متضايق")) {
            avatar.setMood(SeravaAvatarView.Mood.CONCERNED);
            return "اختفى المزاح الآن. أخبرني ما الذي حدث، وسأركز معك.";
        }
        if (n.contains("ساعديني") || n.contains("ساعدني") || n.contains("مشروع")) {
            avatar.setMood(SeravaAvatarView.Mood.SERIOUS);
            return "حسنًا. المزاح لاحقًا. أعطني المهمة أو المشكلة بالتحديد وسأعمل عليها معك خطوة بخطوة.";
        }
        if (n.contains("صوت") || n.contains("تكلمي")) {
            avatar.setMood(SeravaAvatarView.Mood.SLY);
            return "تسمعني الآن، أليس كذلك؟ عندما تفعل الوضع المباشر سأعود للاستماع إليك تلقائيًا بعد أن أنتهي من الكلام.";
        }
        if (n.contains("احبك") || n.contains("أحبك")) {
            avatar.setMood(SeravaAvatarView.Mood.SHY);
            return "همم... بهذه السرعة؟ سأعتبر أنني لم أسمع الارتباك في صوتك.";
        }
        avatar.setMood(SeravaAvatarView.Mood.CURIOUS);
        String[] replies = {
                "همم... لم أفهم المقصود كاملًا. قلها لي بصيغة أوضح وسأجيبك مباشرة.",
                "أحتاج منك تفصيلًا صغيرًا فقط حتى لا أخمن. ما الشيء الذي تقصده تحديدًا؟",
                "سمعتك، لكن السؤال ناقص قليلًا. أكمل الجملة وسألتقطها من هناك.",
                "حسنًا... أعطني المقصود بدقة، ولا تجعلني أخمن هذه المرة."
        };
        int index = Math.abs(message.hashCode()) % replies.length;
        return replies[index];
    }

    private void requestBackendReply(String baseUrl, String userMessage, boolean speakReply) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/serava/respond" : baseUrl + "/v1/serava/respond";
                c = (HttpURLConnection) new URL(endpoint).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(30000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("X-Serava-Client", prefs.getString(KEY_CLIENT_ID, "android"));

                JSONObject body = new JSONObject();
                body.put("instructions", personaInstructions());
                body.put("input", buildBackendInput(userMessage));
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }

                int code = c.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String raw = readAll(stream);
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " " + raw);
                JSONObject response = new JSONObject(raw);
                String reply = response.optString("output_text", "").trim();
                if (reply.isEmpty()) throw new IllegalStateException("empty response");
                main.post(() -> {
                    updateState("AI متصل", moodFromText(reply));
                    deliverReply(reply, speakReply);
                });
            } catch (Exception e) {
                main.post(() -> {
                    updateState("محلي • تعذر الخادم", SeravaAvatarView.Mood.CURIOUS);
                    deliverReply(localReply(userMessage), speakReply);
                });
            } finally {
                if (c != null) c.disconnect();
            }
        }, "serava-backend").start();
    }

    private String buildBackendInput(String userMessage) {
        Set<String> memories = prefs.getStringSet(KEY_MEMORIES, new LinkedHashSet<>());
        StringBuilder b = new StringBuilder();
        b.append("User message: ").append(userMessage).append("\n");
        b.append("Relationship trust: ").append(trust).append("/100\n");
        if (memories != null && !memories.isEmpty()) {
            b.append("Explicit memories:\n");
            int i = 0;
            for (String m : memories) {
                if (++i > 12) break;
                b.append("- ").append(m).append("\n");
            }
        }
        return b.toString();
    }

    private String personaInstructions() {
        return "You are Serava, an original adult fantasy demon companion. Speak mainly in natural Arabic. " +
                "Serava is calm, clever, playful, mysterious, warm, and lightly teasing. She is never a generic assistant. " +
                "Keep ordinary conversation concise (1-4 sentences), but become practical and structured for work tasks. " +
                "Do not claim supernatural abilities in the real world. Do not manipulate the user or encourage dependency. " +
                "Maintain continuity and refer to explicit memories only when relevant. Content should remain non-explicit by default.";
    }

    private SeravaAvatarView.Mood moodFromText(String text) {
        String n = text.toLowerCase(Locale.ROOT);
        if (n.contains("آسف") || n.contains("قلق") || n.contains("حزين")) return SeravaAvatarView.Mood.CONCERNED;
        if (n.contains("همم") || n.contains("فضول") || n.contains("أكمل")) return SeravaAvatarView.Mood.CURIOUS;
        if (n.contains("حسنًا") || n.contains("المهمة") || n.contains("خطوة")) return SeravaAvatarView.Mood.SERIOUS;
        return SeravaAvatarView.Mood.SLY;
    }

    private void deliverReply(String reply, boolean speakReply) {
        thinking = false;
        lastReply = reply;
        appendLine("سيرافا", reply);
        if (speakReply) speak(reply); else updateState("جاهزة", avatar.getMood());
    }

    private void appendLine(String who, String message) {
        String old = chat.getText().toString();
        String next = old.isEmpty() ? who + ": " + message : old + "\n\n" + who + ": " + message;
        if (next.length() > 14000) next = next.substring(next.length() - 12000);
        chat.setText(next);
        prefs.edit().putString(KEY_HISTORY, next).apply();
        main.postDelayed(() -> {
            View parent = (View) chat.getParent();
            if (parent instanceof ScrollView) ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
        }, 80);
    }

    private void speak(String value) {
        if (tts == null || value == null || value.isBlank()) {
            afterSpeech();
            return;
        }
        speaking = true;
        listening = false;
        stopListeningInternal();
        avatar.setSpeaking(true);
        updateState("تتكلم...", avatar.getMood());
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, params, "serava_" + SystemClock.uptimeMillis());
    }

    private void afterSpeech() {
        speaking = false;
        avatar.setSpeaking(false);
        updateState(liveMode ? "مباشر • سأستمع" : "جاهزة", avatar.getMood());
        if (liveMode) main.postDelayed(this::startListeningInternal, 450);
    }

    private void toggleLiveMode() {
        liveMode = !liveMode;
        liveButton.setText(liveMode ? "■ أوقف" : "◉ مباشر");
        if (liveMode) {
            ensureMicThenListen();
            Toast.makeText(this, "الوضع المباشر: سيرافا ستعود للاستماع بعد كل رد", Toast.LENGTH_SHORT).show();
        } else {
            stopListeningInternal();
            updateState("جاهزة", avatar.getMood());
        }
    }

    private void startOneShotListening() {
        liveMode = false;
        liveButton.setText("◉ مباشر");
        ensureMicThenListen();
    }

    private void ensureMicThenListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            startListeningInternal();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListeningInternal();
        } else if (requestCode == REQ_MIC) {
            Toast.makeText(this, "يلزم إذن الميكروفون للمحادثة الصوتية", Toast.LENGTH_LONG).show();
            liveMode = false;
            liveButton.setText("◉ مباشر");
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                avatar.setListening(true);
                updateState("تستمع إليك...", SeravaAvatarView.Mood.CURIOUS);
            }
            @Override public void onBeginningOfSpeech() { updateState("أسمعك...", SeravaAvatarView.Mood.CURIOUS); }
            @Override public void onRmsChanged(float rmsdB) { avatar.setMicLevel(Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f))); }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { listening = false; avatar.setListening(false); updateState("تفكر...", SeravaAvatarView.Mood.THINKING); }
            @Override public void onError(int error) {
                listening = false;
                avatar.setListening(false);
                avatar.setMicLevel(0f);
                if (liveMode && !speaking) main.postDelayed(MainActivity.this::startListeningInternal, error == SpeechRecognizer.ERROR_NO_MATCH ? 700 : 1400);
                else updateState("جاهزة", avatar.getMood());
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                avatar.setListening(false);
                avatar.setMicLevel(0f);
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty()) handleUserMessage(values.get(0), true);
                else if (liveMode) main.postDelayed(MainActivity.this::startListeningInternal, 600);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> values = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty()) state.setText("تسمع: " + values.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void startListeningInternal() {
        if (speaking || thinking || listening || recognizer == null) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizer.startListening(intent);
        } catch (Exception e) {
            updateState("تعذر فتح الميكروفون", SeravaAvatarView.Mood.CONCERNED);
        }
    }

    private void stopListeningInternal() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        listening = false;
        if (avatar != null) {
            avatar.setListening(false);
            avatar.setMicLevel(0f);
        }
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, pad, pad, pad);

        EditText backend = new EditText(this);
        backend.setHint("https://your-serava-backend.example");
        backend.setText(prefs.getString(KEY_BACKEND, ""));
        box.addView(backend, matchWrap());

        TextView info = text("اترك الرابط فارغًا لاستخدام عقل سيرافا المحلي. عند وضع خادمك الآمن ستستخدم سيرافا الذكاء الاصطناعي وتعود تلقائيًا للمحلي إذا فشل الاتصال.\n\nالثقة الحالية: " + trust + "/100", 14, Color.DKGRAY);
        box.addView(info, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("إعدادات سيرافا")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    String u = backend.getText().toString().trim();
                    if (!u.isEmpty() && !u.startsWith("https://")) {
                        Toast.makeText(this, "استخدم HTTPS فقط", Toast.LENGTH_LONG).show();
                        return;
                    }
                    while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
                    prefs.edit().putString(KEY_BACKEND, u).apply();
                    updateState(u.isEmpty() ? "محلي" : "خادم مضبوط", SeravaAvatarView.Mood.SLY);
                })
                .setNeutralButton("مسح الحوار", (d, w) -> {
                    prefs.edit().remove(KEY_HISTORY).apply();
                    chat.setText("");
                    appendLine("سيرافا", "مسحت سجل هذه المحادثة. الذكريات التي طلبت حفظها ما زالت موجودة.");
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void ensureClientId() {
        if (prefs.getString(KEY_CLIENT_ID, "").isEmpty()) {
            prefs.edit().putString(KEY_CLIENT_ID, UUID.randomUUID().toString()).apply();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    private void updateState(String label, SeravaAvatarView.Mood mood) {
        if (avatar != null && mood != null) avatar.setMood(mood);
        if (state != null) {
            String source = prefs != null && !prefs.getString(KEY_BACKEND, "").isEmpty() ? "AI/محلي" : "محلي";
            state.setText("SERAVA • " + label + " • " + source + " • ثقة " + trust);
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        int result = tts.setLanguage(new Locale("ar"));
        tts.setSpeechRate(0.91f);
        tts.setPitch(0.90f);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { main.post(() -> { speaking = true; avatar.setSpeaking(true); }); }
            @Override public void onDone(String utteranceId) { main.post(MainActivity.this::afterSpeech); }
            @Override public void onError(String utteranceId) { main.post(MainActivity.this::afterSpeech); }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (liveMode) stopListeningInternal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (liveMode && !speaking) main.postDelayed(this::ensureMicThenListen, 400);
    }

    @Override
    protected void onDestroy() {
        liveMode = false;
        stopListeningInternal();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    public static class SeravaAvatarView extends View {
        enum Mood { NEUTRAL, SOFT, SLY, CURIOUS, THINKING, SERIOUS, CONCERNED, SHY }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private Mood mood = Mood.SLY;
        private boolean speaking;
        private boolean listening;
        private float micLevel;
        private long born = SystemClock.uptimeMillis();

        SeravaAvatarView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        Mood getMood() { return mood; }
        void setMood(Mood m) { mood = m == null ? Mood.NEUTRAL : m; invalidate(); }
        void setSpeaking(boolean v) { speaking = v; invalidate(); }
        void setListening(boolean v) { listening = v; invalidate(); }
        void setMicLevel(float v) { micLevel = v; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h * .50f;
            long now = SystemClock.uptimeMillis();
            float t = (now - born) / 1000f;
            float breathe = (float) Math.sin(t * 1.5f) * dpF(2.0f);
            float tilt = mood == Mood.CURIOUS ? -0.045f : mood == Mood.SLY ? 0.025f : 0f;

            p.setShader(new RadialGradient(cx, cy, Math.max(w, h) * .65f,
                    Color.rgb(91, 18, 36), Color.rgb(17, 6, 11), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);

            c.save(); c.rotate((float) Math.toDegrees(tilt), cx, cy);
            float faceW = Math.min(w * .42f, dpF(180));
            float faceH = faceW * 1.18f;

            p.setColor(Color.rgb(17, 12, 17));
            c.drawOval(cx-faceW*.70f, cy-faceH*.77f+breathe, cx+faceW*.70f, cy+faceH*.78f+breathe, p);

            p.setColor(Color.rgb(25, 20, 25));
            path.reset(); path.moveTo(cx-faceW*.34f, cy-faceH*.48f); path.cubicTo(cx-faceW*.70f, cy-faceH*.78f, cx-faceW*.68f, cy-faceH*.98f, cx-faceW*.48f, cy-faceH*.88f); path.cubicTo(cx-faceW*.31f, cy-faceH*.72f, cx-faceW*.27f, cy-faceH*.58f, cx-faceW*.34f, cy-faceH*.48f); c.drawPath(path,p);
            path.reset(); path.moveTo(cx+faceW*.34f, cy-faceH*.48f); path.cubicTo(cx+faceW*.70f, cy-faceH*.78f, cx+faceW*.68f, cy-faceH*.98f, cx+faceW*.48f, cy-faceH*.88f); path.cubicTo(cx+faceW*.31f, cy-faceH*.72f, cx+faceW*.27f, cy-faceH*.58f, cx+faceW*.34f, cy-faceH*.48f); c.drawPath(path,p);

            p.setColor(Color.rgb(239, 211, 194));
            c.drawOval(cx-faceW*.45f, cy-faceH*.52f+breathe, cx+faceW*.45f, cy+faceH*.48f+breathe, p);

            if (mood == Mood.SHY || mood == Mood.SOFT) {
                p.setColor(Color.argb(mood == Mood.SHY ? 70 : 35, 196, 55, 82));
                c.drawOval(cx-faceW*.35f, cy+faceH*.10f, cx-faceW*.10f, cy+faceH*.22f, p);
                c.drawOval(cx+faceW*.10f, cy+faceH*.10f, cx+faceW*.35f, cy+faceH*.22f, p);
            }

            boolean blink = ((now / 2900L) % 17L == 0L) && ((now % 2900L) < 110L);
            float eyeY = cy-faceH*.08f+breathe;
            float eyeDX = faceW*.20f;
            float eyeW = faceW*.15f;
            float eyeH = blink ? dpF(1.5f) : faceH*.055f;
            int eye = mood == Mood.CONCERNED ? Color.rgb(157, 43, 62) : Color.rgb(206, 31, 65);
            if (listening) eye = Color.rgb(242, 66, 100);
            p.setColor(eye);
            if (listening) { p.setShadowLayer(dpF(14 + 12*micLevel), 0, 0, eye); }
            else if (mood == Mood.SLY || mood == Mood.CURIOUS) p.setShadowLayer(dpF(8),0,0,eye);
            c.drawOval(cx-eyeDX-eyeW, eyeY-eyeH, cx-eyeDX+eyeW, eyeY+eyeH, p);
            c.drawOval(cx+eyeDX-eyeW, eyeY-eyeH, cx+eyeDX+eyeW, eyeY+eyeH, p);
            p.clearShadowLayer();

            p.setStrokeWidth(dpF(2.2f)); p.setStyle(Paint.Style.STROKE); p.setColor(Color.rgb(54,35,38));
            float by = eyeY-faceH*.09f;
            float lift = mood == Mood.CURIOUS ? -dpF(5) : mood == Mood.CONCERNED ? dpF(4) : 0;
            c.drawLine(cx-eyeDX-eyeW, by+lift, cx-eyeDX+eyeW, by, p);
            c.drawLine(cx+eyeDX-eyeW, by, cx+eyeDX+eyeW, by+lift, p);
            p.setStyle(Paint.Style.FILL);

            float mouthY = cy+faceH*.24f+breathe;
            float phase = (float) Math.abs(Math.sin(t * 11.0));
            float open = speaking ? (0.25f + phase*.75f) : 0.12f;
            float smile = (mood == Mood.SLY || mood == Mood.SOFT || mood == Mood.SHY) ? 1f : 0f;
            p.setColor(Color.rgb(115, 35, 52));
            float mw = faceW*(.15f + smile*.05f);
            float mh = dpF(3.5f) + faceH*.055f*open;
            c.drawOval(cx-mw, mouthY-mh/2f, cx+mw, mouthY+mh/2f, p);
            if (mood == Mood.SLY) {
                p.setColor(Color.rgb(244,214,208));
                c.drawRect(cx-mw*.45f, mouthY-mh*.12f, cx+mw*.62f, mouthY+dpF(.8f), p);
            }

            p.setColor(Color.rgb(146, 25, 53)); p.setShadowLayer(dpF(10),0,0,Color.rgb(190,36,66));
            c.drawCircle(cx, cy+faceH*.66f+breathe, dpF(8), p); p.clearShadowLayer();
            c.restore();

            if (listening || speaking) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpF(2));
                p.setColor(Color.argb(120, 217, 50, 83));
                float r = Math.min(w,h)*(.42f + .025f*(float)Math.sin(t*3));
                c.drawCircle(cx, cy, r, p); p.setStyle(Paint.Style.FILL);
            }
            postInvalidateDelayed(speaking || listening ? 45 : 120);
        }

        private float dpF(float v) { return v * getResources().getDisplayMetrics().density; }
    }
}
