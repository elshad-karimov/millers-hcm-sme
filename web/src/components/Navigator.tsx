import { Drawer } from 'antd'
import { CloseOutlined } from '@ant-design/icons'
import { ModuleBrowser } from './ModuleBrowser'

const PANEL_BG =
  'radial-gradient(70% 60% at 100% 0%, rgba(157,238,58,0.26) 0%, transparent 55%),' +
  'radial-gradient(60% 55% at 88% 4%, rgba(111,224,224,0.32) 0%, transparent 55%),' +
  'linear-gradient(160deg, #5B3FE5 0%, #3F26C5 55%, #2A1AA0 100%)'

/**
 * Global Navigator — the whole module springboard in a drawer that slides down
 * from the top bar's ☰ button, so any module is one click away from any page
 * (the springboard would otherwise only be reachable from Home).
 */
export function Navigator({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Drawer
      placement="top"
      height="92vh"
      open={open}
      onClose={onClose}
      closable
      closeIcon={<CloseOutlined style={{ color: 'rgba(255,255,255,0.85)' }} />}
      title={<span style={{ color: '#fff', letterSpacing: '0.02em' }}>Navigator</span>}
      styles={{
        header: { background: 'transparent', borderBottom: '1px solid rgba(255,255,255,0.14)' },
        body: { background: PANEL_BG, padding: 'clamp(20px, 4vw, 44px)' },
        content: { background: PANEL_BG },
      }}
    >
      <ModuleBrowser onNavigate={onClose} showShortcuts />
    </Drawer>
  )
}
