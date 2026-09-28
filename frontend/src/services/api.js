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

export const register = (email, password, organizationName) =>
  api.post('/auth/register', { email, password, organizationName });

// ── Workflows ─────────────────────────────────────
export const getWorkflows = () => api.get('/workflows');
export const createWorkflow = (data) =>
  api.post('/workflows', { ...data, status: 'ACTIVE' });
export const updateWorkflow = (id, data) =>
  api.put(`/workflows/${id}`, data);
export const deleteWorkflow = (id) => api.delete(`/workflows/${id}`);

// ── Executions ────────────────────────────────────
export const executeWorkflow = (id, payload) =>
  api.post(`/workflows/${id}/execute`, payload);
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
// inputOverrides: { [stepId]: { [key]: value } }
export const approveDraft = (id, inputOverrides = {}) =>
  api.post(`/v1/ai/drafts/${id}/approve`, { inputOverrides });

// POST /api/v1/ai/drafts/{id}/reject
export const rejectDraft = (id) =>
  api.post(`/v1/ai/drafts/${id}/reject`);

export default api;

export const asArray = (value) => {
  if (Array.isArray(value)) return value;
  if (value === null || value === undefined) return [];
  return [value];
};

export const isTokenExpired = (token) => {
  if (!token) return true;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
};

// GET /api/workflows/{workflowId}/required-inputs
export const getRequiredInputs = (workflowId) =>
  api.get(`/workflows/${workflowId}/required-inputs`);

export const getStepTypeFields = (stepType) => {
  const inputs = {
    HTTP: ['url', 'method'],
    EMAIL: ['to', 'subject', 'body'],
    CONDITION: ['condition'],
  };
  return inputs[stepType] || [];
};

export const configureDraftEmailRecipient = async (draftId, config) => {
  const response = await api.post('/ai/drafts/' + draftId + '/email-recipient', config);
  return response.data;
};
