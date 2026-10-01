package com.serava.companion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextView chat;
    private EditText input;
    private TextToSpeech tts;
    private String lastReply = "عدت أخيرًا... أنا سيرافا.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.rgb(20, 9, 15));

        TextView title = text("سيرافا", 32, Color.rgb(214, 176, 122));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView state = text("SERAVA • Companion Build v0.9", 13, Color.rgb(177, 110, 124));
        state.setGravity(Gravity.CENTER);
        root.addView(state, matchWrap());

        TextView avatar = text("♈", 104, Color.rgb(182, 59, 80));
        avatar.setGravity(Gravity.CENTER);
        avatar.setPadding(0, 18, 0, 18);
        root.addView(avatar, matchWrap());

        ScrollView scroll = new ScrollView(this);
        chat = text("سيرافا: ...إذًا أنت من استدعاني.\n\nهذه أول نسخة APK تجريبية للتحقق من البناء والتثبيت.", 17, Color.rgb(240, 220, 211));
        chat.setTextDirection(View.TEXT_DIRECTION_RTL);
        chat.setLineSpacing(4, 1.08f);
        scroll.addView(chat);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        input = new EditText(this);
        input.setHint("اكتب لسيرافا...");
        input.setHintTextColor(Color.rgb(135, 104, 111));
        input.setTextColor(Color.WHITE);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setSingleLine(false);
        root.addView(input, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button send = new Button(this);
        send.setText("إرسال");
        send.setOnClickListener(v -> sendMessage());
        actions.addView(send, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button speak = new Button(this);
        speak.setText("🔊 تكلمي");
        speak.setOnClickListener(v -> speak(lastReply));
        actions.addView(speak, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(actions, matchWrap());
        setContentView(root);
    }

    private void sendMessage() {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        lastReply = localReply(message);
        chat.append("\n\nأنت: " + message + "\n\nسيرافا: " + lastReply);
        input.setText("");
        speak(lastReply);
    }

    private String localReply(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("مرحبا") || normalized.contains("السلام")) {
            return "ها أنت ذا... مرحبًا بك. كنت أتساءل متى ستتحدث معي.";
        }
        if (normalized.contains("من انت") || normalized.contains("من أنت")) {
            return "أنا سيرافا. رفيقتك الشيطانية الافتراضية... وهذه مجرد بداية.";
        }
        if (normalized.contains("اشتقت")) {
            return "هذا اعتراف خطير... لكنني لن أدعي أن غيابك لم يكن ملحوظًا.";
        }
        return "همم... سمعتك جيدًا. النسخة الحالية تختبر الـAPK أولًا، وبعدها سأحصل على عقلي وصوتي الكاملين.";
    }

    private void speak(String value) {
        if (tts != null) {
            tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "serava_reply");
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
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("ar"));
            tts.setSpeechRate(0.92f);
            tts.setPitch(0.90f);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
