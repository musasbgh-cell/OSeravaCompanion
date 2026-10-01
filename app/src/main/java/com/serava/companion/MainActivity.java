package com.serava.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC = 41;
    private static final String PREFS = "serava_local";
    private static final String MODEL_FILE = "Qwen3-4B-Q4_K_M.gguf";
    private static final String MODEL_URL = "https://huggingface.co/ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true";
    private static final long MODEL_BYTES = 2500000000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status, chat;
    private EditText input;
    private Button brainButton, liveButton;
    private ProgressBar progress;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private LocalBrainBridge brain;
    private boolean brainReady, brainLoading, speaking, listening, liveMode;
    private long downloadId = -1L;
    private StringBuilder generation = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(18,7,12));
        getWindow().setNavigationBarColor(Color.rgb(18,7,12));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        tts = new TextToSpeech(this, this);
        setupSpeech();
        brain = new LocalBrainBridge(this);
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        if (modelFile().exists() && modelFile().length() > 2000000000L) loadBrain();
        else setStatus("العقل المحلي غير مثبت");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(8),dp(16),dp(12));
        root.setBackgroundColor(Color.rgb(18,7,12));
        root.setOnApplyWindowInsetsListener((v,i)->{
            android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(16),bars.top+dp(8),dp(16),bars.bottom+dp(10)); return i;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("سيرافا",30,Color.rgb(222,184,130));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1f));
        brainButton = new Button(this); brainButton.setText("🧠 العقل");
        brainButton.setOnClickListener(v->showBrainDialog());
        header.addView(brainButton,new LinearLayout.LayoutParams(dp(105),dp(48)));
        root.addView(header);

        status = text("",12,Color.rgb(200,94,120)); status.setGravity(Gravity.CENTER);
        root.addView(status,matchWrap());

        TextView avatar = text("♈",92,Color.rgb(198,42,72)); avatar.setGravity(Gravity.CENTER);
        avatar.setPadding(0,dp(8),0,dp(8));
        root.addView(avatar,new LinearLayout.LayoutParams(-1,dp(150)));

        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setVisibility(View.GONE);
        root.addView(progress,matchWrap());

        ScrollView scroll=new ScrollView(this);
        chat=text("سيرافا: أنا هنا. نزّل عقلي المحلي مرة واحدة، وبعدها أعمل بدون API أو مفتاح أو إنترنت.",17,Color.rgb(240,222,215));
        chat.setTextDirection(View.TEXT_DIRECTION_RTL); chat.setLineSpacing(dp(3),1.08f);
        scroll.addView(chat);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        input=new EditText(this); input.setHint("اكتب لسيرافا..."); input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(135,104,111)); input.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.addView(input,matchWrap());

        LinearLayout actions=new LinearLayout(this);
        Button send=new Button(this); send.setText("إرسال"); send.setOnClickListener(v->sendTyped()); actions.addView(send,weighted());
        Button mic=new Button(this); mic.setText("🎙 تحدث"); mic.setOnClickListener(v->{liveMode=false; startListening();}); actions.addView(mic,weighted());
        liveButton=new Button(this); liveButton.setText("◉ مباشر"); liveButton.setOnClickListener(v->toggleLive()); actions.addView(liveButton,weighted());
        root.addView(actions,matchWrap());
        setContentView(root); root.requestApplyInsets();
    }

    private void showBrainDialog() {
        if (brainReady) {
            new AlertDialog.Builder(this).setTitle("عقل سيرافا المحلي")
                    .setMessage("Qwen3-4B Q4_K_M\nالحالة: جاهز\nيعمل محليًا بدون API أو مفتاح.")
                    .setPositiveButton("موافق",null).show(); return;
        }
        if (modelFile().exists() && modelFile().length()>2000000000L) { loadBrain(); return; }
        new AlertDialog.Builder(this).setTitle("تنزيل عقل سيرافا")
                .setMessage("سيتم تنزيل نموذج Qwen3-4B مضغوط بحجم يقارب 2.5GB. يتم التنزيل مرة واحدة فقط، وبعدها يعمل الذكاء محليًا بدون مفتاح أو اشتراك. يفضل Wi‑Fi.")
                .setPositiveButton("تنزيل", (d,w)->startModelDownload())
                .setNegativeButton("لاحقًا",null).show();
    }

    private File modelFile() {
        File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir!=null && !dir.exists()) dir.mkdirs();
        return new File(dir,MODEL_FILE);
    }

    private void startModelDownload() {
        File f=modelFile(); if(f.exists()) f.delete();
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(MODEL_URL));
        r.setTitle("Serava Local Brain"); r.setDescription("Qwen3-4B Q4_K_M");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(true); r.setAllowedOverRoaming(false);
        r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,MODEL_FILE);
        downloadId=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        progress.setVisibility(View.VISIBLE); setStatus("تنزيل العقل المحلي...");
        pollDownload();
    }

    private void pollDownload() {
        if(downloadId<0) return;
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query q=new DownloadManager.Query().setFilterById(downloadId);
        try(android.database.Cursor c=dm.query(q)) {
            if(c!=null && c.moveToFirst()) {
                long total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long done=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                if(total>0) {
                    progress.setProgress((int)Math.min(1000,done*1000/total));
                    setStatus("تنزيل العقل: "+(done*100/total)+"%");
                }
            }
        }
        if(!brainReady && downloadId>=0) main.postDelayed(this::pollDownload,1200);
    }

    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            long id=i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);
            if(id!=downloadId) return;
            downloadId=-1; progress.setVisibility(View.GONE);
            if(modelFile().exists() && modelFile().length()>2000000000L) loadBrain();
            else setStatus("فشل تنزيل النموذج");
        }
    };

    private void loadBrain() {
        if(brainLoading || brainReady) return;
        brainLoading=true; setStatus("تحميل عقل سيرافا في الذاكرة...");
        brain.load(modelFile().getAbsolutePath(), new LocalBrainBridge.Listener() {
            public void onLoaded(){ brainLoading=false; brainReady=true; setStatus("العقل المحلي 4B • جاهز"); Toast.makeText(MainActivity.this,"عقل سيرافا جاهز",Toast.LENGTH_SHORT).show(); }
            public void onToken(String t){}
            public void onComplete(){}
            public void onError(String m){ brainLoading=false; brainReady=false; setStatus("خطأ تحميل العقل"); Toast.makeText(MainActivity.this,m,Toast.LENGTH_LONG).show(); }
        });
    }

    private void sendTyped(){ String m=input.getText().toString().trim(); if(m.isEmpty())return; input.setText(""); ask(m); }

    private void ask(String message) {
        append("أنت",message);
        String memory=memoryCommand(message);
        if(memory!=null){ reply(memory); return; }
        if(!brainReady) {
            if(modelFile().exists()) loadBrain();
            reply("عقلي الكامل لم يصبح جاهزًا بعد. اضغط «🧠 العقل» ونزّل النموذج المحلي مرة واحدة، ثم اسألني ما تشاء."); return;
        }
        setStatus("سيرافا تفكر محليًا...");
        generation.setLength(0);
        brain.generate(buildPrompt(message),512,new LocalBrainBridge.Listener(){
            public void onLoaded(){}
            public void onToken(String t){ generation.append(t); }
            public void onComplete(){ String out=sanitize(generation.toString()); if(out.isBlank()) out="لم أستطع تكوين إجابة هذه المرة."; reply(out); }
            public void onError(String m){ setStatus("خطأ في التوليد"); reply("حدث خطأ في العقل المحلي: "+m); }
        });
    }

    private String buildPrompt(String m) {
        Set<String> memories=prefs.getStringSet("memories",new LinkedHashSet<>());
        StringBuilder b=new StringBuilder();
        if(memories!=null && !memories.isEmpty()){ b.append("ذكريات صريحة عن المستخدم، استخدميها فقط إن كانت ذات صلة:\n"); int n=0; for(String x:memories){ if(++n>8)break; b.append("- ").append(x).append("\n");}}
        b.append("\nرسالة المستخدم: ").append(m);
        return b.toString();
    }

    private String memoryCommand(String m){
        for(String p:new String[]{"تذكري أن","تذكري ان","احفظي أن","احفظي ان"}){
            if(m.startsWith(p)){ String fact=m.substring(p.length()).trim(); if(fact.isEmpty())return "قل لي ما الذي تريدني أن أتذكره.";
                Set<String>s=new LinkedHashSet<>(prefs.getStringSet("memories",new LinkedHashSet<>())); s.add(fact); prefs.edit().putStringSet("memories",s).apply(); return "حسنًا. سأحتفظ بهذه المعلومة: «"+fact+"»."; }
        } return null;
    }

    private String sanitize(String s){
        s=s.replaceAll("(?s)<think>.*?</think>","").trim();
        if(s.startsWith("assistant")) s=s.substring(9).trim();
        return s;
    }

    private void reply(String r){ append("سيرافا",r); speak(r); }
    private void append(String who,String m){ chat.append("\n\n"+who+": "+m); }

    private void setupSpeech(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){listening=true;setStatus("أستمع إليك...");}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){listening=false;setStatus("تفكر...");}
            public void onError(int e){listening=false;if(liveMode&&!speaking)main.postDelayed(MainActivity.this::startListening,1000);}
            public void onResults(Bundle b){listening=false;ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty())ask(a.get(0));}
            public void onPartialResults(Bundle b){}
            public void onEvent(int t,Bundle b){}
        });
    }

    private void startListening(){
        if(speaking||listening)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        recognizer.startListening(i);
    }
    private void toggleLive(){liveMode=!liveMode;liveButton.setText(liveMode?"■ أوقف":"◉ مباشر");if(liveMode)startListening();else if(recognizer!=null)recognizer.cancel();}
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startListening();}

    private void speak(String s){ if(tts==null)return; speaking=true; setStatus("سيرافا تتكلم..."); tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"serava"); }
    @Override public void onInit(int st){ if(st!=TextToSpeech.SUCCESS)return;tts.setLanguage(new Locale("ar"));tts.setSpeechRate(.92f);tts.setPitch(.90f);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String u){}public void onDone(String u){main.post(()->{speaking=false;setStatus(brainReady?"العقل المحلي 4B • جاهز":"العقل غير جاهز");if(liveMode)main.postDelayed(MainActivity.this::startListening,450);});}public void onError(String u){onDone(u);}}); }

    private void setStatus(String s){status.setText("SERAVA • "+s);}
    private TextView text(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,-2,1f);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onDestroy(){try{unregisterReceiver(downloadReceiver);}catch(Exception ignored){} if(brain!=null)brain.destroy();if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
