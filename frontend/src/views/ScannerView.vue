<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Camera, Crown, ImageUp, ScanLine } from 'lucide-vue-next'
import CameraCapture from '../components/CameraCapture.vue'
import CardGrid from '../components/catalog/CardGrid.vue'
import CardTile from '../components/catalog/CardTile.vue'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import FormMessage from '../components/FormMessage.vue'
import { GAMES } from '../data/games'
import { catalogApi, scannerApi } from '../services/api'
import { openPremium } from '../stores/premiumDialog'
import { canUseLiveCamera, shrinkPhoto } from '../utils/photo'
import { variantLabel } from '../utils/catalog'
import { t } from '../utils/i18n'
import { usePageMeta } from '../utils/pageMeta'

/**
 * The card scanner (phase 11): take or pick a photo of a card, get the card with its price and the button to add it
 * to the collection. 100 scans with a result are free per account; Premium scans without limit.
 */
const status = ref(null)
const game = ref('')
const preview = ref('')
const scanning = ref(false)
const result = ref(null)
const error = ref('')
const needsPremium = ref(false)
const cameraInput = ref(null)
const fileInput = ref(null)
const takeButton = ref(null)
const cameraOpen = ref(false)

const largeImage = ref('')

/** The card found, with its large picture once that has loaded: the small one is blurry at the size of the photo. */
const matchCard = computed(() => {
  const match = result.value?.match
  return match && largeImage.value ? { ...match, imageUrl: largeImage.value } : match
})

/** The card comes in more than one version, such as normal and reverse holo. */
const hasVersions = computed(() => (result.value?.match?.variants?.length ?? 0) > 1)
/** The version the scanner saw, when the card has it and there's a choice. Only a hint: shine is hard to see. */
const seenFinish = computed(() => {
  const finish = result.value?.reading?.finish
  return hasVersions.value && result.value.match.variants.includes(finish) ? finish : null
})

const scansLeft = computed(() => result.value?.scansLeft ?? status.value?.scansLeft ?? null)
const others = computed(() => {
  if (!result.value) return []
  const matchId = result.value.match?.id
  return result.value.candidates.filter((card) => card.id !== matchId)
})

onMounted(async () => {
  try {
    status.value = await scannerApi.status()
  } catch (exception) {
    error.value = exception.message
  }
})

onBeforeUnmount(() => {
  if (preview.value) URL.revokeObjectURL(preview.value)
})

function takePhoto() {
  if (canUseLiveCamera()) cameraOpen.value = true
  else cameraInput.value.click()
}

async function closeCamera() {
  cameraOpen.value = false
  await nextTick()
  takeButton.value?.focus()
}

function pickInstead() {
  cameraOpen.value = false
  fileInput.value.click()
}

function onCameraPhoto(photo) {
  cameraOpen.value = false
  scan(photo)
}

function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) scan(file)
}

async function scan(file) {
  error.value = ''
  needsPremium.value = false
  result.value = null
  largeImage.value = ''
  scanning.value = true
  try {
    const photo = await shrinkPhoto(file)
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = URL.createObjectURL(photo)
    result.value = await scannerApi.scan(photo, game.value || undefined)
    if (result.value.match) loadLargeImage(result.value.match)
  } catch (exception) {
    if (exception.code === 'premium_required') {
      needsPremium.value = true
      openPremium('scansUsed')
    }
    error.value = exception.message === 'photo' ? t('scanner.unreadable') : exception.message
  } finally {
    scanning.value = false
  }
}

/** Swaps in the large picture only once it has loaded, so the card never shows an empty frame in between. */
async function loadLargeImage(card) {
  try {
    const url = (await catalogApi.card(card.id)).imageLargeUrl
    if (!url || url === card.imageUrl) return
    const image = new Image()
    image.src = url
    await image.decode()
    if (result.value?.match?.id === card.id) largeImage.value = url
  } catch {
    // Keeps the small picture.
  }
}

function onCollectionChange(card, change) {
  card.owned = change.entries
}

usePageMeta(() => ({ title: t('scanner.title'), description: t('scanner.intro') }))
</script>

<template>
  <CatalogBand>
    <h1>{{ t('scanner.title') }}</h1>
    <p class="intro">{{ t('scanner.intro') }}</p>
  </CatalogBand>

  <div class="container page">
    <section class="panel">
      <div class="games" role="group" :aria-label="t('scanner.game')">
        <button type="button" class="chip" :aria-pressed="game === ''" @click="game = ''">{{ t('scanner.anyGame') }}</button>
        <button
          v-for="item in GAMES"
          :key="item.slug"
          type="button"
          class="chip"
          :aria-pressed="game === item.slug"
          @click="game = item.slug"
        >
          {{ t(item.labelKey) }}
        </button>
      </div>

      <CameraCapture v-if="cameraOpen" @photo="onCameraPhoto" @pick="pickInstead" @close="closeCamera" />
      <div v-else class="actions">
        <button ref="takeButton" class="btn btn-primary" type="button" :disabled="scanning || needsPremium" @click="takePhoto">
          <Camera :size="18" aria-hidden="true" />
          {{ t('scanner.takePhoto') }}
        </button>
        <button class="btn btn-secondary" type="button" :disabled="scanning || needsPremium" @click="fileInput.click()">
          <ImageUp :size="18" aria-hidden="true" />
          {{ t('scanner.choosePhoto') }}
        </button>
      </div>
      <input ref="cameraInput" class="hidden-input" type="file" accept="image/*" capture="environment" @change="onFile" />
      <input ref="fileInput" class="hidden-input" type="file" accept="image/jpeg,image/png,image/webp,image/*" @change="onFile" />

      <p v-if="status && !status.premium && scansLeft !== null" class="left">
        {{ t('scanner.left', { count: scansLeft, total: status.freeScans }) }}
        <button class="link-button" type="button" aria-haspopup="dialog" @click="openPremium('scanner')">{{ t('scanner.unlimited') }}</button>
      </p>
      <p v-else-if="status?.premium" class="left premium"><Crown :size="16" aria-hidden="true" /> {{ t('scanner.premium') }}</p>
      <p v-if="status && !status.available" class="left">{{ t('scanner.unavailable') }}</p>

      <details class="tips">
        <summary>{{ t('scanner.tipsTitle') }}</summary>
        <ul>
          <li>{{ t('scanner.tips.light') }}</li>
          <li>{{ t('scanner.tips.flat') }}</li>
          <li>{{ t('scanner.tips.number') }}</li>
          <li>{{ t('scanner.tips.sleeve') }}</li>
        </ul>
      </details>
      <p class="privacy">{{ t('scanner.privacy') }}</p>
    </section>

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
    <section v-if="needsPremium" class="panel premium-prompt">
      <h2><Crown :size="20" aria-hidden="true" /> {{ t('scanner.premiumTitle') }}</h2>
      <p>{{ t('scanner.premiumText') }}</p>
      <button class="btn btn-primary" type="button" aria-haspopup="dialog" @click="openPremium('scansUsed')">{{ t('scanner.toPremium') }}</button>
    </section>

    <div v-if="scanning || result" class="outcome">
      <!-- The photo and the card found side by side at the same size, so the customer can see it's the right one. -->
      <div class="compare">
        <figure v-if="preview" class="photo">
          <img class="preview" :src="preview" :alt="t('scanner.yourPhoto')" />
          <figcaption>{{ t('scanner.yourPhoto') }}</figcaption>
        </figure>
        <div class="found">
          <p v-if="scanning" class="reading" role="status"><ScanLine :size="18" aria-hidden="true" /> {{ t('scanner.reading') }}</p>

          <template v-else-if="result?.match">
            <h2>{{ t('scanner.found') }}</h2>
            <!-- A row per version (normal, reverse holo, holo), so the customer adds the one they have. -->
            <CardTile :card="matchCard" show-set :master="hasVersions" @change="onCollectionChange(result.match, $event)" />
            <p v-if="seenFinish" class="finish">{{ t('scanner.finishSeen', { finish: variantLabel(seenFinish) }) }}</p>
            <p v-else-if="hasVersions" class="finish">{{ t('scanner.finishChoose') }}</p>
          </template>
          <template v-else-if="others.length">
            <h2>{{ t('scanner.choose') }}</h2>
            <p class="read">{{ t('scanner.chooseBelow') }}</p>
          </template>
          <template v-else-if="result">
            <h2>{{ t('scanner.notFound') }}</h2>
            <p v-if="result.reading.name || result.reading.number" class="read">
              {{ t('scanner.read', { text: [result.reading.name, result.reading.setCode, result.reading.number].filter(Boolean).join(' · ') }) }}
            </p>
            <p>{{ t('scanner.notFoundText') }}</p>
          </template>
        </div>
      </div>

      <section v-if="!scanning && others.length" class="others">
        <h2>{{ result.match ? t('scanner.orOneOf') : t('scanner.candidates') }}</h2>
        <CardGrid :cards="others" show-set @change="onCollectionChange" />
      </section>
    </div>
  </div>
</template>

<style scoped>
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

.panel {
  display: grid;
  gap: var(--space-3);
  max-width: 40rem;
}

.games {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip {
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.chip[aria-pressed='true'] {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.hidden-input {
  display: none;
}

.left,
.privacy {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.left.premium {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-brand);
  font-weight: 600;
}

.tips {
  font-size: var(--text-sm);
}

.tips summary {
  min-height: var(--touch-target);
  color: var(--color-link);
  cursor: pointer;
  font-weight: 600;
  line-height: var(--touch-target);
}

.tips ul {
  margin: 0;
  padding-left: var(--space-4);
  color: var(--color-ink-soft);
}

.premium-prompt {
  padding: var(--space-4);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-lg);
}

.premium-prompt h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.premium-prompt .btn {
  justify-self: start;
}

.outcome {
  display: grid;
  gap: var(--space-6);
}

/* Two equal columns, also on a phone: comparing works best side by side. */
.compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  align-items: start;
  max-width: 36rem;
}

.photo {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

/* Below the h2 of the column next to it, so the photo and the card image start at the same height. */
.photo figcaption {
  order: -1;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  line-height: var(--leading-tight);
}

.preview {
  display: block;
  width: 100%;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-panel);
}

.found {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
}

.found h2,
.others h2 {
  font-size: var(--text-lg);
  line-height: var(--leading-tight);
}

.others {
  display: grid;
  gap: var(--space-3);
}

.reading {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-ink-soft);
}

.read,
.finish {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

</style>
