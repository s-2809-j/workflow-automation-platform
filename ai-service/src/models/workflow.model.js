import mongoose from "mongoose";

const WorkflowSchema = new mongoose.Schema(
  {
    intent: { type: String, required: true },
    trigger: { type: Object, default: {} },
    actions: { type: Array, required: true },
    entities: { type: Object, default: {} },

    // Helpful metadata
    sourceText: { type: String, default: "" },
    provider: { type: String, default: "gemini" },
    model: { type: String, default: "" },
    status:{
      type:String,
      enum:["draft","active","paused"],
      default:"draft",
      index:true,
    },
  },
  { timestamps: true }
);

export const Workflow = mongoose.model("Workflow", WorkflowSchema);