import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { createDraft, getDrafts, approveDraft, rejectDraft } from '../services/api';
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

const STATUS_META = {
  PENDING:  { cls: 'ds-pending',  label: 'Pending',  color: '#f59e0b', bg: '#fef3c7' },
  APPROVED: { cls: 'ds-approved', label: 'Approved', color: '#10b981', bg: '#d1fae5' },
  REJECTED: { cls: 'ds-rejected', label: 'Rejected', color: '#ef4444', bg: '#fee2e2' },
};

const STEP_ICONS = {
  ACTION:   '⚡',
  HTTP:     '🌐',
  EMAIL:    '📧',
  LOG:      '📝',
  DELAY:    '⏱️',
  DATABASE: '🗄️',
  SCRIPT:   '💻',
  WEBHOOK:  '🔗',
  TRIGGER:  '🎯',
  CONDITION:'🔀',
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

  const [drafts, setDrafts]           = useState([]);
  const [loading, setLoading]         = useState(true);
  const [prompt, setPrompt]           = useState('');
  const [generating, setGenerating]   = useState(false);
  const [expandedId, setExpandedId]   = useState(null);
  const [approvingId, setApprovingId] = useState(null);
  const [rejectingId, setRejectingId] = useState(null);
  const [toast, setToast]             = useState(null);
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [justCreatedId, setJustCreatedId] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  const fetchDrafts = useCallback(async () => {
    try {
      const res = await getDrafts();
      const sorted = (res.data || []).sort(
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

  // ── Generate draft ─────────────────────────────────────────────────────────

  const handleGenerate = async (e) => {
    e.preventDefault();
    if (!prompt.trim()) return;
    setGenerating(true);
    try {
      const res = await createDraft(prompt.trim());
      const newDraft = res.data;
      setJustCreatedId(newDraft.id);
      setPrompt('');
      await fetchDrafts();
      setExpandedId(newDraft.id);
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
    try {
      const res = await approveDraft(draft.id);
      const workflowId = res.data?.id || res.data?.approvedWorkflowId;
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
      // Some backends may not have a reject endpoint; update locally
      setDrafts(prev => prev.map(d => d.id === id ? { ...d, status: 'REJECTED' } : d));
      showToast('Draft rejected');
    } finally {
      setRejectingId(null);
    }
  };

  // ── Computed ───────────────────────────────────────────────────────────────

  const pendingCount  = drafts.filter(d => d.status === 'PENDING').length;
  const approvedCount = drafts.filter(d => d.status === 'APPROVED').length;
  const rejectedCount = drafts.filter(d => d.status === 'REJECTED').length;

  const filtered = drafts.filter(d =>
    filterStatus === 'ALL' ? true : d.status === filterStatus
  );

  const useExample = (ex) => {
    setPrompt(ex);
    textareaRef.current?.focus();
  };

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
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
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-current">AI Drafts</span>
            </div>
            <h1 className="page-heading">AI Workflow Builder</h1>
          </div>
          {pendingCount > 0 && (
            <div className="pending-notice">
              <div className="pending-dot"/>
              {pendingCount} draft{pendingCount !== 1 ? 's' : ''} awaiting review
            </div>
          )}
        </div>

        {/* ── Prompt Box ── */}
        <div className="prompt-card">
          <div className="prompt-card-header">
            <div className="ai-badge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
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
                      <span className="gen-spinner"/>
                      <span>Generating...</span>
                    </>
                  ) : (
                    <>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
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
                <button key={i} className="example-chip" onClick={() => useExample(ex)} disabled={generating}>
                  {ex.length > 55 ? ex.slice(0, 55) + '…' : ex}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── Stats Row ── */}
        <div className="draft-stats-row">
          {[
            { label: 'Total Drafts', value: drafts.length, color: '#6366f1' },
            { label: 'Pending Review', value: pendingCount, color: '#f59e0b' },
            { label: 'Approved', value: approvedCount, color: '#10b981' },
            { label: 'Rejected', value: rejectedCount, color: '#ef4444' },
          ].map((s, i) => (
            <div key={i} className="draft-stat-tile" style={{ '--c': s.color }}>
              <span className="dst-num">{s.value}</span>
              <span className="dst-lbl">{s.label}</span>
              <div className="dst-bar"/>
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
            {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map(f => (
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
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
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
              const content   = parseDraftContent(draft.jsonContent);
              const steps     = content?.steps || [];
              const isExpanded = expandedId === draft.id;
              const isNew     = justCreatedId === draft.id;
              const sm        = STATUS_META[draft.status] || STATUS_META.PENDING;
              const isPending = draft.status === 'PENDING';

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
                          <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
                        </svg>
                      </div>
                      <div className="draft-info">
                        <div className="draft-name-row">
                          <span className="draft-name">{content?.name || 'Untitled Draft'}</span>
                          {isNew && <span className="new-chip">NEW</span>}
                          <span className="draft-step-count">{steps.length} step{steps.length !== 1 ? 's' : ''}</span>
                        </div>
                        <div className="draft-meta">
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
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
                      <div className={`expand-arrow ${isExpanded ? 'arrow-up' : ''}`}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"/></svg>
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
                                  {si > 0 && <div className="ds-connector"/>}
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
                                              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/></svg>
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
                              <div className="ds-connector"/>
                              <div className="ds-end-chip">
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
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

                      {/* Action Buttons */}
                      {isPending && (
                        <div className="draft-actions">
                          <div className="action-hint">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                            Approving will create a live workflow from this draft
                          </div>
                          <div className="action-btns">
                            <button
                              className="btn-reject"
                              onClick={() => handleReject(draft.id)}
                              disabled={!!rejectingId || !!approvingId}
                            >
                              {rejectingId === draft.id
                                ? <><span className="spin-xs"/>Rejecting...</>
                                : <>
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                    Reject
                                  </>
                              }
                            </button>
                            <button
                              className="btn-approve"
                              onClick={() => handleApprove(draft)}
                              disabled={!!approvingId || !!rejectingId}
                            >
                              {approvingId === draft.id
                                ? <><span className="spin-xs spin-dark"/>Approving...</>
                                : <>
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                                    Approve & Create Workflow
                                  </>
                              }
                            </button>
                          </div>
                        </div>
                      )}

                      {draft.status === 'APPROVED' && draft.approvedWorkflowId && (
                        <div className="approved-notice">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
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
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
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