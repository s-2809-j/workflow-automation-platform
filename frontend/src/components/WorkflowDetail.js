import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Layout from './Layout';
import {
  getSteps, createStep, deleteStep, updateStep,
  executeWorkflow, getExecutions, getRequiredInputs, asArray
} from '../services/api';
import '../styles/WorkflowDetail.css';

// ── Constants ────────────────────────────────────────────────────────────────

const STEP_TYPES = ['HTTP', 'LOG', 'DELAY', 'DATABASE', 'SCRIPT', 'EMAIL', 'WEBHOOK'];

const STEP_META = {
  HTTP: { icon: '🌐', label: 'HTTP', desc: 'Make HTTP API calls (GET, POST, PUT, DELETE)' },
  LOG: { icon: '📝', label: 'LOG', desc: 'Log a message to execution history' },
  DELAY: { icon: '⏱️', label: 'DELAY', desc: 'Pause execution for a set duration (ms)' },
  DATABASE: { icon: '🗄️', label: 'DATABASE', desc: 'Execute a database query' },
  SCRIPT: { icon: '⚡', label: 'SCRIPT', desc: 'Run a custom script' },
  EMAIL: { icon: '📧', label: 'EMAIL', desc: 'Send an email notification' },
  WEBHOOK: { icon: '🔗', label: 'WEBHOOK', desc: 'Trigger a webhook endpoint' },
};

// Default config objects per step type (sent as JsonNode = plain JS object)
const DEFAULT_CONFIG = {
  HTTP: { url: 'https://jsonplaceholder.typicode.com/posts/1', method: 'GET' },
  LOG: { message: 'Step executed successfully' },
  DELAY: { duration: 2000 },
  DATABASE: { query: 'SELECT * FROM users LIMIT 10' },
  SCRIPT: { script: "console.log('hello world')" },
  EMAIL: { recipientSource: 'FIXED', recipient: '', subject: 'Notification', body: '' },
  WEBHOOK: { url: 'https://your-webhook.com/hook', method: 'POST' },
};

const STATUS_STYLE = {
  SUCCESS: { bg: '#dcfce7', color: '#15803d', dot: '#16a34a' },
  FAILED: { bg: '#fee2e2', color: '#b91c1c', dot: '#dc2626' },
  RUNNING: { bg: '#eff6ff', color: '#1d4ed8', dot: '#2563eb' },
  SKIPPED: { bg: '#f1f5f9', color: '#64748b', dot: '#94a3b8' },
};

// ── Helpers ──────────────────────────────────────────────────────────────────

const fmt = (d) =>
  d ? new Date(d).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  }) : '—';

const dur = (a, b) => {
  if (!a || !b) return null;
  const ms = new Date(b) - new Date(a);
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
};

// ── Component ────────────────────────────────────────────────────────────────

const WorkflowDetail = () => {
  const { id } = useParams();          // workflowId from URL
  const navigate = useNavigate();

  // data
  const [steps, setSteps] = useState([]);
  const [executions, setExecutions] = useState([]);
  const [loading, setLoading] = useState(true);

  // ui state
  const [activeTab, setActiveTab] = useState('steps');
  const [showForm, setShowForm] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [running, setRunning] = useState(false);
  const [creating, setCreating] = useState(false);
  const [toast, setToast] = useState(null);
  const [showParamsModal, setShowParamsModal] = useState(false);
  const [paramRows, setParamRows] = useState([{ key: '', value: '' }]);
  const [requiredInputs, setRequiredInputs] = useState([]);
  const [requiredValues, setRequiredValues] = useState({});
    const [loadingInputs, setLoadingInputs] = useState(false);

  // edit step state
  const [editingStepId, setEditingStepId] = useState(null);
  const [editName, setEditName] = useState('');
  const [editStepType, setEditStepType] = useState('HTTP');
  const [editOrder, setEditOrder] = useState(1);
  const [editDependsOn, setEditDependsOn] = useState(null);
  const [editConfigText, setEditConfigText] = useState('');
  const [editConfigError, setEditConfigError] = useState('');
  const [saving, setSaving] = useState(false);
  const [duplicatingId, setDuplicatingId] = useState(null);
  const [reorderingId, setReorderingId] = useState(null);

  // form state — config stored as JS object, serialised on submit
  const [form, setForm] = useState({
    name: '',
    stepType: 'HTTP',
    config: DEFAULT_CONFIG['HTTP'],    // JS object → will be sent as JsonNode
    configText: JSON.stringify(DEFAULT_CONFIG['HTTP'], null, 2),
    configError: '',
    dependsOn: null,                   // null = [] array, or step id string
    // EMAIL-specific structured fields (kept in sync with config)
    emailRecipientSource: 'FIXED',
    emailRecipient: '',
    emailInputKey: '',
    emailPrevStepId: '',
    emailPrevOutputField: '',
  });

  // Derives the config object from structured EMAIL fields
  const buildEmailConfig = (emailFields) => {
    const { emailRecipientSource: source, emailRecipient, emailInputKey, emailPrevStepId, emailPrevOutputField } = emailFields;
    const base = { subject: 'Notification', body: '', isHtml: false };
    const cfg = { ...base, recipientSource: source };
    if (source === 'FIXED') cfg.recipient = emailRecipient;
    if (source === 'WORKFLOW_INPUT') cfg.inputKey = emailInputKey;
    if (source === 'PREVIOUS_STEP_OUTPUT') { cfg.previousStepId = emailPrevStepId; cfg.outputField = emailPrevOutputField; }
    return cfg;
  };

  // ── EMAIL structured field change ─────────────────────────────────────────

  const handleEmailFieldChange = (field, value) => {
    setForm(p => {
      const updated = { ...p, [field]: value };
      const emailCfg = buildEmailConfig(updated);
      return {
        ...updated,
        config: emailCfg,
        configText: JSON.stringify(emailCfg, null, 2),
        configError: '',
      };
    });
  };

  // ── Data fetching ──────────────────────────────────────────────────────────

  const fetchData = useCallback(async () => {
    try {
      const [sRes, eRes] = await Promise.all([
        getSteps(id),
        getExecutions(id),
      ]);
      const sortedSteps = asArray(sRes.data).sort(
        (a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0)
      );
      setSteps(sortedSteps);
      const sorted = asArray(eRes.data).sort(
        (a, b) => new Date(b.startedAt) - new Date(a.startedAt)
      );
      setExecutions(sorted);
    } catch {
      showToast('Failed to load data', 'error');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // ── Toast ─────────────────────────────────────────────────────────────────

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  // ── Step type change ───────────────────────────────────────────────────────

  const handleTypeChange = (type) => {
    const cfg = DEFAULT_CONFIG[type];
    setForm(p => ({
      ...p,
      stepType: type,
      config: cfg,
      configText: JSON.stringify(cfg, null, 2),
      configError: '',
      // Reset EMAIL fields when switching types
      emailRecipientSource: 'FIXED',
      emailRecipient: '',
      emailInputKey: '',
      emailPrevStepId: '',
      emailPrevOutputField: '',
    }));
  };

  // ── Config textarea change ─────────────────────────────────────────────────

  const handleConfigChange = (text) => {
    try {
      const parsed = JSON.parse(text);
      setForm(p => ({ ...p, configText: text, config: parsed, configError: '' }));
    } catch {
      setForm(p => ({ ...p, configText: text, configError: 'Invalid JSON' }));
    }
  };

  // ── Create step ───────────────────────────────────────────────────────────
  // Matches: POST /api/workflows/{workflowId}/steps
  // Body:  { stepOrder, name, stepType, config (JsonNode), dependsOn (JsonNode) }

  const handleCreate = async (e) => {
    e.preventDefault();
    if (form.configError) { showToast('Fix JSON errors first', 'error'); return; }

    setCreating(true);
    try {
      const nextOrder = steps.length + 1;

      // dependsOn: null → empty array JsonNode, selected → ["<uuid>"]
      const dependsOnNode = form.dependsOn ? [form.dependsOn] : [];

      const body = {
        stepOrder: nextOrder,
        name: form.name,
        stepType: form.stepType,     // field name in CreateWorkflowStepRequest = "type" but @JsonProperty("stepType")
        config: form.config,          // plain JS object → Jackson maps to JsonNode
        dependsOn: dependsOnNode,     // array → Jackson maps to JsonNode array
      };

      await createStep(id, body);
      setShowForm(false);
      setForm({
        name: '',
        stepType: 'HTTP',
        config: DEFAULT_CONFIG['HTTP'],
        configText: JSON.stringify(DEFAULT_CONFIG['HTTP'], null, 2),
        configError: '',
        dependsOn: null,
        emailRecipientSource: 'FIXED',
        emailRecipient: '',
        emailInputKey: '',
        emailPrevStepId: '',
        emailPrevOutputField: '',
      });
      await fetchData();
      showToast('Step added successfully');
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to create step', 'error');
    } finally {
      setCreating(false);
    }
  };

  // ── Delete step ───────────────────────────────────────────────────────────
  // Matches: DELETE /api/steps/{id}

  const handleDelete = async (stepId) => {
    setDeletingId(stepId);
    try {
      await deleteStep(stepId);
      setSteps(p => p.filter(s => s.id !== stepId));
      setConfirmDeleteId(null);
      showToast('Step deleted');
    } catch {
      showToast('Failed to delete step', 'error');
    } finally {
      setDeletingId(null);
    }
  };

    // ── Edit step ─────────────────────────────────────────────────────────────

  const handleEditOpen = (step) => {
    setEditingStepId(step.id);
    setEditName(step.name || '');
    setEditStepType(step.stepType || 'HTTP');
    setEditOrder(step.stepOrder || 1);
    const dep = Array.isArray(step.dependsOn) && step.dependsOn.length > 0 ? step.dependsOn[0] : null;
    setEditDependsOn(dep);
    setEditConfigText(JSON.stringify(step.config || {}, null, 2));
    setEditConfigError('');
  };

  const handleEditConfigChange = (text) => {
    setEditConfigText(text);
    try {
      JSON.parse(text);
      setEditConfigError('');
    } catch {
      setEditConfigError('Invalid JSON');
    }
  };

  const handleEditSave = async (stepId) => {
    if (editConfigError) { showToast('Fix JSON errors first', 'error'); return; }
    setSaving(true);
    try {
      await updateStep(stepId, {
        name: editName,
        stepOrder: parseInt(editOrder, 10) || 1,
        type: editStepType,
        config: JSON.parse(editConfigText),
        dependsOn: editDependsOn ? [editDependsOn] : [],
      });
      setEditingStepId(null);
      await fetchData();
      showToast('Step updated successfully');
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to update step', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleEditCancel = () => {
    setEditingStepId(null);
    setEditConfigError('');
  };

  const handleMoveStep = async (step, direction) => {
    const sorted = [...steps].sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0));
    const currIdx = sorted.findIndex(s => s.id === step.id);
    const targetIdx = direction === 'up' ? currIdx - 1 : currIdx + 1;
    if (targetIdx < 0 || targetIdx >= sorted.length) return;

    const targetStep = sorted[targetIdx];
    setReorderingId(step.id);
    try {
      const currentOrder = step.stepOrder ?? (currIdx + 1);
      const targetOrder = targetStep.stepOrder ?? (targetIdx + 1);
      await updateStep(step.id, { stepOrder: targetOrder });
      await updateStep(targetStep.id, { stepOrder: currentOrder });
      await fetchData();
      showToast('Step reordered');
    } catch {
      showToast('Failed to reorder step', 'error');
    } finally {
      setReorderingId(null);
    }
  };

  // ── Run workflow ──────────────────────────────────────────────────────────

  // Opens the runtime parameters modal (fetches required inputs from backend)
  const handleRun = async () => {
    if (steps.length === 0) {
      showToast('Add at least one step before running', 'error');
      return;
    }
    setLoadingInputs(true);
    try {
      const res = await getRequiredInputs(id);
      const inputs = Array.isArray(res.data) ? res.data : [];
      setRequiredInputs(inputs);
      const initial = {};
      inputs.forEach(inp => { initial[inp.key] = ''; });
      setRequiredValues(initial);
      setParamRows([{ key: '', value: '' }]);
    } catch {
      setRequiredInputs([]);
      setRequiredValues({});
      setParamRows([{ key: '', value: '' }]);
    } finally {
      setLoadingInputs(false);
      setShowParamsModal(true);
    }
  };

  // Called from the modal "Run" button — assembles the params object then executes
  const handleRunWithParams = async () => {
    setShowParamsModal(false);
    setRunning(true);
    try {
      // Merge required inputs + optional freeform params into one payload
      const params = { ...requiredValues };
      paramRows.forEach(({ key, value }) => {
        const k = key.trim();
        if (k) params[k] = value;
      });
      const payload = Object.keys(params).length > 0 ? params : undefined;
      await executeWorkflow(id, payload);
      showToast('Workflow started!');
      setTimeout(async () => {
        await fetchData();
        setActiveTab('executions');
      }, 2500);
    } catch {
      showToast('Failed to start workflow', 'error');
    } finally {
      setRunning(false);
    }
  };

  // ── Duplicate step ────────────────────────────────────────────────────────

  const handleDuplicate = async (step) => {
    setDuplicatingId(step.id);
    try {
      const nextOrder = steps.length + 1;
      const body = {
        stepOrder: nextOrder,
        name: `${step.name} (copy)`,
        stepType: step.stepType,
        config: step.config || {},
        dependsOn: [],
      };
      await createStep(id, body);
      await fetchData();
      showToast(`"${step.name}" duplicated`);
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to duplicate step', 'error');
    } finally {
      setDuplicatingId(null);
    }
  };

  // ── Render ────────────────────────────────────────────────────────────────

  const latestExec = executions[0];
  const successCount = executions.filter(e => e.status === 'SUCCESS').length;
  const failedCount = executions.filter(e => e.status === 'FAILED').length;

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div /><div /><div /><div /></div>
      <p>Loading workflow...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={showToast}>
      <div className="detail-layout">

        {/* ── Page Header ── */}
        <div className="detail-header">
          <div className="header-left">
            <button className="back-btn" onClick={() => navigate('/workflows')}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="15 18 9 12 15 6" /></svg>
              Workflows
            </button>
            <div>
              <div className="breadcrumb">
                <span className="bc-link" onClick={() => navigate('/workflows')}>Dashboard</span>
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6" /></svg>
                <span className="bc-link" onClick={() => navigate('/workflows')}>Workflows</span>
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6" /></svg>
                <span className="bc-active">Detail</span>
              </div>
              <div className="title-row">
                <h1 className="page-title">Workflow Steps</h1>
                <span className="count-pill">{steps.length} step{steps.length !== 1 ? 's' : ''}</span>
                {latestExec && (() => {
                  const s = STATUS_STYLE[latestExec.status] || STATUS_STYLE.SKIPPED;
                  return (
                    <span className="last-run-pill" style={{ background: s.bg, color: s.color }}>
                      Last: {latestExec.status}
                    </span>
                  );
                })()}
              </div>
              <p className="page-sub">
                ID: <code className="mono">{id.slice(0, 18)}...</code>
              </p>
            </div>
          </div>

          <div className="header-actions">
            <button className="btn-outline" onClick={() => setShowForm(true)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
              Add Step
            </button>
            <button
              className={`btn-primary ${running || loadingInputs ? 'btn-loading' : ''}`}
              onClick={handleRun}
              disabled={running || loadingInputs}
            >
              {running || loadingInputs
                ? <><span className="spin-xs" /><span>{loadingInputs ? 'Loading...' : 'Running...'}</span></>
                : <><svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg><span>Run Workflow</span></>
              }
            </button>
          </div>
        </div>

        {/* ── Stats Strip ── */}
        <div className="stats-strip">
          {[
            { label: 'Steps', value: steps.length, color: '#6366f1' },
            { label: 'Total Runs', value: executions.length, color: '#8b5cf6' },
            { label: 'Successful', value: successCount, color: '#10b981' },
            { label: 'Failed', value: failedCount, color: '#ef4444' },
          ].map((s, i) => (
            <div key={i} className="stat-pill" style={{ '--c': s.color }}>
              <span className="stat-val">{s.value}</span>
              <span className="stat-key">{s.label}</span>
            </div>
          ))}
        </div>

        {/* ── Tabs ── */}
        <div className="tabs-bar">
          {[
            { key: 'steps', label: `Steps (${steps.length})`, icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11' },
            { key: 'executions', label: `Executions (${executions.length})`, icon: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2' },
          ].map(t => (
            <button
              key={t.key}
              className={`tab ${activeTab === t.key ? 'tab-on' : ''}`}
              onClick={() => setActiveTab(t.key)}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d={t.icon} /></svg>
              {t.label}
            </button>
          ))}
        </div>

        {/* ── STEPS TAB ── */}
        {activeTab === 'steps' && (
          <div className="tab-body">
            {steps.length === 0 ? (
              <div className="empty-box">
                <div className="empty-icon">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><polyline points="9 11 12 14 22 4" /><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" /></svg>
                </div>
                <h3>No steps yet</h3>
                <p>Add steps to define what this workflow automates</p>
                <button className="btn-outline" style={{ marginTop: 16 }} onClick={() => setShowForm(true)}>
                  Add First Step
                </button>
              </div>
            ) : (
              <div className="pipeline">
                {steps.map((step, idx) => {
                  const meta = STEP_META[step.stepType] || { icon: '⚙️', desc: '' };
                  const config = step.config || {};
                  return (
                    <div key={step.id} className="pipeline-item">
                      {/* Connector between steps */}
                      {idx > 0 && (
                        <div className="connector">
                          <div className="conn-line" />
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--subtle)" strokeWidth="2.5"><polyline points="6 9 12 15 18 9" /></svg>
                        </div>
                      )}

                      {/* Step Card */}
                      <div className="step-card">
                        <div className="step-left">
                          <div className="order-bubble">{step.stepOrder}</div>
                          <span className="type-emoji">{meta.icon}</span>
                          <div className="step-body">
                            <div className="step-name">{step.name}</div>
                            <div className="step-sub">
                              <span className="type-chip">{step.stepType}</span>
                              <span className="type-hint">{meta.desc}</span>
                            </div>
                            {/* Config preview */}
                            <div className="config-row">
                              {step.stepType === 'HTTP' && config.url && (
                                <span className="config-tag">
                                  <span className="method-tag">{config.method || 'GET'}</span>
                                  <span className="url-text">{config.url}</span>
                                </span>
                              )}
                              {step.stepType === 'LOG' && config.message && (
                                <span className="config-tag">"{config.message}"</span>
                              )}
                              {step.stepType === 'DELAY' && config.duration && (
                                <span className="config-tag">⏱ {config.duration}ms</span>
                              )}
                              {step.stepType === 'EMAIL' && (
                                <span className="config-tag">→ {config.recipient || 'Recipient configuration required'}</span>
                              )}
                              {step.stepType === 'WEBHOOK' && config.url && (
                                <span className="config-tag">{config.url}</span>
                              )}
                            </div>
                            {/* Dependency info */}
                            {step.dependsOn && Array.isArray(step.dependsOn) && step.dependsOn.length > 0 && (
                              <div className="dep-row">
                                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="17 1 21 5 17 9" /><path d="M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4" /><path d="M21 13v2a4 4 0 0 1-4 4H3" /></svg>
                                {(() => {
                                  const depId = step.dependsOn[0];
                                  const parent = steps.find(s => s.id === depId);
                                  if (parent) {
                                    return `depends on step #${parent.stepOrder || steps.indexOf(parent) + 1} (${parent.name})`;
                                  }
                                  return `depends on ${depId.slice(0, 8)}...`;
                                })()}
                              </div>
                            )}
                          </div>
                        </div>
                                              <div className="step-right">
                          <span className="step-id-text">{step.id.slice(0, 8)}...</span>
                          <button
                            className="del-btn"
                            onClick={() => handleMoveStep(step, 'up')}
                            title="Move step up"
                            disabled={idx === 0 || reorderingId === step.id}
                            style={{ marginRight: 4, opacity: idx === 0 ? 0.35 : 1 }}
                          >
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="18 15 12 9 6 15"/></svg>
                          </button>
                          <button
                            className="del-btn"
                            onClick={() => handleMoveStep(step, 'down')}
                            title="Move step down"
                            disabled={idx === steps.length - 1 || reorderingId === step.id}
                            style={{ marginRight: 4, opacity: idx === steps.length - 1 ? 0.35 : 1 }}
                          >
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"/></svg>
                          </button>
                          <button
                            className="del-btn"
                            onClick={() => handleEditOpen(step)}
                            title="Edit step"
                            style={{ marginRight: 4, color: '#6366f1' }}
                          >
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                          </button>
                          <button
                            className="del-btn"
                            onClick={() => handleDuplicate(step)}
                            title="Duplicate step"
                            disabled={duplicatingId === step.id}
                            style={{ marginRight: 4, color: '#10b981' }}
                          >
                            {duplicatingId === step.id
                              ? <span className="spin-xs" style={{ borderTopColor: '#10b981' }} />
                              : <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                            }
                          </button>
                          <button
                            className="del-btn"
                            onClick={() => setConfirmDeleteId(step.id)}
                            title="Delete step"
                          >
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg>
                          </button>
                        </div>
                      </div>

                      {/* Inline edit panel — shown below the card when editing */}
                      {editingStepId === step.id && (
                        <div style={{
                          marginTop: 12, padding: '16px', background: '#f8faff',
                          border: '1px solid #e0e7ff', borderRadius: 10
                        }}>
                          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 10, marginBottom: 10 }}>
                            <div>
                              <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                                Step Name
                              </label>
                              <input
                                type="text"
                                value={editName}
                                onChange={e => setEditName(e.target.value)}
                                style={{
                                  width: '100%', padding: '8px 10px', borderRadius: 6,
                                  border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box'
                                }}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                                Step Type
                              </label>
                              <select
                                value={editStepType}
                                onChange={e => setEditStepType(e.target.value)}
                                style={{
                                  width: '100%', padding: '8px 10px', borderRadius: 6,
                                  border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box', background: '#fff'
                                }}
                              >
                                {STEP_TYPES.map(t => (
                                  <option key={t} value={t}>{t}</option>
                                ))}
                              </select>
                            </div>
                            <div>
                              <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                                Order #
                              </label>
                              <input
                                type="number"
                                min="1"
                                value={editOrder}
                                onChange={e => setEditOrder(e.target.value)}
                                style={{
                                  width: '100%', padding: '8px 10px', borderRadius: 6,
                                  border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box'
                                }}
                              />
                            </div>
                          </div>

                          <div style={{ marginBottom: 10 }}>
                            <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                              Runs After (Dependency)
                            </label>
                            <select
                              value={editDependsOn || ''}
                              onChange={e => setEditDependsOn(e.target.value || null)}
                              style={{
                                width: '100%', padding: '8px 10px', borderRadius: 6,
                                border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box', background: '#fff'
                              }}
                            >
                              <option value="">No dependency (Runs immediately)</option>
                              {steps.filter(s => s.id !== step.id).map(s => (
                                <option key={s.id} value={s.id}>
                                  Step #{s.stepOrder}: {s.name} ({s.stepType})
                                </option>
                              ))}
                            </select>
                          </div>

                          <div style={{ marginBottom: 10 }}>
                            <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                              Config (JSON)
                              {editConfigError && (
                                <span style={{ color: '#ef4444', marginLeft: 8, fontSize: 11 }}>
                                  {editConfigError}
                                </span>
                              )}
                            </label>
                            <textarea
                              value={editConfigText}
                              onChange={e => handleEditConfigChange(e.target.value)}
                              rows={6}
                              spellCheck={false}
                              style={{
                                width: '100%', padding: '8px 10px', borderRadius: 6, boxSizing: 'border-box',
                                border: `1px solid ${editConfigError ? '#ef4444' : 'var(--border)'}`,
                                fontSize: 12, fontFamily: 'monospace', resize: 'vertical'
                              }}
                            />
                            {editStepType === 'HTTP' && (
                              <p style={{ fontSize: 11, color: '#6366f1', marginTop: 4 }}>
                                💡 For HTTP steps, replace the <code>url</code> with your real API endpoint.
                              </p>
                            )}
                          </div>
                          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                            <button
                              type="button"
                              className="btn-ghost"
                              onClick={handleEditCancel}
                              style={{ fontSize: 13, padding: '6px 14px' }}
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              className="btn-primary"
                              onClick={() => handleEditSave(step.id)}
                              disabled={saving || !!editConfigError}
                              style={{ fontSize: 13, padding: '6px 14px' }}
                            >
                              {saving ? 'Saving...' : 'Save Changes'}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}

                {/* Terminal node */}
                <div className="pipeline-end">
                  <div className="conn-line" style={{ height: 28 }} />
                  <div className="end-chip">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12" /></svg>
                    Workflow Complete
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── EXECUTIONS TAB ── */}
        {activeTab === 'executions' && (
          <div className="tab-body">
            {executions.length === 0 ? (
              <div className="empty-box">
                <div className="empty-icon">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
                </div>
                <h3>No executions yet</h3>
                <p>Click "Run Workflow" above to start the first execution</p>
              </div>
            ) : (
              <div className="exec-list">
                {executions.map((exec, i) => {
                  const s = STATUS_STYLE[exec.status] || STATUS_STYLE.SKIPPED;
                  const d = dur(exec.startedAt, exec.completedAt);
                  return (
                    <div key={exec.id} className="exec-card" style={{ animationDelay: `${i * 0.04}s` }}>
                      <div className="exec-left">
                        <div className="exec-dot" style={{ background: s.dot }} />
                        <div>
                          <div className="exec-label">
                            Run #{executions.length - i}
                            <span className="exec-id-mono">{exec.id.slice(0, 12)}...</span>
                          </div>
                          <div className="exec-time">
                            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
                            {fmt(exec.startedAt)}
                            {d && <span className="exec-dur"> · {d}</span>}
                          </div>
                        </div>
                      </div>
                      <div className="exec-right">
                        <span className="exec-badge" style={{ background: s.bg, color: s.color }}>
                          {exec.status}
                        </span>
                        {exec.errorMessage && (
                          <span className="err-chip" title={exec.errorMessage}>Error</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ── ADD STEP MODAL ── */}
        {showForm && (
          <div className="overlay" onClick={e => e.target === e.currentTarget && setShowForm(false)}>
            <div className="modal">
              <div className="modal-top">
                <div>
                  <h2>Add Step</h2>
                  <p>Configure a new step for this workflow</p>
                </div>
                <button className="x-btn" onClick={() => setShowForm(false)}>
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                </button>
              </div>

              <form onSubmit={handleCreate}>
                <div className="modal-body">

                  {/* Name */}
                  <div className="field">
                    <label>Step Name <span className="req">*</span></label>
                    <input
                      type="text"
                      placeholder="e.g. Fetch Employee Data"
                      value={form.name}
                      onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
                      required autoFocus
                    />
                  </div>

                  {/* Step Type */}
                  <div className="field">
                    <label>Step Type <span className="req">*</span></label>
                    <div className="type-grid">
                      {STEP_TYPES.map(t => (
                        <button
                          key={t}
                          type="button"
                          className={`type-card ${form.stepType === t ? 'type-on' : ''}`}
                          onClick={() => handleTypeChange(t)}
                        >
                          <span className="tc-emoji">{STEP_META[t].icon}</span>
                          <span className="tc-label">{t}</span>
                        </button>
                      ))}
                    </div>
                    <p className="field-hint">{STEP_META[form.stepType].desc}</p>
                  </div>

                  {/* Config — structured for EMAIL, raw JSON for all others */}
                  {form.stepType === 'EMAIL' ? (
                    <div className="field">
                      <label>Email Recipient <span className="req">*</span></label>

                      {/* Recipient Source */}
                      <div style={{ marginBottom: 10 }}>
                        <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Recipient Source</label>
                        <select
                          className="json-editor"
                          style={{ height: 'auto', padding: '8px 10px', fontFamily: 'inherit' }}
                          value={form.emailRecipientSource}
                          onChange={e => handleEmailFieldChange('emailRecipientSource', e.target.value)}
                        >
                          <option value="FIXED">Fixed address</option>
                          <option value="WORKFLOW_INPUT">Workflow input key</option>
                          <option value="PREVIOUS_STEP_OUTPUT">Previous step output</option>
                        </select>
                      </div>

                      {/* Conditional fields */}
                      {form.emailRecipientSource === 'FIXED' && (
                        <div style={{ marginBottom: 10 }}>
                          <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Email Address <span className="req">*</span></label>
                          <input
                            type="email"
                            placeholder="e.g. user@example.com"
                            value={form.emailRecipient}
                            onChange={e => handleEmailFieldChange('emailRecipient', e.target.value)}
                            required
                            style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box' }}
                          />
                        </div>
                      )}
                      {form.emailRecipientSource === 'WORKFLOW_INPUT' && (
                        <div style={{ marginBottom: 10 }}>
                          <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Input Key <span className="req">*</span></label>
                          <input
                            type="text"
                            placeholder="e.g. recipientEmail"
                            value={form.emailInputKey}
                            onChange={e => handleEmailFieldChange('emailInputKey', e.target.value)}
                            required
                            style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box' }}
                          />
                          <p style={{ fontSize: 11, color: 'var(--muted)', margin: '4px 0 0' }}>Key from the workflow's trigger data</p>
                        </div>
                      )}
                      {form.emailRecipientSource === 'PREVIOUS_STEP_OUTPUT' && (
                        <div style={{ marginBottom: 10 }}>
                          <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Previous Step ID <span className="req">*</span></label>
                          <input
                            type="text"
                            placeholder="UUID of the previous step"
                            value={form.emailPrevStepId}
                            onChange={e => handleEmailFieldChange('emailPrevStepId', e.target.value)}
                            required
                            style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, marginBottom: 8, boxSizing: 'border-box' }}
                          />
                          <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Output Field <span className="req">*</span></label>
                          <input
                            type="text"
                            placeholder="e.g. email"
                            value={form.emailPrevOutputField}
                            onChange={e => handleEmailFieldChange('emailPrevOutputField', e.target.value)}
                            required
                            style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, boxSizing: 'border-box' }}
                          />
                        </div>
                      )}

                      {/* Subject + Body always shown */}
                      <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4, marginTop: 8 }}>Subject</label>
                      <input
                        type="text"
                        placeholder="Email subject"
                        value={form.config?.subject || ''}
                        onChange={e => setForm(p => {
                          const cfg = { ...p.config, subject: e.target.value };
                          return { ...p, config: cfg, configText: JSON.stringify(cfg, null, 2) };
                        })}
                        style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, marginBottom: 8, boxSizing: 'border-box' }}
                      />
                      <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>Body</label>
                      <textarea
                        placeholder="Email body"
                        value={form.config?.body || ''}
                        onChange={e => setForm(p => {
                          const cfg = { ...p.config, body: e.target.value };
                          return { ...p, config: cfg, configText: JSON.stringify(cfg, null, 2) };
                        })}
                        rows={3}
                        style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13, resize: 'vertical', boxSizing: 'border-box' }}
                      />
                    </div>
                  ) : (
                    <div className="field">
                      <label>
                        Config (JSON) <span className="req">*</span>
                        {form.configError && (
                          <span className="json-err">{form.configError}</span>
                        )}
                      </label>
                      <textarea
                        className={`json-editor ${form.configError ? 'json-invalid' : ''}`}
                        value={form.configText}
                        onChange={e => handleConfigChange(e.target.value)}
                        rows={6}
                        spellCheck={false}
                      />
                    </div>
                  )}

                  {/* Depends On */}
                  <div className="field">
                    <label>
                      Runs After
                      <span className="field-sub">Which step must complete before this one?</span>
                    </label>
                    <div className="dep-list">
                      <label className="dep-item">
                        <input
                          type="radio"
                          name="dep"
                          checked={form.dependsOn === null}
                          onChange={() => setForm(p => ({ ...p, dependsOn: null }))}
                        />
                        <div className="dep-content">
                          <span className="dep-title">No dependency</span>
                          <span className="dep-sub">Runs immediately when workflow starts</span>
                        </div>
                      </label>
                      {steps.map(s => (
                        <label key={s.id} className="dep-item">
                          <input
                            type="radio"
                            name="dep"
                            checked={form.dependsOn === s.id}
                            onChange={() => setForm(p => ({ ...p, dependsOn: s.id }))}
                          />
                          <div className="dep-content">
                            <span className="dep-title">
                              <span className="dep-num">#{s.stepOrder}</span>
                              {s.name}
                              <span className="dep-type">{s.stepType}</span>
                            </span>
                            <span className="dep-sub">{s.id.slice(0, 12)}...</span>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>

                </div>

                <div className="modal-foot">
                  <button type="button" className="btn-ghost" onClick={() => setShowForm(false)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn-primary" disabled={
                    creating ||
                    !!form.configError ||
                    (form.stepType === 'EMAIL' && form.emailRecipientSource === 'FIXED' && !form.emailRecipient.trim()) ||
                    (form.stepType === 'EMAIL' && form.emailRecipientSource === 'WORKFLOW_INPUT' && !form.emailInputKey.trim()) ||
                    (form.stepType === 'EMAIL' && form.emailRecipientSource === 'PREVIOUS_STEP_OUTPUT' && (!form.emailPrevStepId.trim() || !form.emailPrevOutputField.trim()))
                  }>
                    {creating
                      ? <><span className="spin-xs spin-dark" />Adding...</>
                      : 'Add Step'
                    }
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* ── DELETE CONFIRM MODAL ── */}
        {confirmDeleteId && (
          <div className="overlay" onClick={e => e.target === e.currentTarget && setConfirmDeleteId(null)}>
            <div className="modal modal-sm">
              <div className="del-icon-wrap">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="1.5"><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg>
              </div>
              <h2 style={{ textAlign: 'center', marginBottom: 8, fontSize: 17 }}>Delete Step?</h2>
              <p style={{ textAlign: 'center', color: 'var(--muted)', fontSize: 13, lineHeight: 1.6, marginBottom: 24 }}>
                This step will be permanently removed. Other steps that depend on it may fail.
              </p>
              <div className="modal-foot">
                <button className="btn-ghost" onClick={() => setConfirmDeleteId(null)}>Cancel</button>
                <button
                  className="btn-danger"
                  onClick={() => handleDelete(confirmDeleteId)}
                  disabled={!!deletingId}
                >
                  {deletingId
                    ? <><span className="spin-xs" />Deleting...</>
                    : 'Delete Step'
                  }
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── RUNTIME PARAMETERS MODAL ── */}
        {showParamsModal && (
          <div className="overlay" onClick={e => e.target === e.currentTarget && setShowParamsModal(false)}>
            <div className="modal">
              <div className="modal-top">
                <div>
                  <h2>Run Workflow</h2>
                  <p>
                    {requiredInputs.length > 0
                      ? 'Fill in the required fields before running.'
                      : 'Optionally supply runtime parameters (key = value).'}
                  </p>
                </div>
                <button className="x-btn" onClick={() => setShowParamsModal(false)}>
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>

              <div className="modal-body">

                {/* Required inputs — rendered dynamically from backend */}
                {requiredInputs.length > 0 && (
                  <div className="field">
                    <label>Required Inputs</label>
                    {requiredInputs.map((inp) => (
                      <div key={inp.key} style={{ marginBottom: 12 }}>
                        <label style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                          {inp.label} <span className="req">*</span>
                          <span style={{ fontSize: 11, color: 'var(--muted)', marginLeft: 6 }}>
                            ({inp.stepType} · key: <code>{inp.key}</code>)
                          </span>
                        </label>
                        <input
                          type={inp.type || 'text'}
                          placeholder={inp.type === 'email' ? 'e.g. user@example.com' : inp.key}
                          value={requiredValues[inp.key] || ''}
                          onChange={e => setRequiredValues(p => ({ ...p, [inp.key]: e.target.value }))}
                          style={{
                            width: '100%', padding: '8px 10px', borderRadius: 6,
                            border: `1px solid ${requiredValues[inp.key]?.trim() ? 'var(--border)' : '#ef4444'}`,
                            fontSize: 13, boxSizing: 'border-box'
                          }}
                        />
                      </div>
                    ))}
                  </div>
                )}

                {/* Optional freeform params — always available */}
                <div className="field">
                  <label>
                    Additional Parameters
                    <span style={{ fontWeight: 400, fontSize: 12, color: 'var(--muted)', marginLeft: 6 }}>optional</span>
                  </label>
                  {paramRows.map((row, idx) => (
                    <div key={idx} style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                      <input
                        type="text"
                        placeholder="key"
                        value={row.key}
                        onChange={e => {
                          const updated = paramRows.map((r, i) => i === idx ? { ...r, key: e.target.value } : r);
                          setParamRows(updated);
                        }}
                        style={{ flex: 1, padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13 }}
                      />
                      <span style={{ color: 'var(--muted)', fontWeight: 600, flexShrink: 0 }}>=</span>
                      <input
                        type="text"
                        placeholder="value"
                        value={row.value}
                        onChange={e => {
                          const updated = paramRows.map((r, i) => i === idx ? { ...r, value: e.target.value } : r);
                          setParamRows(updated);
                        }}
                        style={{ flex: 2, padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 13 }}
                      />
                      {paramRows.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setParamRows(paramRows.filter((_, i) => i !== idx))}
                          style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: '4px 6px', flexShrink: 0 }}
                          title="Remove row"
                        >
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                          </svg>
                        </button>
                      )}
                    </div>
                  ))}
                  <button
                    type="button"
                    className="btn-outline"
                    style={{ marginTop: 4, fontSize: 12, padding: '5px 12px' }}
                    onClick={() => setParamRows([...paramRows, { key: '', value: '' }])}
                  >
                    + Add Parameter
                  </button>
                  <p style={{ fontSize: 11, color: 'var(--muted)', marginTop: 8 }}>
                    Parameters are passed as runtime <code>triggerData</code> and are available
                    to any step via <code>WORKFLOW_INPUT</code> source.
                  </p>
                </div>
              </div>

              <div className="modal-foot">
                <button type="button" className="btn-ghost" onClick={() => setShowParamsModal(false)}>
                  Cancel
                </button>
                <button
                  type="button"
                  className={`btn-primary ${running ? 'btn-loading' : ''}`}
                  onClick={handleRunWithParams}
                  disabled={
                    running ||
                    requiredInputs.some(inp => !requiredValues[inp.key]?.trim())
                  }
                >
                  {running
                    ? <><span className="spin-xs" /><span>Running...</span></>
                    : <><svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg><span>Run Workflow</span></>
                  }
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default WorkflowDetail;
