import express from "express";
import mongoose from "mongoose";
import { Workflow } from "./../models/workflow.model.js";

const router = express.Router();

router.get("/execution/workflows/:id", async (req, res) => {
  const { id } = req.params;

  // 1) Validate MongoDB ObjectId
  if (!mongoose.isValidObjectId(id)) {
    return res.status(400).json({
      status: "error",
      code: "VALIDATION_ERROR",
      message: "Invalid workflow id.",
      requestId: req.requestId,
    });
  }

  // 2) Fetch workflow
  const wf = await Workflow.findById(id);

  // 3) If not found
  if (!wf) {
    return res.status(404).json({
      status: "error",
      code: "NOT_FOUND",
      message: "Workflow not found.",
      requestId: req.requestId,
    });
  }

  // 4) Ensure workflow is active before execution
  if (wf.status !== "active") {
    return res.status(400).json({
      status: "error",
      code: "WORKFLOW_NOT_ACTIVE",
      message: "Workflow is not active and cannot be executed.",
      requestId: req.requestId,
    });
  }

  // 5) Return execution contract (DTO)
  return res.json({
    status: "success",
    contractVersion: 1, // version of the execution contract
    contract: {
      workflowId: wf._id,
      status: wf.status,
      intent: wf.intent,
      trigger: wf.trigger,
      actions: wf.actions,
      entities: wf.entities,
      provider: wf.provider,
      model: wf.model,
      createdAt: wf.createdAt,
      updatedAt: wf.updatedAt,
    },
    requestId: req.requestId,
  });
});

export default router;