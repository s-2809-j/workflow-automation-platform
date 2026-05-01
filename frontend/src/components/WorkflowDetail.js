import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  getSteps, createStep, deleteStep,
  executeWorkflow, getExecutions
} from '../services/api';
import '../styles/WorkflowDetail.css';

// ── Constants ────────────────────────────────────────────────────────────────

const STEP_TYPES = ['HTTP', 'LOG', 'DELAY', 'DATABASE', 'SCRIPT', 'EMAIL', 'WEBHOOK'];

const STEP_META = {
  HTTP:     { icon: '🌐', label: 'HTTP',     desc: 'Make HTTP API calls (GET, POST, PUT, DELETE)' },
  LOG:      { icon: '📝', label: 'LOG',      desc: 'Log a message to execution history' },
  DELAY:    { icon: '⏱️', label: 'DELAY',    desc: 'Pause execution for a set duration (ms)' },
  DATABASE: { icon: '🗄️', label: 'DATABASE', desc: 'Execute a database query' },
  SCRIPT:   { icon: '⚡', label: 'SCRIPT',   desc: 'Run a custom script' },
  EMAIL:    { icon: '📧', label: 'EMAIL',    desc: 'Send an email notification' },
  WEBHOOK:  { icon: '🔗', label: 'WEBHOOK',  desc: 'Trigger a webhook endpoint' },
};

// Default config objects per step type (sent as JsonNode = plain JS object)
const DEFAULT_CONFIG = {
  HTTP:     { url: 'https://jsonplaceholder.typicode.com/posts/1', method: 'GET' },
  LOG:      { message: 'Step executed successfully' },
  DELAY:    { duration: 2000 },
  DATABASE: { query: 'SELECT * FROM users LIMIT 10' },
  SCRIPT:   { script: "console.log('hello world')" },
  EMAIL:    { to: 'user@example.com', subject: 'Notification', body: 'Workflow step completed' },
  WEBHOOK:  { url: 'https://your-webhook.com/hook', method: 'POST' },
};

const STATUS_STYLE = {
  SUCCESS: { bg: '#dcfce7', color: '#15803d', dot: '#16a34a' },
  FAILED:  { bg: '#fee2e2', color: '#b91c1c', dot: '#dc2626' },
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
  const [steps, setSteps]           = useState([]);
  const [executions, setExecutions] = useState([]);
  const [loading, setLoading]       = useState(true);

  // ui state
  const [activeTab, setActiveTab]         = useState('steps');
  const [showForm, setShowForm]           = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [deletingId, setDeletingId]       = useState(null);
  const [running, setRunning]             = useState(false);
  const [creating, setCreating]           = useState(false);
  const [toast, setToast]                 = useState(null);

  // form state — config stored as JS object, serialised on submit
  const [form, setForm] = useState({
    name: '',
    stepType: 'HTTP',
    config: DEFAULT_CONFIG['HTTP'],    // JS object → will be sent as JsonNode
    configText: JSON.stringify(DEFAULT_CONFIG['HTTP'], null, 2),
    configError: '',
    dependsOn: null,                   // null = [] array, or step id string
  });

  // ── Data fetching ──────────────────────────────────────────────────────────

  const fetchData = useCallback(async () => {
    try {
      const [sRes, eRes] = await Promise.all([
        getSteps(id),
        getExecutions(id),
      ]);
      setSteps(sRes.data || []);
      const sorted = (eRes.data || []).sort(
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

  // ── Run workflow ──────────────────────────────────────────────────────────

  const handleRun = async () => {
    if (steps.length === 0) {
      showToast('Add at least one step before running', 'error');
      return;
    }
    setRunning(true);
    try {
      await executeWorkflow(id);
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

  // ── Render ────────────────────────────────────────────────────────────────

  const latestExec    = executions[0];
  const successCount  = executions.filter(e => e.status === 'SUCCESS').length;
  const failedCount   = executions.filter(e => e.status === 'FAILED').length;

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
      <p>Loading workflow...</p>
    </div>
  );

  return (
    <div className="detail-layout">

      {/* ── Page Header ── */}
      <div className="detail-header">
        <div className="header-left">
          <button className="back-btn" onClick={() => navigate('/workflows')}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="15 18 9 12 15 6"/></svg>
            Workflows
          </button>
          <div>
            <div className="breadcrumb">
              <span className="bc-link" onClick={() => navigate('/workflows')}>Dashboard</span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-link" onClick={() => navigate('/workflows')}>Workflows</span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
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
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add Step
          </button>
          <button
            className={`btn-primary ${running ? 'btn-loading' : ''}`}
            onClick={handleRun}
            disabled={running}
          >
            {running
              ? <><span className="spin-xs"/><span>Running...</span></>
              : <><svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg><span>Run Workflow</span></>
            }
          </button>
        </div>
      </div>

      {/* ── Stats Strip ── */}
      <div className="stats-strip">
        {[
          { label: 'Steps',      value: steps.length,     color: '#6366f1' },
          { label: 'Total Runs', value: executions.length, color: '#8b5cf6' },
          { label: 'Successful', value: successCount,      color: '#10b981' },
          { label: 'Failed',     value: failedCount,       color: '#ef4444' },
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
          { key: 'steps',      label: `Steps (${steps.length})`,          icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11' },
          { key: 'executions', label: `Executions (${executions.length})`, icon: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2' },
        ].map(t => (
          <button
            key={t.key}
            className={`tab ${activeTab === t.key ? 'tab-on' : ''}`}
            onClick={() => setActiveTab(t.key)}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d={t.icon}/></svg>
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
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
              </div>
              <h3>No steps yet</h3>
              <p>Add steps to define what this workflow automates</p>
              <button className="btn-outline" style={{marginTop:16}} onClick={() => setShowForm(true)}>
                Add First Step
              </button>
            </div>
          ) : (
            <div className="pipeline">
              {steps.map((step, idx) => {
                const meta   = STEP_META[step.stepType] || { icon: '⚙️', desc: '' };
                const config = step.config || {};
                return (
                  <div key={step.id} className="pipeline-item">
                    {/* Connector between steps */}
                    {idx > 0 && (
                      <div className="connector">
                        <div className="conn-line"/>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--subtle)" strokeWidth="2.5"><polyline points="6 9 12 15 18 9"/></svg>
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
                            {step.stepType === 'EMAIL' && config.to && (
                              <span className="config-tag">→ {config.to}</span>
                            )}
                            {step.stepType === 'WEBHOOK' && config.url && (
                              <span className="config-tag">{config.url}</span>
                            )}
                          </div>
                          {/* Dependency info */}
                          {step.dependsOn && Array.isArray(step.dependsOn) && step.dependsOn.length > 0 && (
                            <div className="dep-row">
                              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
                              depends on step #{steps.findIndex(s => s.id === step.dependsOn[0]) + 1}
                            </div>
                          )}
                        </div>
                      </div>
                      <div className="step-right">
                        <span className="step-id-text">{step.id.slice(0, 8)}...</span>
                        <button
                          className="del-btn"
                          onClick={() => setConfirmDeleteId(step.id)}
                          title="Delete step"
                        >
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}

              {/* Terminal node */}
              <div className="pipeline-end">
                <div className="conn-line" style={{height:28}}/>
                <div className="end-chip">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
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
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
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
                      <div className="exec-dot" style={{ background: s.dot }}/>
                      <div>
                        <div className="exec-label">
                          Run #{executions.length - i}
                          <span className="exec-id-mono">{exec.id.slice(0, 12)}...</span>
                        </div>
                        <div className="exec-time">
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
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
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
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

                {/* Config */}
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
                <button type="submit" className="btn-primary" disabled={creating || !!form.configError}>
                  {creating
                    ? <><span className="spin-xs spin-dark"/>Adding...</>
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
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="1.5"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
            </div>
            <h2 style={{textAlign:'center',marginBottom:8,fontSize:17}}>Delete Step?</h2>
            <p style={{textAlign:'center',color:'var(--muted)',fontSize:13,lineHeight:1.6,marginBottom:24}}>
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
                  ? <><span className="spin-xs"/>Deleting...</>
                  : 'Delete Step'
                }
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── TOAST ── */}
      {toast && (
        <div className={`toast toast-${toast.type}`}>
          {toast.type === 'success'
            ? <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            : <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          }
          {toast.message}
        </div>
      )}
    </div>
  );
};

export default WorkflowDetail;