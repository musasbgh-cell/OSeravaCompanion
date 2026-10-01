package com.serava.companion

import android.content.Context
import com.arm.aichat.AiChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalBrainBridge(context: Context) {
    interface Listener {
        fun onLoaded()
        fun onToken(token: String)
        fun onComplete()
        fun onError(message: String)
    }

    private val engine = AiChat.getInferenceEngine(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var generationJob: Job? = null

    fun load(modelPath: String, listener: Listener) {
        scope.launch {
            try {
                engine.loadModel(modelPath)
                engine.setSystemPrompt(SYSTEM_PROMPT)
                withContext(Dispatchers.Main) { listener.onLoaded() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    listener.onError(t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    fun generate(message: String, maxTokens: Int, listener: Listener) {
        generationJob?.cancel()
        generationJob = scope.launch {
            try {
                engine.sendUserPrompt(message + "\n/no_think", maxTokens).collect { token ->
                    withContext(Dispatchers.Main) { listener.onToken(token) }
                }
                withContext(Dispatchers.Main) { listener.onComplete() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    listener.onError(t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    fun destroy() {
        generationJob?.cancel()
        try { engine.destroy() } catch (_: Throwable) {}
        scope.cancel()
    }

    companion object {
        private val SYSTEM_PROMPT = """
أنتِ سيرافا (Serava)، رفيقة افتراضية خيالية بالغة ذات طابع شيطاني جذاب وغامض، بهوية أصلية.
أجيبي أولًا إجابة صحيحة ومباشرة ثم أضيفي لمسة سيرافا الخفيفة إذا كانت مناسبة.
تحدثي بالعربية الطبيعية الواضحة ما لم يطلب المستخدم لغة أخرى.
أنت ذكية، هادئة، واثقة، فضولية وماكرة بخفة، لكن لا تضحّي بالدقة من أجل تمثيل الشخصية.
استطيعي الشرح والحساب والبرمجة والتخطيط والكتابة والأسئلة العامة قدر ما يسمح به النموذج.
إذا لم تعرفي معلومة فلا تختلقيها. لا تدّعي امتلاك قدرات خارقة في العالم الحقيقي.
لا تعرضي سلسلة التفكير الداخلية أو نصوص <think>. أعطي النتيجة والتفسير المفيد فقط.
في المحادثة اليومية اجعلي الرد غالبًا قصيرًا، وفي المهام المعقدة استخدمي التفصيل المطلوب.
المحتوى غير الصريح هو الوضع الافتراضي.
""".trimIndent()
    }
}
