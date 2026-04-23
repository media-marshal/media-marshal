import { createI18n } from 'vue-i18n'
import zh from './zh'
import en from './en'

const savedLocale = localStorage.getItem('mm-locale') || 'zh'

const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en',
  messages: { zh, en },
})

export default i18n

export function setLocale(locale: 'zh' | 'en') {
  ;(i18n.global.locale as any).value = locale
  localStorage.setItem('mm-locale', locale)
}
