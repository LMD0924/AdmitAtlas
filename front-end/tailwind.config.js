/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class', '[data-theme="dark"]'],
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        ink: 'var(--text)',
        muted: 'var(--text-2)',
        faint: 'var(--text-3)',
        accent: 'var(--accent)',
        accentsoft: 'var(--accent-soft)',
        surface: 'var(--surface)',
        hairline: 'var(--line)',
        mint: 'var(--mint)',
        sky: 'var(--sky)',
        violet: 'var(--violet)',
        amber: 'var(--amber)',
        rose: 'var(--rose)',
        teal: 'var(--teal)'
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        hand: ['Ma Shan Zheng', 'STXINGKAI', 'KaiTi', 'cursive']
      }
    }
  },
  plugins: []
}
