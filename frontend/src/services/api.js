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
export const getSteps = (workflowId) =>
  api.get(`/workflows/${workflowId}/steps`);
export const createStep = (workflowId, data) =>
  api.post(`/workflows/${workflowId}/steps`, data);
export const updateStep = (stepId, data) =>
  api.put(`/steps/${stepId}`, data);
export const deleteStep = (stepId) =>
  api.delete(`/steps/${stepId}`);

// ── AI Drafts ─────────────────────────────────────
// POST /api/v1/ai/drafts  { prompt }
export const createDraft = (prompt) =>
  api.post('/v1/ai/drafts', { prompt });

// GET /api/v1/ai/drafts  (userId resolved from JWT on backend)
export const getDrafts = () =>
  api.get('/v1/ai/drafts');

// GET /api/v1/ai/drafts/{id}
export const getDraft = (id) =>
  api.get(`/v1/ai/drafts/${id}`);

// POST /api/v1/ai/drafts/{id}/approve
export const approveDraft = (id) =>
  api.post(`/v1/ai/drafts/${id}/approve`);

// POST /api/v1/ai/drafts/{id}/reject
export const rejectDraft = (id) =>
  api.post(`/v1/ai/drafts/${id}/reject`);

export default api;