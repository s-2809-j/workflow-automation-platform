import { callLLM } from './llm.service.js';

export async function generateEmailBody(workflowName, stepName, subject) {
  const prompt = `
You are generating an email body for a workflow automation system.

Workflow: "${workflowName || 'Automated Workflow'}"
Step: "${stepName || 'Email Notification'}"
Subject: "${subject || 'Notification'}"

Generate a professional, concise email body for this notification.
Return ONLY this JSON structure, nothing else:
{
  "body": "<the email body text here>",
  "isHtml": false
}
`;

  const raw = await callLLM(prompt,
    "You are an email content generator. Return ONLY valid JSON with keys: body (string), isHtml (boolean). No markdown, no backticks."
  );

  try {
    const parsed = JSON.parse(raw);
    return {
      body: parsed.body || '',
      isHtml: parsed.isHtml || false,
    };
  } catch {
    return { body: raw, isHtml: false };
  }
}