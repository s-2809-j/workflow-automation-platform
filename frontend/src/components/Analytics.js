import React, { useState, useEffect, useCallback } from 'react';
import Layout from './Layout';
import { getWorkflows, getExecutions } from '../services/api';
import '../styles/Analytics.css';

const Analytics = () => {
  const [workflows, setWorkflows] = useState([]);
  const [allExecutions, setAllExecutions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState(null);
  const [range, setRange] = useState(7); // days

  const showToast = (msg, type = 'info') => {
    setToast({ message: msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchAll = useCallback(async () => {
    try {
      const wfRes = await getWorkflows();
      const wfs = wfRes.data || [];
      setWorkflows(wfs);
      const execArrays = await Promise.all(
        wfs.map(wf => getExecutions(wf.id).then(r => (r.data || []).map(e => ({ ...e, workflowName: wf.name }))).catch(() => []))
      );
      setAllExecutions(execArrays.flat());
    } catch {
      showToast('Failed to load analytics data', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ── Computed metrics ──
  const now = new Date();
  const cutoff = new Date(now.getTime() - range * 24 * 60 * 60 * 1000);
  const recent = allExecutions.filter(e => new Date(e.startedAt) >= cutoff);

  const total = recent.length;
  const success = recent.filter(e => e.status === 'SUCCESS').length;
  const failed = recent.filter(e => e.status === 'FAILED').length;
  const running = recent.filter(e => e.status === 'RUNNING').length;
  const rate = total > 0 ? Math.round((success / total) * 100) : 0;

  const avgDuration = (() => {
    const completed = recent.filter(e => e.startedAt && e.completedAt);
    if (!completed.length) return null;
    const avg = completed.reduce((sum, e) => sum + (new Date(e.completedAt) - new Date(e.startedAt)), 0) / completed.length;
    return avg < 1000 ? `${Math.round(avg)}ms` : `${(avg / 1000).toFixed(1)}s`;
  })();

  // ── Daily runs chart data ──
  const dailyData = (() => {
    const days = [];
    for (let i = range - 1; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i);
      const label = d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
      const dayStart = new Date(d.setHours(0,0,0,0));
      const dayEnd = new Date(d.setHours(23,59,59,999));
      const dayExecs = allExecutions.filter(e => {
        const t = new Date(e.startedAt);
        return t >= dayStart && t <= dayEnd;
      });
      days.push({
        label,
        total: dayExecs.length,
        success: dayExecs.filter(e => e.status === 'SUCCESS').length,
        failed: dayExecs.filter(e => e.status === 'FAILED').length,
      });
    }
    return days;
  })();

  const maxVal = Math.max(...dailyData.map(d => d.total), 1);

  // ── Per-workflow breakdown ──
  const wfBreakdown = workflows.map(wf => {
    const execs = recent.filter(e => e.workflowName === wf.name);
    const s = execs.filter(e => e.status === 'SUCCESS').length;
    const f = execs.filter(e => e.status === 'FAILED').length;
    return { name: wf.name, total: execs.length, success: s, failed: f, rate: execs.length > 0 ? Math.round((s / execs.length) * 100) : 0 };
  }).sort((a, b) => b.total - a.total);

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-ring"><div/><div/><div/><div/></div>
      <p>Loading analytics...</p>
    </div>
  );

  return (
    <Layout toast={toast} onToast={showToast}>
      <div className="analytics-page">
        <div className="topbar">
          <div>
            <div className="breadcrumb">
              <span>Dashboard</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-current">Analytics</span>
            </div>
            <h1 className="page-heading">Analytics</h1>
          </div>
          <div className="range-tabs">
            {[7, 14, 30].map(d => (
              <button key={d} className={`range-tab ${range === d ? 'range-active' : ''}`} onClick={() => setRange(d)}>
                {d}d
              </button>
            ))}
          </div>
        </div>

        {/* KPI Cards */}
        <div className="kpi-row">
          {[
            { label: 'Total Runs', value: total, sub: `Last ${range} days`, accent: '#6366f1', icon: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2' },
            { label: 'Success Rate', value: `${rate}%`, sub: `${success} succeeded`, accent: '#10b981', icon: 'M22 11.08V12a10 10 0 1 1-5.93-9.14M22 4L12 14.01l-3-3' },
            { label: 'Failed Runs', value: failed, sub: failed === 0 ? 'All clear!' : 'Need attention', accent: '#ef4444', icon: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01' },
            { label: 'Avg Duration', value: avgDuration || '—', sub: 'Per execution', accent: '#f59e0b', icon: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2' },
          ].map((k, i) => (
            <div key={i} className="kpi-card" style={{ '--kc': k.accent }}>
              <div className="kpi-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={k.accent} strokeWidth="1.8"><path d={k.icon}/></svg>
              </div>
              <div className="kpi-body">
                <span className="kpi-val">{k.value}</span>
                <span className="kpi-lbl">{k.label}</span>
                <span className="kpi-sub">{k.sub}</span>
              </div>
              <div className="kpi-accent-bar"/>
            </div>
          ))}
        </div>

        <div className="analytics-grid">
          {/* Daily Runs Bar Chart */}
          <div className="chart-card chart-wide">
            <div className="chart-header">
              <h3>Daily Runs</h3>
              <div className="chart-legend">
                <span className="legend-dot" style={{background:'#6366f1'}}/>Success
                <span className="legend-dot" style={{background:'#ef4444', marginLeft:12}}/>Failed
              </div>
            </div>
            <div className="bar-chart">
              {dailyData.map((d, i) => (
                <div key={i} className="bar-col">
                  <div className="bar-stack">
                    {d.total === 0 ? (
                      <div className="bar-empty"/>
                    ) : (
                      <>
                        <div
                          className="bar-seg bar-success"
                          style={{ height: `${(d.success / maxVal) * 100}%` }}
                          title={`${d.success} success`}
                        />
                        {d.failed > 0 && (
                          <div
                            className="bar-seg bar-failed"
                            style={{ height: `${(d.failed / maxVal) * 100}%` }}
                            title={`${d.failed} failed`}
                          />
                        )}
                      </>
                    )}
                  </div>
                  <span className="bar-label">{d.label}</span>
                  {d.total > 0 && <span className="bar-count">{d.total}</span>}
                </div>
              ))}
            </div>
          </div>

          {/* Success/Fail Donut */}
          <div className="chart-card">
            <div className="chart-header"><h3>Run Outcomes</h3></div>
            <div className="donut-wrap">
              {total === 0 ? (
                <div className="donut-empty">No data yet</div>
              ) : (
                <>
                  <svg viewBox="0 0 120 120" className="donut-svg">
                    <circle cx="60" cy="60" r="48" fill="none" stroke="#f0f0f5" strokeWidth="16"/>
                    {/* Success arc */}
                    <circle cx="60" cy="60" r="48" fill="none" stroke="#10b981" strokeWidth="16"
                      strokeDasharray={`${(success / total) * 301.6} 301.6`}
                      strokeLinecap="round"
                      transform="rotate(-90 60 60)"
                    />
                    {/* Failed arc */}
                    <circle cx="60" cy="60" r="48" fill="none" stroke="#ef4444" strokeWidth="16"
                      strokeDasharray={`${(failed / total) * 301.6} 301.6`}
                      strokeLinecap="round"
                      strokeDashoffset={`${-(success / total) * 301.6}`}
                      transform="rotate(-90 60 60)"
                    />
                    <text x="60" y="56" textAnchor="middle" fontSize="18" fontWeight="700" fill="#1a1a2e">{rate}%</text>
                    <text x="60" y="70" textAnchor="middle" fontSize="9" fill="#888">success</text>
                  </svg>
                  <div className="donut-legend">
                    <div className="dl-row"><span className="dl-dot" style={{background:'#10b981'}}/><span>Success</span><strong>{success}</strong></div>
                    <div className="dl-row"><span className="dl-dot" style={{background:'#ef4444'}}/><span>Failed</span><strong>{failed}</strong></div>
                    {running > 0 && <div className="dl-row"><span className="dl-dot" style={{background:'#6366f1'}}/><span>Running</span><strong>{running}</strong></div>}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Workflow Breakdown Table */}
          <div className="chart-card chart-wide">
            <div className="chart-header"><h3>Per-Workflow Breakdown</h3></div>
            {wfBreakdown.length === 0 ? (
              <div className="breakdown-empty">No workflow data available</div>
            ) : (
              <div className="breakdown-table">
                <div className="bt-head">
                  <span>Workflow</span>
                  <span>Runs</span>
                  <span>Success</span>
                  <span>Failed</span>
                  <span>Rate</span>
                </div>
                {wfBreakdown.map((wf, i) => (
                  <div key={i} className="bt-row">
                    <span className="bt-name">
                      <div className="bt-dot" style={{background: wf.rate >= 80 ? '#10b981' : wf.rate >= 50 ? '#f59e0b' : '#ef4444'}}/>
                      {wf.name}
                    </span>
                    <span className="bt-num">{wf.total}</span>
                    <span className="bt-num bt-success">{wf.success}</span>
                    <span className="bt-num bt-fail">{wf.failed}</span>
                    <span className="bt-rate-cell">
                      <div className="rate-bar-track">
                        <div className="rate-bar-fill" style={{
                          width: `${wf.rate}%`,
                          background: wf.rate >= 80 ? '#10b981' : wf.rate >= 50 ? '#f59e0b' : '#ef4444'
                        }}/>
                      </div>
                      <span className="rate-pct">{wf.rate}%</span>
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Analytics;