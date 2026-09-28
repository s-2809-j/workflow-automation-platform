import React from 'react';
import { Navigate } from 'react-router-dom';
import { isTokenExpired } from '../services/api';

const ProtectedRoute = ({ children }) => {
  const token = localStorage.getItem('token');

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (isTokenExpired(token)) {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    return <Navigate to="/login?expired=1" replace />;
  }

  return children;
};

export default ProtectedRoute;