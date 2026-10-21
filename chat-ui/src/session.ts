import type { Session } from './types';

const SESSION_KEY = 'dev-assistant-chat.session';
export const SESSION_EXPIRED_EVENT = 'dev-assistant-chat.session-expired';

let current: Session | null = read();

function read(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function getSession(): Session | null {
  return current && new Date(current.expiresAt) > new Date() ? current : null;
}

export function setSession(session: Session | null) {
  current = session;
  try {
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    else localStorage.removeItem(SESSION_KEY);
  } catch {
    // storage unavailable (private mode etc.), session lives in memory only
  }
}

export function expireSession() {
  setSession(null);
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}
