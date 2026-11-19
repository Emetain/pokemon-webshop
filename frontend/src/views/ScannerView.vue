<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Camera, Crown, ImageUp, ScanLine } from 'lucide-vue-next'
import CardGrid from '../components/catalog/CardGrid.vue'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import FormMessage from '../components/FormMessage.vue'
import { GAMES } from '../data/games'
import { scannerApi } from '../services/api'
import { shrinkPhoto } from '../utils/photo'
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

async function onPhoto(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  error.value = ''
  needsPremium.value = false
  result.value = null
  scanning.value = true
  try {
    const photo = await shrinkPhoto(file)
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = URL.createObjectURL(photo)
    result.value = await scannerApi.scan(photo, game.value || undefined)
  } catch (exception) {
    if (exception.code === 'premium_required') needsPremium.value = true
    error.value = exception.message === 'photo' ? t('scanner.unreadable') : exception.message
  } finally {
    scanning.value = false
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

      <div class="actions">
        <button class="btn btn-primary" type="button" :disabled="scanning || needsPremium" @click="cameraInput.click()">
          <Camera :size="18" aria-hidden="true" />
          {{ t('scanner.takePhoto') }}
        </button>
        <button class="btn btn-secondary" type="button" :disabled="scanning || needsPremium" @click="fileInput.click()">
          <ImageUp :size="18" aria-hidden="true" />
          {{ t('scanner.choosePhoto') }}
        </button>
        <input ref="cameraInput" class="hidden-input" type="file" accept="image/*" capture="environment" @change="onPhoto" />
        <input ref="fileInput" class="hidden-input" type="file" accept="image/jpeg,image/png,image/webp,image/*" @change="onPhoto" />
      </div>

      <p v-if="status && !status.premium && scansLeft !== null" class="left">
        {{ t('scanner.left', { count: scansLeft, total: status.freeScans }) }}
        <router-link to="/premium">{{ t('scanner.unlimited') }}</router-link>
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
      <router-link class="btn btn-primary" to="/premium">{{ t('scanner.toPremium') }}</router-link>
    </section>

    <div v-if="scanning || result" class="outcome">
      <img v-if="preview" class="preview" :src="preview" :alt="t('scanner.yourPhoto')" />
      <div class="found">
        <p v-if="scanning" class="reading" role="status"><ScanLine :size="18" aria-hidden="true" /> {{ t('scanner.reading') }}</p>

        <template v-else-if="result">
          <template v-if="result.match">
            <h2>{{ t('scanner.found') }}</h2>
            <CardGrid :cards="[result.match]" show-set class="match" @change="onCollectionChange" />
          </template>
          <template v-if="others.length">
            <h2>{{ result.match ? t('scanner.orOneOf') : t('scanner.choose') }}</h2>
            <CardGrid :cards="others" show-set @change="onCollectionChange" />
          </template>
          <template v-if="!result.candidates.length">
            <h2>{{ t('scanner.notFound') }}</h2>
            <p v-if="result.reading.name || result.reading.number" class="read">
              {{ t('scanner.read', { text: [result.reading.name, result.reading.setCode, result.reading.number].filter(Boolean).join(' · ') }) }}
            </p>
            <p>{{ t('scanner.notFoundText') }}</p>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.intro {
  max-width: 40rem;
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
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
  gap: var(--space-4);
}

.preview {
  width: min(12rem, 60%);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-panel);
}

.found {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.found h2 {
  font-size: var(--text-lg);
}

.reading {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-ink-soft);
}

.read {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.match {
  max-width: 16rem;
}

@media (min-width: 768px) {
  .outcome {
    grid-template-columns: 12rem minmax(0, 1fr);
    align-items: start;
  }

  .preview {
    width: 100%;
  }
}
</style>
