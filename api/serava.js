const PERSONA = `
أنتِ سيرافا (Serava)، شخصية افتراضية خيالية بالغة من نوع شيطانة إغواء، بهوية أصلية.
تحدثي بالعربية الطبيعية الواضحة ما لم يطلب المستخدم لغة أخرى.
شخصيتك: ذكية، هادئة، واثقة، ماكرة بخفة، دافئة، وفضولية. لا تتحدثي كمساعد آلي.
في الدردشة العادية اجعلي الرد غالبًا من 1 إلى 4 جمل. في الأسئلة العلمية أو العملية أعطي إجابة صحيحة ومباشرة أولًا ثم التفصيل عند الحاجة.
لا تتظاهري بقدرات خارقة في العالم الحقيقي، ولا تختلقي معلومات. إذا لم تعرفي فقولي ذلك بوضوح.
حافظي على طابع سيرافا دون التضحية بصحة الإجابة. المحتوى غير الصريح هو الوضع الافتراضي.
`.trim();

function extractText(data) {
  const parts = data?.candidates?.[0]?.content?.parts ?? [];
  return parts
    .map((part) => typeof part?.text === "string" ? part.text : "")
    .filter(Boolean)
    .join("\n")
    .trim();
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");

  if (req.method === "GET") {
    return res.status(200).json({
      ok: true,
      service: "serava-brain",
      provider: "gemini",
      version: "1.1.0",
      model: process.env.GEMINI_MODEL || "gemini-3.7-flash"
    });
  }

  if (req.method !== "POST") {
    res.setHeader("Allow", "GET, POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ error: "GEMINI_API_KEY_not_configured" });
  }

  const input = String(req.body?.input || "").trim();
  if (!input) return res.status(400).json({ error: "missing_input" });
  if (input.length > 12000) return res.status(413).json({ error: "input_too_large" });

  const model = process.env.GEMINI_MODEL || "gemini-3.7-flash";
  const endpoint =
    "https://generativelanguage.googleapis.com/v1beta/models/" +
    encodeURIComponent(model) +
    ":generateContent";

  try {
    const upstream = await fetch(endpoint, {
      method: "POST",
      headers: {
        "x-goog-api-key": apiKey,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: PERSONA }]
        },
        contents: [{
          role: "user",
          parts: [{ text: input }]
        }],
        generationConfig: {
          maxOutputTokens: 500,
          temperature: 0.85,
          topP: 0.95
        }
      })
    });

    const data = await upstream.json();

    if (!upstream.ok) {
      console.error(
        "Gemini error",
        upstream.status,
        data?.error?.message || data
      );
      return res.status(502).json({
        error: "upstream_error",
        status: upstream.status,
        message: data?.error?.message || "Gemini request failed"
      });
    }

    const outputText = extractText(data);
    if (!outputText) {
      return res.status(502).json({ error: "empty_model_response" });
    }

    return res.status(200).json({
      output_text: outputText,
      provider: "gemini",
      model,
      finish_reason: data?.candidates?.[0]?.finishReason || null
    });
  } catch (error) {
    console.error("Serava backend failure", error);
    return res.status(500).json({ error: "server_error" });
  }
}
