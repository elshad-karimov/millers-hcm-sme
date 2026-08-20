import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App as AntdApp } from 'antd'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { AppErrorBoundary } from './components/AppErrorBoundary'
import 'antd/dist/reset.css'
// M228 — must import the i18n bootstrap BEFORE any component that
// calls useTranslation; the side-effect init configures the global
// i18next singleton.
import './i18n'
import { LanguageProvider } from './i18n/LanguageProvider'

// Hide the pre-React boot splash AFTER React has confirmed it can mount.
// Setting display:none up front would re-blank the page if a downstream
// component throws — better to keep the splash visible until we know we
// have something to render.
function hideSplash() {
  const splash = document.getElementById('boot-splash')
  if (splash) splash.style.display = 'none'
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {/* LanguageProvider supplies the AntD ConfigProvider with the
        right locale (AZ ↔ EN) reactively. Theme tokens live there
        so theme + locale stay in lockstep — one provider, not two. */}
    <LanguageProvider>
      <AntdApp>
        {/* Inside AntdApp so the fallback can use AntD components, and above
            AuthProvider so a failure in auth bootstrap is reported too rather
            than blanking the page. */}
        <AppErrorBoundary>
          <AuthProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </AuthProvider>
        </AppErrorBoundary>
      </AntdApp>
    </LanguageProvider>
  </React.StrictMode>,
)

// React's render is synchronous — by the time we reach this line the
// commit has flushed at least one paint into #root, so the splash is
// safe to hide. requestAnimationFrame ensures the browser has had a
// chance to paint before we tear it down.
requestAnimationFrame(hideSplash)
