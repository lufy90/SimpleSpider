export function normalizePlaySrc(playSrc) {
  if (!playSrc) return null
  if (typeof playSrc === 'string') {
    return { content_type: 'video', video: playSrc }
  }
  return playSrc
}

export function isPlayable(playSrc) {
  const ps = normalizePlaySrc(playSrc)
  if (!ps) return false
  if (ps.content_type === 'video') return !!ps.video
  const slides = ps.slides || []
  return !!(ps.music || slides.length)
}

export function isSlidesPlaySrc(playSrc) {
  const ps = normalizePlaySrc(playSrc)
  if (!ps) return false
  return ps.content_type === 'photo_slides' || ps.content_type === 'video_slides'
}

export function primaryOpenUrl(playSrc) {
  const ps = normalizePlaySrc(playSrc)
  if (!ps) return null
  if (ps.content_type === 'video') return ps.video || null
  if (ps.music) return ps.music
  const slides = ps.slides || []
  return slides[0]?.url || null
}

export function formatPlaySrcDisplay(playSrc) {
  const ps = normalizePlaySrc(playSrc)
  if (!ps) return '-'
  if (ps.content_type === 'video') return ps.video || '-'
  const lines = []
  if (ps.music) lines.push(`music: ${ps.music}`)
  for (const slide of ps.slides || []) {
    lines.push(`${slide.kind}[${slide.index}]: ${slide.url}`)
  }
  return lines.length ? lines.join('\n') : '-'
}
