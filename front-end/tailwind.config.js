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
        surface: 'var(--glass)',
        hairline: 'var(--line)',
        brand: {
          50: '#E6F1FB',
          100: '#B5D4F4',
          400: '#378ADD',
          600: '#185FA5',
          800: '#0C447C'
        }
      },
      fontFamily: {
        sans: ['Outfit', 'Geist', 'Satoshi', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', 'sans-serif']
      }
    }
  },
  plugins: []
}
