import type { AuthResponse } from '../types';
import { apiJson, setAccessToken, setRefreshToken, clearAccessToken, clearRefreshToken } from './client';

export async function register(email: string, password: string, confirmPassword: string): Promise<AuthResponse> {
  const data = await apiJson<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, confirmPassword }),
  });
  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken);
  return data;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const data = await apiJson<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken);
  return data;
}

export async function logout(): Promise<void> {
  try {
    await apiJson('/api/auth/logout', { method: 'POST' });
  } finally {
    clearAccessToken();
    clearRefreshToken();
  }
}
