/**
 * 院校主题色：列表卡片与详情页共用同一套配色，避免整页卡片「都是同一个白」。
 *
 * - 主题由院校代码中的数字序号轮转分配：同一所院校在任何页面、任何一次检索中
 *   颜色都稳定，且代码相邻的院校颜色必定不同，避免一整页卡片撞色；无需后端新增字段。
 * - 纯字母代码回落到「代码 + 校名」的 FNV-1a 散列。
 * - 层次标签（985/211/双一流/省重点/普通）另走语义色，用于传达信息而非装饰。
 */

export const SCHOOL_THEMES = ['sky', 'violet', 'teal', 'amber', 'rose', 'mint']

function hashTheme(key) {
  let hash = 2166136261
  for (let i = 0; i < key.length; i += 1) {
    hash ^= key.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return SCHOOL_THEMES[(hash >>> 0) % SCHOOL_THEMES.length]
}

export function schoolTheme(school = {}) {
  const code = String(school?.schoolCode ?? '')
  const digits = code.replace(/\D/g, '')
  if (digits) {
    // 只取末 6 位，避免超长代码超出安全整数范围。
    const n = Number(digits.slice(-6))
    if (Number.isFinite(n)) return SCHOOL_THEMES[n % SCHOOL_THEMES.length]
  }
  return hashTheme(`${code}|${school?.schoolName ?? ''}`)
}

/** 层次标签 → 语义色 class；未知标签回落到中性色。 */
export function tierPill(tier = '') {
  const t = String(tier)
  if (t.includes('985')) return 'pill-violet'
  if (t.includes('211')) return 'pill-sky'
  if (t.includes('双一流')) return 'pill-teal'
  if (t.includes('省重点')) return 'pill-amber'
  if (t.includes('普通')) return 'pill-rose'
  return 'pill-neutral'
}

export function splitTiers(tierTags) {
  return String(tierTags || '').split(',').map((t) => t.trim()).filter(Boolean)
}

/** 卡片左上角的校名首字徽标；中文取首字，英文取首字母大写。 */
export function schoolInitial(school = {}) {
  const name = String(school?.schoolName || school?.schoolCode || '院')
  return name.slice(0, 1).toUpperCase()
}
