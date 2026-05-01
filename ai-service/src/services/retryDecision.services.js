export function decideRetry(errorType) {
  const temporary = [
    "TIMEOUT",
    "NETWORK_ERROR",
    "SERVICE_UNAVAILABLE",
    "AI_QUOTA",
    "RUN_FAILED" // 🔥 IMPORTANT FIX (connects anomaly → retry)
  ];

  const permanent = [
    "AI_AUTH_ERROR",
    "INVALID_INPUT",
    "VALIDATION_ERROR"
  ];

  if (temporary.includes(errorType)) {
    return {
      shouldRetry: true,
      reason: "TEMPORARY_ERROR",
      maxRetries: 3,
      strategy: "exponential",
      baseDelayMs: 30000,
    };
  }

  if (permanent.includes(errorType)) {
    return {
      shouldRetry: false,
      reason: "PERMANENT_ERROR",
      action: "escalate",
    };
  }

  return {
    shouldRetry: true,
    reason: "UNKNOWN_ERROR_FALLBACK",
    maxRetries: 1,
    strategy: "fixed",
    baseDelayMs: 15000,
  };
}