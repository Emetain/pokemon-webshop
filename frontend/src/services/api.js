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

/** Leaves out empty values, so a filter that isn't set doesn't end up in the URL. */
function queryString(params) {
  const filled = Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  return filled.length ? `?${new URLSearchParams(filled)}` : ''
}

const setPath = (game, code) => `/catalog/${game}/sets/${encodeURIComponent(code)}`

export const catalogApi = {
  sets: (game) => request('GET', `/catalog/${game}/sets`),
  set: (game, code) => request('GET', setPath(game, code)),
  cards: (game, code, params) => request('GET', `${setPath(game, code)}/cards${queryString(params)}`),
  card: (id) => request('GET', `/catalog/cards/${id}`),
  search: (params) => request('GET', `/catalog/search${queryString(params)}`),
  exchangeRates: () => request('GET', '/catalog/exchange-rates'),
}

export const collectionApi = {
  overview: () => request('GET', '/collection'),
  history: () => request('GET', '/collection/history'),
  startedSets: (game) => request('GET', `/collection/${game}/sets`),
  set: (game, code) => request('GET', `/collection/${game}/sets/${encodeURIComponent(code)}`),
  card: (id) => request('GET', `/collection/cards/${id}`),
  add: (data) => request('POST', '/collection/entries', data),
  addBulk: (cardIds) => request('POST', '/collection/entries/bulk', { cardIds }),
  update: (id, data) => request('PUT', `/collection/entries/${id}`, data),
  remove: (id) => request('DELETE', `/collection/entries/${id}`),
  share: () => request('PUT', '/collection/share'),
  stopSharing: () => request('DELETE', '/collection/share'),
  shared: (slug) => request('GET', `/shared-collections/${encodeURIComponent(slug)}`),
}

export const pokedexApi = {
  all: () => request('GET', '/pokedex'),
  entry: (dexNumber) => request('GET', `/pokedex/${dexNumber}`),
  cards: (dexNumber, params) => request('GET', `/pokedex/${dexNumber}/cards${queryString(params)}`),
}

export const wishlistApi = {
  cards: (params) => request('GET', `/wishlist/cards${queryString(params)}`),
  add: (cardId) => request('PUT', `/wishlist/cards/${cardId}`),
  remove: (cardId) => request('DELETE', `/wishlist/cards/${cardId}`),
}

export const binderApi = {
  page: (page) => request('GET', `/binder${queryString({ page })}`),
  selection: () => request('GET', '/binder/selection'),
  select: (cardId) => request('PUT', `/binder/selection/${cardId}`),
  deselect: (cardId) => request('DELETE', `/binder/selection/${cardId}`),
}

export const adminApi = {
  status: () => request('GET', '/admin/status'),
  syncStatus: () => request('GET', '/admin/catalog/sync'),
  startSync: (game) => request('POST', `/admin/catalog/sync/${game}`),
}
