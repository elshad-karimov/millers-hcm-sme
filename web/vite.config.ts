import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8082',
      // M33 moved Keycloak behind nginx with a self-signed cert at
      // https://localhost:8443. Browsers (especially Firefox, which has
      // its own NSS-backed cert store) refuse to trust it without manual
      // intervention — the SPA's keycloak-js init would silently fail
      // and the page would stay blank with no console error. The Vite
      // proxies below make Keycloak look like it lives on the SPA origin
      // (http://localhost:5180), so the browser never sees the cert.
      '/realms':    { target: 'https://localhost:8443', secure: false, changeOrigin: true },
      '/resources': { target: 'https://localhost:8443', secure: false, changeOrigin: true },
      '/js':        { target: 'https://localhost:8443', secure: false, changeOrigin: true },
    },
  },
})
