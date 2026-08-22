import { useEffect, useMemo, useRef, useState } from 'react'
import { Layout, AutoComplete, Input, Dropdown, Avatar, Button, Tag, Space } from 'antd'
import type { MenuProps } from 'antd'
import {
  ArrowLeftOutlined,
  MenuOutlined,
  HomeOutlined,
  StarOutlined,
  SearchOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { Link, Outlet, useLocation, useNavigate, useNavigationType } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'
import { Navigator } from './Navigator'
import { OwnBackProvider } from './PageBack'
import { brand } from '../theme'
import { LanguageSwitcher } from '../i18n/LanguageSwitcher'
import { ALL_TILES, icon, isHiddenScreen, moduleOfRoute } from '../nav/modules'
import { areaVisible } from '../nav/selfServiceAreas'
import { useFavorites } from '../nav/navPrefs'
import { useEnabledModules } from '../nav/moduleSettings'

const { Header, Content } = Layout

/** Dark brand bar behind the Redwood-style utilities. */
const HEADER_BG = 'linear-gradient(90deg, #17123A 0%, #1D1650 100%)'

function initials(name: string): string {
  const parts = name.replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(' ').filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return name.slice(0, 2).toUpperCase()
}

/**
 * Redwood-style global chrome: a slim dark bar with ☰ Navigator, a centred
 * jump-to search, and Home / Favorites / Notifications / avatar on the right.
 *
 * The full module list lives in the Navigator (☰) and the Home springboard —
 * there is no per-module mega-menu here, so a module is reachable from any page
 * without first returning Home.
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const { t: tNav } = useTranslation('nav')
  const { t: tCommon } = useTranslation('common')
  const navigate = useNavigate()
  const location = useLocation()
  const navigationType = useNavigationType()

  const [navOpen, setNavOpen] = useState(false)
  /** Set by a page that renders its own back control (see PageBack). */
  const [pageHasOwnBack, setPageHasOwnBack] = useState(false)
  const [search, setSearch] = useState('')

  const { favorites } = useFavorites()
  const { loaded, disabled: disabledModules, notInPlan, plan } = useEnabledModules()

  // Guard: a deep-link into an unavailable module bounces (open until loaded).
  // Out-of-plan lands on the upgrade page — a silent redirect to Home reads as
  // a broken link when the real answer is "this needs a higher plan". Switched
  // off by the tenant's own admin still goes Home; nothing to sell there.
  useEffect(() => {
    if (!loaded) return
    const mod = moduleOfRoute(location.pathname)
    if (!mod || !disabledModules.has(mod)) return
    if (notInPlan.has(mod)) {
      navigate(`/upgrade?module=${encodeURIComponent(mod)}`, { replace: true })
    } else {
      navigate('/home', { replace: true })
    }
  }, [location.pathname, loaded, disabledModules, notInPlan, navigate])

  /**
   * One Back control for every screen, rather than each page inventing its own
   * (several never did, which is what prompted this).
   *
   * Not shown on the springboard, where there is nothing behind, nor on the
   * /me/... boards, which already carry "All apps" in the same corner.
   */
  const showBack =
    location.pathname !== '/home' &&
    location.pathname !== '/' &&
    !location.pathname.startsWith('/me/') &&
    // A page with its own control — "Back to list" on the forms — says so, and
    // gets to keep it alone.
    !pageHasOwnBack

  /**
   * How many pages deep into the app we are.
   *
   * Counted here rather than read from history.state.idx, which was the first
   * attempt and sent everything to Home: keycloak-js rewrites history state
   * when it strips the auth code from the URL after login, and the index does
   * not reliably survive that. Counting PUSH and POP ourselves depends on
   * nothing outside React Router.
   */
  const depth = useRef(0)
  useEffect(() => {
    if (navigationType === 'PUSH') depth.current += 1
    else if (navigationType === 'POP') depth.current = Math.max(0, depth.current - 1)
    // REPLACE keeps the depth: it swaps the current entry rather than adding one.
  }, [location.key, navigationType])

  const goBack = () => {
    // Only fall back to Home when there is genuinely nothing of ours behind —
    // a deep link, a bookmark, a fresh tab — where going back would leave the
    // application altogether.
    if (depth.current > 0) navigate(-1)
    else navigate('/home')
  }

  const username = user?.username ?? ''

  // Search + favorites exclude tiles from disabled modules, and screens this
  // edition does not show — otherwise a hidden screen is still one search away.
  const searchOptions = useMemo(
    () =>
      ALL_TILES.filter(
        (t) =>
          !isHiddenScreen(t.to) &&
          !disabledModules.has(t.module) &&
          areaVisible(t.to, disabledModules),
      ).map((t) => ({ value: t.to, label: t.label })),
    [disabledModules],
  )

  const favMenu: MenuProps = useMemo(() => {
    const shown = favorites.filter(
      (f) => !isHiddenScreen(f.to) && !disabledModules.has(f.needs ?? moduleOfRoute(f.to) ?? ''),
    )
    return {
      items: shown.length
        ? shown.map((f) => ({ key: f.to, icon: icon(f.icon), label: f.label }))
        : [{ key: '_none', disabled: true, label: 'No favorites yet — tap the ☆ on any app' }],
      onClick: ({ key }) => {
        if (key !== '_none') navigate(key)
      },
    }
  }, [favorites, disabledModules, navigate])

  const avatarMenu: MenuProps = {
    items: [
      {
        key: '_id',
        disabled: true,
        label: (
          <div style={{ padding: '4px 0', maxWidth: 240 }}>
            <div style={{ fontWeight: 600, color: brand.ink }}>{username}</div>
            <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {(user?.roles ?? []).map((r) => {
                const enumKey = r.replace('ROLE_', '')
                return (
                  <Tag
                    key={r}
                    style={{
                      margin: 0,
                      background: 'rgba(91,63,229,0.08)',
                      borderColor: 'rgba(91,63,229,0.20)',
                      color: brand.purpleDeep,
                      fontWeight: 500,
                    }}
                  >
                    {tCommon(`roles.${enumKey}`, { defaultValue: enumKey })}
                  </Tag>
                )
              })}
            </div>
          </div>
        ),
      },
      { type: 'divider' },
      { key: 'preferences', icon: <SettingOutlined />, label: tNav('preferences') },
      { key: 'signout', icon: <LogoutOutlined />, label: tNav('signOut') },
    ],
    onClick: ({ key }) => {
      if (key === 'preferences') navigate('/my/notifications')
      if (key === 'signout') logout()
    },
  }

  const iconBtn = { color: 'rgba(255,255,255,0.92)', fontSize: 18 } as const

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          background: HEADER_BG,
          padding: '0 20px',
          height: 64,
        }}
      >
        {/* Left — Navigator opener + brand mark */}
        <Button
          type="text"
          aria-label="Open navigator"
          icon={<MenuOutlined style={iconBtn} />}
          onClick={() => setNavOpen(true)}
          style={{ display: 'flex', alignItems: 'center' }}
        />
        <Link to="/home" style={{ display: 'flex', alignItems: 'center' }}>
          <Logo size={30} />
        </Link>

        {/* Center — jump-to search */}
        <div style={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
          <AutoComplete
            value={search}
            options={searchOptions}
            onChange={setSearch}
            onSelect={(val) => {
              navigate(val)
              setSearch('')
            }}
            filterOption={(input, option) =>
              String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
            style={{ width: '100%', maxWidth: 620 }}
          >
            <Input
              size="large"
              allowClear
              variant="borderless"
              prefix={<SearchOutlined style={{ color: 'rgba(255,255,255,0.6)' }} />}
              placeholder="Search apps and modules"
              style={{
                background: 'rgba(255,255,255,0.12)',
                borderRadius: 999,
                color: '#fff',
              }}
            />
          </AutoComplete>
        </div>

        {/* Right — utilities */}
        <Space size={4} align="center">
          {/* Edition badge. Hidden on ENTERPRISE (nothing to upsell) and until
              the config loads, so it never flashes a wrong plan. */}
          {loaded && plan !== 'ENTERPRISE' && (
            <Link to="/upgrade" aria-label={`${plan} plan — see what an upgrade adds`}>
              <Tag
                color={brand.green}
                style={{
                  color: brand.ink,
                  fontWeight: 600,
                  marginInlineEnd: 4,
                  cursor: 'pointer',
                  border: 'none',
                }}
              >
                {plan}
              </Tag>
            </Link>
          )}
          <Link to="/home">
            <Button type="text" aria-label="Home" icon={<HomeOutlined style={iconBtn} />} />
          </Link>
          <Dropdown menu={favMenu} trigger={['click']} placement="bottomRight">
            <Button type="text" aria-label="Favorites" icon={<StarOutlined style={iconBtn} />} />
          </Dropdown>
          <LanguageSwitcher color="rgba(255,255,255,0.92)" />
          <NotificationBell color="rgba(255,255,255,0.92)" />
          <Dropdown menu={avatarMenu} trigger={['click']} placement="bottomRight">
            <Avatar
              style={{
                background: brand.green,
                color: brand.ink,
                fontWeight: 600,
                cursor: 'pointer',
                marginLeft: 4,
              }}
            >
              {initials(username)}
            </Avatar>
          </Dropdown>
        </Space>
      </Header>

      <Content style={{ padding: 24, background: brand.cream }}>
        {showBack && (
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={goBack}
            style={{ marginBottom: 12, paddingInlineStart: 0 }}
          >
            Back
          </Button>
        )}
        <OwnBackProvider value={setPageHasOwnBack}>
          <Outlet />
        </OwnBackProvider>
      </Content>

      <Navigator open={navOpen} onClose={() => setNavOpen(false)} />
    </Layout>
  )
}
