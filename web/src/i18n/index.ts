/**
 * M228 — i18n bootstrap.
 *
 * Single source of truth for client-side localisation. Every page goes
 * through `useTranslation('namespace')` from `react-i18next` — no page
 * holds its own string map, no module hand-rolls its own language
 * detection. Adding a new language later = drop a folder under
 * `locales/<code>/` + extend `SUPPORTED_LANGUAGES`; zero code changes
 * elsewhere.
 *
 * Persistence: the chosen language is written to `localStorage` under
 * the key `hcm.lang`. First-time visitors with no stored choice get
 * Azerbaijani by default (the Millers HCM PRD targets an Azerbaijani
 * customer base; the AZ 2026 payroll/holiday seed reflects this).
 *
 * Namespacing strategy: one JSON file per concern so a missing key in
 * one namespace can't crash unrelated screens. Namespaces are loaded
 * up-front (small files; cheaper than the runtime overhead of lazy
 * imports).
 */
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import azCommon from './locales/az/common.json'
import azNav from './locales/az/nav.json'
import azEmployee from './locales/az/employee.json'
import azDashboard from './locales/az/dashboard.json'
import enCommon from './locales/en/common.json'
import enNav from './locales/en/nav.json'
import enEmployee from './locales/en/employee.json'
import enDashboard from './locales/en/dashboard.json'

/** Languages the SPA ships with. Order = order shown in the switcher. */
export const SUPPORTED_LANGUAGES = [
  { code: 'az', label: 'Azərbaycan', flag: '🇦🇿' },
  { code: 'en', label: 'English', flag: '🇬🇧' },
] as const

export type SupportedLang = (typeof SUPPORTED_LANGUAGES)[number]['code']

/** localStorage key — kept central so any other component reading it
 *  (e.g. login bootstrap) imports the same constant. */
export const LANG_STORAGE_KEY = 'hcm.lang'

/** Default + fallback. AZ default because the target customer base is
 *  Azerbaijani; EN fallback so any missing AZ key still renders English
 *  instead of a key path. */
export const DEFAULT_LANG: SupportedLang = 'az'
export const FALLBACK_LANG: SupportedLang = 'en'

/** Resources are wired here, never duplicated in `init` calls. */
const resources = {
  az: { common: azCommon, nav: azNav, employee: azEmployee, dashboard: azDashboard },
  en: { common: enCommon, nav: enNav, employee: enEmployee, dashboard: enDashboard },
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: FALLBACK_LANG,
    supportedLngs: SUPPORTED_LANGUAGES.map((l) => l.code),
    ns: ['common', 'nav', 'employee', 'dashboard'],
    defaultNS: 'common',
    interpolation: { escapeValue: false }, // React already escapes
    detection: {
      // Read order: explicit localStorage choice → browser navigator
      // → caches back into localStorage so it sticks.
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: LANG_STORAGE_KEY,
      caches: ['localStorage'],
    },
  })

// Honour the AZ default when the detector found nothing (fresh browser).
if (!localStorage.getItem(LANG_STORAGE_KEY) && i18n.language !== DEFAULT_LANG) {
  i18n.changeLanguage(DEFAULT_LANG)
}

export default i18n
