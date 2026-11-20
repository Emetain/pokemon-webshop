<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { BadgeCheck, Camera, Crown, ImageUp } from 'lucide-vue-next'
import CameraCapture from '../components/CameraCapture.vue'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import FormMessage from '../components/FormMessage.vue'
import EdgeGuides from '../components/pregrade/EdgeGuides.vue'
import { pregradeApi } from '../services/api'
import { openPremium } from '../stores/premiumDialog'
import { useSession } from '../stores/session'
import { DEFAULT_OUTER, PERFECT_BACK, PERFECT_FRONT, centering, guessInner, maxGradeByCentering } from '../utils/centering'
import { t } from '../utils/i18n'
import { canUseLiveCamera, shrinkPhoto } from '../utils/photo'
import { usePageMeta } from '../utils/pageMeta'

/**
 * The pre-grade check (phase 11, Premium): the front and the back of a card, the centering measured from lines the
 * customer can drag onto the exact edges, and the wear a model can see. An indication, never a grade.
 */
const PHOTO_SIDE = 1600
const SIDES = ['front', 'back']

const session = useSession()
const photos = reactive({ front: null, back: null })
const previews = reactive({ front: '', back: '' })
const result = ref(null)
const boxes = reactive({ front: null, back: null })
const busy = ref(false)
const error = ref('')
const needsPremium = ref(false)

const premium = computed(() => session.isPremium.value)
const ready = computed(() => photos.front && photos.back && !busy.value)

// The photo of each side: taken now (the phone's camera app, or the live camera view on a computer) or chosen from
// the photos already on the device.
const cameraSide = ref(null)
const cameraInputs = {}
const fileInputs = {}
const takeButtons = {}

function takePhoto(side) {
  if (canUseLiveCamera()) cameraSide.value = side
  else cameraInputs[side].click()
}

async function closeCamera() {
  const side = cameraSide.value
  cameraSide.value = null
  await nextTick()
  takeButtons[side]?.focus()
}

function pickInstead() {
  const side = cameraSide.value
  cameraSide.value = null
  fileInputs[side].click()
}

function onCameraPhoto(photo) {
  const side = cameraSide.value
  cameraSide.value = null
  choose(side, photo)
}

function onFile(side, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) choose(side, file)
}

async function choose(side, file) {
  error.value = ''
  try {
    const photo = await shrinkPhoto(file, PHOTO_SIDE)
    if (previews[side]) URL.revokeObjectURL(previews[side])
    photos[side] = photo
    previews[side] = URL.createObjectURL(photo)
    result.value = null
  } catch {
    error.value = t('scanner.unreadable')
  }
}

async function check() {
  busy.value = true
  error.value = ''
  needsPremium.value = false
  try {
    result.value = await pregradeApi.check(photos.front, photos.back)
    for (const side of SIDES) {
      const found = result.value.sides.find((item) => item.side === side)
      const outer = found?.outer ?? DEFAULT_OUTER
      boxes[side] = { outer, inner: found?.inner ?? guessInner(outer) }
    }
  } catch (exception) {
    if (exception.code === 'premium_required') {
      needsPremium.value = true
      openPremium('pregrade')
    }
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const sideResult = (side) => result.value?.sides.find((item) => item.side === side)
const measured = computed(() =>
  Object.fromEntries(SIDES.map((side) => [side, boxes[side] ? centering(boxes[side].outer, boxes[side].inner) : null])),
)
const maxGrade = computed(() => maxGradeByCentering(measured.value.front?.worst, measured.value.back?.worst))
const perfect = computed(
  () => (measured.value.front?.worst ?? 100) <= PERFECT_FRONT && (measured.value.back?.worst ?? 100) <= PERFECT_BACK,
)

const wear = (side) => {
  const found = sideResult(side)
  if (!found) return []
  return [...found.corners, ...found.edges, ...(found.surface && found.surface.condition !== 'clean' ? [found.surface] : [])]
}

onBeforeUnmount(() => SIDES.forEach((side) => previews[side] && URL.revokeObjectURL(previews[side])))

usePageMeta(() => ({ title: t('pregrade.title'), description: t('pregrade.intro') }))
</script>

<template>
  <CatalogBand>
    <p class="eyebrow"><Crown :size="16" aria-hidden="true" /> {{ t('premium.eyebrow') }}</p>
    <h1>{{ t('pregrade.title') }}</h1>
    <p class="intro">{{ t('pregrade.intro') }}</p>
  </CatalogBand>

  <div class="container page">
    <p class="disclaimer"><BadgeCheck :size="18" aria-hidden="true" /> {{ t('pregrade.disclaimer') }}</p>

    <section v-if="!premium" class="panel locked">
      <h2><Crown :size="20" aria-hidden="true" /> {{ t('pregrade.lockedTitle') }}</h2>
      <p>{{ t('pregrade.lockedText') }}</p>
      <button class="btn btn-primary" type="button" aria-haspopup="dialog" @click="openPremium('pregrade')">{{ t('scanner.toPremium') }}</button>
    </section>

    <template v-else>
      <section class="panel">
        <h2>{{ t('pregrade.photosTitle') }}</h2>
        <ul class="tips">
          <li>{{ t('pregrade.tips.flat') }}</li>
          <li>{{ t('pregrade.tips.straight') }}</li>
          <li>{{ t('pregrade.tips.light') }}</li>
          <li>{{ t('pregrade.tips.sleeve') }}</li>
        </ul>
        <div v-if="cameraSide" class="camera">
          <h3 class="camera-side">{{ t('pregrade.cameraFor', { side: t(`pregrade.${cameraSide}`).toLowerCase() }) }}</h3>
          <CameraCapture :hint="t('pregrade.cameraHint')" @photo="onCameraPhoto" @pick="pickInstead" @close="closeCamera" />
        </div>
        <div v-else class="uploads">
          <div v-for="side in SIDES" :key="side" class="upload" role="group" :aria-labelledby="`upload-${side}`">
            <span :id="`upload-${side}`" class="upload-label">{{ t(`pregrade.${side}`) }}</span>
            <img v-if="previews[side]" :src="previews[side]" :alt="t(`pregrade.${side}`)" />
            <span v-else class="placeholder"><ImageUp :size="28" aria-hidden="true" /></span>
            <button
              :ref="(element) => (takeButtons[side] = element)"
              class="btn btn-primary btn-small"
              type="button"
              @click="takePhoto(side)"
            >
              <Camera :size="16" aria-hidden="true" />
              {{ t('scanner.takePhoto') }}
            </button>
            <button class="btn btn-secondary btn-small" type="button" @click="fileInputs[side].click()">
              <ImageUp :size="16" aria-hidden="true" />
              {{ t('scanner.choosePhoto') }}
            </button>
          </div>
        </div>
        <template v-for="side in SIDES" :key="side">
          <input
            :ref="(element) => (cameraInputs[side] = element)"
            class="hidden-input"
            type="file"
            accept="image/*"
            capture="environment"
            @change="onFile(side, $event)"
          />
          <input :ref="(element) => (fileInputs[side] = element)" class="hidden-input" type="file" accept="image/*" @change="onFile(side, $event)" />
        </template>
        <button class="btn btn-primary check" type="button" :disabled="!ready" @click="check">
          {{ busy ? t('pregrade.checking') : t('pregrade.check') }}
        </button>
        <p class="small">{{ t('pregrade.privacy') }}</p>
      </section>

      <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
      <p v-if="needsPremium">
        <button class="link-button" type="button" aria-haspopup="dialog" @click="openPremium('pregrade')">{{ t('scanner.toPremium') }}</button>
      </p>

      <section v-if="result" class="results" aria-live="polite">
        <h2>{{ t('pregrade.resultTitle') }}</h2>
        <p class="hint">{{ t('pregrade.dragHint') }}</p>

        <div class="sides">
          <div v-for="side in SIDES" :key="side" class="side">
            <EdgeGuides
              v-if="boxes[side]"
              v-model:outer="boxes[side].outer"
              v-model:inner="boxes[side].inner"
              :src="previews[side]"
              :label="t(`pregrade.${side}`)"
            />
            <FormMessage v-if="sideResult(side) && !sideResult(side).usable" type="error">
              {{ sideResult(side).problem || t('pregrade.unusable') }}
            </FormMessage>
            <dl v-if="measured[side]" class="numbers">
              <div>
                <dt>{{ t('pregrade.leftRight') }}</dt>
                <dd>{{ measured[side].leftRight.join('/') }}</dd>
              </div>
              <div>
                <dt>{{ t('pregrade.topBottom') }}</dt>
                <dd>{{ measured[side].topBottom.join('/') }}</dd>
              </div>
            </dl>
            <p v-if="side === 'front' && !sideResult(side)?.inner" class="small">{{ t('pregrade.noFrame') }}</p>
            <h3>{{ t('pregrade.wearTitle') }}</h3>
            <ul v-if="wear(side).length" class="wear">
              <li v-for="item in wear(side)" :key="item.position" :class="item.condition">
                <strong>{{ t(`pregrade.positions.${item.position}`) }}</strong>
                <span>{{ t(`pregrade.conditions.${item.condition}`) }}{{ item.note ? `: ${item.note}` : '' }}</span>
              </li>
            </ul>
            <p v-else class="small">{{ t('pregrade.noWear') }}</p>
          </div>
        </div>

        <div class="verdict">
          <p class="grade">{{ t('pregrade.maxGrade', { grade: maxGrade }) }}</p>
          <p class="small">{{ perfect ? t('pregrade.perfect') : t('pregrade.scaleNote') }}</p>
          <p class="small">{{ t('pregrade.disclaimerLong') }}</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  opacity: 0.85;
  font-size: var(--text-sm);
  font-weight: 700;
  text-transform: uppercase;
}

/* The band sets the text colour for its accent. */
.intro {
  max-width: 40rem;
  margin-top: var(--space-2);
}

.page {
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-6);
}

.disclaimer {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  max-width: 48rem;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

.panel {
  display: grid;
  gap: var(--space-3);
  max-width: 48rem;
}

.panel h2,
.results h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.locked {
  padding: var(--space-5);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-lg);
}

.locked .btn,
.check {
  justify-self: start;
}

.tips {
  margin: 0;
  padding-left: var(--space-4);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.uploads {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  max-width: 30rem;
}

.upload {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1.5px dashed var(--color-line-strong);
  border-radius: var(--radius-md);
  text-align: center;
}

.upload .btn {
  width: 100%;
  padding-inline: var(--space-2);
}

.camera {
  display: grid;
  gap: var(--space-2);
  max-width: 40rem;
}

.camera-side {
  font-size: var(--text-md);
}

.upload img {
  width: 100%;
  border-radius: var(--radius-sm);
}

.placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 63 / 88;
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  color: var(--color-brand);
}

.upload-label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.hidden-input {
  display: none;
}

.small,
.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.results {
  display: grid;
  gap: var(--space-3);
}

.sides {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr));
  gap: var(--space-5);
}

.side {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.side h3 {
  margin-top: var(--space-2);
  font-size: var(--text-md);
}

.numbers {
  display: flex;
  gap: var(--space-5);
  margin: 0;
}

.numbers dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.numbers dd {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.wear {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.wear li {
  display: grid;
  padding: var(--space-2);
  border-left: 3px solid var(--color-line-strong);
  background: var(--color-muted-soft);
}

.wear li.clear {
  border-left-color: var(--color-danger);
}

.wear li.light {
  border-left-color: var(--color-warning);
}

.verdict {
  display: grid;
  gap: var(--space-1);
  max-width: 48rem;
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.grade {
  font-size: var(--text-lg);
  font-weight: 700;
}
</style>
