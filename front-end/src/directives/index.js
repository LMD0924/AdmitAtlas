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

/** v-count="112" — count up once the number enters the viewport */
export const count = {
  mounted(el, binding) {
    const target = Number(binding.value) || 0
    const duration = 1150
    if (reducedMotion()) {
      el.textContent = String(target)
      return
    }
    const run = () => {
      let start = null
      const step = (ts) => {
        if (start === null) start = ts
        const progress = Math.min((ts - start) / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        el.textContent = String(Math.round(target * eased))
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
    el.__aaCountRun = run
    countObserver.observe(el)
  },
  unmounted(el) {
    countObserver?.unobserve(el)
    if (el.__aaRaf) cancelAnimationFrame(el.__aaRaf)
  }
}
