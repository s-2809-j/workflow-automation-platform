import express from "express";
import { ExecutionLog } from "../models/executionLog.model.js";
import { executionLogSchema } from "../schemas/executionLog.schema.js";
import { detectAnomaly } from "../services/anomaly.services.js";
import { decideRetry } from "../services/retryDecision.services.js";

const router = express.Router();

// 🔹 Baseline
const baselineMap = {
  "64f8c2a9e13b2c0012a12345": {
    maxDurationMs: 60000,
  },
};

// ======================================================
// 🔹 CONTRACT 2 → /api/v1/analyze
// ======================================================
router.post("/analyze", async (req, res) => {
  try {
    const { workflowId, errorType, durationMs } = req.body;

    if (!workflowId || !errorType) {
      return res.status(400).json({
        status: "error",
        message: "workflowId and errorType required",
      });
    }

    const run = {
      workflowId,
      status: "failed",
      durationMs: durationMs || 0,
      errorType,
    };

    const anomaly = detectAnomaly(run, baselineMap);
    const retryDecision = decideRetry(errorType);

    return res.json({
      shouldRetry: retryDecision.shouldRetry,
      reason: retryDecision.reason || "Decision based on error type",
    });

  } catch (err) {
    return res.status(500).json({
      status: "error",
      code: "AI_ANALYZE_FAILED",
      message: err.message,
    });
  }
});

// ======================================================
// 🔹 EXISTING LOG API (keep it)
// ======================================================
router.post("/execution/logs", async (req, res, next) => {
  try {
    const validatedData = executionLogSchema.parse(req.body);

    const saved = await ExecutionLog.create(validatedData);

    const anomaly = detectAnomaly(validatedData, baselineMap);

    const retryDecision = anomaly.hasAnomaly
      ? decideRetry(validatedData.errorType)
      : null;

    let nextAction = "NO_ACTION";

    if (retryDecision?.shouldRetry) nextAction = "EXECUTE_RETRY";
    else if (retryDecision && !retryDecision.shouldRetry) nextAction = "ESCALATE";

    res.json({
      status: "success",
      anomaly,
      retryDecision,
      nextAction,
    });

  } catch (err) {
    next(err);
  }
});

export default router;