import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider, App as AntdApp } from 'antd'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { brand } from './theme'
import 'antd/dist/reset.css'

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
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: brand.purple,
          colorInfo: brand.cyanDeep,
          colorSuccess: brand.greenDeep,
          colorLink: brand.purple,
          colorTextHeading: brand.ink,
          colorBgLayout: brand.cream,
          borderRadius: 8,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, "Helvetica Neue", sans-serif',
        },
        components: {
          Layout: {
            headerBg: '#ffffff',
            siderBg: '#ffffff',
            bodyBg: brand.cream,
          },
          Menu: {
            itemSelectedBg: 'rgba(91, 63, 229, 0.10)',
            itemSelectedColor: brand.purpleDeep,
          },
          Button: {
            primaryShadow: '0 4px 14px rgba(91, 63, 229, 0.35)',
          },
        },
      }}
    >
      <AntdApp>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
)

// React's render is synchronous — by the time we reach this line the
// commit has flushed at least one paint into #root, so the splash is
// safe to hide. requestAnimationFrame ensures the browser has had a
// chance to paint before we tear it down.
requestAnimationFrame(hideSplash)
