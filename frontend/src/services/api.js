import { t } from '../utils/i18n'

const BASE_URL = '/api/v1'

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request(method, path, body) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'network_error', t('errors.network'))
  }

  if (response.status === 204) return null

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, data?.error ?? 'unknown', data?.message ?? t('errors.generic'))
  }
  return data
}

export const authApi = {
  me: () => request('GET', '/auth/me'),
  register: (data) => request('POST', '/auth/register', data),
  login: (data) => request('POST', '/auth/login', data),
  logout: () => request('POST', '/auth/logout'),
  forgotPassword: (data) => request('POST', '/auth/forgot-password', data),
  resetPassword: (data) => request('POST', '/auth/reset-password', data),
}

export const accountApi = {
  update: (data) => request('PATCH', '/account', data),
  addresses: () => request('GET', '/account/addresses'),
  createAddress: (data) => request('POST', '/account/addresses', data),
  updateAddress: (id, data) => request('PUT', `/account/addresses/${id}`, data),
  deleteAddress: (id) => request('DELETE', `/account/addresses/${id}`),
}

export const adminApi = {
  status: () => request('GET', '/admin/status'),
}
