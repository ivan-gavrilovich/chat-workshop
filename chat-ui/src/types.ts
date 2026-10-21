export interface User {
  id: number;
  email: string;
  fullName: string;
}

export interface Session {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}
