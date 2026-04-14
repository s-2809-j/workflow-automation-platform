export function workflowPrompt(userInput) {
  return `
You convert workflow instructions into STRICT JSON.

CRITICAL RULES (MUST FOLLOW):
1. Return ONLY valid JSON.
2. No markdown.
3. No code fences.
4. No explanation text.
5. No comments.
6. Output must be directly parseable using JSON.parse().
7. Use EXACTLY these top-level keys and no others:
   - intent
   - trigger
   - actions
   - entities

User instruction:
${JSON.stringify(userInput)}

Required JSON format:
{
  "intent": "short sentence describing what user wants",
  "trigger": {},
  "actions": [],
  "entities": {}
}

Field Rules:
- intent: short summary sentence.
- trigger: must ALWAYS be an object (never null). If no trigger found, return {}.

- actions: MUST follow these STRICT rules:
  1. Must be a NON-EMPTY array (minimum 1 step).
  2. ALWAYS break the workflow into MULTIPLE logical steps if possible.
  3. DO NOT combine multiple operations into a single step.
  4. Each action must represent ONE clear operation.
  5. Prefer 2–5 steps for most workflows.
  6. Use natural execution order.

  Examples of good actions:
  - "Fetch employee list"
  - "Filter active employees"
  - "Prepare email content"
  - "Send email to employees"

  BAD example (DO NOT DO THIS):
  - "Fetch employees and send email"

  Each step can be:
    - a simple string
    OR
    - { "type": "string (optional)", "description": "string" }

- entities: extract app names, services, tools, people, places if present. If none, return {}.

Important:
- Do NOT wrap JSON in backticks.
- Do NOT add extra fields.
- Ensure valid JSON syntax.
- Prefer multiple meaningful steps over a single generic step.

Return ONLY the JSON object.
`.trim();
}