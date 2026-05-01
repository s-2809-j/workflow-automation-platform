import mongoose from "mongoose";
import { Workflow } from "../models/workflow.model.js";
import { z } from "zod";

// ✅ Helper: Convert UTC → IST
function convertToIST(date) {
  return new Date(date).toLocaleString("en-IN", {
    timeZone: "Asia/Kolkata",
  });
}

// ✅ Query validation schema
const listWorkflowsQuerySchema = z.object({
  page: z.coerce.number().int().min(1).optional(),
  limit: z.coerce.number().int().min(1).max(100).optional(),

  status: z.enum(["draft", "active", "paused"]).optional(),
  provider: z.string().min(1).optional(),
  model: z.string().min(1).optional(),

  from: z.string().min(1).optional(),
  to: z.string().min(1).optional(),
});

// ✅ PATCH validation schema
const updateWorkflowBodySchema = z
  .object({
    status: z.enum(["draft", "active", "paused"]).optional(),
    intent: z.string().min(1).optional(),
    trigger: z.any().optional(),
    actions: z.array(z.any()).optional(),
    entities: z.any().optional(),
  })
  .strict();

// ==============================
// GET ALL WORKFLOWS
// ==============================
export async function listWorkflows(req, res) {
  try {
    const parsed = listWorkflowsQuerySchema.safeParse(req.query);
    if (!parsed.success) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: "Invalid query parameters.",
        errors: parsed.error.issues,
      });
    }

    const q = parsed.data;

    const page = Math.max(Number(q.page || 1), 1);
    const limit = Math.min(Math.max(Number(q.limit || 20), 1), 100);
    const skip = (page - 1) * limit;

    const { status, provider, model, from, to } = q;

    const filter = {};
    if (status) filter.status = status;
    if (provider) filter.provider = provider;
    if (model) filter.model = model;

    if (from || to) {
      filter.createdAt = {};

      if (from) {
        const dFrom = new Date(from);
        if (isNaN(dFrom.getTime())) {
          return res.status(400).json({
            status: "error",
            code: "VALIDATION_ERROR",
            message: "Invalid 'from' date.",
          });
        }
        filter.createdAt.$gte = dFrom;
      }

      if (to) {
        const dTo = new Date(to);
        if (isNaN(dTo.getTime())) {
          return res.status(400).json({
            status: "error",
            code: "VALIDATION_ERROR",
            message: "Invalid 'to' date.",
          });
        }
        filter.createdAt.$lte = dTo;
      }
    }

    const [workflows, total] = await Promise.all([
      Workflow.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit),
      Workflow.countDocuments(filter),
    ]);

    // ✅ Convert timestamps to IST
    const formattedWorkflows = workflows.map((wf) => ({
      ...wf.toObject(),
      createdAt: convertToIST(wf.createdAt),
      updatedAt: convertToIST(wf.updatedAt),
    }));

    return res.json({
      status: "success",
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit),
      count: formattedWorkflows.length,
      workflows: formattedWorkflows,
    });
  } catch (err) {
    return res.status(500).json({
      status: "error",
      code: "DB_ERROR",
      message: err.message,
    });
  }
}

// ==============================
// GET WORKFLOW BY ID
// ==============================
export async function getWorkflowById(req, res) {
  try {
    const { id } = req.params;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: "Invalid workflow id.",
      });
    }

    const workflow = await Workflow.findById(id);
    if (!workflow) {
      return res.status(404).json({
        status: "error",
        code: "NOT_FOUND",
        message: "Workflow not found.",
      });
    }

    // ✅ Convert timestamps
    const formattedWorkflow = {
      ...workflow.toObject(),
      createdAt: convertToIST(workflow.createdAt),
      updatedAt: convertToIST(workflow.updatedAt),
    };

    return res.json({
      status: "success",
      workflow: formattedWorkflow,
    });
  } catch (err) {
    return res.status(500).json({
      status: "error",
      code: "DB_ERROR",
      message: err.message,
    });
  }
}

// ==============================
// PATCH WORKFLOW
// ==============================
export async function updateWorkflow(req, res) {
  try {
    const { id } = req.params;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: "Invalid workflow id.",
      });
    }

    const parsed = updateWorkflowBodySchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: "Invalid request body.",
        errors: parsed.error.issues,
      });
    }

    const allowed = ["status", "intent", "trigger", "actions", "entities"];
    const updates = {};

    for (const key of allowed) {
      if (key in parsed.data) {
        updates[key] = parsed.data[key];
      }
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: `No valid fields to update.`,
      });
    }

    const updated = await Workflow.findByIdAndUpdate(id, updates, { new: true });

    if (!updated) {
      return res.status(404).json({
        status: "error",
        code: "NOT_FOUND",
        message: "Workflow not found.",
      });
    }

    // ✅ Convert timestamps
    const formattedWorkflow = {
      ...updated.toObject(),
      createdAt: convertToIST(updated.createdAt),
      updatedAt: convertToIST(updated.updatedAt),
    };

    return res.json({
      status: "success",
      workflow: formattedWorkflow,
    });
  } catch (err) {
    return res.status(500).json({
      status: "error",
      code: "DB_ERROR",
      message: err.message,
    });
  }
}

// ==============================
// DELETE WORKFLOW
// ==============================
export async function deleteWorkflow(req, res) {
  try {
    const { id } = req.params;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({
        status: "error",
        code: "VALIDATION_ERROR",
        message: "Invalid workflow id.",
      });
    }

    const deleted = await Workflow.findByIdAndDelete(id);

    if (!deleted) {
      return res.status(404).json({
        status: "error",
        code: "NOT_FOUND",
        message: "Workflow not found.",
      });
    }

    // ✅ Convert timestamps
    const formattedWorkflow = {
      ...deleted.toObject(),
      createdAt: convertToIST(deleted.createdAt),
      updatedAt: convertToIST(deleted.updatedAt),
    };

    return res.json({
      status: "success",
      message: "Workflow deleted.",
      workflow: formattedWorkflow,
    });
  } catch (err) {
    return res.status(500).json({
      status: "error",
      code: "DB_ERROR",
      message: err.message,
    });
  }
}