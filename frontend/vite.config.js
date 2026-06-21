import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true, // Expose to local network (crucial for Docker port mapping)
    proxy: {
      '/api': {
        // Fallback to localhost if BACKEND_URL environment variable is not defined
        target: process.env.BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
