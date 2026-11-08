let revealObserver = null
let countObserver = null

function reducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function getRevealObserver() {
  if (revealObserver) return revealObserver
  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add('is-visible')
        revealObserver.unobserve(entry.target)
      })
    },
    { threshold: 0.08, rootMargin: '0px 0px -36px 0px' }
  )
  return revealObserver
}

/** v-reveal="2" or v-reveal="{ delay: 2 }" — staggered fade/slide on scroll */
export const reveal = {
  mounted(el, binding) {
    const delay = typeof binding.value === 'number'
      ? binding.value
      : Number(binding.value?.delay ?? 0)
    el.style.setProperty('--d', String(Math.max(0, delay)))
    el.classList.add('reveal')
    if (reducedMotion()) {
      el.classList.add('is-visible')
      return
    }
    getRevealObserver().observe(el)
  },
  unmounted(el) {
    revealObserver?.unobserve(el)
  }
}

/** v-spotlight — pointer-following soft accent light on glass surfaces */
export const spotlight = {
  mounted(el) {
    const onMove = (event) => {
      const rect = el.getBoundingClientRect()
      el.style.setProperty('--mx', `${event.clientX - rect.left}px`)
      el.style.setProperty('--my', `${event.clientY - rect.top}px`)
    }
    el.__aaSpotlight = onMove
    el.addEventListener('pointermove', onMove, { passive: true })
  },
  unmounted(el) {
    if (el.__aaSpotlight) el.removeEventListener('pointermove', el.__aaSpotlight)
  }
}

/**
 * v-count="112" — count up once the number enters the viewport.
 * The bound value is often loaded asynchronously (overview / rule policy),
 * so the target is kept on the element and re-applied via `updated`.
 */
export const count = {
  mounted(el, binding) {
    el.__aaCountTarget = Number(binding.value) || 0
    el.__aaCountStarted = false
    el.__aaCountRun = () => {
      el.__aaCountStarted = true
      if (el.__aaRaf) cancelAnimationFrame(el.__aaRaf)
      const target = el.__aaCountTarget
      if (reducedMotion()) {
        el.textContent = String(target)
        return
      }
      const from = Number(String(el.textContent).replace(/[^\d.-]/g, '')) || 0
      const duration = 1150
      let start = null
      const step = (ts) => {
        if (start === null) start = ts
        const progress = Math.min((ts - start) / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        el.textContent = String(Math.round(from + (target - from) * eased))
        if (progress < 1) el.__aaRaf = requestAnimationFrame(step)
      }
      el.__aaRaf = requestAnimationFrame(step)
    }
    if (!countObserver) {
      countObserver = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return
          countObserver.unobserve(entry.target)
          entry.target.__aaCountRun?.()
        })
      }, { threshold: 0.4 })
    }
    countObserver.observe(el)
  },
  updated(el, binding) {
    const next = Number(binding.value) || 0
    if (next === el.__aaCountTarget) return
    el.__aaCountTarget = next
    // 已进入视口：从当前显示值平滑过渡到新值；否则等 observer 首次触发时读取最新 target
    if (el.__aaCountStarted) el.__aaCountRun?.()
  },
  unmounted(el) {
    countObserver?.unobserve(el)
    if (el.__aaRaf) cancelAnimationFrame(el.__aaRaf)
  }
}
