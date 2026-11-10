import { t } from '../utils/i18n'

const BASE_URL = '/api/v1'

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function send(method, path, options) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, { method, ...options })
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

function request(method, path, body) {
  if (body === undefined) return send(method, path)
  return send(method, path, { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
}

/** The browser sets the content type of a form itself, with the boundary it needs. */
function upload(path, file) {
  const body = new FormData()
  body.append('file', file)
  return send('POST', path, { body })
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
  imageCards: (params) => request('GET', `/admin/catalog/images/cards${queryString(params)}`),
  uploadCardImage: (id, file) => upload(`/admin/catalog/images/cards/${id}`, file),
  resetCardImage: (id) => request('DELETE', `/admin/catalog/images/cards/${id}`),
  uploadSetLogo: (game, code, file) => upload(`/admin/catalog/images/sets/${game}/${encodeURIComponent(code)}`, file),
  resetSetLogo: (game, code) => request('DELETE', `/admin/catalog/images/sets/${game}/${encodeURIComponent(code)}`),
}

export const shopApi = {
  products: (params) => request('GET', `/products${queryString(params)}`),
  product: (slug) => request('GET', `/products/${encodeURIComponent(slug)}`),
  newArrivals: (limit) => request('GET', `/products/new-arrivals${queryString({ limit })}`),
  bestsellers: (limit) => request('GET', `/products/bestsellers${queryString({ limit })}`),
  stockAlert: (slug, email) => request('POST', `/products/${encodeURIComponent(slug)}/stock-alerts`, { email }),
  series: (game) => request('GET', `/shop/${game}/series`),
  seriesDetail: (game, slug) => request('GET', `/shop/${game}/series/${encodeURIComponent(slug)}`),
  heroSlides: () => request('GET', '/shop/hero-slides'),
}

export const productWishlistApi = {
  products: (params) => request('GET', `/wishlist/products${queryString(params)}`),
  add: (productId) => request('PUT', `/wishlist/products/${productId}`),
  remove: (productId) => request('DELETE', `/wishlist/products/${productId}`),
}

export const adminShopApi = {
  products: (params) => request('GET', `/admin/products${queryString(params)}`),
  product: (id) => request('GET', `/admin/products/${id}`),
  create: (data) => request('POST', '/admin/products', data),
  update: (id, data) => request('PUT', `/admin/products/${id}`, data),
  duplicate: (id) => request('POST', `/admin/products/${id}/duplicate`),
  archive: (id) => request('POST', `/admin/products/${id}/archive`),
  addImage: (id, file) => upload(`/admin/products/${id}/images`, file),
  removeImage: (id, index) => request('DELETE', `/admin/products/${id}/images/${index}`),
  reorderImages: (id, urls) => request('PUT', `/admin/products/${id}/images`, { urls }),
  movements: (id, page) => request('GET', `/admin/products/${id}/stock/movements${queryString({ page })}`),
  adjustStock: (id, data) => request('POST', `/admin/products/${id}/stock/adjustments`, data),
  countStock: (id, data) => request('POST', `/admin/products/${id}/stock/count`, data),
  createSingle: (data) => request('POST', '/admin/products/singles', data),
  market: (cardId) => request('GET', `/admin/products/singles/market/${cardId}`),
  lowStock: () => request('GET', '/admin/stock/low'),
  latestMovements: (page) => request('GET', `/admin/stock/movements${queryString({ page })}`),
  importCardmarket: (game) => request('POST', `/admin/product-import/cardmarket/${game}`),
  setTemplates: (game) => request('GET', `/admin/product-import/set-templates/${game}`),
  createForSet: (data) => request('POST', '/admin/product-import/set-products', data),
  importCsv: (file) => upload('/admin/product-import/csv', file),
  /** A plain link: the browser downloads the file with the session cookie. */
  csvUrl: `${BASE_URL}/admin/product-import/csv`,
  slides: () => request('GET', '/admin/hero-slides'),
  createSlide: (data) => request('POST', '/admin/hero-slides', data),
  updateSlide: (id, data) => request('PUT', `/admin/hero-slides/${id}`, data),
  deleteSlide: (id) => request('DELETE', `/admin/hero-slides/${id}`),
  moveSlide: (id, direction) => request('POST', `/admin/hero-slides/${id}/move${queryString({ direction })}`),
  uploadSlideImage: (id, file) => upload(`/admin/hero-slides/${id}/image`, file),
  removeSlideImage: (id) => request('DELETE', `/admin/hero-slides/${id}/image`),
}

export const cartApi = {
  get: (country) => request('GET', `/cart${queryString({ country })}`),
  add: (productId, quantity, country) => request('POST', `/cart/items${queryString({ country })}`, { productId, quantity }),
  setQuantity: (productId, quantity, country) =>
    request('PUT', `/cart/items/${productId}${queryString({ country })}`, { quantity }),
  remove: (productId, country) => request('DELETE', `/cart/items/${productId}${queryString({ country })}`),
}

export const checkoutApi = {
  options: () => request('GET', '/checkout/options'),
  start: (data) => request('POST', '/checkout', data),
}

export const orderApi = {
  get: (number, token) => request('GET', `/orders/${encodeURIComponent(number)}${queryString({ token })}`),
  cancel: (number, token) => request('POST', `/orders/${encodeURIComponent(number)}/cancel`, { token: token ?? '' }),
  createAccount: (number, token, password) =>
    request('POST', `/orders/${encodeURIComponent(number)}/account`, { token, password }),
  mine: (page) => request('GET', `/account/orders${queryString({ page })}`),
  myOrder: (number) => request('GET', `/account/orders/${encodeURIComponent(number)}`),
}

export const adminOrderApi = {
  list: (params) => request('GET', `/admin/orders${queryString(params)}`),
  get: (number) => request('GET', `/admin/orders/${encodeURIComponent(number)}`),
  setStatus: (number, data) => request('POST', `/admin/orders/${encodeURIComponent(number)}/status`, data),
  ship: (number, data) => request('POST', `/admin/orders/${encodeURIComponent(number)}/shipment`, data),
  cancel: (number, data) => request('POST', `/admin/orders/${encodeURIComponent(number)}/cancel`, data),
  refund: (number, data) => request('POST', `/admin/orders/${encodeURIComponent(number)}/refund`, data),
  shippingRates: () => request('GET', '/admin/shipping-rates'),
  updateShippingRate: (country, data) => request('PUT', `/admin/shipping-rates/${country}`, data),
  dashboard: () => request('GET', '/admin/dashboard'),
}

const dropPath = (slug) => `/drops/${encodeURIComponent(slug)}`

export const dropApi = {
  list: () => request('GET', '/drops'),
  live: () => request('GET', '/drops/live'),
  get: (slug) => request('GET', dropPath(slug)),
  remind: (slug, data) => request('POST', `${dropPath(slug)}/reminders`, data),
  join: (slug, data = {}) => request('POST', `${dropPath(slug)}/queue`, data),
  ping: (slug) => request('POST', `${dropPath(slug)}/queue/ping`),
  cart: (slug, country) => request('GET', `${dropPath(slug)}/cart${queryString({ country })}`),
  setQuantity: (slug, dropProductId, quantity, country) =>
    request('PUT', `${dropPath(slug)}/cart/${dropProductId}${queryString({ country })}`, { quantity }),
  checkout: (slug, data) => request('POST', `${dropPath(slug)}/checkout`, data),
}

export const adminDropApi = {
  list: () => request('GET', '/admin/drops'),
  get: (id) => request('GET', `/admin/drops/${id}`),
  create: (data) => request('POST', '/admin/drops', data),
  update: (id, data) => request('PUT', `/admin/drops/${id}`, data),
  remove: (id) => request('DELETE', `/admin/drops/${id}`),
  publish: (id) => request('POST', `/admin/drops/${id}/publish`),
  unpublish: (id) => request('POST', `/admin/drops/${id}/unpublish`),
  addProduct: (id, data) => request('POST', `/admin/drops/${id}/products`, data),
  updateProduct: (id, dropProductId, data) => request('PUT', `/admin/drops/${id}/products/${dropProductId}`, data),
  removeProduct: (id, dropProductId) => request('DELETE', `/admin/drops/${id}/products/${dropProductId}`),
  uploadBanner: (id, file) => upload(`/admin/drops/${id}/banner`, file),
  removeBanner: (id) => request('DELETE', `/admin/drops/${id}/banner`),
  live: (id) => request('GET', `/admin/drops/${id}/live`),
  steerQueue: (id, data) => request('POST', `/admin/drops/${id}/queue`, data),
  end: (id) => request('POST', `/admin/drops/${id}/end`),
  returnLeftover: (id) => request('POST', `/admin/drops/${id}/leftover`),
}
