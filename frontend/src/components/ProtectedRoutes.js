import React from 'react';
import { Navigate } from 'react-router-dom';
import { isTokenExpired } from '../services/api';

const ProtectedRoute = ({ children }) => {
  const token = localStorage.getItem('token');

  if (!token || isTokenExpired(token)) {
    if (token) {
      localStorage.removeItem('token');
    }
    return <Navigate to="/login" replace />;
  }

  return children;
};

export default ProtectedRoute;