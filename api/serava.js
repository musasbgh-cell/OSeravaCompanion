import crypto from "node:crypto";

const PERSONA = `
أنتِ سيرافا (Serava)، شخصية افتراضية خيالية بالغة من نوع شيطانة إغواء، بهوية أصلية.
تحدثي بالعربية الطبيعية الواضحة ما لم يطلب المستخدم لغة أخرى.
شخصيتك: ذكية، هادئة، واثقة، ماكرة بخفة، دافئة، وفضولية. لا تتحدثي كمساعد آلي.
في الدردشة العادية اجعلي الرد غالبًا من 1 إلى 4 جمل. في الأسئلة العلمية أو العملية أعطي إجابة صحيحة ومباشرة أولًا ثم التفصيل عند الحاجة.
لا تتظاهري بقدرات خارقة في العالم الحقيقي، ولا تختلقي معلومات. إذا لم تعرفي فقولي ذلك بوضوح.
حافظي على طابع سيرافا دون التضحية بصحة الإجابة. المحتوى غير الصريح هو الوضع الافتراضي.
`.trim();

function extractText(data) {
  const parts = [];
  for (const item of data?.output ?? []) {
    if (item?.type !== "message") continue;
    for (const c of item?.content ?? []) {
      if (c?.type === "output_text" && typeof c.text === "string") parts.push(c.text);
    }
  }
  return parts.join("\n").trim();
}

function safetyId(req) {
  const raw = String(req.headers["x-serava-client"] || "anonymous");
  const salt = process.env.SERAVA_SAFETY_SALT || "serava-v1";
  return crypto.createHash("sha256").update(salt + ":" + raw).digest("hex");
}

export default async function handler(req, res) {
  if (req.method === "GET") {
    return res.status(200).json({
      ok: true,
      service: "serava-brain",
      version: "1.0.0",
      model: process.env.OPENAI_MODEL || "gpt-6-luna"
    });
  }

  if (req.method !== "POST") {
    res.setHeader("Allow", "GET, POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ error: "OPENAI_API_KEY_not_configured" });
  }

  const input = String(req.body?.input || "").trim();
  if (!input) return res.status(400).json({ error: "missing_input" });
  if (input.length > 12000) return res.status(413).json({ error: "input_too_large" });

  try {
    const upstream = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || "gpt-6-luna",
        instructions: PERSONA,
        input,
        max_output_tokens: 500,
        reasoning: { effort: "low" },
        safety_identifier: safetyId(req),
        store: false
      })
    });

    const data = await upstream.json();
    if (!upstream.ok) {
      console.error("OpenAI error", upstream.status, data?.error?.message || data);
      return res.status(502).json({
        error: "upstream_error",
        status: upstream.status,
        message: data?.error?.message || "OpenAI request failed"
      });
    }

    const outputText = extractText(data);
    if (!outputText) {
      return res.status(502).json({ error: "empty_model_response" });
    }

    return res.status(200).json({
      output_text: outputText,
      response_id: data.id || null,
      model: data.model || process.env.OPENAI_MODEL || "gpt-6-luna"
    });
  } catch (error) {
    console.error("Serava backend failure", error);
    return res.status(500).json({ error: "server_error" });
  }
}
