/**
 * M228 — language switcher dropdown.
 *
 * Single component reused wherever a language picker is needed (today
 * the top-bar; tomorrow possibly the login screen). Reads the active
 * language from i18next so it stays in sync if anything changes it
 * elsewhere; writes back through {@link changeLanguage} so the
 * localStorage + dayjs sides are always in lockstep.
 */
import { Dropdown, Button } from 'antd'
import type { MenuProps } from 'antd'
import { GlobalOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES, type SupportedLang } from './index'
import { changeLanguage } from './LanguageProvider'

export function LanguageSwitcher({ color }: { color?: string }) {
  const { i18n, t } = useTranslation('common')
  const current = (i18n.language?.split('-')[0] as SupportedLang) ?? 'az'
  const active = SUPPORTED_LANGUAGES.find((l) => l.code === current) ?? SUPPORTED_LANGUAGES[0]

  const items: MenuProps['items'] = SUPPORTED_LANGUAGES.map((l) => ({
    key: l.code,
    label: (
      <span>
        <span style={{ marginRight: 8 }}>{l.flag}</span>
        {l.label}
      </span>
    ),
  }))

  return (
    <Dropdown
      menu={{
        items,
        selectable: true,
        selectedKeys: [current],
        onClick: ({ key }) => changeLanguage(key as SupportedLang),
      }}
      placement="bottomRight"
      trigger={['click']}
    >
      <Button
        type="text"
        icon={<GlobalOutlined />}
        style={{ color, fontWeight: 500 }}
        title={t('language')}
      >
        {active.flag} {active.code.toUpperCase()}
      </Button>
    </Dropdown>
  )
}
