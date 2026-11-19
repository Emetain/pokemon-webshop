import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/** Items per load (phase 11). */
export const PAGE_SIZE = 25

/** Loads in a row before "Meer laden" takes over, so the footer with the KVK number stays reachable. */
const AUTO_LOADS = 3

/** Starts the next load this far before the end of the list, so it's usually there before the customer is. */
const LOOK_AHEAD = '800px 0px'

/**
 * A list that loads the next items when the customer nears its end, instead of page buttons (phase 11). The URL's
 * `page` says how many pages are loaded, so a shared link and the back button open the list as deep as it was; a
 * load replaces the URL instead of adding a history entry. Filters stay in the URL as before: a change of [key] starts
 * the list over.
 *
 * @param fetchPage (pageIndex starting at 0, size) => Promise<{ items, totalItems, totalPages }>
 * @param key () => anything that, when it changes, means a new list (game, set, filters). Compared as JSON.
 * @param page () => the page number in the URL, starting at 1
 */
export function useInfiniteList({ fetchPage, key, page, pageSize = PAGE_SIZE, autoLoads = AUTO_LOADS }) {
  const route = useRoute()
  const router = useRouter()

  const items = ref([])
  const totalItems = ref(0)
  const totalPages = ref(0)
  const loadedPages = ref(0)
  const loading = ref(true)
  const loadingMore = ref(false)
  const error = ref(null)
  const moreError = ref(null)
  const autoLeft = ref(autoLoads)
  /** The element at the end of the list. When it comes near the screen, the next page loads. */
  const sentinel = ref(null)
  const loaded = ref(false)

  const hasMore = computed(() => loadedPages.value < totalPages.value)
  const showMoreButton = computed(() => hasMore.value && !loading.value && (autoLeft.value <= 0 || moreError.value))

  let generation = 0
  let observer = null

  /** Loads pages 1 to [pages] at once: the first visit, a shared link or the back button. */
  async function reset(pages) {
    const run = ++generation
    const wanted = Math.max(1, pages)
    loading.value = true
    error.value = null
    moreError.value = null
    autoLeft.value = autoLoads
    try {
      const results = await Promise.all(Array.from({ length: wanted }, (_, index) => fetchPage(index, pageSize)))
      if (run !== generation) return
      items.value = unique(results.flatMap((result) => result.items))
      totalItems.value = results[0].totalItems
      totalPages.value = results[0].totalPages
      loadedPages.value = Math.min(wanted, Math.max(results[0].totalPages, 1))
      loaded.value = true
    } catch (exception) {
      if (run !== generation) return
      items.value = []
      totalItems.value = 0
      totalPages.value = 0
      loadedPages.value = 0
      error.value = exception
    } finally {
      if (run === generation) loading.value = false
    }
    if (run === generation) checkSentinel()
  }

  /** The next page, below what's there. [auto] counts towards the loads before the button. */
  async function loadMore({ auto = false } = {}) {
    if (loading.value || loadingMore.value || !hasMore.value) return
    const run = generation
    loadingMore.value = true
    moreError.value = null
    try {
      const result = await fetchPage(loadedPages.value, pageSize)
      if (run !== generation) return
      items.value = unique([...items.value, ...result.items])
      totalItems.value = result.totalItems
      totalPages.value = result.totalPages
      loadedPages.value += 1
      autoLeft.value = auto ? autoLeft.value - 1 : autoLoads
      router.replace({ query: { ...route.query, page: String(loadedPages.value) } })
    } catch (exception) {
      if (run === generation) moreError.value = exception
    } finally {
      if (run === generation) loadingMore.value = false
    }
    if (run === generation) checkSentinel()
  }

  /** Reloads what's loaded, for example after adding cards to the collection. */
  function refresh() {
    return reset(loadedPages.value || page())
  }

  /** A page that still fits on the screen gets the next one right away, as long as automatic loads are left. */
  async function checkSentinel() {
    await nextTick()
    const element = sentinel.value
    if (!element || autoLeft.value <= 0 || !hasMore.value) return
    const margin = Number.parseInt(LOOK_AHEAD, 10)
    if (element.getBoundingClientRect().top < window.innerHeight + margin) loadMore({ auto: true })
  }

  function observe(element) {
    observer?.disconnect()
    observer = null
    if (!element || typeof IntersectionObserver === 'undefined') return
    observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting) && autoLeft.value > 0) loadMore({ auto: true })
      },
      { rootMargin: LOOK_AHEAD },
    )
    observer.observe(element)
  }

  watch(sentinel, observe)

  watch(
    () => [JSON.stringify(key()), page()],
    ([newKey, newPage], old) => {
      // The URL changing because a load wrote it needs nothing; anything else starts over at the URL's depth.
      if (old && newKey === old[0] && newPage === loadedPages.value) return
      reset(newPage)
    },
    { immediate: true },
  )

  onBeforeUnmount(() => observer?.disconnect())

  return {
    items,
    totalItems,
    totalPages,
    loadedPages,
    loading,
    loaded,
    loadingMore,
    error,
    moreError,
    hasMore,
    showMoreButton,
    sentinel,
    loadMore,
    refresh,
    reset,
  }
}

/** A list can shift between two loads (a new card, a price that changed the order): never show an item twice. */
function unique(list) {
  const seen = new Set()
  return list.filter((item) => {
    const id = item.id ?? item.slug
    if (id == null) return true
    if (seen.has(id)) return false
    seen.add(id)
    return true
  })
}
