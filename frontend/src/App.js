import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/Login';
import Workflows from './components/Workflows';
import WorkflowDetail from './components/WorkflowDetail';
import Executions from './components/Executions';
import Analytics from './components/Analytics';
import Logs from './components/Logs';
import Settings from './components/Settings';
import ProtectedRoute from './components/ProtectedRoutes';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/workflows" element={<ProtectedRoute><Workflows /></ProtectedRoute>} />
        <Route path="/workflows/:id" element={<ProtectedRoute><WorkflowDetail /></ProtectedRoute>} />
        <Route path="/workflows/:id/executions" element={<ProtectedRoute><Executions /></ProtectedRoute>} />
        <Route path="/executions" element={<ProtectedRoute><Executions /></ProtectedRoute>} />
        <Route path="/analytics" element={<ProtectedRoute><Analytics /></ProtectedRoute>} />
        <Route path="/logs" element={<ProtectedRoute><Logs /></ProtectedRoute>} />
        <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/login" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;