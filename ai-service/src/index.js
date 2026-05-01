import "dotenv/config";
import express from "express";
import cors from "cors";
import helmet from "helmet";

// ✅ Routes (ONLY required + optional ones)
import aiRoutes from "./routes/ai.routes.js";
import executionLogsRoutes from "./routes/executionLogs.route.js";

// (Optional - keep if needed internally, won't affect integration)
import workflowRoutes from "./routes/workflow.routes.js";
import executionRoutes from "./routes/execution.routes.js";

// ✅ DB
import { connectDB } from "./config/db.js";

// ✅ Middlewares
import { requestIdMiddleware } from "./middlewares/requestId.middlewares.js";
import { errorHandlerMiddleware } from "./middlewares/errorHandler.middlewares.js";

// ✅ Rate limiters
import { aiLimiter, logLimiter, generalLimiter } from "./middlewares/rateLimiter.js";

const app = express();

// =====================================================
// 🔐 GLOBAL MIDDLEWARE
// =====================================================
app.use(helmet());
app.use(cors());
app.use(express.json());

// =====================================================
// ❤️ HEALTH CHECK (MUST MATCH CONTRACT EXACTLY)
// =====================================================
app.get("/health", (req, res) => {
  res.json({ status: "UP" });
});

// =====================================================
// 🧾 REQUEST TRACKING
// =====================================================
app.use(requestIdMiddleware);

// =====================================================
// 🚦 RATE LIMITING
// =====================================================

// AI workflow generation
app.use("/api/v1/generate-workflow", aiLimiter);

// Execution logs
app.use("/api/v1/execution/logs", logLimiter);

// General workflows
app.use("/api/v1/workflows", generalLimiter);

// =====================================================
// 📌 ROUTES (IMPORTANT)
// =====================================================

// ✅ REQUIRED CONTRACT ROUTES
app.use("/api/v1", aiRoutes);
app.use("/api/v1", executionLogsRoutes);

// ✅ OPTIONAL (safe to keep, won't break integration)
app.use("/api/v1", workflowRoutes);
app.use("/api/v1", executionRoutes);

// Legacy support (optional)
app.use("/api/ai", aiRoutes);

// =====================================================
// ❌ ERROR HANDLER (ALWAYS LAST)
// =====================================================
app.use(errorHandlerMiddleware);

// =====================================================
// 🚀 DB CONNECTION (NO SERVER START HERE)
// =====================================================
connectDB();

// =====================================================
// ✅ EXPORT APP (CRITICAL FOR ROOT index.js)
// =====================================================
export default app;