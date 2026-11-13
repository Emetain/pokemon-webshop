import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * One clock for every countdown on the page, set to the shop's time. The browser's clock can be minutes off, so
 * replies with a `serverTime` set the difference. Every countdown reads the same difference and the same tick, so
 * two timers on one page always show the same second, and change it at the same moment.
 *
 * A reply that took long says less about the time, so of the last few replies the quickest one counts. The tick
 * falls on the whole seconds of the shop's clock: a countdown changes when the shop's second changes.
 */
const SAMPLES = 8

const samples = []
const offset = ref(0)
const browserNow = ref(Date.now())
let users = 0
let timer = null

const now = computed(() => browserNow.value + offset.value)

/**
 * Takes the shop's time from a reply. With `sentAt` and `receivedAt` (the browser's `Date.now()` around the request),
 * the shop answered about halfway. Without them, the sample only counts while there is no measured one.
 */
export function sampleServerTime(serverTime, sentAt, receivedAt) {
  const stamp = Date.parse(serverTime)
  if (Number.isNaN(stamp)) return
  const measured = sentAt != null && receivedAt != null
  const rtt = measured ? Math.max(0, receivedAt - sentAt) : Number.POSITIVE_INFINITY
  const local = measured ? (sentAt + receivedAt) / 2 : Date.now()
  samples.push({ offset: stamp - local, rtt })
  if (samples.length > SAMPLES) samples.shift()
  const best = samples.reduce((quickest, sample) => (sample.rtt <= quickest.rtt ? sample : quickest))
  if (best.offset === offset.value) return
  offset.value = best.offset
  if (timer !== null) {
    browserNow.value = Date.now()
    schedule()
  }
}

function schedule() {
  clearTimeout(timer)
  const intoSecond = (((Date.now() + offset.value) % 1000) + 1000) % 1000
  // A few milliseconds past the whole second, so the countdown is surely on the new second.
  timer = setTimeout(tick, 1000 - intoSecond + 5)
}

function tick() {
  browserNow.value = Date.now()
  schedule()
}

/** The shop's time in milliseconds, updated every second while a component that uses it is on the page. */
export function useServerClock() {
  onMounted(() => {
    users += 1
    if (users === 1) {
      browserNow.value = Date.now()
      schedule()
    }
  })
  onBeforeUnmount(() => {
    users -= 1
    if (users === 0) {
      clearTimeout(timer)
      timer = null
    }
  })
  return { now }
}
