import { ref, watch } from 'vue'

const STORAGE_KEY = 'admitatlas-theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

function readStored() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

function systemTheme() {
  return window.matchMedia?.(DARK_QUERY).matches ? 'dark' : 'light'
}

const theme = ref(readStored() || (typeof window !== 'undefined' ? systemTheme() : 'light'))
let listening = false

function apply(value) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', value)
  document.documentElement.style.colorScheme = value
}

watch(theme, (value) => {
  apply(value)
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    /* storage may be unavailable in private mode */
  }
})

export function useTheme() {
  function initTheme() {
    apply(readStored() || systemTheme())
    if (listening || typeof window === 'undefined') return
    listening = true
    window.matchMedia?.(DARK_QUERY).addEventListener?.('change', (event) => {
      if (!readStored()) theme.value = event.matches ? 'dark' : 'light'
    })
  }

  function setTheme(value) {
    if (value === 'light' || value === 'dark') theme.value = value
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  return { theme, initTheme, setTheme, toggleTheme }
}
