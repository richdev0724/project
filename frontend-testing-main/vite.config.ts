import { ValidateEnv } from '@julr/vite-plugin-validate-env'
import react from '@vitejs/plugin-react-swc'
import path from 'path'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), ValidateEnv()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  optimizeDeps: {
    exclude: [],
    include: ['react', 'react-dom', 'react-router-dom'],
  },
  build: {
    outDir: 'dist',
  },
  server: {
    host: true,
    port: 5173,
    hmr: {
      port: 5173,
      host: 'localhost',
    },
    allowedHosts: ['mmtesting2025.ngrok.app'],
    proxy: {
      '/api': {
        target: 'http://localhost:3001',
        changeOrigin: true,
        secure: false,
        ws: true,
        configure: (proxy) => {
          proxy.on('error', () => {
            // Proxy error occurred
          })
          proxy.on('proxyReq', () => {
            // Sending request to target
          })
          proxy.on('proxyRes', () => {
            // Received response from target
          })
        },
      },
      '/socket.io': {
        target: 'http://localhost:3001',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
