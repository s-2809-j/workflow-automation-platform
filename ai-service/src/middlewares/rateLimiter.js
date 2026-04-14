import rateLimit from "express-rate-limit";

// 🔥 AI endpoint (STRICT)
export const aiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 10,
  message: "Too many AI requests, please try again later"
});

// 📊 Execution logs (MEDIUM)
export const logLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  message: "Too many log submissions"
});

// 📦 General APIs (RELAXED)
export const generalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 100,
  message: "Too many requests"
});