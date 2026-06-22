import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.response.use(
  res => res.data,
  err => {
    console.error('[api] error:', err.message)
    return Promise.reject(err)
  }
)

export const deviceApi = {
  list: () => http.get('/device/list'),
  stat: () => http.get('/device/stat'),
  detail: (id) => http.get(`/device/${id}`)
}

export const telemetryApi = {
  latest: (deviceId) => http.get(`/telemetry/latest/${deviceId}`),
  history: (deviceId, range = '1h', window = '1m') =>
    http.get('/telemetry/history', { params: { deviceId, range, window } })
}

export const alertApi = {
  recent: (deviceId, limit = 50) =>
    http.get('/alert/recent', { params: { deviceId, limit } }),
  handle: (id, status = 1) =>
    http.post(`/alert/handle/${id}`, null, { params: { status } })
}
