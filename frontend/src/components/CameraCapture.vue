<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Camera, ImageUp, X } from 'lucide-vue-next'
import FormMessage from './FormMessage.vue'
import { t } from '../utils/i18n'

/**
 * A live camera view with a shutter button, for computers: their browsers ignore `capture` on a file input and open
 * the file picker instead of the camera. Phones keep the file input, which opens their own camera app with autofocus.
 * Emits `photo` with a JPEG of the card frame and a margin around it, `pick` when the customer would rather choose a file (offered
 * when there's no camera or no access to it), and `close`.
 */
defineProps({
  /** The line below the camera; the scanner's by default. */
  hint: { type: String, default: '' },
})

const emit = defineEmits(['photo', 'pick', 'close'])

const video = ref(null)
const shutter = ref(null)
const guide = ref(null)
const ready = ref(false)
const failure = ref('')
let stream = null
let closed = false

onMounted(start)
onBeforeUnmount(stop)

async function start() {
  let opened
  try {
    opened = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
    })
  } catch (exception) {
    failure.value = failureKind(exception)
    return
  }
  if (closed) {
    // Closed while the browser was still asking for access.
    opened.getTracks().forEach((track) => track.stop())
    return
  }
  stream = opened
  // Never mirrored, not even for a camera facing the customer as in a video call: the card's text has to read the
  // right way round, and the view shows exactly the photo that is taken.
  video.value.srcObject = stream
  try {
    await video.value.play()
  } catch {
    // Autoplay rules don't apply to a muted video, but a browser may still refuse; the shutter then waits for a frame.
  }
  ready.value = true
  await nextTick()
  shutter.value?.focus()
}

function failureKind(exception) {
  switch (exception?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'denied'
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'missing'
    case 'NotReadableError':
      return 'busy'
    default:
      return 'other'
  }
}

function stop() {
  closed = true
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

/** Room around the frame in the photo, as a share of the frame's height, for a card held a little outside it. */
const CROP_MARGIN = 0.12

/**
 * The part of the camera picture inside the card frame plus a margin, in the video's own pixels. A webcam picture is
 * wide with the card in the middle: cropping gives the scanner more pixels on the card, and a photo that looks like
 * the card it's compared with.
 */
function cropArea(element) {
  const width = element.videoWidth
  const height = element.videoHeight
  const shown = element.getBoundingClientRect()
  const frame = guide.value?.getBoundingClientRect()
  if (!frame?.height) return { x: 0, y: 0, width, height }
  // object-fit: contain, so the picture is scaled to fit and centred in the element.
  const scale = Math.min(shown.width / width, shown.height / height)
  const left = shown.left + (shown.width - width * scale) / 2
  const top = shown.top + (shown.height - height * scale) / 2
  const margin = frame.height * CROP_MARGIN
  const x = Math.max(0, (frame.left - margin - left) / scale)
  const y = Math.max(0, (frame.top - margin - top) / scale)
  const right = Math.min(width, (frame.right + margin - left) / scale)
  const bottom = Math.min(height, (frame.bottom + margin - top) / scale)
  return { x, y, width: right - x, height: bottom - y }
}

function capture() {
  const element = video.value
  if (!element?.videoWidth) return
  const area = cropArea(element)
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(area.width)
  canvas.height = Math.round(area.height)
  canvas.getContext('2d').drawImage(element, area.x, area.y, area.width, area.height, 0, 0, canvas.width, canvas.height)
  canvas.toBlob(
    (blob) => {
      if (!blob) return
      stop()
      emit('photo', blob)
    },
    'image/jpeg',
    0.92,
  )
}
</script>

<template>
  <section class="camera" :aria-label="t('camera.label')" @keydown.esc="emit('close')">
    <div v-if="!failure" class="viewport">
      <video ref="video" playsinline muted autoplay></video>
      <span v-if="ready" ref="guide" class="guide" aria-hidden="true"></span>
      <p v-else class="starting" role="status">{{ t('camera.starting') }}</p>
    </div>
    <FormMessage v-else>{{ t(`camera.${failure}`) }}</FormMessage>

    <div class="actions">
      <button v-if="!failure" ref="shutter" class="btn btn-primary" type="button" :disabled="!ready" @click="capture">
        <Camera :size="18" aria-hidden="true" />
        {{ t('camera.shoot') }}
      </button>
      <button v-else class="btn btn-primary" type="button" @click="emit('pick')">
        <ImageUp :size="18" aria-hidden="true" />
        {{ t('camera.pick') }}
      </button>
      <button class="btn btn-secondary" type="button" @click="emit('close')">
        <X :size="18" aria-hidden="true" />
        {{ t('camera.cancel') }}
      </button>
    </div>
    <p v-if="!failure" class="hint">{{ hint || t('camera.hint') }}</p>
  </section>
</template>

<style scoped>
.camera {
  display: grid;
  gap: var(--space-3);
}

/* Always over a camera picture, in any theme, so these colours are fixed. */
.viewport {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-lg);
  background: #000;
}

.viewport video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

/* The shape of a card (63 × 88 mm), with the rest of the picture dimmed, to show where the card goes. */
.guide {
  position: absolute;
  top: 50%;
  left: 50%;
  height: 82%;
  aspect-ratio: 63 / 88;
  border: 2px dashed rgb(255 255 255 / 0.9);
  border-radius: 4.5% / 3.2%;
  box-shadow: 0 0 0 100vmax rgb(0 0 0 / 0.35);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.starting {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  color: rgb(255 255 255 / 0.85);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
