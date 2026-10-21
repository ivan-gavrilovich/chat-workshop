import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    // In local dev auth-service runs on 8081 and chat-service on 8082; in Docker nginx does the proxying.
    proxy: {
      '/api/auth': 'http://localhost:8081',
      '/api/chat': 'http://localhost:8082',
    },
  },
});
