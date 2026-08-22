import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { Button } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { selfApi } from '../api/self'
import { icon, isHiddenScreen } from '../nav/modules'
import { findArea } from '../nav/selfServiceAreas'
import { useEnabledModules } from '../nav/moduleSettings'
import { brand } from '../theme'

/**
 * Second level of self-service: /me/<area>.
 *
 * A banner with the employee's own name, then one card per thing they can do in
 * this area.  Cards whose module is off for the tenant are dropped, so the board
 * only ever offers what will actually open.
 */
export default function SelfServiceAreaPage() {
  const { area: areaKey } = useParams()
  const navigate = useNavigate()
  const { disabled } = useEnabledModules()
  const [name, setName] = useState('')
  const [subtitle, setSubtitle] = useState('')

  const area = findArea(areaKey)

  useEffect(() => {
    let alive = true
    selfApi
      .profile()
      .then((p) => {
        if (!alive) return
        const mid = p.middleName ? ` ${p.middleName}` : ''
        setName(`${p.lastName}, ${p.firstName}${mid}`)
        setSubtitle([p.positionTitle, p.departmentName].filter(Boolean).join(' · '))
      })
      .catch(() => {
        /* no employee record linked — the cards still work, the banner just stays bare */
      })
    return () => {
      alive = false
    }
  }, [])

  const cards = useMemo(
    // Screens this edition hides count too, not just switched-off modules —
    // otherwise the board keeps offering cards whose screen is gone. That is
    // how Add Permission and Permission Balance survived here after every
    // permission screen was removed.
    () => (area?.cards ?? []).filter(
      (c) => !isHiddenScreen(c.to) && (!c.needs || !disabled.has(c.needs)),
    ),
    [area, disabled],
  )

  // Unknown area, or every card gated away — nothing to show, go back to the board.
  if (!area) return <Navigate to="/home" replace />

  return (
    <div style={{ margin: -24 }}>
      {/* Banner — who this is about, so a shared screenshot is never ambiguous. */}
      <div style={banner}>
        <Button
          type="text"
          onClick={() => navigate('/home')}
          icon={<ArrowLeftOutlined />}
          style={backBtn}
        >
          All apps
        </Button>
        <div style={avatar}>{initials(name)}</div>
        <div style={{ color: '#fff', fontSize: 22, fontWeight: 600, marginTop: 14 }}>{name}</div>
        {subtitle && <div style={{ color: 'rgba(255,255,255,0.75)', marginTop: 4 }}>{subtitle}</div>}
      </div>

      <div style={{ padding: '28px clamp(20px, 5vw, 64px) 56px', background: brand.cream, minHeight: 360 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 600, color: brand.ink }}>{area.label}</h1>
        <div style={{ color: 'rgba(26,26,46,0.6)', marginTop: 6, fontSize: 14 }}>{area.blurb}</div>

        {cards.length === 0 ? (
          <div style={{ marginTop: 28, color: 'rgba(26,26,46,0.6)' }}>
            Nothing here is available on your company's current plan.
          </div>
        ) : (
          <div style={grid}>
            {cards.map((c) => (
              <div
                key={c.label}
                role="button"
                tabIndex={0}
                onClick={() => navigate(c.to)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') navigate(c.to)
                }}
                style={cardBox}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-3px)'
                  e.currentTarget.style.boxShadow = brand.cardShadow
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'none'
                  e.currentTarget.style.boxShadow = '0 1px 3px rgba(26,26,46,0.08)'
                }}
              >
                <div style={cardIcon}>{icon(c.icon)}</div>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600, color: brand.ink }}>{c.label}</div>
                  <div style={{ marginTop: 6, fontSize: 13, color: 'rgba(26,26,46,0.6)', lineHeight: 1.45 }}>
                    {c.desc}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function initials(name: string): string {
  const parts = name.replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(' ').filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return parts[0]?.slice(0, 2).toUpperCase() ?? ''
}

const banner: CSSProperties = {
  position: 'relative',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  padding: '28px 24px 34px',
  background:
    'radial-gradient(70% 120% at 100% 0%, rgba(111,224,224,0.35) 0%, transparent 60%),' +
    `linear-gradient(160deg, ${brand.purple} 0%, ${brand.purpleDeep} 100%)`,
}
const backBtn: CSSProperties = {
  position: 'absolute',
  left: 16,
  top: 16,
  color: 'rgba(255,255,255,0.9)',
}
const avatar: CSSProperties = {
  width: 88,
  height: 88,
  borderRadius: 12,
  background: 'rgba(255,255,255,0.18)',
  border: '1px solid rgba(255,255,255,0.28)',
  color: '#fff',
  fontSize: 30,
  fontWeight: 600,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}
const grid: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
  gap: 18,
  marginTop: 26,
}
const cardBox: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 16,
  padding: '22px 22px 26px',
  background: '#fff',
  border: '1px solid rgba(26,26,46,0.08)',
  borderRadius: 10,
  boxShadow: '0 1px 3px rgba(26,26,46,0.08)',
  cursor: 'pointer',
  transition: 'transform .15s, box-shadow .15s',
}
const cardIcon: CSSProperties = {
  flex: '0 0 auto',
  width: 46,
  height: 46,
  borderRadius: '50%',
  background: brand.purple,
  color: '#fff',
  fontSize: 21,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}
