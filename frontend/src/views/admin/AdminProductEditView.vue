<script setup>
import { computed, reactive, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Copy, ExternalLink } from 'lucide-vue-next'
import CardPicker from '../../components/admin/CardPicker.vue'
import ProductImages from '../../components/admin/ProductImages.vue'
import StockPanel from '../../components/admin/StockPanel.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import {
  ACCESSORY_TYPES,
  CONDITIONS,
  GRADING_COMPANIES,
  LANGUAGES,
  PRODUCT_TYPES,
  accessoryLabel,
  centsToEuros,
  conditionLabel,
  eurosToCents,
  languageLabel,
  typeLabel,
} from '../../data/shop'
import { adminShopApi, catalogApi } from '../../services/api'
import { statusLabel } from '../../utils/admin'
import { variantLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

/** Creates a product without `id`, edits one with it. Photos and stock come once the product exists. */
const props = defineProps({
  id: { type: String, default: '' },
})

const router = useRouter()
const uid = useId()
const field = (name) => `${uid}-${name}`

const product = ref(null)
const sets = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const card = ref(null)

const form = reactive({
  game: 'pokemon',
  type: 'BOOSTER_BOX',
  accessoryType: '',
  name: '',
  slug: '',
  description: '',
  language: 'EN',
  setCode: '',
  cardVariant: '',
  condition: 'NEAR_MINT',
  gradingCompany: 'PSA',
  grade: '',
  price: '',
  salePrice: '',
  status: 'DRAFT',
  preOrder: false,
  releaseDate: '',
  shipsFrom: '',
  lowStockThreshold: '2',
})

const editing = computed(() => Boolean(props.id))
const forCard = computed(() => form.type === 'SINGLE' || form.type === 'GRADED_SLAB')
const importNote = computed(() => {
  const p = product.value
  if (!p?.importSource) return ''
  const source = t(`admin.products.sources.${p.importSource}`)
  return p.editedByAdmin ? t('admin.productForm.editedImport', { source }) : t('admin.productForm.imported', { source })
})

function fill(value) {
  product.value = value
  Object.assign(form, {
    game: value.game ?? '',
    type: value.type,
    accessoryType: value.accessoryType ?? '',
    name: value.name,
    slug: value.slug,
    description: value.description ?? '',
    language: value.language ?? '',
    setCode: value.setCode ?? '',
    cardVariant: value.cardVariant ?? '',
    condition: value.condition ?? 'NEAR_MINT',
    gradingCompany: value.gradingCompany ?? 'PSA',
    grade: value.grade ?? '',
    price: centsToEuros(value.priceCents),
    salePrice: centsToEuros(value.salePriceCents),
    status: value.status,
    preOrder: value.preOrder,
    releaseDate: value.releaseDate ?? '',
    shipsFrom: value.shipsFrom ?? '',
    lowStockThreshold: String(value.lowStockThreshold),
  })
  card.value = value.card
}

async function load() {
  if (!editing.value) return
  loading.value = true
  error.value = ''
  try {
    fill(await adminShopApi.product(props.id))
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

async function loadSets() {
  sets.value = []
  if (!form.game) return
  try {
    sets.value = await catalogApi.sets(form.game)
  } catch {
    // A product without a set still works.
  }
}

function chooseCard(chosen) {
  card.value = {
    id: chosen.id,
    game: chosen.game,
    setCode: chosen.setCode,
    setName: chosen.setName,
    number: chosen.number,
    name: chosen.name,
    imageUrl: chosen.imageUrl,
    variants: chosen.variants,
  }
  form.cardVariant = chosen.variants[0] ?? ''
  if (!form.name.trim()) form.name = `${chosen.name} (${chosen.setName} ${chosen.number})`
}

function payload() {
  const price = eurosToCents(form.price)
  const salePrice = eurosToCents(form.salePrice)
  if (form.price.trim() && price === null) throw new Error(t('admin.productForm.priceHint'))
  if (form.salePrice.trim() && salePrice === null) throw new Error(t('admin.productForm.salePriceHint'))
  return {
    game: form.game || null,
    type: form.type,
    accessoryType: form.type === 'ACCESSORY' ? form.accessoryType || null : null,
    name: form.name,
    slug: form.slug.trim() || null,
    description: form.description,
    language: form.language || null,
    setCode: forCard.value ? null : form.setCode || null,
    cardId: forCard.value ? card.value?.id ?? null : null,
    cardVariant: forCard.value ? form.cardVariant || null : null,
    condition: form.type === 'SINGLE' ? form.condition : null,
    gradingCompany: form.type === 'GRADED_SLAB' ? form.gradingCompany : null,
    grade: form.type === 'GRADED_SLAB' ? form.grade : null,
    priceCents: price,
    salePriceCents: salePrice,
    status: form.status,
    preOrder: form.preOrder,
    releaseDate: form.releaseDate || null,
    shipsFrom: form.shipsFrom || null,
    lowStockThreshold: Number.parseInt(form.lowStockThreshold, 10) || 0,
  }
}

async function save() {
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    const body = payload()
    if (editing.value) {
      fill(await adminShopApi.update(props.id, body))
      message.value = t('admin.productForm.saved')
    } else {
      const created = await adminShopApi.create(body)
      await router.replace(`/admin/producten/${created.id}`)
      message.value = t('admin.productForm.created')
    }
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

async function duplicate() {
  error.value = ''
  try {
    const copy = await adminShopApi.duplicate(props.id)
    await router.push(`/admin/producten/${copy.id}`)
    message.value = t('admin.productForm.duplicated')
  } catch (exception) {
    error.value = exception.message
  }
}

async function archive() {
  if (!window.confirm(t('admin.productForm.archiveConfirm'))) return
  error.value = ''
  try {
    fill(await adminShopApi.archive(props.id))
    message.value = t('admin.productForm.archived')
  } catch (exception) {
    error.value = exception.message
  }
}

function onStock(stock) {
  product.value = { ...product.value, stock }
}

watch(() => props.id, load, { immediate: true })
watch(() => form.game, loadSets, { immediate: true })
</script>

<template>
  <div class="editor">
    <router-link class="back" to="/admin/producten">
      <ArrowLeft :size="16" aria-hidden="true" />
      {{ t('admin.productForm.back') }}
    </router-link>

    <div class="head">
      <h1>{{ editing ? t('admin.productForm.editTitle') : t('admin.productForm.newTitle') }}</h1>
      <div v-if="product" class="head-actions">
        <router-link class="btn btn-secondary btn-small" :to="{ name: 'product', params: { slug: product.slug } }">
          <ExternalLink :size="16" aria-hidden="true" />
          {{ t('admin.products.view') }}
        </router-link>
        <button class="btn btn-secondary btn-small" type="button" @click="duplicate">
          <Copy :size="16" aria-hidden="true" />
          {{ t('admin.productForm.duplicate') }}
        </button>
        <button v-if="product.status !== 'ARCHIVED'" class="link-button danger" type="button" @click="archive">
          {{ t('admin.productForm.archive') }}
        </button>
      </div>
    </div>
    <p v-if="importNote" class="admin-intro">{{ importNote }}</p>

    <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

    <p v-if="loading" class="admin-intro">{{ t('admin.images.loading') }}</p>

    <form v-else class="form" @submit.prevent="save">
      <section class="admin-panel">
        <h2>{{ t('admin.productForm.main') }}</h2>
        <div class="admin-fields">
          <div class="admin-field wide">
            <label class="admin-label" :for="field('name')">{{ t('admin.productForm.name') }}</label>
            <input :id="field('name')" v-model="form.name" class="admin-input" required maxlength="255" />
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('type')">{{ t('admin.productForm.type') }}</label>
            <select :id="field('type')" v-model="form.type" class="admin-input">
              <option v-for="item in PRODUCT_TYPES" :key="item.type" :value="item.type">{{ typeLabel(item.type) }}</option>
            </select>
          </div>
          <div v-if="form.type === 'ACCESSORY'" class="admin-field">
            <label class="admin-label" :for="field('accessory')">{{ t('admin.productForm.accessoryType') }}</label>
            <select :id="field('accessory')" v-model="form.accessoryType" class="admin-input" required>
              <option value="" disabled></option>
              <option v-for="item in ACCESSORY_TYPES" :key="item.type" :value="item.type">{{ accessoryLabel(item.type) }}</option>
            </select>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('game')">{{ t('admin.productForm.game') }}</label>
            <select :id="field('game')" v-model="form.game" class="admin-input">
              <option v-if="form.type === 'ACCESSORY'" value="">{{ t('admin.products.noGame') }}</option>
              <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
            </select>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('language')">{{ t('admin.productForm.language') }}</label>
            <select :id="field('language')" v-model="form.language" class="admin-input">
              <option value="">{{ t('admin.productForm.noLanguage') }}</option>
              <option v-for="code in LANGUAGES" :key="code" :value="code">{{ languageLabel(code) }}</option>
            </select>
          </div>
          <div v-if="!forCard" class="admin-field">
            <label class="admin-label" :for="field('set')">{{ t('admin.productForm.set') }}</label>
            <select :id="field('set')" v-model="form.setCode" class="admin-input" :disabled="!form.game">
              <option value="">{{ form.game ? t('admin.productForm.noSet') : t('admin.productForm.setHint') }}</option>
              <option v-for="set in sets" :key="set.code" :value="set.code">{{ set.name }} ({{ set.code }})</option>
            </select>
          </div>
          <div class="admin-field wide">
            <label class="admin-label" :for="field('slug')">{{ t('admin.productForm.slug') }}</label>
            <input :id="field('slug')" v-model="form.slug" class="admin-input" maxlength="180" autocomplete="off" />
            <p class="admin-hint">{{ t('admin.productForm.slugHint') }}</p>
          </div>
          <div class="admin-field wide">
            <label class="admin-label" :for="field('description')">{{ t('admin.productForm.description') }}</label>
            <textarea :id="field('description')" v-model="form.description" class="admin-input" maxlength="10000"></textarea>
            <p class="admin-hint">{{ t('admin.productForm.descriptionHint') }}</p>
          </div>
        </div>
      </section>

      <section v-if="forCard" class="admin-panel">
        <h2>{{ t('admin.productForm.card') }}</h2>
        <div v-if="card" class="chosen">
          <CardImage class="chosen-image" :src="card.imageUrl" :alt="card.name" />
          <div>
            <p class="chosen-name">{{ card.name }}</p>
            <p class="admin-hint">{{ card.setName }}, #{{ card.number }}</p>
            <button class="link-button" type="button" @click="card = null">{{ t('admin.productForm.cardChange') }}</button>
          </div>
        </div>
        <CardPicker v-else :game="form.game" @choose="chooseCard" />

        <div class="admin-fields">
          <div v-if="card?.variants?.length" class="admin-field">
            <label class="admin-label" :for="field('variant')">{{ t('admin.productForm.cardVariant') }}</label>
            <select :id="field('variant')" v-model="form.cardVariant" class="admin-input">
              <option v-for="variant in card.variants" :key="variant" :value="variant">{{ variantLabel(variant) }}</option>
            </select>
          </div>
          <div v-if="form.type === 'SINGLE'" class="admin-field">
            <label class="admin-label" :for="field('condition')">{{ t('admin.productForm.condition') }}</label>
            <select :id="field('condition')" v-model="form.condition" class="admin-input">
              <option v-for="condition in CONDITIONS" :key="condition" :value="condition">{{ conditionLabel(condition) }}</option>
            </select>
          </div>
          <template v-if="form.type === 'GRADED_SLAB'">
            <div class="admin-field">
              <label class="admin-label" :for="field('company')">{{ t('admin.productForm.gradingCompany') }}</label>
              <select :id="field('company')" v-model="form.gradingCompany" class="admin-input">
                <option v-for="company in GRADING_COMPANIES" :key="company" :value="company">{{ company }}</option>
              </select>
            </div>
            <div class="admin-field">
              <label class="admin-label" :for="field('grade')">{{ t('admin.productForm.grade') }}</label>
              <input :id="field('grade')" v-model="form.grade" class="admin-input" maxlength="20" required />
            </div>
          </template>
        </div>
      </section>

      <section class="admin-panel">
        <h2>{{ t('admin.productForm.prices') }}</h2>
        <div class="admin-fields">
          <div class="admin-field">
            <label class="admin-label" :for="field('price')">{{ t('admin.productForm.price') }}</label>
            <input :id="field('price')" v-model="form.price" class="admin-input" inputmode="decimal" autocomplete="off" />
            <p class="admin-hint">{{ t('admin.productForm.priceHint') }}</p>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('sale')">{{ t('admin.productForm.salePrice') }}</label>
            <input :id="field('sale')" v-model="form.salePrice" class="admin-input" inputmode="decimal" autocomplete="off" />
            <p class="admin-hint">{{ t('admin.productForm.salePriceHint') }}</p>
          </div>
        </div>
      </section>

      <section class="admin-panel">
        <h2>{{ t('admin.productForm.shop') }}</h2>
        <div class="admin-fields">
          <div class="admin-field">
            <label class="admin-label" :for="field('status')">{{ t('admin.productForm.status') }}</label>
            <select :id="field('status')" v-model="form.status" class="admin-input">
              <option v-for="status in ['DRAFT', 'ACTIVE', 'ARCHIVED']" :key="status" :value="status">
                {{ statusLabel(status) }}
              </option>
            </select>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('low')">{{ t('admin.productForm.lowStock') }}</label>
            <input :id="field('low')" v-model="form.lowStockThreshold" class="admin-input" inputmode="numeric" />
            <p class="admin-hint">{{ t('admin.productForm.lowStockHint') }}</p>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('release')">{{ t('admin.productForm.releaseDate') }}</label>
            <input :id="field('release')" v-model="form.releaseDate" class="admin-input" type="date" />
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="field('ships')">{{ t('admin.productForm.shipsFrom') }}</label>
            <input :id="field('ships')" v-model="form.shipsFrom" class="admin-input" type="date" />
          </div>
          <label class="admin-check">
            <input v-model="form.preOrder" type="checkbox" />
            {{ t('admin.productForm.preOrder') }}
          </label>
        </div>
      </section>

      <div class="admin-actions">
        <button class="btn btn-primary" type="submit" :disabled="saving">
          {{ editing ? t('admin.productForm.save') : t('admin.productForm.create') }}
        </button>
      </div>
    </form>

    <template v-if="product && !loading">
      <ProductImages :product="product" @change="fill" />
      <StockPanel :product-id="product.id" :stock="product.stock" @change="onStock" />
    </template>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.back:hover {
  text-decoration: underline;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
}

.message {
  margin-top: var(--space-3);
}

/* The form, the photos and the stock share one width. */
.editor {
  max-width: 60rem;
}

.chosen {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.chosen-name {
  font-weight: 650;
}
</style>
