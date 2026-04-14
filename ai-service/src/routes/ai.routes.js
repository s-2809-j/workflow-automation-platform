import express from "express";
import crypto from "crypto";
import { z } from "zod";
import { Workflow } from "../models/workflow.model.js";
import { callLLM } from "../services/llm.service.js";
import { workflowPrompt } from "../utils/promptTemplates.js";
import { computeConfidenceScore } from "./../services/confidence.services.js";

const router = express.Router();

// ---- Helpers ----
const toBool = (v) => typeof v === "string" && ["true", "1", "yes"].includes(v.toLowerCase());
const MOCK_MODE = toBool(process.env.MOCK_MODE);
const MAX_TEXT_LEN = Number(process.env.MAX_TEXT_LEN || 2000);

// ---- Schema ----
const ActionSchema = z.union([
  z.string().min(1),
  z.object({
    description: z.string().min(1),
    type: z.string().optional(),
  }).passthrough(),
]);

const WorkflowSchema = z.object({
  intent: z.string().min(1),
  trigger: z.record(z.any()).default({}),
  actions: z.array(ActionSchema).min(1),
  entities: z.record(z.any()).default({}),
}).passthrough();

// ---- Utils ----
function tryExtractJsonObject(text) {
  const raw = String(text || "").trim();

  try {
    return JSON.parse(raw);
  } catch (_) {}

  const match = raw.match(/\{[\s\S]*\}/);
  if (match) {
    return JSON.parse(match[0]);
  }

  throw new Error("AI_INVALID_JSON");
}

// ======================================================
// 🔹 CONTRACT 1 → /api/v1/workflows/generate
// ======================================================
router.post("/workflows/generate", async (req, res) => {
  const requestId = crypto.randomUUID();

  try {
    const text = req.body?.prompt;

    if (!text) {
      return res.status(400).json({
        status: "error",
        message: "prompt is required",
        requestId,
      });
    }

    const prompt = workflowPrompt(text);
    const aiText = await callLLM(prompt);

    const workflowObj = tryExtractJsonObject(aiText);

    const parsed = WorkflowSchema.safeParse(workflowObj);
    if (!parsed.success) {
      return res.status(502).json({
        status: "error",
        message: "Invalid AI schema",
        requestId,
      });
    }

    const confidence = computeConfidenceScore(parsed.data, text);

    // ✅ Convert actions → steps (IMPORTANT FIX)
    const steps = parsed.data.actions.map((action, index) => ({
      id: `step-${index + 1}`,
      stepType: "ACTION",
      name: typeof action === "string" ? action : action.description,
      config: {},
      dependsOn: index === 0 ? [] : [`step-${index}`],
    }));

    return res.json({
      workflow: {
        name: parsed.data.intent,
        steps,
      },
      confidence: confidence.score,
    });

  } catch (err) {
    return res.status(500).json({
      status: "error",
      message: err.message,
    });
  }
});

// ======================================================
// 🔹 OLD API (Keep for your testing)
// ======================================================
router.post("/generate-workflow", async (req, res) => {
  const requestId = crypto.randomUUID();

  try {
    const text = req.body?.text;

    if (!text) {
      return res.status(400).json({ message: "Text required" });
    }

    const prompt = workflowPrompt(text);
    const aiText = await callLLM(prompt);

    const workflowObj = tryExtractJsonObject(aiText);

    const parsed = WorkflowSchema.safeParse(workflowObj);
    if (!parsed.success) {
      return res.status(502).json({ message: "Invalid schema" });
    }

    const confidence = computeConfidenceScore(parsed.data, text);

    return res.json({
      status: "success",
      workflow: parsed.data,
      confidence,
      requestId,
    });

  } catch (err) {
    return res.status(500).json({ message: err.message });
  }
});

export default router;