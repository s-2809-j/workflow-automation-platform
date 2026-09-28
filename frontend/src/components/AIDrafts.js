import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { createDraft, getDrafts, getDraft, approveDraft, rejectDraft, configureDraftEmailRecipient, asArray } from '../services/api';
import '../styles/AIDrafts.css';

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmt = (d) =>
  d ? new Date(d).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }) : '—';

const parseDraftContent = (jsonContent) => {
  if (!jsonContent) return null;
  try {
    return typeof jsonContent === 'string' ? JSON.parse(jsonContent) : jsonContent;
  } catch {
    return null;
  }
};

// Returns EMAIL steps that still need recipient configuration
// If the email step recipient is being provided via smartConfigs, skip it
const getUnconfiguredEmailSteps = (content, smartConfigs, draftId) => {
  if (!content?.steps) return [];
  return content.steps.filter(s => {
    if (s.stepType !== 'EMAIL') return false;
    const c = s.config || {};
    const src = (c.recipientSource || '').toUpperCase();

    // Check if user has filled recipient in the smart config panel
    const smartKey = `${draftId}_${s.id}_recipient`;
    const smartRecipient = smartConfigs[smartKey];
    if (smartRecipient && smartRecipient.toString().trim()) return false;

    if (!src) return true;
    if (src === 'FIXED' && !c.recipient) return true;
    if (src === 'WORKFLOW_INPUT' && !c.inputKey) return true;
    if (src === 'PREVIOUS_STEP_OUTPUT' && (!c.previousStepId || !c.outputField)) return true;
    return false;
  });
};

// Extracts user-configurable inputs from SCRIPT steps and empty EMAIL recipients
const getConfigurableInputs = (content) => {
  if (!content?.steps) return [];
  const result = [];
  content.steps.forEach(step => {
    if (step.stepType === 'SCRIPT' && step.config?.inputs) {
      const inputs = step.config.inputs;
      if (Object.keys(inputs).length > 0) {
        result.push({ stepId: step.id, stepName: step.name, inputs });
      }
    }
    if (step.stepType === 'EMAIL' && step.config?.recipient === '') {
      result.push({
        stepId: step.id,
        stepName: step.name,
        inputs: { recipient: '' }
      });
    }
  });
  return result;
};

// Makes input keys human readable
const humanizeKey = (key) => {
  const map = {
    threshold:      'Alert Threshold',
    targetCurrency: 'Target Currency',
    recipient:      'Alert Email Address',
    inputKey:       'Email Input Key',
    baseUrl:        'API Base URL',
    limit:          'Row Limit',
    duration:       'Delay Duration (ms)',
  };
  return map[key] || key
    .replace(/([A-Z])/g, ' $1')
    .replace(/_/g, ' ')
    .replace(/^\w/, c => c.toUpperCase());
};

const STATUS_META = {
  PENDING:  { cls: 'ds-pending',  label: 'Pending',  color: '#f59e0b', bg: '#fef3c7' },
  ACTIVE:   { cls: 'ds-approved', label: 'Approved', color: '#10b981', bg: '#d1fae5' },
  REJECTED: { cls: 'ds-rejected', label: 'Rejected', color: '#ef4444', bg: '#fee2e2' },
};

const STEP_ICONS = {
  ACTION:    '⚡',
  HTTP:      '🌐',
  EMAIL:     '📧',
  LOG:       '📝',
  DELAY:     '⏱️',
  DATABASE:  '🗄️',
  SCRIPT:    '💻',
  WEBHOOK:   '🔗',
  TRIGGER:   '🎯',
  CONDITION: '🔀',
};

const EXAMPLE_PROMPTS = [
  'Send a weekly summary email to all users every Monday morning',
  'Monitor CPU usage every 5 minutes and alert the on-call engineer if it exceeds 90%',
  'Fetch exchange rates daily and notify the finance team if USD/INR crosses 85',
  'Check all critical service endpoints every hour and log their status',
  'Pull daily sales data and send a report to management every evening',
];

// ── Component ─────────────────────────────────────────────────────────────────

const AIDrafts = () => {
  const navigate = useNavigate();
  const textareaRef = useRef(null);

  const [drafts, setDrafts]             = useState([]);
  const [loading, setLoading]           = useState(true);
  const [prompt, setPrompt]             = useState('');
  const [generating, setGenerating]     = useState(false);
  const [expandedId, setExpandedId]     = useState(null);
  const [approvingId, setApprovingId]   = useState(null);
  const [rejectingId, setRejectingId]   = useState(null);
  const [toast, setToast]               = useState(null);
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [justCreatedId, setJustCreatedId] = useState(null);
  const [emailConfigs, setEmailConfigs] = useState({});
  const [smartConfigs, setSmartConfigs] = useState({});
  const [savingEmailId, setSavingEmailId] = useState(null);
  const [refreshingDraftId, setRefreshingDraftId] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  const handleRefreshDraft = async (draftId) => {
    setRefreshingDraftId(draftId);
    try {
      const res = await getDraft(draftId);
      const updated = res.data;
      setDrafts(prev => prev.map(d => d.id === draftId ? updated : d));
    } catch {
      showToast('Failed to refresh draft', 'error');
    } finally {
      setRefreshingDraftId(null);
    }
  };

  const fetchDrafts = useCallback(async () => {
    try {
      const res = await getDrafts();
      const sorted = asArray(res.data).sort(
        (a, b) => new Date(b.createdAt) - new Date(a.createdAt)
      );
      setDrafts(sorted);
    } catch {
      showToast('Failed to load drafts', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchDrafts(); }, [fetchDrafts]);

  // ── Save email recipient config (legacy panel) ─────────────────────────────

  const handleSaveEmailConfig = async (draftId, stepId) => {
    const key = `${draftId}_${stepId}`;
    const cfg = emailConfigs[key] || {};
    setSavingEmailId(key);
    try {
      await configureDraftEmailRecipient(draftId, {
        stepId,
        recipientSource: cfg.recipientSource || 'FIXED',
        recipient:       cfg.recipient       || null,
        inputKey:        cfg.inputKey        || null,
        previousStepId:  cfg.previousStepId  || null,
        outputField:     cfg.outputField     || null,
      });
      showToast('Recipient configured!');
      await fetchDrafts();
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to save email config', 'error');
    } finally {
      setSavingEmailId(null);
    }
  };

  // ── Generate draft ─────────────────────────────────────────────────────────

  const handleGenerate = async (e) => {
    e.preventDefault();
    if (!prompt.trim()) return;
    setGenerating(true);
    try {
      const res = await createDraft(prompt.trim());
      const draftId = res.data?.draftId;
      setPrompt('');
      await fetchDrafts();
      setJustCreatedId(draftId);
      setExpandedId(draftId);
      showToast('Draft generated successfully!');
      setTimeout(() => setJustCreatedId(null), 4000);
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to generate draft', 'error');
    } finally {
      setGenerating(false);
    }
  };

  // ── Approve draft ──────────────────────────────────────────────────────────

  const handleApprove = async (draft) => {
    setApprovingId(draft.id);

    // Collect all smart config overrides for this draft
    const inputOverrides = {};
    Object.entries(smartConfigs).forEach(([cfgKey, value]) => {
      if (!cfgKey.startsWith(draft.id + '_')) return;
      // cfgKey = draftId_stepId_inputKey
      const remainder = cfgKey.slice(draft.id.length + 1);
      const firstUnderscore = remainder.indexOf('_');
      if (firstUnderscore === -1) return;
      const stepId = remainder.slice(0, firstUnderscore);
      const inputKey = remainder.slice(firstUnderscore + 1);
      if (!inputOverrides[stepId]) inputOverrides[stepId] = {};
      inputOverrides[stepId][inputKey] = value;
    });

    try {
      const res = await approveDraft(draft.id, inputOverrides);
      const workflowId = res.data?.workflowId;
      await fetchDrafts();
      showToast('Workflow created from draft!');
      if (workflowId) {
        setTimeout(() => navigate(`/workflows/${workflowId}`), 1200);
      }
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to approve draft', 'error');
    } finally {
      setApprovingId(null);
    }
  };

  // ── Reject draft ───────────────────────────────────────────────────────────

  const handleReject = async (id) => {
    setRejectingId(id);
    try {
      await rejectDraft(id);
      await fetchDrafts();
      showToast('Draft rejected');
    } catch {
      setDrafts(prev => prev.map(d => d.id === id ? { ...d, status: 'REJECTED' } : d));
      showToast('Draft rejected');
    } finally {
      setRejectingId(null);
    }
  };

  // ── Computed ───────────────────────────────────────────────────────────────

  const pendingCount  = drafts.filter(d => d.status === 'PENDING').length;
  const approvedCount = drafts.filter(d => d.status === 'ACTIVE').length;
  const rejectedCount = drafts.filter(d => d.status === 'REJECTED').length;

  const filtered = drafts.filter(d =>
    filterStatus === 'ALL' ? true : d.status === filterStatus
  );

  const applyExample = (ex) => {
    setPrompt(ex);
    textareaRef.current?.focus();
  };

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div /><div /><div /><div /></div>
      <p>Loading AI drafts...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={showToast}>
      <div className="ai-page">

        {/* ── Top Bar ── */}
        <div className="topbar">
          <div>
            <div className="breadcrumb">
              <span>Dashboard</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="9 18 15 12 9 6" />
              </svg>
              <span className="bc-current">AI Drafts</span>
            </div>
            <h1 className="page-heading">AI Workflow Builder</h1>
          </div>
          {pendingCount > 0 && (
            <div className="pending-notice">
              <div className="pending-dot" />
              {pendingCount} draft{pendingCount !== 1 ? 's' : ''} awaiting review
            </div>
          )}
        </div>

        {/* ── Prompt Box ── */}
        <div className="prompt-card">
          <div className="prompt-card-header">
            <div className="ai-badge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
              AI-Powered
            </div>
            <h2 className="prompt-title">Describe your automation</h2>
            <p className="prompt-sub">
              Tell us what you want to automate in plain English — the AI will generate a structured workflow draft for you to review and approve.
            </p>
          </div>

          <form onSubmit={handleGenerate}>
            <div className="prompt-input-wrap">
              <textarea
                ref={textareaRef}
                className="prompt-textarea"
                placeholder="e.g. Every Monday morning, fetch all active users from the database and send each one a weekly summary email with their stats..."
                value={prompt}
                onChange={e => setPrompt(e.target.value)}
                rows={4}
                maxLength={500}
                disabled={generating}
              />
              <div className="prompt-footer">
                <span className="char-count">{prompt.length}/500</span>
                <button
                  type="submit"
                  className={`btn-generate ${generating ? 'btn-generating' : ''}`}
                  disabled={generating || !prompt.trim()}
                >
                  {generating ? (
                    <>
                      <span className="gen-spinner" />
                      <span>Generating...</span>
                    </>
                  ) : (
                    <>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
                      </svg>
                      <span>Generate Draft</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </form>

          {/* Example Prompts */}
          <div className="examples-row">
            <span className="examples-label">Try an example:</span>
            <div className="examples-chips">
              {EXAMPLE_PROMPTS.map((ex, i) => (
                <button key={i} className="example-chip" onClick={() => applyExample(ex)} disabled={generating}>
                  {ex.length > 55 ? ex.slice(0, 55) + '…' : ex}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── Stats Row ── */}
        <div className="draft-stats-row">
          {[
            { label: 'Total Drafts',   value: drafts.length,  color: '#6366f1' },
            { label: 'Pending Review', value: pendingCount,   color: '#f59e0b' },
            { label: 'Approved',       value: approvedCount,  color: '#10b981' },
            { label: 'Rejected',       value: rejectedCount,  color: '#ef4444' },
          ].map((s, i) => (
            <div key={i} className="draft-stat-tile" style={{ '--c': s.color }}>
              <span className="dst-num">{s.value}</span>
              <span className="dst-lbl">{s.label}</span>
              <div className="dst-bar" />
            </div>
          ))}
        </div>

        {/* ── Filter Tabs ── */}
        <div className="drafts-toolbar">
          <h2 className="section-title">
            Draft History
            <span className="section-count">{drafts.length}</span>
          </h2>
          <div className="filter-tabs">
            {['ALL', 'PENDING', 'ACTIVE', 'REJECTED'].map(f => (
              <button
                key={f}
                className={`filter-tab ${filterStatus === f ? 'tab-active' : ''}`}
                onClick={() => setFilterStatus(f)}
              >
                {f === 'ALL' ? `All (${drafts.length})` : STATUS_META[f]?.label}
                {f === 'PENDING' && pendingCount > 0 && (
                  <span className="tab-badge">{pendingCount}</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* ── Draft List ── */}
        {filtered.length === 0 ? (
          <div className="drafts-empty">
            <div className="empty-glyph">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
            </div>
            <h3>{filterStatus === 'ALL' ? 'No drafts yet' : `No ${filterStatus.toLowerCase()} drafts`}</h3>
            <p>
              {filterStatus === 'ALL'
                ? 'Type a prompt above and click Generate Draft to get started'
                : `Switch to "All" to see other drafts`}
            </p>
          </div>
        ) : (
          <div className="drafts-list">
            {filtered.map((draft, i) => {
              const content    = parseDraftContent(draft.jsonContent);
              const steps      = content?.steps || [];
              const isExpanded = expandedId === draft.id;
              const isNew      = justCreatedId === draft.id;
              const sm         = STATUS_META[draft.status] || STATUS_META.PENDING;
              const isPending  = draft.status === 'PENDING';

              return (
                <div
                  key={draft.id}
                  className={`draft-card ${isExpanded ? 'draft-open' : ''} ${isNew ? 'draft-new' : ''}`}
                  style={{ animationDelay: `${i * 0.04}s` }}
                >
                  {/* Card Header Row */}
                  <div className="draft-row" onClick={() => setExpandedId(isExpanded ? null : draft.id)}>
                    <div className="draft-row-left">
                      <div className="draft-ai-icon">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                        </svg>
                      </div>
                      <div className="draft-info">
                        <div className="draft-name-row">
                          <span className="draft-name">{content?.name || 'Untitled Draft'}</span>
                          {isNew && <span className="new-chip">NEW</span>}
                          <span className="draft-step-count">{steps.length} step{steps.length !== 1 ? 's' : ''}</span>
                        </div>
                        <div className="draft-meta">
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
                          </svg>
                          {fmt(draft.createdAt)}
                          <span className="draft-id-mono">{draft.id.slice(0, 12)}...</span>
                          {draft.approvedWorkflowId && (
                            <button
                              className="view-workflow-link"
                              onClick={e => { e.stopPropagation(); navigate(`/workflows/${draft.approvedWorkflowId}`); }}
                            >
                              View Workflow →
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="draft-row-right">
                      <span className="draft-status-badge" style={{ background: sm.bg, color: sm.color }}>
                        {sm.label}
                      </span>
                      {content?.confidence != null && (
                        <span className="confidence-chip" title="AI confidence score">
                          {Math.round(content.confidence * 100)}%
                        </span>
                      )}
                      <button
                        className="btn-draft-refresh"
                        onClick={e => {
                          e.stopPropagation();
                          handleRefreshDraft(draft.id);
                        }}
                        disabled={refreshingDraftId === draft.id}
                        title="Refresh draft"
                      >
                        <svg
                          width="13"
                          height="13"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          style={{ animation: refreshingDraftId === draft.id ? 'ring 0.8s linear infinite' : 'none' }}
                        >
                          <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                        </svg>
                      </button>
                      <div className={`expand-arrow ${isExpanded ? 'arrow-up' : ''}`}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <polyline points="6 9 12 15 18 9" />
                        </svg>
                      </div>
                    </div>
                  </div>

                  {/* Expanded Panel */}
                  {isExpanded && (
                    <div className="draft-panel">

                      {/* Steps Pipeline */}
                      <div className="draft-steps-section">
                        <div className="ds-label">Generated Steps</div>
                        {steps.length === 0 ? (
                          <div className="ds-empty">No steps in this draft</div>
                        ) : (
                          <div className="ds-pipeline">
                            {steps.map((step, si) => {
                              const icon = STEP_ICONS[step.stepType] || '⚙️';
                              const deps = step.dependsOn || [];
                              return (
                                <div key={step.id || si} className="ds-step">
                                  {si > 0 && <div className="ds-connector" />}
                                  <div className="ds-step-card">
                                    <div className="ds-step-left">
                                      <div className="ds-order">{si + 1}</div>
                                      <span className="ds-emoji">{icon}</span>
                                      <div className="ds-step-body">
                                        <div className="ds-step-name">{step.name}</div>
                                        <div className="ds-step-meta">
                                          {step.stepType && (
                                            <span className="ds-type-chip">{step.stepType}</span>
                                          )}
                                          {deps.length > 0 && (
                                            <span className="ds-dep-chip">
                                              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                                <polyline points="17 1 21 5 17 9" />
                                                <path d="M3 11V9a4 4 0 0 1 4-4h14" />
                                              </svg>
                                              after {deps[0].slice(0, 6)}...
                                            </span>
                                          )}
                                        </div>
                                      </div>
                                    </div>
                                    <div className="ds-step-id">{(step.id || '').slice(0, 8)}</div>
                                  </div>
                                </div>
                              );
                            })}
                            <div className="ds-end">
                              <div className="ds-connector" />
                              <div className="ds-end-chip">
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                  <polyline points="20 6 9 17 4 12" />
                                </svg>
                                Workflow Complete
                              </div>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Raw JSON toggle */}
                      <details className="raw-json-block">
                        <summary className="raw-json-toggle">View raw JSON</summary>
                        <pre className="raw-json-pre">{JSON.stringify(content, null, 2)}</pre>
                      </details>

                      {/* ── Smart Configuration Panel ── */}
                      {isPending && (() => {
                        const configurables = getConfigurableInputs(content);
                        if (configurables.length === 0) return null;
                        return (
                          <div style={{
                            background: '#f0f9ff', border: '1px solid #bae6fd',
                            borderRadius: 8, padding: '16px', marginBottom: 14
                          }}>
                            <div style={{
                              fontWeight: 600, color: '#0369a1', fontSize: 13,
                              marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8
                            }}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#0369a1" strokeWidth="2">
                                <circle cx="12" cy="12" r="3" />
                                <path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83" />
                              </svg>
                              Configure Your Workflow
                            </div>
                            <p style={{ fontSize: 12, color: '#0369a1', marginBottom: 14, marginTop: 0 }}>
                              Set your preferences below — no technical knowledge needed.
                            </p>
                            {configurables.map(({ stepId, stepName, inputs }) => (
                              <div key={stepId} style={{
                                background: '#fff', borderRadius: 6,
                                padding: '12px 14px', marginBottom: 10,
                                border: '1px solid #e0f2fe'
                              }}>
                                <div style={{
                                  fontSize: 11, fontWeight: 600, color: '#64748b',
                                  marginBottom: 10, textTransform: 'uppercase', letterSpacing: '0.5px'
                                }}>
                                  {stepName}
                                </div>
                                {Object.entries(inputs).map(([key, defaultVal]) => {
                                  const cfgKey = `${draft.id}_${stepId}_${key}`;
                                  const currentVal = smartConfigs[cfgKey] !== undefined
                                    ? smartConfigs[cfgKey]
                                    : defaultVal;
                                  return (
                                    <div key={key} style={{ marginBottom: 10 }}>
                                      <label style={{
                                        fontSize: 12, color: '#374151',
                                        display: 'block', marginBottom: 4, fontWeight: 500
                                      }}>
                                        {humanizeKey(key)}
                                      </label>
                                      <input
                                        type={
                                          key === 'recipient' ? 'email'
                                          : typeof defaultVal === 'number' ? 'number'
                                          : 'text'
                                        }
                                        value={currentVal}
                                        placeholder={`e.g. ${defaultVal}`}
                                        onChange={e => setSmartConfigs(prev => ({
                                          ...prev,
                                          [cfgKey]: typeof defaultVal === 'number'
                                            ? parseFloat(e.target.value) || defaultVal
                                            : e.target.value
                                        }))}
                                        style={{
                                          width: '100%', padding: '8px 10px',
                                          borderRadius: 6, fontSize: 13,
                                          border: '1px solid #bae6fd',
                                          boxSizing: 'border-box'
                                        }}
                                      />
                                      <p style={{ fontSize: 11, color: '#94a3b8', margin: '3px 0 0' }}>
                                        Default: {String(defaultVal)}
                                      </p>
                                    </div>
                                  );
                                })}
                              </div>
                            ))}
                          </div>
                        );
                      })()}

                      {/* ── Legacy Email Recipient Panel ──
                          Only shown for EMAIL steps NOT already covered by smart config panel */}
                      {isPending && (() => {
                        const unconfigured = getUnconfiguredEmailSteps(content, smartConfigs, draft.id);
                        if (unconfigured.length === 0) return null;
                        return (
                          <div style={{
                            background: '#fef3c7', border: '1px solid #fcd34d',
                            borderRadius: 8, padding: '14px 16px', marginBottom: 14
                          }}>
                            <div style={{
                              display: 'flex', alignItems: 'center', gap: 8,
                              marginBottom: 12, fontWeight: 600, color: '#92400e', fontSize: 13
                            }}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#92400e" strokeWidth="2">
                                <circle cx="12" cy="12" r="10" />
                                <line x1="12" y1="8" x2="12" y2="12" />
                                <line x1="12" y1="16" x2="12.01" y2="16" />
                              </svg>
                              {unconfigured.length} EMAIL step{unconfigured.length > 1 ? 's' : ''} need recipient configuration before this draft can be approved.
                            </div>
                            {unconfigured.map(step => {
                              const key = `${draft.id}_${step.id}`;
                              const cfg = emailConfigs[key] || {
                                recipientSource: 'FIXED', recipient: '',
                                inputKey: '', previousStepId: '', outputField: ''
                              };
                              const setField = (field, val) =>
                                setEmailConfigs(prev => ({ ...prev, [key]: { ...cfg, [field]: val } }));
                              const canSave = cfg.recipientSource === 'FIXED'
                                ? !!cfg.recipient?.trim()
                                : cfg.recipientSource === 'WORKFLOW_INPUT'
                                ? !!cfg.inputKey?.trim()
                                : (!!cfg.previousStepId?.trim() && !!cfg.outputField?.trim());
                              return (
                                <div key={step.id} style={{
                                  background: '#fff', borderRadius: 6,
                                  padding: '12px 14px', marginBottom: 10,
                                  border: '1px solid #e5e7eb'
                                }}>
                                  <div style={{ fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
                                    📧 {step.name}
                                  </div>
                                  <div style={{ marginBottom: 8 }}>
                                    <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 3 }}>
                                      Recipient Source
                                    </label>
                                    <select
                                      style={{ width: '100%', padding: '6px 8px', borderRadius: 5, border: '1px solid #d1d5db', fontSize: 12, background: '#fff' }}
                                      value={cfg.recipientSource || 'FIXED'}
                                      onChange={e => setField('recipientSource', e.target.value)}
                                    >
                                      <option value="FIXED">Fixed address</option>
                                      <option value="WORKFLOW_INPUT">Workflow input key</option>
                                      <option value="PREVIOUS_STEP_OUTPUT">Previous step output</option>
                                    </select>
                                  </div>
                                  {(cfg.recipientSource || 'FIXED') === 'FIXED' && (
                                    <div style={{ marginBottom: 8 }}>
                                      <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 3 }}>
                                        Email Address *
                                      </label>
                                      <input
                                        type="email"
                                        placeholder="user@example.com"
                                        value={cfg.recipient || ''}
                                        onChange={e => setField('recipient', e.target.value)}
                                        style={{ width: '100%', padding: '6px 8px', borderRadius: 5, border: '1px solid #d1d5db', fontSize: 12, boxSizing: 'border-box' }}
                                      />
                                    </div>
                                  )}
                                  {cfg.recipientSource === 'WORKFLOW_INPUT' && (
                                    <div style={{ marginBottom: 8 }}>
                                      <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 3 }}>
                                        Input Key *
                                      </label>
                                      <input
                                        type="text"
                                        placeholder="e.g. recipientEmail"
                                        value={cfg.inputKey || ''}
                                        onChange={e => setField('inputKey', e.target.value)}
                                        style={{ width: '100%', padding: '6px 8px', borderRadius: 5, border: '1px solid #d1d5db', fontSize: 12, boxSizing: 'border-box' }}
                                      />
                                    </div>
                                  )}
                                  {cfg.recipientSource === 'PREVIOUS_STEP_OUTPUT' && (
                                    <>
                                      <div style={{ marginBottom: 6 }}>
                                        <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 3 }}>
                                          Previous Step ID *
                                        </label>
                                        <input
                                          type="text"
                                          placeholder="step-1"
                                          value={cfg.previousStepId || ''}
                                          onChange={e => setField('previousStepId', e.target.value)}
                                          style={{ width: '100%', padding: '6px 8px', borderRadius: 5, border: '1px solid #d1d5db', fontSize: 12, boxSizing: 'border-box' }}
                                        />
                                      </div>
                                      <div style={{ marginBottom: 8 }}>
                                        <label style={{ fontSize: 11, color: '#6b7280', display: 'block', marginBottom: 3 }}>
                                          Output Field *
                                        </label>
                                        <input
                                          type="text"
                                          placeholder="email"
                                          value={cfg.outputField || ''}
                                          onChange={e => setField('outputField', e.target.value)}
                                          style={{ width: '100%', padding: '6px 8px', borderRadius: 5, border: '1px solid #d1d5db', fontSize: 12, boxSizing: 'border-box' }}
                                        />
                                      </div>
                                    </>
                                  )}
                                  <button
                                    onClick={() => handleSaveEmailConfig(draft.id, step.id)}
                                    disabled={!canSave || savingEmailId === key}
                                    style={{
                                      fontSize: 12, padding: '6px 14px', borderRadius: 5,
                                      background: canSave ? '#6366f1' : '#e5e7eb',
                                      color: canSave ? '#fff' : '#9ca3af',
                                      border: 'none', cursor: canSave ? 'pointer' : 'not-allowed', fontWeight: 500
                                    }}
                                  >
                                    {savingEmailId === key ? 'Saving...' : 'Save Recipient'}
                                  </button>
                                </div>
                              );
                            })}
                          </div>
                        );
                      })()}

                      {/* ── Action Buttons ── */}
                      {isPending && (() => {
                        const unconfigured = getUnconfiguredEmailSteps(content, smartConfigs, draft.id);
                        const hasUnconfigured = unconfigured.length > 0;
                        return (
                          <div className="draft-actions">
                            <div className="action-hint">
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <circle cx="12" cy="12" r="10" />
                                <line x1="12" y1="16" x2="12" y2="12" />
                                <line x1="12" y1="8" x2="12.01" y2="8" />
                              </svg>
                              {hasUnconfigured
                                ? 'Configure all email recipients above before approving'
                                : 'Approving will create a live workflow from this draft'}
                            </div>
                            <div className="action-btns">
                              <button
                                className="btn-reject"
                                onClick={() => handleReject(draft.id)}
                                disabled={!!rejectingId || !!approvingId}
                              >
                                {rejectingId === draft.id
                                  ? <><span className="spin-xs" />Rejecting...</>
                                  : <>
                                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                                      </svg>
                                      Reject
                                    </>
                                }
                              </button>
                              <button
                                className="btn-approve"
                                onClick={() => handleApprove(draft)}
                                disabled={!!approvingId || !!rejectingId || hasUnconfigured}
                                title={hasUnconfigured ? 'Configure all email recipients first' : ''}
                              >
                                {approvingId === draft.id
                                  ? <><span className="spin-xs spin-dark" />Approving...</>
                                  : <>
                                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                        <polyline points="20 6 9 17 4 12" />
                                      </svg>
                                      Approve &amp; Create Workflow
                                    </>
                                }
                              </button>
                            </div>
                          </div>
                        );
                      })()}

                      {draft.status === 'ACTIVE' && draft.approvedWorkflowId && (
                        <div className="approved-notice">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5">
                            <polyline points="20 6 9 17 4 12" />
                          </svg>
                          Workflow created successfully.
                          <button
                            className="view-wf-btn"
                            onClick={() => navigate(`/workflows/${draft.approvedWorkflowId}`)}
                          >
                            View Workflow →
                          </button>
                        </div>
                      )}

                      {draft.status === 'REJECTED' && (
                        <div className="rejected-notice">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2">
                            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                          </svg>
                          This draft was rejected.
                        </div>
                      )}

                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default AIDrafts;