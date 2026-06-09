/**
 * M228 — couples i18next's active language to Ant Design's
 * {@link ConfigProvider} locale + dayjs' active locale.
 *
 * Wrap the rest of the app exactly once at the top of the tree. Every
 * AntD component below this provider — DatePicker month names, Table
 * pagination labels, Popconfirm yes/no, etc. — picks up the right
 * locale automatically, so individual pages never have to set it.
 */
import { useEffect, useState } from 'react'
import { ConfigProvider, theme as antdTheme } from 'antd'
import azAZ from 'antd/locale/az_AZ'
import enUS from 'antd/locale/en_US'
import dayjs from 'dayjs'
import 'dayjs/locale/az'
import 'dayjs/locale/en'
import i18n, { DEFAULT_LANG, LANG_STORAGE_KEY, type SupportedLang } from './index'
import { brand } from '../theme'

/** AntD locale resolution — one place; if we ever add a 3rd language
 *  this is the only switch that grows. */
function antdLocaleFor(lang: SupportedLang) {
  switch (lang) {
    case 'az': return azAZ
    case 'en': return enUS
  }
}

/** dayjs locale resolution. dayjs requires the side-effect import
 *  ABOVE before {@link dayjs.locale} accepts the code. */
function applyDayjsLocale(lang: SupportedLang) {
  dayjs.locale(lang)
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  // i18n.language can be a region tag ("en-US"); normalise to the
  // base code the switcher emits.
  const [lang, setLang] = useState<SupportedLang>(
    (i18n.language?.split('-')[0] as SupportedLang) ?? DEFAULT_LANG,
  )

  useEffect(() => {
    applyDayjsLocale(lang)
    // Keep our local React state in sync with i18next when something
    // outside the provider changes the language (e.g. tests).
    const onChange = (code: string) => {
      const base = (code?.split('-')[0] as SupportedLang) ?? DEFAULT_LANG
      setLang(base)
      applyDayjsLocale(base)
    }
    i18n.on('languageChanged', onChange)
    return () => i18n.off('languageChanged', onChange)
  }, [lang])

  return (
    <ConfigProvider
      locale={antdLocaleFor(lang)}
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
        algorithm: antdTheme.defaultAlgorithm,
      }}
    >
      {children}
    </ConfigProvider>
  )
}

/**
 * Centralised setter for any caller that needs to change the
 * language imperatively (the switcher, deep links, programmatic
 * locale changes). Always go through this — direct calls to
 * `i18n.changeLanguage` bypass the localStorage write and dayjs
 * sync would drift.
 */
export function changeLanguage(next: SupportedLang) {
  localStorage.setItem(LANG_STORAGE_KEY, next)
  i18n.changeLanguage(next)
}
