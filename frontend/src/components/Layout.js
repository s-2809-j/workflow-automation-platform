import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import '../styles/Layout.css';

const NAV_ITEMS = [
  {
    section: 'Workspace',
    items: [
      {
        path: '/workflows', label: 'Workflows',
        icon: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
      },
      {
        path: '/ai-drafts', label: 'AI Builder',
        icon: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
        badge: 'AI',
      },
      {
        path: '/executions', label: 'Executions',
        icon: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2',
      },
      {
        path: '/analytics', label: 'Analytics',
        icon: 'M22 12h-4l-3 9L9 3l-3 9H2',
      },
      {
        path: '/security', label: 'Security', disabled: true,
        icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
      },
    ],
  },
  {
    section: 'System',
    items: [
      {
        path: '/settings', label: 'Settings',
        icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z',
      },
      {
        path: '/logs', label: 'Logs',
        icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8',
      },
      {
        path: '/documentation', label: 'Documentation', disabled: true,
        icon: 'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2zM22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z',
      },
    ],
  },
];

const Layout = ({ children, toast, onToast }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const email = localStorage.getItem('email') || 'user@company.com';
  const initials = email.slice(0, 2).toUpperCase();

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    navigate('/login');
  };

  const isActive = (path) => location.pathname === path || location.pathname.startsWith(path + '/');

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
            </svg>
          </div>
          <span className="brand-name">FlowEngine</span>
        </div>

        {NAV_ITEMS.map(group => (
          <div key={group.section}>
            <p className="sidebar-section-label">{group.section}</p>
            <nav className="sidebar-nav">
              {group.items.map(item => (
                <button
                  key={item.label}
                  className={`nav-item ${isActive(item.path) && !item.disabled ? 'nav-active' : ''} ${item.disabled ? 'nav-disabled' : ''}`}
                  onClick={() => {
                    if (item.disabled) {
                      onToast && onToast(`${item.label} — coming soon`, 'info');
                    } else {
                      navigate(item.path);
                    }
                  }}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d={item.icon}/>
                  </svg>
                  <span>{item.label}</span>
                  {item.badge && (
                    <span className="nav-ai-badge">{item.badge}</span>
                  )}
                  {isActive(item.path) && !item.disabled && <div className="nav-indicator"/>}
                </button>
              ))}
            </nav>
          </div>
        ))}

        <div className="sidebar-user">
          <div className="avatar-circle">{initials}</div>
          <div className="user-info-block">
            <span className="user-email-label">{email}</span>
            <span className="user-tier">Pro Plan</span>
          </div>
          <button className="sign-out-btn" onClick={handleLogout} title="Sign out">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
        </div>
      </aside>

      <main className="main-content">
        {children}
      </main>

      {toast && (
        <div className={`toast toast-${toast.type}`}>
          {toast.type === 'success' && <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>}
          {toast.type === 'error' && <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>}
          {toast.type === 'info' && <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>}
          {toast.message}
        </div>
      )}
    </div>
  );
};

export default Layout;