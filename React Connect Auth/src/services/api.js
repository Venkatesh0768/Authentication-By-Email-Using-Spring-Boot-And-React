import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

let accessToken = null; // keep in memory
const refreshTokenKey = 'refreshToken'; // localStorage key

export function setSession(tokens) {
  accessToken = tokens?.accessToken ?? null;
  if (tokens?.refreshToken) {
    localStorage.setItem(refreshTokenKey, tokens.refreshToken);
  }
}

export function clearSession() {
  accessToken = null;
  localStorage.removeItem(refreshTokenKey);
  localStorage.removeItem('user');
}

// Request interceptor to add token
api.interceptors.request.use(
  (config) => {
    // Don't add token to forgot-password and reset-password endpoints
    // as they should be public according to backend config
    const publicAuthEndpoints = ['/auth/forgot-password', '/auth/reset-password'];
    const isPublicEndpoint = publicAuthEndpoints.some(endpoint => config.url.includes(endpoint));
    
    if (accessToken && !isPublicEndpoint) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token refresh
let isRefreshing = false;
let pending = [];
const processQueue = (error, token = null) => {
  pending.forEach(p => (error ? p.reject(error) : p.resolve(token)));
  pending = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error?.response?.status;

    if (status === 401 && !originalRequest.__isRetryRequest) {
      originalRequest.__isRetryRequest = true;

      const storedRefresh = localStorage.getItem(refreshTokenKey);
      if (!storedRefresh) {
        clearSession();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pending.push({ resolve, reject });
        }).then((newToken) => {
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return api.request(originalRequest);
        });
      }

      try {
        isRefreshing = true;
        const resp = await api.post('/auth/refresh-token', null, {
          headers: { Authorization: `Bearer ${storedRefresh}` },
        });
        const { accessToken: newAccess } = resp.data;
        accessToken = newAccess;
        processQueue(null, newAccess);
        originalRequest.headers.Authorization = `Bearer ${newAccess}`;
        return api.request(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        clearSession();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export const authAPI = {
  signup: (data) => api.post('/auth/signup', data),
  login: (data) => api.post('/auth/login', data),
  verifyOTP: (data) => api.post('/auth/verify-otp', data),
  resendOTP: (email) => api.post(`/auth/resend-otp?email=${email}`),
  forgotPassword: (data) => api.post('/auth/forgot-password', data),
  resetPassword: (data) => api.post('/auth/reset-password', data),
  refreshToken: (refreshToken) => api.post('/auth/refresh-token', { refreshToken }),
};

export const userAPI = {
  getProfile: () => api.get('/user/profile'),
};

export const adminAPI = {
  getDashboard: () => api.get('/admin/dashboard'),
};

export default api;
