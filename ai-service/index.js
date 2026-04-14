const express = require('express');
const app = express();
app.use(express.json());

// Health Check
app.get('/health', (req, res) => {
  res.json({ status: 'UP' });
});

// Contract 1: Analyze Execution Log
app.post('/api/v1/execution/logs', (req, res) => {
  const { workflowId, status, durationMs, errorType } = req.body;

  console.log('[Mock AI] Received log → workflowId=' + workflowId + ', status=' + status + ', errorType=' + errorType);

  const hasAnomaly = status === 'failed';

  const retryMap = {
    TIMEOUT:             { shouldRetry: true,  strategy: 'exponential', maxRetries: 3, baseDelayMs: 3000 },
    NETWORK_ERROR:       { shouldRetry: true,  strategy: 'exponential', maxRetries: 3, baseDelayMs: 3000 },
    SERVICE_UNAVAILABLE: { shouldRetry: true,  strategy: 'linear',      maxRetries: 2, baseDelayMs: 5000 },
    AI_QUOTA:            { shouldRetry: false, strategy: null,           maxRetries: 0, baseDelayMs: 0    },
    AI_AUTH_ERROR:       { shouldRetry: false, strategy: null,           maxRetries: 0, baseDelayMs: 0    },
    INVALID_INPUT:       { shouldRetry: false, strategy: null,           maxRetries: 0, baseDelayMs: 0    },
    VALIDATION_ERROR:    { shouldRetry: false, strategy: null,           maxRetries: 0, baseDelayMs: 0    },
  };

  const retryDecision = retryMap[errorType] || {
    shouldRetry: true, strategy: 'exponential', maxRetries: 3, baseDelayMs: 3000
  };

  const nextAction = !hasAnomaly
    ? 'NO_ACTION'
    : retryDecision.shouldRetry ? 'EXECUTE_RETRY' : 'ESCALATE';

  res.json({
    status: 'success',
    anomaly: {
      hasAnomaly: hasAnomaly,
      anomalies: hasAnomaly ? [errorType || 'RUN_FAILED'] : [],
    },
    retryDecision: retryDecision,
    nextAction: nextAction,
  });
});

// Contract 2: Generate Workflow from Text
app.post('/api/v1/generate-workflow', (req, res) => {
  const input = req.body;
  console.log('[Mock AI] Generate workflow request received');

  res.json({
    requestId: 'mock-req-001',
    workflow: {
      intent: 'mock_intent',
      trigger: { type: 'manual' },
      actions: [{ type: 'mock_action', params: {} }],
      entities: [],
    },
    confidence: { score: 0.85, pass: true, reasons: [] },
    status: 'active',
  });
});

// Start Server
const PORT = 3001;
app.listen(PORT, function() {
  console.log('Mock AI Service running on http://localhost:' + PORT);
  console.log('Health check: http://localhost:' + PORT + '/health');
});