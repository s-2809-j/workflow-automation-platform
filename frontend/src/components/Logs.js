import React, { useState, useEffect, useCallback, useRef } from 'react';
import Layout from './Layout';
import { getWorkflows, getExecutions } from '../services/api';
import axios from 'axios';
import '../styles/Logs.css';

const api = axios.create({ baseURL: 'http://localhost:8080/api' });
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
const getStepExecutions = (executionId) => api.get(`/executions/${executionId}/steps`);

const LOG_LEVELS = {
  SUCCESS: { label: 'INFO',  cls: 'log-info',    prefix: '[INFO ]' },
  FAILED:  { label: 'ERROR', cls: 'log-error',   prefix: '[ERROR]' },
  RUNNING: { label: 'WARN',  cls: 'log-warn',    prefix: '[WARN ]' },
  PENDING: { label: 'DEBUG', cls: 'log-debug',   prefix: '[DEBUG]' },
  SKIPPED: { label: 'SKIP',  cls: 'log-skip',    prefix: '[SKIP ]' },
};

const fmt = (d) => d ? new Date(d).toISOString().replace('T', ' ').slice(0, 23) : '—';

const Logs = () => {
  const [workflows, setWorkflows] = useState([]);
  const [selectedWf, setSelectedWf] = useState('ALL');
  const [executions, setExecutions] = useState([]);
  const [selectedExec, setSelectedExec] = useState(null);
  const [logEntries, setLogEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(false);
  const [toast, setToast] = useState(null);
  const [levelFilter, setLevelFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [autoScroll, setAutoScroll] = useState(true);
  const logRef = useRef(null);

  const showToast = (msg, type = 'info') => {
    setToast({ message: msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchWorkflows = useCallback(async () => {
    try {
      const res = await getWorkflows();
      setWorkflows(res.data || []);
    } catch { showToast('Failed to load workflows', 'error'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchWorkflows(); }, [fetchWorkflows]);

  // When workflow filter changes, load executions
  useEffect(() => {
    const loadExecs = async () => {
      setExecutions([]);
      setSelectedExec(null);
      setLogEntries([]);
      if (selectedWf === 'ALL') {
        try {
          const arrays = await Promise.all(
            workflows.map(wf => getExecutions(wf.id).then(r => (r.data || []).map(e => ({ ...e, workflowName: wf.name }))).catch(() => []))
          );
          const sorted = arrays.flat().sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
          setExecutions(sorted.slice(0, 50)); // cap to 50 for perf
        } catch {}
      } else {
        try {
          const res = await getExecutions(selectedWf);
          const wf = workflows.find(w => w.id === selectedWf);
          const sorted = (res.data || []).map(e => ({ ...e, workflowName: wf?.name }))
            .sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
          setExecutions(sorted);
        } catch {}
      }
    };
    if (workflows.length > 0) loadExecs();
  }, [selectedWf, workflows]);

  // When execution selected, load step logs
  useEffect(() => {
    if (!selectedExec) { setLogEntries([]); return; }
    const loadLogs = async () => {
      setLogsLoading(true);
      try {
        const exec = executions.find(e => e.id === selectedExec);
        const stepsRes = await getStepExecutions(selectedExec);
        const steps = stepsRes.data || [];

        // Build log entries from execution + steps
        const entries = [];

        entries.push({
          ts: exec?.startedAt,
          level: 'RUNNING',
          message: `Workflow execution started — ID: ${selectedExec}`,
          source: 'WorkflowExecutionService',
        });

        steps.forEach((step, i) => {
          entries.push({
            ts: step.updatedAt,
            level: step.status === 'SUCCESS' ? 'SUCCESS' : step.status === 'FAILED' ? 'FAILED' : 'RUNNING',
            message: `Step ${i + 1} [${step.stepId?.slice(0,8) || step.id?.slice(0,8)}...] — ${step.status} after ${step.attemptCount} attempt${step.attemptCount !== 1 ? 's' : ''}`,
            source: 'WorkflowStepExecutionService',
          });
        });

        if (exec?.status === 'SUCCESS' || exec?.status === 'FAILED') {
          entries.push({
            ts: exec?.completedAt,
            level: exec.status,
            message: `Workflow execution ${exec.status.toLowerCase()}${exec.errorMessage ? ' — ' + exec.errorMessage : ''}`,
            source: 'WorkflowExecutionService',
          });
        }

        entries.sort((a, b) => new Date(a.ts) - new Date(b.ts));
        setLogEntries(entries);
      } catch { showToast('Failed to load logs', 'error'); }
      finally { setLogsLoading(false); }
    };
    loadLogs();
  }, [selectedExec, executions]);

  // Auto-scroll
  useEffect(() => {
    if (autoScroll && logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight;
    }
  }, [logEntries, autoScroll]);

  const filtered = logEntries.filter(e => {
    const matchLevel = levelFilter === 'ALL' || e.level === levelFilter;
    const matchSearch = !searchTerm || e.message.toLowerCase().includes(searchTerm.toLowerCase()) || e.source.toLowerCase().includes(searchTerm.toLowerCase());
    return matchLevel && matchSearch;
  });

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
      <p>Loading logs...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={showToast}>
      <div className="logs-page">
        <div className="topbar">
          <div>
            <div className="breadcrumb">
              <span>Dashboard</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-current">Logs</span>
            </div>
            <h1 className="page-heading">Execution Logs</h1>
          </div>
        </div>

        <div className="logs-layout">
          {/* Left: Selectors */}
          <div className="logs-sidebar">
            <div className="logs-sidebar-section">
              <p className="logs-section-label">Workflow</p>
              <select className="logs-select" value={selectedWf} onChange={e => setSelectedWf(e.target.value)}>
                <option value="ALL">All Workflows</option>
                {workflows.map(wf => (
                  <option key={wf.id} value={wf.id}>{wf.name}</option>
                ))}
              </select>
            </div>

            <div className="logs-sidebar-section">
              <p className="logs-section-label">Execution Run</p>
              {executions.length === 0 ? (
                <p className="logs-empty-msg">No executions found</p>
              ) : (
                <div className="exec-select-list">
                  {executions.map((exec, i) => {
                    const statusColors = { SUCCESS: '#10b981', FAILED: '#ef4444', RUNNING: '#6366f1' };
                    return (
                      <button
                        key={exec.id}
                        className={`exec-select-item ${selectedExec === exec.id ? 'exec-item-active' : ''}`}
                        onClick={() => setSelectedExec(exec.id)}
                      >
                        <div className="exec-item-dot" style={{ background: statusColors[exec.status] || '#aaa' }}/>
                        <div className="exec-item-info">
                          <span className="exec-item-name">{exec.workflowName || 'Workflow'}</span>
                          <span className="exec-item-time">
                            {exec.startedAt ? new Date(exec.startedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'}
                          </span>
                        </div>
                        <span className="exec-item-status" style={{ color: statusColors[exec.status] || '#aaa' }}>
                          {exec.status}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Right: Log Viewer */}
          <div className="logs-viewer">
            <div className="logs-toolbar">
              <div className="log-search">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <input placeholder="Search logs..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)}/>
              </div>
              <div className="log-level-filters">
                {['ALL', 'SUCCESS', 'FAILED', 'RUNNING'].map(l => (
                  <button key={l} className={`level-chip ${levelFilter === l ? 'level-active' : ''} level-${l.toLowerCase()}`} onClick={() => setLevelFilter(l)}>
                    {l === 'ALL' ? 'All' : LOG_LEVELS[l]?.label || l}
                  </button>
                ))}
              </div>
              <label className="autoscroll-toggle">
                <input type="checkbox" checked={autoScroll} onChange={e => setAutoScroll(e.target.checked)}/>
                <span>Auto-scroll</span>
              </label>
            </div>

            <div className="log-terminal" ref={logRef}>
              {!selectedExec ? (
                <div className="log-placeholder">
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
                  <p>Select an execution run to view logs</p>
                </div>
              ) : logsLoading ? (
                <div className="log-placeholder">
                  <div className="spin-xs" style={{borderTopColor:'#10b981'}}/>
                  <p style={{color:'#aaa'}}>Loading logs...</p>
                </div>
              ) : filtered.length === 0 ? (
                <div className="log-placeholder">
                  <p style={{color:'#555'}}>No log entries match your filter</p>
                </div>
              ) : (
                filtered.map((entry, i) => {
                  const lvl = LOG_LEVELS[entry.level] || LOG_LEVELS.PENDING;
                  return (
                    <div key={i} className={`log-line ${lvl.cls}`}>
                      <span className="log-ts">{fmt(entry.ts)}</span>
                      <span className="log-prefix">{lvl.prefix}</span>
                      <span className="log-source">[{entry.source}]</span>
                      <span className="log-msg">{entry.message}</span>
                    </div>
                  );
                })
              )}
            </div>

            <div className="log-footer">
              <span>{filtered.length} entries</span>
              {selectedExec && <span className="log-exec-id">exec: {selectedExec.slice(0,18)}...</span>}
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Logs;