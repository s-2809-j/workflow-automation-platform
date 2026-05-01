import React, { useState } from 'react';
import Layout from './Layout';
import '../styles/Settings.css';

const Settings = () => {
  const [toast, setToast] = useState(null);
  const [activeSection, setActiveSection] = useState('profile');

  // Profile state
  const [email] = useState(localStorage.getItem('email') || 'user@company.com');
  const [displayName, setDisplayName] = useState(localStorage.getItem('displayName') || '');
  const [timezone, setTimezone] = useState(localStorage.getItem('timezone') || 'Asia/Kolkata');

  // Notifications
  const [notifExecFail, setNotifExecFail] = useState(true);
  const [notifExecSuccess, setNotifExecSuccess] = useState(false);
  const [notifWeeklyReport, setNotifWeeklyReport] = useState(true);

  // API
  const [showKey, setShowKey] = useState(false);
  const mockKey = 'sk-flow-••••••••••••••••••••••••••••••••••';
  const realKey = 'sk-flow-x9k2mZ4pQrLvNdTsWfHbJcYeUiOaGhBn';

  // Appearance
  const [dateFormat, setDateFormat] = useState('en-IN');
  const [defaultView, setDefaultView] = useState('grid');

  const showToast = (msg, type = 'success') => {
    setToast({ message: msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  const handleSaveProfile = () => {
    localStorage.setItem('displayName', displayName);
    localStorage.setItem('timezone', timezone);
    showToast('Profile settings saved');
  };

  const handleSaveNotifications = () => {
    showToast('Notification preferences saved');
  };

  const handleCopyKey = () => {
    navigator.clipboard.writeText(realKey).then(() => showToast('API key copied to clipboard'));
  };

  const handleRegenerateKey = () => {
    showToast('API key regeneration requires backend integration', 'info');
  };

  const SECTIONS = [
    { key: 'profile',       label: 'Profile',        icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z' },
    { key: 'notifications', label: 'Notifications',  icon: 'M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0' },
    { key: 'api',           label: 'API Keys',        icon: 'M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4' },
    { key: 'appearance',    label: 'Appearance',      icon: 'M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-1-5h2v2h-2zm0-8h2v6h-2z' },
    { key: 'danger',        label: 'Danger Zone',     icon: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01' },
  ];

  return (
    <Layout toast={toast} onToast={(msg, type) => showToast(msg, type)}>
      <div className="settings-page">
        <div className="topbar">
          <div>
            <div className="breadcrumb">
              <span>Dashboard</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
              <span className="bc-current">Settings</span>
            </div>
            <h1 className="page-heading">Settings</h1>
          </div>
        </div>

        <div className="settings-layout">
          {/* Settings nav */}
          <nav className="settings-nav">
            {SECTIONS.map(s => (
              <button
                key={s.key}
                className={`setting-nav-item ${activeSection === s.key ? 'setting-nav-active' : ''} ${s.key === 'danger' ? 'setting-nav-danger' : ''}`}
                onClick={() => setActiveSection(s.key)}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d={s.icon}/></svg>
                {s.label}
              </button>
            ))}
          </nav>

          {/* Settings content */}
          <div className="settings-content">

            {/* Profile */}
            {activeSection === 'profile' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Profile</h2>
                  <p>Manage your personal information</p>
                </div>
                <div className="settings-fields">
                  <div className="settings-field">
                    <label>Email Address</label>
                    <input type="text" value={email} disabled className="field-disabled"/>
                    <small>Email is managed by your organization</small>
                  </div>
                  <div className="settings-field">
                    <label>Display Name</label>
                    <input
                      type="text" placeholder="Your name"
                      value={displayName}
                      onChange={e => setDisplayName(e.target.value)}
                    />
                  </div>
                  <div className="settings-field">
                    <label>Timezone</label>
                    <select value={timezone} onChange={e => setTimezone(e.target.value)}>
                      <option value="Asia/Kolkata">Asia/Kolkata (IST +5:30)</option>
                      <option value="UTC">UTC (+0:00)</option>
                      <option value="America/New_York">America/New_York (EST)</option>
                      <option value="America/Los_Angeles">America/Los_Angeles (PST)</option>
                      <option value="Europe/London">Europe/London (GMT)</option>
                      <option value="Europe/Berlin">Europe/Berlin (CET)</option>
                    </select>
                  </div>
                </div>
                <div className="settings-card-footer">
                  <button className="btn-settings-save" onClick={handleSaveProfile}>Save Changes</button>
                </div>
              </div>
            )}

            {/* Notifications */}
            {activeSection === 'notifications' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Notifications</h2>
                  <p>Choose what events trigger notifications</p>
                </div>
                <div className="settings-toggles">
                  {[
                    { label: 'Execution Failed', sub: 'Get notified when a workflow execution fails', val: notifExecFail, set: setNotifExecFail },
                    { label: 'Execution Succeeded', sub: 'Get notified when a workflow completes successfully', val: notifExecSuccess, set: setNotifExecSuccess },
                    { label: 'Weekly Report', sub: 'Receive a weekly summary of workflow activity', val: notifWeeklyReport, set: setNotifWeeklyReport },
                  ].map((item, i) => (
                    <div key={i} className="toggle-row">
                      <div className="toggle-info">
                        <span className="toggle-label">{item.label}</span>
                        <span className="toggle-sub">{item.sub}</span>
                      </div>
                      <button
                        className={`toggle-switch ${item.val ? 'toggle-on' : ''}`}
                        onClick={() => item.set(!item.val)}
                      >
                        <div className="toggle-thumb"/>
                      </button>
                    </div>
                  ))}
                </div>
                <div className="settings-card-footer">
                  <button className="btn-settings-save" onClick={handleSaveNotifications}>Save Preferences</button>
                </div>
              </div>
            )}

            {/* API Keys */}
            {activeSection === 'api' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>API Keys</h2>
                  <p>Use these keys to authenticate API requests</p>
                </div>
                <div className="api-key-block">
                  <div className="api-key-row">
                    <div className="api-key-info">
                      <span className="api-key-label">Primary Key</span>
                      <span className="api-key-value">{showKey ? realKey : mockKey}</span>
                    </div>
                    <div className="api-key-actions">
                      <button className="btn-api-action" onClick={() => setShowKey(!showKey)}>
                        {showKey ? (
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                        ) : (
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        )}
                        {showKey ? 'Hide' : 'Show'}
                      </button>
                      <button className="btn-api-action" onClick={handleCopyKey}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                        Copy
                      </button>
                      <button className="btn-api-action btn-api-regen" onClick={handleRegenerateKey}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                        Regenerate
                      </button>
                    </div>
                  </div>
                  <div className="api-key-note">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                    Keep your API key secret. Never expose it in client-side code.
                  </div>
                </div>
                <div className="settings-card-header" style={{marginTop:24, borderTop:'1px solid #f0f0f5', paddingTop:24}}>
                  <h3>API Base URL</h3>
                </div>
                <div className="api-url-block">
                  <code>http://localhost:8080/api</code>
                  <button className="btn-api-action" onClick={() => { navigator.clipboard.writeText('http://localhost:8080/api'); showToast('URL copied'); }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    Copy
                  </button>
                </div>
              </div>
            )}

            {/* Appearance */}
            {activeSection === 'appearance' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Appearance</h2>
                  <p>Customize how the dashboard looks and feels</p>
                </div>
                <div className="settings-fields">
                  <div className="settings-field">
                    <label>Date Format</label>
                    <select value={dateFormat} onChange={e => setDateFormat(e.target.value)}>
                      <option value="en-IN">DD MMM YYYY (13 Apr 2026)</option>
                      <option value="en-US">MM/DD/YYYY (04/13/2026)</option>
                      <option value="en-GB">DD/MM/YYYY (13/04/2026)</option>
                      <option value="ISO">ISO 8601 (2026-04-13)</option>
                    </select>
                  </div>
                  <div className="settings-field">
                    <label>Default Workflow View</label>
                    <div className="view-toggle">
                      <button className={`view-opt ${defaultView === 'grid' ? 'view-opt-active' : ''}`} onClick={() => setDefaultView('grid')}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                        Grid
                      </button>
                      <button className={`view-opt ${defaultView === 'list' ? 'view-opt-active' : ''}`} onClick={() => setDefaultView('list')}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
                        List
                      </button>
                    </div>
                  </div>
                </div>
                <div className="settings-card-footer">
                  <button className="btn-settings-save" onClick={() => showToast('Appearance saved')}>Save Changes</button>
                </div>
              </div>
            )}

            {/* Danger Zone */}
            {activeSection === 'danger' && (
              <div className="settings-card settings-card-danger">
                <div className="settings-card-header">
                  <h2 style={{color:'#ef4444'}}>Danger Zone</h2>
                  <p>These actions are irreversible. Proceed with caution.</p>
                </div>
                <div className="danger-items">
                  {[
                    { title: 'Clear All Execution History', sub: 'Permanently delete all execution records across all workflows. Steps and workflows remain intact.', btn: 'Clear History' },
                    { title: 'Delete All Workflows', sub: 'Delete every workflow, step, and execution record in your organization. This cannot be undone.', btn: 'Delete All Workflows' },
                    { title: 'Deactivate Account', sub: 'Your account will be deactivated and you will lose access to the platform.', btn: 'Deactivate Account' },
                  ].map((item, i) => (
                    <div key={i} className="danger-item">
                      <div>
                        <span className="danger-item-title">{item.title}</span>
                        <span className="danger-item-sub">{item.sub}</span>
                      </div>
                      <button className="btn-danger-action" onClick={() => showToast(`${item.btn} requires backend integration`, 'info')}>
                        {item.btn}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Settings;