import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import Markdown from 'react-markdown';
import { api } from './api';
import type { ChatMessage, User } from './types';

const SUGGESTIONS = [
  'How do I check CPU and memory usage on Linux?',
  'How do I connect to PostgreSQL from the terminal?',
  'How do I read application logs in Docker?',
  'What are common causes of OutOfMemoryError in Java?',
];

interface Props {
  user: User;
  onSignOut: () => void;
}

export default function Chat({ user, onSignOut }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Restore the conversation stored by chat-service
  useEffect(() => {
    api
      .getChatHistory()
      .then(setMessages)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
    inputRef.current?.focus();
    return () => abortRef.current?.abort();
  }, []);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages, busy]);

  const send = async (text: string) => {
    const message = text.trim();
    if (!message || busy) return;
    setInput('');
    setError(null);
    setBusy(true);
    setMessages((prev) => [...prev, { role: 'user', text: message }, { role: 'assistant', text: '' }]);

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await api.streamChat(
        message,
        (chunk) =>
          setMessages((prev) => {
            const last = prev[prev.length - 1];
            return [...prev.slice(0, -1), { ...last, text: last.text + chunk }];
          }),
        controller.signal,
      );
    } catch (e) {
      if (!controller.signal.aborted) {
        setError(e instanceof Error ? e.message : String(e));
        // drop the empty assistant placeholder if nothing arrived
        setMessages((prev) => (prev[prev.length - 1]?.text === '' ? prev.slice(0, -1) : prev));
      }
    } finally {
      setBusy(false);
      abortRef.current = null;
      inputRef.current?.focus();
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    void send(input);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void send(input);
    }
  };

  const newChat = async () => {
    abortRef.current?.abort();
    try {
      await api.clearChatHistory();
      setMessages([]);
      setError(null);
      inputRef.current?.focus();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const firstName = user.fullName.split(' ')[0];

  return (
    <div className="chat-app">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden>
            ✦
          </span>
          <div>
            <strong>AI Assistant</strong>
            <small>Developer Assistant · powered by Gemini</small>
          </div>
        </div>
        <div className="topbar-actions">
          <button type="button" onClick={newChat} disabled={busy || messages.length === 0}>
            New chat
          </button>
          <div className="user">
            <strong>{user.fullName}</strong>
            <small>{user.email}</small>
          </div>
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="conversation" ref={listRef}>
        <div className="column">
          {!loading && messages.length === 0 && !busy && (
            <div className="welcome">
              <h2>Hi {firstName}, how can I help?</h2>
              <p>Ask me about debugging errors, checking system resources, or anything else about the application.</p>
              <div className="suggestions">
                {SUGGESTIONS.map((s) => (
                  <button key={s} type="button" onClick={() => void send(s)}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((m, i) => (
            <div key={i} className={`message ${m.role}`}>
              {m.role === 'assistant' ? (
                m.text ? <Markdown>{m.text}</Markdown> : <span className="typing">Thinking…</span>
              ) : (
                m.text
              )}
            </div>
          ))}

          {error && <div className="error">{error}</div>}
        </div>
      </main>

      <form className="composer" onSubmit={handleSubmit}>
        <div className="column composer-row">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Message the assistant…  (Enter to send, Shift+Enter for a new line)"
            rows={2}
            maxLength={4000}
          />
          <button type="submit" className="primary" disabled={busy || !input.trim()}>
            Send
          </button>
        </div>
      </form>
    </div>
  );
}
