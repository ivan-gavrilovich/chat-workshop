import { useEffect, useState } from 'react';
import AuthPage from './AuthPage';
import Chat from './Chat';
import { getSession, SESSION_EXPIRED_EVENT, setSession } from './session';
import type { Session } from './types';

export default function App() {
  const [session, setSessionState] = useState<Session | null>(getSession);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const onExpired = () => {
      setSessionState(null);
      setNotice('Your session has expired, please sign in again');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const signIn = (next: Session) => {
    setSession(next);
    setSessionState(next);
    setNotice(null);
  };

  const signOut = () => {
    setSession(null);
    setSessionState(null);
  };

  if (!session) {
    return <AuthPage onAuthenticated={signIn} notice={notice} />;
  }
  return <Chat user={session.user} onSignOut={signOut} />;
}
