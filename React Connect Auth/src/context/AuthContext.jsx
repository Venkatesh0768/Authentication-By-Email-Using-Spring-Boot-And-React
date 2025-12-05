import React, { createContext, useState, useContext, useEffect } from 'react';
import { authAPI, setSession, clearSession } from '../services/api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      setUser(JSON.parse(storedUser));
    }
    setLoading(false);
  }, []);

  const signup = async (data) => {
    const response = await authAPI.signup(data);
    return response.data;
  };

  const login = async (data) => {
    const response = await authAPI.login(data);
    const { accessToken, refreshToken, user } = response.data;

    if (!accessToken || !refreshToken) {
      throw new Error('Invalid response: missing tokens');
    }

    setSession({ accessToken, refreshToken });
    localStorage.setItem('user', JSON.stringify(user));
    setUser(user);

    return response.data;
  };

  const verifyOTP = async (data) => {
    const response = await authAPI.verifyOTP(data);
    return response.data;
  };

  const resendOTP = async (email) => {
    const response = await authAPI.resendOTP(email);
    return response.data;
  };

  const forgotPassword = async (email) => {
    const response = await authAPI.forgotPassword({ email });
    return response.data;
  };

  const resetPassword = async (data) => {
    const response = await authAPI.resetPassword(data);
    return response.data;
  };

  const logout = () => {
    clearSession();
    setUser(null);
  };

  const hasRole = (role) => {
    return user?.roles?.includes(role);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        signup,
        login,
        verifyOTP,
        resendOTP,
        forgotPassword,
        resetPassword,
        logout,
        hasRole,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};