const BASE_URL = 'http://localhost:8080';

// In-memory token store — never written to localStorage
let accessToken: string | null = null;

export function setAccessToken(token: string) {
  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}

export function hasAccessToken() {
  return accessToken !== null;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// Called by auth.ts after a successful login/register/refresh
export function getRefreshToken(): string | null {
  return localStorage.getItem('refreshToken');
}

export function setRefreshToken(token: string) {
  localStorage.setItem('refreshToken', token);
}

export function clearRefreshToken() {
  localStorage.removeItem('refreshToken');
}

// Core fetch wrapper — auto-attaches JWT and handles 401 refresh once
export async function apiFetch(
  path: string,
  init: RequestInit = {},
  _isRetry = false,
): Promise<Response> {
  const headers = new Headers(init.headers);

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const res = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (res.status === 401 && !_isRetry) {
    // Attempt a silent token refresh
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      const refreshRes = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (refreshRes.ok) {
        const data = await refreshRes.json() as { accessToken: string; refreshToken: string };
        setAccessToken(data.accessToken);
        setRefreshToken(data.refreshToken);
        // Retry original request with new token
        return apiFetch(path, init, true);
      }
    }
    // Refresh failed — clear auth state so UI shows login page
    clearAccessToken();
    clearRefreshToken();
    window.dispatchEvent(new CustomEvent('auth:expired'));
    throw new ApiError(401, 'Session expired. Please log in again.');
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.clone().json() as { message?: string; error?: string };
      message = body.message ?? body.error ?? message;
    } catch {
      // ignore parse error
    }
    throw new ApiError(res.status, message);
  }

  return res;
}

// Convenience helpers
export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await apiFetch(path, { ...init, headers });
  return res.json() as Promise<T>;
}

export async function apiBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  const res = await apiFetch(path, { ...init, headers });
  return res.blob();
}
