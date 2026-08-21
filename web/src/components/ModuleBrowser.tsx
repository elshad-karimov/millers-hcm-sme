import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { LeftOutlined, RightOutlined, StarFilled, StarOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'
import { CATEGORIES, icon, isHiddenScreen, moduleOfRoute, type Tile } from '../nav/modules'
import { describeScreen } from '../nav/screenDescriptions'
import { areaVisible } from '../nav/selfServiceAreas'
import { useFavorites, useRecents } from '../nav/navPrefs'
import { useEnabledModules } from '../nav/moduleSettings'

/**
 * The category tabs + QUICK ACTIONS + APPS grid, designed to sit on the
 * Millers purple backdrop (white text, translucent tiles).  Rendered by both
 * the Home springboard and the global Navigator drawer, so the module list
 * lives in exactly one place (nav/modules.tsx).
 */
export function ModuleBrowser({
  onNavigate,
  showShortcuts = true,
}: {
  /** Fired after a tile is opened — the Navigator uses it to close the drawer. */
  onNavigate?: () => void
  /** Show the Favorites + Recents rows above the tabs. */
  showShortcuts?: boolean
}) {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const { favorites, isFav, toggle } = useFavorites()
  const { recents } = useRecents()
  const { disabled: disabledModules } = useEnabledModules()

  const cats = useMemo(
    () =>
      CATEGORIES.filter(
        (c) =>
          (!c.roles || hasRole(...c.roles)) &&
          !disabledModules.has(c.key) &&
          // A category whose every screen is hidden would otherwise render as a
          // tab with two empty columns under it. Benefits is exactly that once
          // Allowances goes, and any module could become it after a plan change.
          [...c.apps, ...c.quick].some((t) => !isHiddenScreen(t.to)),
      ),
    [hasRole, disabledModules],
  )
  // Hide any tile whose owning module is off for this tenant — `needs` when the
  // tile is borrowed from another module (Self-Service's "Pay and Payslips",
  // "Learning", …), otherwise the module its route belongs to.
  // `isHiddenScreen` also covers Favourites and Recents below, so a screen
  // somebody starred before it was switched off stops appearing too.
  const visible = (tiles: Tile[]) =>
    tiles.filter(
      (t) =>
        !isHiddenScreen(t.to) &&
        areaVisible(t.to, disabledModules) &&
        !disabledModules.has(t.needs ?? moduleOfRoute(t.to) ?? ''),
    )
  const favVisible = visible(favorites)
  const recentVisible = visible(recents)
  const [active, setActive] = useState(cats[0]?.key ?? '')
  const cat = cats.find((c) => c.key === active) ?? cats[0]

  const open = (to: string) => {
    navigate(to)
    onNavigate?.()
  }

  const apps = visible(cat?.apps ?? [])
  const quick = visible(cat?.quick ?? [])

  // Single-row tab strip that scrolls, with ‹ › arrows when it overflows.
  const stripRef = useRef<HTMLDivElement>(null)
  const [atStart, setAtStart] = useState(true)
  const [atEnd, setAtEnd] = useState(true)
  const measure = () => {
    const el = stripRef.current
    if (!el) return
    setAtStart(el.scrollLeft <= 1)
    setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 1)
  }
  useLayoutEffect(measure, [cats.length])
  useEffect(() => {
    const el = stripRef.current
    if (!el) return
    el.addEventListener('scroll', measure, { passive: true })
    window.addEventListener('resize', measure)
    return () => {
      el.removeEventListener('scroll', measure)
      window.removeEventListener('resize', measure)
    }
  }, [])
  const nudge = (dir: number) => stripRef.current?.scrollBy({ left: dir * 320, behavior: 'smooth' })

  return (
    <div>
      {showShortcuts && favVisible.length > 0 && (
        <ShortcutRow label="FAVORITES" tiles={favVisible} onOpen={open} />
      )}
      {showShortcuts && recentVisible.length > 0 && (
        <ShortcutRow label="RECENT" tiles={recentVisible} onOpen={open} />
      )}

      {/* Category tabs — one scrolling row (Redwood style); active one underlined green. */}
      <div style={{ position: 'relative', borderBottom: '1px solid rgba(255,255,255,0.16)' }}>
        {!atStart && (
          <button aria-label="Scroll left" onClick={() => nudge(-1)} style={{ ...arrowBtn, left: -6 }}>
            <LeftOutlined />
          </button>
        )}
        <div ref={stripRef} className="mb-tabstrip" style={tabRow}>
          {cats.map((c) => {
            const on = c.key === active
            return (
              <button
                key={c.key}
                onClick={() => setActive(c.key)}
                style={{
                  ...tabBtn,
                  color: on ? '#fff' : 'rgba(255,255,255,0.66)',
                  fontWeight: on ? 600 : 500,
                  borderBottom: on ? '3px solid #9DEE3A' : '3px solid transparent',
                }}
              >
                {c.label}
              </button>
            )
          })}
        </div>
        {!atEnd && (
          <button aria-label="Scroll right" onClick={() => nudge(1)} style={{ ...arrowBtn, right: -6 }}>
            <RightOutlined />
          </button>
        )}
        <style>{`.mb-tabstrip::-webkit-scrollbar{display:none}`}</style>
      </div>

      <div style={grid}>
        <div>
          <div style={sectionLabel}>QUICK ACTIONS</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {quick.map((tl) => (
              <button
                key={tl.label + tl.to}
                onClick={() => open(tl.to)}
                style={quickRow}
                onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.08)')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <span style={{ fontSize: 17, color: 'rgba(255,255,255,0.85)', width: 22, textAlign: 'center' }}>
                  {icon(tl.icon)}
                </span>
                <span>{tl.label}</span>
              </button>
            ))}
          </div>
        </div>
        <div>
          <div style={sectionLabel}>APPS</div>
          <div style={appGrid}>
            {apps.map((tl) => (
              <AppTile key={tl.label + tl.to} tile={tl} onOpen={open} starred={isFav(tl.to)} onStar={() => toggle(tl.to)} />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function AppTile({
  tile,
  onOpen,
  starred,
  onStar,
}: {
  tile: Tile
  onOpen: (to: string) => void
  starred: boolean
  onStar: () => void
}) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onOpen(tile.to)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onOpen(tile.to)
      }}
      style={appTile}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = 'rgba(255,255,255,0.18)'
        e.currentTarget.style.transform = 'translateY(-3px)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = 'rgba(255,255,255,0.10)'
        e.currentTarget.style.transform = 'none'
      }}
    >
      <button
        title={starred ? 'Remove from favorites' : 'Add to favorites'}
        onClick={(e) => {
          e.stopPropagation()
          onStar()
        }}
        style={starBtn}
      >
        {starred ? <StarFilled style={{ color: '#9DEE3A' }} /> : <StarOutlined style={{ color: 'rgba(255,255,255,0.5)' }} />}
      </button>
      <span style={{ fontSize: 24, color: '#fff', lineHeight: 1, flex: '0 0 auto', marginTop: 2 }}>
        {icon(tile.icon)}
      </span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <span style={{ color: 'rgba(255,255,255,0.94)', fontSize: 15, fontWeight: 600, lineHeight: 1.25 }}>
          {tile.label}
        </span>
        {describeScreen(tile.to) && (
          <span style={{ color: 'rgba(255,255,255,0.62)', fontSize: 13, lineHeight: 1.35 }}>
            {describeScreen(tile.to)}
          </span>
        )}
      </span>
    </div>
  )
}

function ShortcutRow({ label, tiles, onOpen }: { label: string; tiles: Tile[]; onOpen: (to: string) => void }) {
  return (
    <div style={{ marginBottom: 22 }}>
      <div style={sectionLabel}>{label}</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        {tiles.map((tl) => (
          <button
            key={tl.label + tl.to}
            onClick={() => onOpen(tl.to)}
            style={chip}
            onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.20)')}
            onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.10)')}
          >
            <span style={{ fontSize: 15 }}>{icon(tl.icon)}</span>
            <span>{tl.label}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

const tabRow: CSSProperties = {
  display: 'flex',
  flexWrap: 'nowrap',
  gap: 26,
  overflowX: 'auto',
  scrollbarWidth: 'none',
  scrollBehavior: 'smooth',
}
const arrowBtn: CSSProperties = {
  position: 'absolute',
  top: 0,
  height: 34,
  width: 30,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'rgba(30,20,80,0.72)',
  border: 'none',
  borderRadius: 8,
  color: '#fff',
  cursor: 'pointer',
  zIndex: 2,
}
const tabBtn: CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: 16,
  padding: '0 2px 12px',
  whiteSpace: 'nowrap',
  marginBottom: -1,
  transition: 'color .15s',
}
const grid: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'minmax(200px, 260px) 1fr',
  gap: 'clamp(28px, 5vw, 72px)',
  marginTop: 30,
  alignItems: 'start',
}
const appGrid: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))',
  gap: 16,
}
const sectionLabel: CSSProperties = {
  color: 'rgba(255,255,255,0.6)',
  fontSize: 12,
  fontWeight: 700,
  letterSpacing: '0.09em',
  marginBottom: 16,
}
const quickRow: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  padding: '9px 10px',
  borderRadius: 8,
  color: 'rgba(255,255,255,0.9)',
  fontSize: 15,
  textAlign: 'left',
  background: 'transparent',
  border: 'none',
  cursor: 'pointer',
  transition: 'background .12s',
}
const appTile: CSSProperties = {
  position: 'relative',
  display: 'flex',
  // Icon beside the words, matching the self-service area boards — the same
  // shape of card everywhere, so a screen reads the same wherever it is met.
  flexDirection: 'row',
  alignItems: 'flex-start',
  gap: 14,
  minHeight: 96,
  // Right padding clears the absolutely-positioned favourite star.
  padding: '16px 34px 16px 18px',
  background: 'rgba(255,255,255,0.10)',
  border: '1px solid rgba(255,255,255,0.14)',
  borderRadius: 14,
  cursor: 'pointer',
  transition: 'background .15s, transform .15s',
}
const starBtn: CSSProperties = {
  position: 'absolute',
  top: 8,
  right: 8,
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: 14,
  padding: 2,
  lineHeight: 1,
}
const chip: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
  padding: '7px 14px',
  borderRadius: 999,
  background: 'rgba(255,255,255,0.10)',
  border: '1px solid rgba(255,255,255,0.16)',
  color: 'rgba(255,255,255,0.92)',
  fontSize: 13,
  fontWeight: 500,
  cursor: 'pointer',
  transition: 'background .12s',
}
