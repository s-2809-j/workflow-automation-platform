import mongoose from "mongoose";

const ALLOWED_STATUSES = ["ACTIVE", "INACTIVE", "PENDING"];

const WorkflowSchema = new mongoose.Schema(
  {
    intent: { type: String, required: true },
    trigger: { type: Object, default: {} },
    actions: { type: Array, required: true },
    entities: { type: Object, default: {} },

    organizationId: { type: String, index: true },

    sourceText: { type: String, default: "" },
    provider: { type: String, default: "gemini" },
    model: { type: String, default: "" },
    status: {
      type: String,
      enum: ALLOWED_STATUSES,
      default: "PENDING",
      index: true,
    },
  },
  { timestamps: true }
);

export const Workflow = mongoose.model("Workflow", WorkflowSchema);
export { ALLOWED_STATUSES };
