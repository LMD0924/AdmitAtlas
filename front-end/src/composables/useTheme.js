import { ref, watch } from 'vue'

const STORAGE_KEY = 'admitatlas-theme'
// The reference design is a light, paper-like UI, so light is the default.
// A user's explicit toggle is remembered; we no longer auto-switch on OS changes.

function readStored() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

const theme = ref(readStored() || 'light')

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
    apply(theme.value)
  }

  function setTheme(value) {
    if (value === 'light' || value === 'dark') theme.value = value
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  return { theme, initTheme, setTheme, toggleTheme }
}
