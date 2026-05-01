import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { getWorkflows, getExecutions } from '../services/api';
import axios from 'axios';
import '../styles/Executions.css';

const api = axios.create({ baseURL: 'http://localhost:8080/api' });
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
const getStepExecutions = (executionId) => api.get(`/executions/${executionId}/steps`);

const STATUS_CONFIG = {
  SUCCESS: { cls: 'status-success', label: 'Success', dot: '#10b981', bg: '#dcfce7', color: '#15803d' },
  FAILED:  { cls: 'status-failed',  label: 'Failed',  dot: '#ef4444', bg: '#fee2e2', color: '#b91c1c' },
  RUNNING: { cls: 'status-running', label: 'Running', dot: '#6366f1', bg: '#eff6ff', color: '#1d4ed8' },
  SKIPPED: { cls: 'status-skipped', label: 'Skipped', dot: '#94a3b8', bg: '#f1f5f9', color: '#64748b' },
  PENDING: { cls: 'status-pending', label: 'Pending', dot: '#f59e0b', bg: '#fef9c3', color: '#92400e' },
};

const formatDate = (d) => d
  ? new Date(d).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : '—';

const calcDuration = (start, end) => {
  if (!start || !end) return null;
  const ms = new Date(end) - new Date(start);
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms/1000).toFixed(1)}s`;
  return `${Math.floor(ms/60000)}m ${Math.floor((ms%60000)/1000)}s`;
};

const Executions = () => {
  const { id: workflowIdParam } = useParams(); // may be undefined on /executions
  const navigate = useNavigate();

  const [workflows, setWorkflows] = useState([]);
  const [allExecutions, setAllExecutions] = useState([]); // [{...exec, workflowName}]
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);
  const [stepsMap, setStepsMap] = useState({});
  const [stepsLoading, setStepsLoading] = useState({});
  const [toast, setToast] = useState(null);
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [search, setSearch] = useState('');

  const showToast = (msg, type = 'info') => {
    setToast({ message: msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchAll = useCallback(async () => {
    try {
      const wfRes = await getWorkflows();
      const wfs = wfRes.data || [];
      setWorkflows(wfs);

      const targetWfs = workflowIdParam
        ? wfs.filter(w => w.id === workflowIdParam)
        : wfs;

      const execArrays = await Promise.all(
        targetWfs.map(wf =>
          getExecutions(wf.id)
            .then(r => (r.data || []).map(e => ({ ...e, workflowName: wf.name, workflowId: wf.id })))
            .catch(() => [])
        )
      );
      const sorted = execArrays.flat().sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
      setAllExecutions(sorted);
    } catch {
      showToast('Failed to load executions', 'error');
    } finally {
      setLoading(false);
    }
  }, [workflowIdParam]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const toggleExpand = async (execId) => {
    if (expandedId === execId) { setExpandedId(null); return; }
    setExpandedId(execId);
    if (stepsMap[execId]) return;
    setStepsLoading(p => ({ ...p, [execId]: true }));
    try {
      const res = await getStepExecutions(execId);
      setStepsMap(p => ({ ...p, [execId]: res.data || [] }));
    } catch {
      setStepsMap(p => ({ ...p, [execId]: [] }));
    } finally {
      setStepsLoading(p => ({ ...p, [execId]: false }));
    }
  };

  const filtered = allExecutions.filter(e => {
    const matchStatus = filterStatus === 'ALL' || e.status === filterStatus;
    const q = search.toLowerCase();
    const matchSearch = !q || (e.workflowName || '').toLowerCase().includes(q) || e.id.toLowerCase().includes(q);
    return matchStatus && matchSearch;
  });

  const successCount = allExecutions.filter(e => e.status === 'SUCCESS').length;
  const failedCount  = allExecutions.filter(e => e.status === 'FAILED').length;
  const rate = allExecutions.length > 0 ? Math.round((successCount / allExecutions.length) * 100) : 0;

  const pageTitle = workflowIdParam
    ? `Executions — ${workflows.find(w => w.id === workflowIdParam)?.name || workflowIdParam.slice(0,8)}`
    : 'All Executions';

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
      <p>Loading execution history...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={showToast}>
      <div className="exec-page">
        <div className="topbar">
          <div>
            <div className="breadcrumb">
              <span>Dashboard</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-current">Executions</span>
            </div>
            <h1 className="page-heading">{pageTitle}</h1>
          </div>
          {workflowIdParam && (
            <button className="btn-back" onClick={() => navigate(`/workflows/${workflowIdParam}`)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6"/></svg>
              Back to Workflow
            </button>
          )}
        </div>

        {/* Stats */}
        <div className="exec-stats-row">
          {[
            { label: 'Total Runs', value: allExecutions.length, color: '#6366f1' },
            { label: 'Successful', value: successCount, color: '#10b981' },
            { label: 'Failed', value: failedCount, color: '#ef4444' },
            { label: 'Success Rate', value: `${rate}%`, color: '#f59e0b' },
          ].map((s, i) => (
            <div key={i} className="exec-stat-tile" style={{ '--c': s.color }}>
              <span className="exec-stat-val">{s.value}</span>
              <span className="exec-stat-lbl">{s.label}</span>
              <div className="exec-stat-bar"/>
            </div>
          ))}
        </div>

        {/* Filters */}
        <div className="exec-toolbar">
          <div className="search-box">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input
              placeholder="Search by workflow name or execution ID..."
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <div className="filter-tabs">
            {['ALL', 'SUCCESS', 'FAILED', 'RUNNING'].map(s => (
              <button key={s} className={`filter-tab ${filterStatus === s ? 'tab-active' : ''}`} onClick={() => setFilterStatus(s)}>
                {s === 'ALL' ? 'All' : STATUS_CONFIG[s]?.label || s}
              </button>
            ))}
          </div>
        </div>

        {/* List */}
        {filtered.length === 0 ? (
          <div className="exec-empty">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            <h3>No executions found</h3>
            <p>Run a workflow to see execution history here</p>
          </div>
        ) : (
          <div className="exec-list">
            {filtered.map((exec, i) => {
              const cfg = STATUS_CONFIG[exec.status] || STATUS_CONFIG.PENDING;
              const isExpanded = expandedId === exec.id;
              const steps = stepsMap[exec.id] || [];
              const dur = calcDuration(exec.startedAt, exec.completedAt);

              return (
                <div key={exec.id} className={`exec-card ${isExpanded ? 'exec-card-open' : ''}`} style={{ animationDelay: `${i * 0.03}s` }}>
                  <div className="exec-row" onClick={() => toggleExpand(exec.id)}>
                    <div className="exec-row-left">
                      <div className="status-dot" style={{ background: cfg.dot }}/>
                      <div className="exec-info">
                        <div className="exec-run-label">
                          <span className="exec-workflow-name">{exec.workflowName}</span>
                          <span className="exec-id-mono">{exec.id.slice(0,14)}...</span>
                        </div>
                        <div className="exec-timestamps">
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                          {formatDate(exec.startedAt)}
                          {dur && <span className="exec-dur">· {dur}</span>}
                        </div>
                      </div>
                    </div>
                    <div className="exec-row-right">
                      <span className="exec-status-badge" style={{ background: cfg.bg, color: cfg.color }}>
                        <span className="badge-dot" style={{ background: cfg.dot }}/>
                        {cfg.label}
                      </span>
                      {exec.errorMessage && (
                        <span className="exec-error-chip" title={exec.errorMessage}>
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                          Error
                        </span>
                      )}
                      <div className={`expand-arrow ${isExpanded ? 'arrow-open' : ''}`}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"/></svg>
                      </div>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="steps-panel">
                      {exec.errorMessage && (
                        <div className="error-block">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                          <div>
                            <strong>Error</strong>
                            <p>{exec.errorMessage}</p>
                          </div>
                        </div>
                      )}

                      <div className="steps-panel-header">
                        <span>Step Executions</span>
                        <span className="steps-count-badge">
                          {stepsLoading[exec.id] ? '...' : `${steps.length} steps`}
                        </span>
                      </div>

                      {stepsLoading[exec.id] ? (
                        <div className="steps-loading-row">
                          <div className="spin-xs"/>Loading steps...
                        </div>
                      ) : steps.length === 0 ? (
                        <div className="steps-empty-msg">No step execution data available</div>
                      ) : (
                        <div className="steps-grid">
                          {steps.map((step, si) => {
                            const sCfg = STATUS_CONFIG[step.status] || STATUS_CONFIG.PENDING;
                            return (
                              <div key={step.id} className="step-exec-row" style={{ animationDelay: `${si * 0.04}s` }}>
                                <div className="step-exec-left">
                                  <div className="step-mini-dot" style={{ background: sCfg.dot }}/>
                                  <div>
                                    <div className="step-exec-id">{step.stepId?.slice(0,16) || step.id?.slice(0,16)}...</div>
                                    <div className="step-exec-time">
                                      Updated {step.updatedAt ? new Date(step.updatedAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'}
                                    </div>
                                  </div>
                                </div>
                                <div className="step-exec-right">
                                  <span className="step-attempt-chip">
                                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                                    {step.attemptCount} attempt{step.attemptCount !== 1 ? 's' : ''}
                                  </span>
                                  <span className="step-status-badge" style={{ background: sCfg.bg, color: sCfg.color }}>
                                    {sCfg.label}
                                  </span>
                                </div>
                              </div>
                            );
                          })}
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

export default Executions;