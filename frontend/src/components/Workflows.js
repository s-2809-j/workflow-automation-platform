import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { getWorkflows, executeWorkflow, createWorkflow, deleteWorkflow, getExecutions } from '../services/api';
import '../styles/Workflows.css';

const Workflows = () => {
  const [workflows, setWorkflows] = useState([]);
  const [executionMap, setExecutionMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [creating, setCreating] = useState(false);
  const [runningId, setRunningId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [toast, setToast] = useState(null);
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState('ALL');
  const navigate = useNavigate();

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  const fetchWorkflows = useCallback(async () => {
    try {
      const response = await getWorkflows();
      const wfs = response.data;
      setWorkflows(wfs);
      setError('');
      const execPromises = wfs.map(async (wf) => {
        try {
          const res = await getExecutions(wf.id);
          const execs = res.data;
          if (execs && execs.length > 0) {
            const latest = execs.sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt))[0];
            return { id: wf.id, execution: latest };
          }
          return { id: wf.id, execution: null };
        } catch { return { id: wf.id, execution: null }; }
      });
      const results = await Promise.all(execPromises);
      const map = {};
      results.forEach(r => { map[r.id] = r.execution; });
      setExecutionMap(map);
    } catch {
      setError('Failed to load workflows. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchWorkflows(); }, [fetchWorkflows]);

  const handleRun = async (id, name) => {
    setRunningId(id);
    try {
      await executeWorkflow(id);
      showToast(`"${name}" started successfully`);
      setTimeout(() => fetchWorkflows(), 2500);
    } catch { showToast('Failed to start workflow', 'error'); }
    finally { setRunningId(null); }
  };

  const handleDelete = async (id) => {
    setDeletingId(id);
    try {
      await deleteWorkflow(id);
      setWorkflows(prev => prev.filter(w => w.id !== id));
      setConfirmDeleteId(null);
      showToast('Workflow deleted');
    } catch { showToast('Failed to delete workflow', 'error'); }
    finally { setDeletingId(null); }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      const res = await createWorkflow({ ...formData, status: 'ACTIVE' });
      const newId = res.data?.id;
      setShowForm(false);
      setFormData({ name: '', description: '' });
      await fetchWorkflows();
      showToast('Workflow created! Add steps to get started.');
      // Navigate to detail page so user can immediately add steps
      if (newId) setTimeout(() => navigate(`/workflows/${newId}`), 800);
    } catch { showToast('Failed to create workflow', 'error'); }
    finally { setCreating(false); }
  };

  const filteredWorkflows = workflows.filter(wf => {
    const q = search.toLowerCase();
    const matchSearch = wf.name.toLowerCase().includes(q) || (wf.description || '').toLowerCase().includes(q);
    if (filterStatus === 'ALL') return matchSearch;
    return matchSearch && (wf.status || 'ACTIVE') === filterStatus;
  });

  const totalRuns = Object.values(executionMap).filter(Boolean).length;
  const successRuns = Object.values(executionMap).filter(e => e && e.status === 'SUCCESS').length;
  const successRate = totalRuns > 0 ? Math.round((successRuns / totalRuns) * 100) : 0;

  const getExecBadge = (wfId) => {
    const exec = executionMap[wfId];
    if (!exec) return null;
    const map = {
      SUCCESS: { cls: 'badge-success', label: 'Last: Success' },
      FAILED:  { cls: 'badge-failed',  label: 'Last: Failed' },
      RUNNING: { cls: 'badge-running', label: 'Running' }
    };
    return map[exec.status] || null;
  };

  const formatDate = (d) => d
    ? new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
      <p>Loading your workspace...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={(msg, type) => showToast(msg, type)}>
      <div className="topbar">
        <div>
          <div className="breadcrumb">
            <span>Dashboard</span>
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
            <span className="bc-current">Workflows</span>
          </div>
          <h1 className="page-heading">My Workflows</h1>
        </div>
        <button className="btn-new" onClick={() => setShowForm(true)}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          New Workflow
        </button>
      </div>

      <div className="stats-row">
        {[
          { label: 'Total Workflows', value: workflows.length, accent: '#6366f1' },
          { label: 'Total Runs', value: totalRuns, accent: '#8b5cf6' },
          { label: 'Success Rate', value: `${successRate}%`, accent: '#10b981' },
          { label: 'Active Now', value: workflows.filter(w => (w.status || 'ACTIVE') === 'ACTIVE').length, accent: '#f59e0b' },
        ].map((s, i) => (
          <div key={i} className="stat-tile" style={{ '--accent': s.accent, animationDelay: `${i * 0.06}s` }}>
            <span className="stat-num">{s.value}</span>
            <span className="stat-lbl">{s.label}</span>
            <div className="stat-bar" style={{ background: s.accent }}/>
          </div>
        ))}
      </div>

      <div className="toolbar">
        <div className="search-box">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input
            placeholder="Search by name or description..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {search && <button className="clear-search" onClick={() => setSearch('')}>✕</button>}
        </div>
        <div className="filter-tabs">
          {['ALL', 'ACTIVE', 'INACTIVE'].map(f => (
            <button key={f} className={`filter-tab ${filterStatus === f ? 'tab-active' : ''}`} onClick={() => setFilterStatus(f)}>
              {f === 'ALL' ? `All (${workflows.length})` : f.charAt(0) + f.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="err-banner">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          {error}
        </div>
      )}

      {filteredWorkflows.length === 0 ? (
        <div className="empty-box">
          <div className="empty-glyph">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>
          </div>
          <h3>{search ? `No results for "${search}"` : 'No workflows yet'}</h3>
          <p>{search ? 'Try a different search term' : 'Create your first automated workflow to get started'}</p>
          {!search && <button className="btn-new" style={{marginTop:16}} onClick={() => setShowForm(true)}>Create first workflow</button>}
        </div>
      ) : (
        <div className="wf-grid">
          {filteredWorkflows.map((wf, i) => {
            const badge = getExecBadge(wf.id);
            const lastExec = executionMap[wf.id];
            return (
              <div key={wf.id} className="wf-card" style={{ animationDelay: `${i * 0.04}s` }}>
                <div className="wf-card-header">
                  <div className="wf-letter-icon">{wf.name.slice(0,1).toUpperCase()}</div>
                  <div className="wf-header-badges">
                    <span className="badge badge-status">{wf.status || 'ACTIVE'}</span>
                    {badge && <span className={`badge ${badge.cls}`}>{badge.label}</span>}
                  </div>
                </div>
                <h3 className="wf-title">{wf.name}</h3>
                <p className="wf-desc">{wf.description || 'No description provided'}</p>
                <div className="wf-meta">
                  <span className="meta-chip">
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                    {formatDate(wf.createdAt)}
                  </span>
                  {lastExec && (
                    <span className="meta-chip">
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                      {formatDate(lastExec.startedAt)}
                    </span>
                  )}
                  <span className="meta-chip meta-id-chip">{wf.id.slice(0,8)}...</span>
                </div>
                <div className="wf-card-footer">
                  <button className="btn-history" onClick={() => navigate(`/workflows/${wf.id}`)}>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    View
                  </button>
                  <button
                    className={`btn-run ${runningId === wf.id ? 'btn-run-loading' : ''}`}
                    onClick={() => handleRun(wf.id, wf.name)}
                    disabled={!!runningId}
                  >
                    {runningId === wf.id
                      ? <><span className="spinner-xs"/><span>Running</span></>
                      : <><svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg><span>Run</span></>
                    }
                  </button>
                  <button className="btn-delete" onClick={() => setConfirmDeleteId(wf.id)} title="Delete">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Modal */}
      {showForm && (
        <div className="overlay" onClick={e => e.target === e.currentTarget && setShowForm(false)}>
          <div className="modal">
            <div className="modal-header">
              <div>
                <h2>Create Workflow</h2>
                <p>Give it a name and description — you'll add steps after</p>
              </div>
              <button className="close-btn" onClick={() => setShowForm(false)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="modal-body">
                <div className="form-field">
                  <label>Workflow Name <span className="req">*</span></label>
                  <input
                    type="text"
                    placeholder="e.g. Email Notification Pipeline"
                    value={formData.name}
                    onChange={e => setFormData({ ...formData, name: e.target.value })}
                    required autoFocus maxLength={80}
                  />
                  <small>{formData.name.length}/80</small>
                </div>
                <div className="form-field">
                  <label>Description <span className="req">*</span></label>
                  <textarea
                    placeholder="What does this workflow automate?"
                    value={formData.description}
                    onChange={e => setFormData({ ...formData, description: e.target.value })}
                    required rows={3} maxLength={200}
                  />
                  <small>{formData.description.length}/200</small>
                </div>
                <div className="info-note">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                  After creating, you'll be taken to the workflow page to add steps.
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn-cancel" onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" className="btn-submit" disabled={creating}>
                  {creating ? <><span className="spinner-xs spinner-dark"/>Creating...</> : 'Create & Add Steps →'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirm */}
      {confirmDeleteId && (
        <div className="overlay" onClick={e => e.target === e.currentTarget && setConfirmDeleteId(null)}>
          <div className="modal modal-sm">
            <div className="delete-icon-wrap">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="1.5"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
            </div>
            <h2 style={{textAlign:'center',marginBottom:8,fontSize:18}}>Delete Workflow?</h2>
            <p style={{textAlign:'center',color:'#888',fontSize:13,marginBottom:24,lineHeight:1.6}}>
              This action is permanent. All execution history will also be removed.
            </p>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={() => setConfirmDeleteId(null)}>Cancel</button>
              <button className="btn-delete-confirm" onClick={() => handleDelete(confirmDeleteId)} disabled={!!deletingId}>
                {deletingId ? <><span className="spinner-xs"/>Deleting...</> : 'Delete Permanently'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  );
};

export default Workflows;