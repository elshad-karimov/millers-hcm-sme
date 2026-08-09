import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { selfApi } from '../api/self'
import { ModuleBrowser } from '../components/ModuleBrowser'

/* Millers brand gradient — true brand purple base with the logo's cyan (left
   bar) + green (right bar) glowing from the top-right, kept clear of the
   top-left greeting so white text stays legible. */
const HERO_BG =
  'radial-gradient(70% 60% at 100% 0%, rgba(157,238,58,0.30) 0%, transparent 55%),' +
  'radial-gradient(65% 58% at 86% 6%, rgba(111,224,224,0.38) 0%, transparent 55%),' +
  'linear-gradient(160deg, #5B3FE5 0%, #3F26C5 52%, #2A1AA0 100%)'

function greetingWord() {
  const h = new Date().getHours()
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
}

export default function HomePage() {
  const { user } = useAuth()
  const [name, setName] = useState('')
  const [subtitle, setSubtitle] = useState('')

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
        if (alive) setName(user?.username ?? '')
      })
    return () => {
      alive = false
    }
  }, [user])

  return (
    <div style={{ minHeight: 'calc(100vh - 64px)', background: HERO_BG, margin: -24, padding: '40px clamp(24px, 5vw, 72px) 64px' }}>
      <h1 style={{ color: '#fff', fontSize: 'clamp(26px, 3.4vw, 40px)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>
        {greetingWord()}
        {name ? `, ${name}` : ''}
      </h1>
      {subtitle && <div style={{ color: 'rgba(255,255,255,0.72)', marginTop: 6, fontSize: 15 }}>{subtitle}</div>}

      <div style={{ marginTop: 30 }}>
        <ModuleBrowser showShortcuts />
      </div>
    </div>
  )
}
