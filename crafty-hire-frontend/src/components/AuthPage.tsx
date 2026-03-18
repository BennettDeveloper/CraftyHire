import { useState } from 'react';
import { login, register } from '../api/auth';
import { ApiError } from '../api/client';

interface AuthPageProps {
  onAuth: (email: string) => void;
}

export default function AuthPage({ onAuth }: AuthPageProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (mode === 'register') {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email)) {
        setError('Please enter a valid email address.');
        return;
      }
      if (password.length < 8) {
        setError('Password must be at least 8 characters.');
        return;
      }
      if (password !== confirmPassword) {
        setError('Passwords do not match.');
        return;
      }
    }

    setLoading(true);
    try {
      const data =
        mode === 'login'
          ? await login(email, password)
          : await register(email, password, confirmPassword);
      onAuth(data.email);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-brand">
          <span className="auth-brand__icon" aria-hidden="true">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
            </svg>
          </span>
          <h1 className="auth-brand__name">Crafty Hire</h1>
        </div>
        <p className="auth-brand__tagline">
          AI-powered resume and cover letter optimization
        </p>

        <div className="auth-tabs">
          <button
            className={`auth-tab${mode === 'login' ? ' auth-tab--active' : ''}`}
            onClick={() => { setMode('login'); setError(null); }}
          >
            Sign In
          </button>
          <button
            className={`auth-tab${mode === 'register' ? ' auth-tab--active' : ''}`}
            onClick={() => { setMode('register'); setError(null); }}
          >
            Create Account
          </button>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label className="field-label" htmlFor="auth-email">Email</label>
            <input
              id="auth-email"
              className="input"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>

          <div className="auth-field">
            <label className="field-label" htmlFor="auth-password">Password</label>
            <input
              id="auth-password"
              className="input"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </div>

          {mode === 'register' && (
            <div className="auth-field">
              <label className="field-label" htmlFor="auth-confirm">Confirm Password</label>
              <input
                id="auth-confirm"
                className="input"
                type="password"
                placeholder="••••••••"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
              />
            </div>
          )}

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <button
            className="btn btn--primary btn--full"
            type="submit"
            disabled={loading}
            style={{ marginTop: '0.5rem' }}
          >
            {loading
              ? mode === 'login' ? 'Signing in...' : 'Creating account...'
              : mode === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  );
}
