import { z } from "zod";
import mongoose from "mongoose";

export const executionLogSchema = z.object({
  workflowId: z
    .string()
    .refine((val) => mongoose.Types.ObjectId.isValid(val), {
      message: "Invalid workflowId",
    }),

  runId: z.string().min(1, "runId is required"),

  status: z.enum(["success", "failed"]),

  errorType: z.string().optional(),

  durationMs: z.number().nonnegative().optional(),

  records: z.number().nonnegative().optional(),

  meta: z.object({}).optional(),
}).strict();