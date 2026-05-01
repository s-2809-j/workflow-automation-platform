import express from "express";
import crypto from "crypto";
import { z } from "zod";
import { callLLM } from "../services/llm.service.js";
import { workflowPrompt } from "../utils/promptTemplates.js";
import { computeConfidenceScore } from "../services/confidence.services.js";

const router = express.Router();

// ---- Schema ----
const StepSchema = z.object({
  id:        z.string().min(1),
  stepType:  z.enum(["HTTP","LOG","DELAY","DATABASE","SCRIPT","EMAIL","WEBHOOK","ACTION"]),
  name:      z.string().optional().default("Unnamed Step"),
  config:    z.record(z.any()).default({}),
  dependsOn: z.array(z.string()).default([]),
}).passthrough();

const WorkflowSchema = z.object({
  name:  z.string().min(1),
  steps: z.array(StepSchema).min(1),
}).passthrough();

// ---- Utils ----

// Handles bare arrays AND objects from Gemini
function tryExtractJsonObject(text) {
  const raw = String(text || "").trim();
  try { return JSON.parse(raw); } catch (_) {}
  // Try bare array first
  const arrMatch = raw.match(/\[[\s\S]*\]/);
  if (arrMatch) {
    try { return JSON.parse(arrMatch[0]); } catch (_) {}
  }
  // Try object
  const objMatch = raw.match(/\{[\s\S]*\}/);
  if (objMatch) {
    try { return JSON.parse(objMatch[0]); } catch (_) {}
  }
  throw new Error("AI_INVALID_JSON");
}

// Normalizes all Gemini response shapes into { name, steps[] }
function normalizeGeminiOutput(parsed_raw) {

  // Shape 1: bare array  →  [ { id, stepType, ... } ]
  if (Array.isArray(parsed_raw)) {
    return {
      name: "Automated Workflow",
      steps: parsed_raw.map((step, index) => normalizeStep(step, index)),
    };
  }

  let rawWorkflow = parsed_raw;

  // Shape 2: { workflow: { name, steps } }
  if (
    parsed_raw.workflow &&
    typeof parsed_raw.workflow === "object" &&
    !Array.isArray(parsed_raw.workflow)
  ) {
    rawWorkflow = parsed_raw.workflow;
  }

  // Shape 3: { workflow: [ ...steps ] }
  if (Array.isArray(parsed_raw.workflow)) {
    return {
      name: parsed_raw.name || parsed_raw.intent || "Automated Workflow",
      steps: parsed_raw.workflow.map((step, index) => normalizeStep(step, index)),
    };
  }

  // Shape 4: { name, steps } or { intent, actions } — standard
  const name =
    rawWorkflow.name    ||
    rawWorkflow.intent  ||
    parsed_raw.name     ||
    parsed_raw.intent   ||
    "Automated Workflow";

  const rawSteps =
    Array.isArray(rawWorkflow.steps)   ? rawWorkflow.steps   :
    Array.isArray(rawWorkflow.actions) ? rawWorkflow.actions :
    Array.isArray(parsed_raw.steps)    ? parsed_raw.steps    :
    Array.isArray(parsed_raw.actions)  ? parsed_raw.actions  :
    [];

  return {
    name,
    steps: rawSteps.map((step, index) => normalizeStep(step, index)),
  };
}

function normalizeStep(step, index) {
  // Handle case where step is a plain string (old workflowPrompt actions)
  if (typeof step === "string") {
    return {
      id:        `step-${index + 1}`,
      stepType:  "ACTION",
      name:      step,
      config:    {},
      dependsOn: index === 0 ? [] : [`step-${index}`],
    };
  }
  return {
    ...step,
    id:        step.id        || `step-${index + 1}`,
    name:      step.name      || step.id || `Step ${index + 1}`,
    stepType:  step.stepType  || "ACTION",
    config:    step.config    || {},
    dependsOn: Array.isArray(step.dependsOn) ? step.dependsOn : [],
  };
}
function upgradeActionSteps(steps) {
  return steps.map((step, index) => {
    if (step.stepType !== "ACTION") return step;

    const name = (step.name || "").toLowerCase();
    const config = step.config || {};

    // Upgrade to EMAIL
    if (name.includes("email") || name.includes("send") || name.includes("notify") || name.includes("distribute")) {
      return {
        ...step,
        stepType: "EMAIL",
        config: {
          to: config.to || "team@company.com",
          subject: config.subject || step.name,
          body: config.body || `Automated notification: ${step.name}`,
          isHtml: false,
          ...config,
        },
      };
    }

    // Upgrade to HTTP
    if (name.includes("fetch") || name.includes("retrieve") || name.includes("get") || name.includes("api") || name.includes("pull")) {
      return {
        ...step,
        stepType: "HTTP",
        config: {
          url: config.url || "https://api.example.com/data",
          method: "GET",
          ...config,
        },
      };
    }

    // Upgrade to DATABASE
    if (name.includes("fetch") && (name.includes("user") || name.includes("data") || name.includes("record"))) {
      return {
        ...step,
        stepType: "DATABASE",
        config: {
          query: config.query || "SELECT * FROM users WHERE active = true",
          queryType: "SELECT",
          ...config,
        },
      };
    }

    // Upgrade to SCRIPT
    if (name.includes("generate") || name.includes("compute") || name.includes("process") || name.includes("calculate") || name.includes("analyze")) {
      return {
        ...step,
        stepType: "SCRIPT",
        config: {
          script: config.script || `// Auto-generated: ${step.name}\nconst result = { step: '${step.name}', status: 'completed' };\nresult;`,
          ...config,
        },
      };
    }

    // Upgrade to LOG
    if (name.includes("log") || name.includes("record") || name.includes("track")) {
      return {
        ...step,
        stepType: "LOG",
        config: {
          message: config.message || `${step.name} completed`,
          level: "INFO",
          ...config,
        },
      };
    }

    // Upgrade to WEBHOOK
    if (name.includes("webhook") || name.includes("trigger") || name.includes("alert")) {
      return {
        ...step,
        stepType: "WEBHOOK",
        config: {
          url: config.url || "https://hooks.example.com/notify",
          method: "POST",
          payload: config.payload || `{"event": "${step.name}"}`,
          ...config,
        },
      };
    }

    // Keep as ACTION with a LOG fallback so it always succeeds
    return {
      ...step,
      stepType: "LOG",
      config: {
        message: `${step.name} executed`,
        level: "INFO",
      },
    };
  });
}
// ======================================================
// CONTRACT → /api/v1/workflows/generate  (called by Java)
// ======================================================
router.post("/workflows/generate", async (req, res) => {
  console.log("systemPrompt received:", !!req.body?.systemPrompt);
  console.log("prompt received:", req.body?.prompt);

  const requestId    = crypto.randomUUID();
  const text         = req.body?.prompt;
  const systemPrompt = req.body?.systemPrompt;

  if (!text) {
    return res.status(400).json({
      status: "error", message: "prompt is required", requestId,
    });
  }
  if (!systemPrompt) {
    return res.status(400).json({
      status: "error", message: "systemPrompt is required", requestId,
    });
  }

  try {
    // systemPrompt goes to Gemini as systemInstruction (via llm.service.js)
    const aiText     = await callLLM(text, systemPrompt);
    const parsed_raw = tryExtractJsonObject(aiText);

    console.log("Gemini raw output:", JSON.stringify(parsed_raw, null, 2));

    const normalized = normalizeGeminiOutput(parsed_raw);
    normalized.steps = upgradeActionSteps(normalized.steps);

    console.log("Normalized:", JSON.stringify(normalized, null, 2));

    const parsed = WorkflowSchema.safeParse(normalized);

    if (!parsed.success) {
      console.error("Schema validation failed:",
        JSON.stringify(parsed.error.format(), null, 2));
      return res.status(502).json({
        status: "error", message: "Invalid AI schema", requestId,
      });
    }

    return res.json({
      workflow:   parsed.data,
      confidence: 0.9,
    });

  } catch (err) {
    console.error("Generate workflow error:", err.message);
    return res.status(500).json({
      status: "error", message: err.message,
    });
  }
});

// ======================================================
// OLD API — kept for direct testing only
// ======================================================
router.post("/generate-workflow", async (req, res) => {
  const requestId = crypto.randomUUID();
  try {
    const text = req.body?.text;
    if (!text) {
      return res.status(400).json({ message: "Text required" });
    }
    const prompt     = workflowPrompt(text);
    const aiText     = await callLLM(prompt);
    const parsed_raw = tryExtractJsonObject(aiText);
    const normalized = normalizeGeminiOutput(parsed_raw);
    const confidence = computeConfidenceScore(normalized, text);
    return res.json({
      status: "success",
      workflow: normalized,
      confidence,
      requestId,
    });
  } catch (err) {
    return res.status(500).json({ message: err.message });
  }
});

export default router;