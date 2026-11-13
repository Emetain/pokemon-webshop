import { computed, reactive, watch } from 'vue'
import { supportApi } from '../services/api'

/**
 * The conversation with the chatbot, shared by the chat button on every page and the contact page, so it continues
 * where it was and can go along with a ticket. It lives in this tab only (sessionStorage, so a reload keeps it): the
 * shop stores no conversations.
 */
const STORAGE_KEY = 'poketopia.support-chat'

function restore() {
  try {
    const saved = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(saved) ? saved.filter((line) => typeof line?.text === 'string' && ['user', 'assistant'].includes(line.role)) : []
  } catch {
    return []
  }
}

const state = reactive({
  /** Null until the shop said whether the chatbot is on. */
  available: null,
  messages: restore(),
  busy: false,
  error: '',
  /** The last answer said a ticket would help. */
  suggestTicket: false,
})

/** The shop takes the last 30 lines at most. */
const MAX_LINES = 30

watch(
  () => state.messages.length,
  () => {
    try {
      window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state.messages.slice(-MAX_LINES)))
    } catch {
      // Private mode or storage off: the conversation just ends with the page.
    }
  },
)

let statusRequest = null

function loadStatus() {
  if (!statusRequest) {
    statusRequest = supportApi
      .chatStatus()
      .then((result) => (state.available = result.available))
      .catch(() => (state.available = false))
  }
  return statusRequest
}

async function ask(text) {
  const question = text.trim()
  if (!question || state.busy) return
  state.messages.push({ role: 'user', text: question })
  state.busy = true
  state.error = ''
  try {
    const result = await supportApi.chat(state.messages.slice(-MAX_LINES))
    state.messages.push({ role: 'assistant', text: result.answer })
    state.suggestTicket = result.suggestTicket
  } catch (exception) {
    state.error = exception.message
    if (exception.code === 'chat_unavailable') state.available = false
  } finally {
    state.busy = false
  }
}

function reset() {
  state.messages = []
  state.error = ''
  state.suggestTicket = false
}

export function useSupportChat() {
  return {
    state,
    hasConversation: computed(() => state.messages.length > 0),
    loadStatus,
    ask,
    reset,
  }
}
