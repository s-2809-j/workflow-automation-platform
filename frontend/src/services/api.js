import axios from 'axios';

const API_URL = 'http://localhost:8080/api';

const api = axios.create({ baseURL: API_URL });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Auth ──────────────────────────────────────────
export const login = (email, password) =>
  api.post('/auth/login', { email, password });

// ── Workflows ─────────────────────────────────────
export const getWorkflows = () => api.get('/workflows');

export const createWorkflow = (data) =>
  api.post('/workflows', { ...data, status: 'ACTIVE' });

export const deleteWorkflow = (id) => api.delete(`/workflows/${id}`);

// ── Executions ────────────────────────────────────
export const executeWorkflow = (id) =>
  api.post(`/workflows/${id}/execute`);

export const getExecutions = (workflowId) =>
  api.get(`/workflows/${workflowId}/executions`);

export const getStepExecutions = (executionId) =>
  api.get(`/executions/${executionId}/steps`);

// ── Steps ─────────────────────────────────────────
// GET  /api/workflows/{workflowId}/steps
export const getSteps = (workflowId) =>
  api.get(`/workflows/${workflowId}/steps`);

// POST /api/workflows/{workflowId}/steps
// Body: { stepOrder, name, stepType, config (JsonNode), dependsOn (JsonNode) }
export const createStep = (workflowId, data) =>
  api.post(`/workflows/${workflowId}/steps`, data);

// PUT  /api/steps/{id}
export const updateStep = (stepId, data) =>
  api.put(`/steps/${stepId}`, data);

// DELETE /api/steps/{id}
export const deleteStep = (stepId) =>
  api.delete(`/steps/${stepId}`);

export default api;