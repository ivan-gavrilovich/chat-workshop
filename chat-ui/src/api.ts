import { expireSession, getSession } from './session';
import type { ChatMessage, Session } from './types';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

function authHeaders(): Record<string, string> {
  const accessToken = getSession()?.accessToken;
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

async function toApiError(response: Response, path: string): Promise<ApiError> {
  // Services return RFC 9457 problem details
  let message = `Request failed with status ${response.status}`;
  try {
    const problem = await response.json();
    message = problem.detail ?? problem.title ?? message;
  } catch {
    // body was not JSON, keep the generic message
  }
  // An expired or invalid token on a protected call ends the session
  if (response.status === 401 && !path.startsWith('/auth/')) {
    expireSession();
    message = 'Your session has expired, please sign in again';
  }
  return new ApiError(message, response.status);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...init?.headers },
  });
  if (!response.ok) {
    throw await toApiError(response, path);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

/**
 * Sends a chat message and reads the server-sent event stream, calling onChunk for every piece of the answer.
 */
async function streamChat(message: string, onChunk: (text: string) => void, signal?: AbortSignal): Promise<void> {
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...authHeaders() },
    body: JSON.stringify({ message, timeZone }),
    signal,
  });
  if (!response.ok || !response.body) {
    throw await toApiError(response, '/chat');
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return;
    buffer += value;
    let separator: RegExpMatchArray | null;
    while ((separator = buffer.match(/\r?\n\r?\n/)) && separator.index !== undefined) {
      const rawEvent = buffer.slice(0, separator.index);
      buffer = buffer.slice(separator.index + separator[0].length);

      let event = 'message';
      const data: string[] = [];
      for (const line of rawEvent.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5));
      }
      if (data.length === 0) continue;
      const { text } = JSON.parse(data.join('\n')) as { text: string };
      if (event === 'error') throw new ApiError(text, 502);
      if (event === 'done') return;
      onChunk(text);
    }
  }
}

export const api = {
  register: (body: { email: string; fullName: string; password: string }) =>
    request<Session>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),

  login: (body: { email: string; password: string }) =>
    request<Session>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),

  getChatHistory: () => request<ChatMessage[]>('/chat/history'),

  clearChatHistory: () => request<void>('/chat/history', { method: 'DELETE' }),

  streamChat,
};
