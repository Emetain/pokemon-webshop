import { computed, reactive, watch } from 'vue'
import { cartApi } from '../services/api'
import { useRegion } from './region'
import { useSession } from './session'

const state = reactive({
  cart: null,
  loading: false,
  error: '',
  drawerOpen: false,
  /** The product that was just added, for the message at the top of the drawer. */
  lastAdded: null,
})

const session = useSession()
const region = useRegion()

let latestRequest = 0

/** Every change returns the whole cart. Only the answer to the latest request counts. */
async function apply(call) {
  const request = ++latestRequest
  const cart = await call()
  if (request === latestRequest) state.cart = cart
  return cart
}

async function load() {
  state.loading = true
  state.error = ''
  try {
    await apply(() => cartApi.get(region.country.value))
  } catch (exception) {
    state.error = exception.message
  } finally {
    state.loading = false
  }
}

/** Throws with the shop's message, such as "Er zijn er nog maar 2 van dit product", so the caller can show it. */
async function add(product, quantity = 1) {
  await apply(() => cartApi.add(product.id, quantity, region.country.value))
  state.lastAdded = { name: product.name, quantity }
  state.drawerOpen = true
}

function setQuantity(productId, quantity) {
  return apply(() => cartApi.setQuantity(productId, quantity, region.country.value))
}

function remove(productId) {
  return apply(() => cartApi.remove(productId, region.country.value))
}

function openDrawer() {
  state.lastAdded = null
  state.drawerOpen = true
}

function closeDrawer() {
  state.drawerOpen = false
}

const count = computed(() => state.cart?.itemCount ?? 0)

/** How many of a product are in the cart, for the buttons on tiles and the product page. */
function quantityOf(productId) {
  return state.cart?.items.find((item) => item.productId === productId)?.quantity ?? 0
}

let started = false

/** Loads the cart once, and again when someone logs in or out, or picks another country. */
function start() {
  if (started) return
  started = true
  session.ensureLoaded().then(() => {
    load()
    // Logging in merges the guest cart into the account cart, logging out leaves an empty guest cart.
    watch(() => session.state.user?.id, load)
    watch(region.country, load)
  })
}

export function useCart() {
  return { state, count, start, load, add, setQuantity, remove, openDrawer, closeDrawer, quantityOf }
}
