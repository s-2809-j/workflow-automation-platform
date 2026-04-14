export function computeConfidenceScore(workflow, sourceText = "") {
  let score = 1.0;
  const reasons = [];

  const intent = String(workflow.intent || "").trim().toLowerCase();
  const actions = Array.isArray(workflow.actions) ? workflow.actions : [];
  const trigger = workflow.trigger || {};
  const source = String(sourceText || "").trim().toLowerCase();

  const genericIntentPatterns = [
    "do something",
    "generic action",
    "perform a generic action",
    "unspecified",
    "undefined",
    "handle an empty",
  ];

  const genericActionPatterns = [
    "do something",
    "execute the unspecified task",
    "prompt user",
    "generic",
    "placeholder",
    "undefined",
  ];

  // 1) Intent checks
  if (!intent) {
    score -= 0.3;
    reasons.push("Intent is missing");
  } else {
    if (intent.length < 12) {
      score -= 0.15;
      reasons.push("Intent is too short");
    }

    if (genericIntentPatterns.some((pattern) => intent.includes(pattern))) {
      score -= 0.2;
      reasons.push("Intent appears too generic");
    }
  }

  // 2) Actions checks
  if (!actions.length) {
    score -= 0.4;
    reasons.push("No actions found");
  } else {
    let vagueActions = 0;

    for (const action of actions) {
      const actionText =
        typeof action === "string"
          ? action.toLowerCase()
          : String(action?.description || "").toLowerCase();

      if (!actionText || actionText.length < 8) {
        vagueActions++;
      }

      if (genericActionPatterns.some((pattern) => actionText.includes(pattern))) {
        vagueActions++;
      }
    }

    if (vagueActions > 0) {
      score -= Math.min(0.3, vagueActions * 0.1);
      reasons.push("One or more actions appear vague or placeholder-like");
    }
  }

  // 3) Trigger checks
  if (!trigger || Object.keys(trigger).length === 0) {
    score -= 0.2;
    reasons.push("Trigger is missing or empty");
  }

  // 4) Source text checks
  if (!source || source.length < 5) {
    score -= 0.2;
    reasons.push("Source input is too short");
  }

  if ([".", "..", "...", "do something", "start", "workflow"].includes(source)) {
    score -= 0.2;
    reasons.push("Source input is too vague");
  }

  score = Math.max(0, Math.min(1, score));

  return {
    score: Number(score.toFixed(2)),
    pass: score >= 0.7,
    reasons,
  };
}