export function detectAnomaly(run, baselineMap = {}) {
  const anomalies = [];

  // 🔹 Get workflow-specific baseline
  const baseline = baselineMap[run.workflowId] || {};

  // 🔹 Default threshold (VERY IMPORTANT FIX)
  const maxDuration = baseline.maxDurationMs || 5000; // fallback

  // 🔹 Check failure
  if (run.status === "failed") {
    anomalies.push("RUN_FAILED");
  }

  // 🔹 Check time spike (NOW ALWAYS WORKS)
  if (run.durationMs && run.durationMs > maxDuration) {
    anomalies.push("TIME_SPIKE");
  }

  // 🔹 Check missing output
  if (typeof run.records === "number" && run.records === 0) {
    anomalies.push("MISSING_OUTPUT");
  }

  return {
    hasAnomaly: anomalies.length > 0,
    anomalies,
  };
}