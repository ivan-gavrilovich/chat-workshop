import { useState, type FormEvent } from 'react';
import { api } from './api';
import type { Session } from './types';

interface Props {
  onAuthenticated: (session: Session) => void;
  notice?: string | null;
}

export default function AuthPage({ onAuthenticated, notice }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const isRegister = mode === 'register';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = isRegister
        ? await api.register({ email, fullName, password })
        : await api.login({ email, password });
      onAuthenticated(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const switchMode = (next: 'login' | 'register') => {
    setMode(next);
    setError(null);
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>✦ AI Assistant</h1>
        <p className="subtitle">Sign in to access the Developer Assistant</p>
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={!isRegister} className={!isRegister ? 'active' : ''} onClick={() => switchMode('login')}>
            Sign in
          </button>
          <button type="button" role="tab" aria-selected={isRegister} className={isRegister ? 'active' : ''} onClick={() => switchMode('register')}>
            Register
          </button>
        </div>

        {notice && !error && <div className="info">{notice}</div>}

        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus />
        </label>

        {isRegister && (
          <label>
            Full name
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={100} autoComplete="name" required />
          </label>
        )}

        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={isRegister ? 8 : undefined}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            required
          />
          {isRegister && <small className="hint">At least 8 characters</small>}
        </label>

        {error && <div className="error">{error}</div>}

        <button type="submit" className="primary block" disabled={busy}>
          {isRegister ? 'Create account' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
