import { computed, reactive } from 'vue'
import { accountApi, authApi, collectionApi } from '../services/api'

const state = reactive({
  user: null,
  loaded: false,
})

let loading = null

const isLoggedIn = computed(() => state.user !== null)
const isAdmin = computed(() => state.user?.role === 'ADMIN')
/** Premium right now, admins included (phase 11). Only for showing things: the server checks every feature itself. */
const isPremium = computed(() => Boolean(state.user?.premium))

/** Asks the backend who is logged in, once. Without an answer the visitor counts as logged out. */
function ensureLoaded() {
  if (state.loaded) return Promise.resolve()
  loading ??= authApi
    .me()
    .then((user) => {
      state.user = user
    })
    .catch(() => {
      state.user = null
    })
    .finally(() => {
      state.loaded = true
    })
  return loading
}

/** Asks again, for example after Premium started or ended. */
async function refresh() {
  try {
    state.user = await authApi.me()
  } catch {
    // Keeps what it had: a failed check doesn't log anyone out.
  }
}

async function login(credentials) {
  state.user = await authApi.login(credentials)
}

async function register(details) {
  state.user = await authApi.register(details)
}

async function logout() {
  await authApi.logout()
  state.user = null
}

async function updateProfile(changes) {
  state.user = await accountApi.update(changes)
}

async function shareCollection() {
  state.user = await collectionApi.share()
}

async function stopSharingCollection() {
  state.user = await collectionApi.stopSharing()
}

export function useSession() {
  return {
    state,
    isLoggedIn,
    isAdmin,
    isPremium,
    ensureLoaded,
    refresh,
    login,
    register,
    logout,
    updateProfile,
    shareCollection,
    stopSharingCollection,
  }
}
