import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy API requests to the Spring Boot backend during development.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

