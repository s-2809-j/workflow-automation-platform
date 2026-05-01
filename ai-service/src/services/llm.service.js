import { GoogleGenerativeAI } from "@google/generative-ai";

const AI_TIMEOUT_MS = Number(process.env.AI_TIMEOUT_MS || 25000);
const LLM_PROVIDER = process.env.LLM_PROVIDER || "gemini";

if (!process.env.GEMINI_API_KEY) {
  console.warn("⚠️ GEMINI_API_KEY is missing in .env");
}

let genAI;
if (LLM_PROVIDER === "gemini") {
  genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
}

function timeoutPromise(ms) {
  return new Promise((_, reject) =>
    setTimeout(
      () => reject(Object.assign(new Error("AI request timed out"), { code: "AI_TIMEOUT" })),
      ms
    )
  );
}

// systemInstruction is now a second parameter — passed from ai.routes.js
export async function callLLM(userPrompt, systemInstruction = null) {
  if (LLM_PROVIDER !== "gemini") {
    throw Object.assign(new Error("Unsupported LLM provider"), {
      code: "AI_PROVIDER_ERROR",
    });
  }
  try {
    const model = genAI.getGenerativeModel({
      model: process.env.GEMINI_MODEL || "gemini-1.5-flash",
      systemInstruction: systemInstruction ||
        "You are a workflow automation assistant. Return ONLY valid JSON. No markdown, no backticks, no explanation.",
      generationConfig: {
        temperature: 0.2,
        responseMimeType: "application/json",
      },
    });

    const result = await Promise.race([
      model.generateContent(userPrompt),
      timeoutPromise(AI_TIMEOUT_MS)
    ]);

    return result.response.text();

  } catch (error) {
    console.error("Gemini raw error:", error);
    console.error("Gemini message:", error?.message);
    console.error("Gemini status:", error?.status);

    if (error?.code === "AI_TIMEOUT") throw error;

    const status = Number(error?.status);
    const msg = String(error?.message || "");

    if (status === 404 || msg.toLowerCase().includes("not found") || msg.toLowerCase().includes("listmodels")) {
      throw Object.assign(new Error("Gemini model not found / not supported for generateContent"), {
        code: "AI_MODEL_NOT_FOUND",
      });
    }

    if (status === 429 || msg.toLowerCase().includes("quota") || msg.toLowerCase().includes("rate")) {
      throw Object.assign(new Error("Gemini quota exceeded / rate-limited"), {
        code: "AI_QUOTA",
      });
    }

    if (status === 401 || status === 403) {
      throw Object.assign(new Error("Gemini authentication/permission error (check API key & project)"), {
        code: "AI_AUTH_ERROR",
      });
    }

    if (status === 503 || msg.toLowerCase().includes("unavailable") || msg.toLowerCase().includes("high demand")) {
      throw Object.assign(new Error("Gemini service temporarily unavailable, please retry later"), {
        code: "AI_SERVICE_UNAVAILABLE",
      });
    }

    throw Object.assign(new Error(msg || "Gemini call failed"), {
      code: "AI_CALL_FAILED",
    });
  }
}