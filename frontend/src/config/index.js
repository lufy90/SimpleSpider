/**
 * Map frontend request host (window.location.hostname) to API domain (host:port).
 * Add entries for each IP/host you use to access the frontend.
 */
export const HOST_TO_APIDOMAIN = {
  localhost: '192.168.1.134:8003',
  '127.0.0.1': '192.168.1.134:8003',
  '192.168.1.137': '192.168.1.137:8000',
  '10.0.1.137': '10.0.1.137:8000',
  '192.168.1.134': '192.168.1.134:8001',
}

const DEFAULT_APIDOMAIN =
  process.env.NODE_ENV === 'development' ? '192.168.1.134:8003' : '192.168.1.137:8000'

function getApiDomain() {
  if (typeof window === 'undefined') return DEFAULT_APIDOMAIN
  const host = window.location.hostname
  return HOST_TO_APIDOMAIN[host] ?? DEFAULT_APIDOMAIN
}

export const APIDOMAIN = getApiDomain()
export const APIURL = `http://${APIDOMAIN}/api`
export const STATICURL = `http://${APIDOMAIN}/static/`
export const timeOption = { timeZone: 'Asia/Shanghai' }
export const DYURL = `http://${APIDOMAIN}/api/dydata/`

