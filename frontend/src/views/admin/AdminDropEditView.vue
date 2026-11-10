<script setup>
import { computed, reactive, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, ExternalLink, Plus, Upload } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import ProductImage from '../../components/shop/ProductImage.vue'
import { centsToEuros, eurosToCents } from '../../data/shop'
import { adminDropApi, adminShopApi } from '../../services/api'
import { fromLocalInput, statusLabel, toLocalInput } from '../../utils/admin'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Create or edit a drop: its details, banner and products. Prices, limits and times lock once it is live. */
const props = defineProps({
  id: { type: String, default: null },
})

const MAX_FILE_BYTES = 5 * 1024 * 1024
const SEARCH_DELAY_MS = 250

const router = useRouter()
const uid = useId()
const field = (name) => `${uid}-${name}`

const drop = ref(null)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blank = () => ({
  name: '',
  slug: '',
  description: '',
  startsAt: '',
  endsAt: '',
  maxPerCustomer: '',
  admitPerMinute: '60',
  leftover: 'RETURN_TO_STOCK',
  requiresAccount: true,
  showInHero: true,
  heroColour: 'BLACK',
})
const form = reactive(blank())

/** The backgrounds a drop can have in the hero. main.css draws each with a slight gradient. */
const HERO_COLOURS = ['BLACK', 'RED', 'YELLOW', 'GREEN']

/** Per product in the drop: what the admin is typing, until they save it. */
const rows = reactive({})

const status = computed(() => drop.value?.status ?? 'DRAFT')
const beforeStart = computed(() => status.value === 'DRAFT' || status.value === 'SCHEDULED')
const ended = computed(() => status.value === 'ENDED')

function fill(value) {
  drop.value = value
  Object.assign(form, {
    name: value.name,
    slug: value.slug,
    description: value.description ?? '',
    startsAt: toLocalInput(value.startsAt),
    endsAt: toLocalInput(value.endsAt),
    maxPerCustomer: value.maxPerCustomer ?? '',
    admitPerMinute: String(value.admitPerMinute),
    leftover: value.leftover,
    requiresAccount: value.requiresAccount,
    showInHero: value.showInHero,
    heroColour: value.heroColour ?? 'BLACK',
  })
  for (const key of Object.keys(rows)) delete rows[key]
  for (const product of value.products) {
    rows[product.id] = {
      allocated: String(product.allocated),
      maxPerCustomer: product.maxPerCustomer ?? '',
      price: centsToEuros(product.priceCents),
    }
  }
}

async function load() {
  if (!props.id) return
  try {
    fill(await adminDropApi.get(props.id))
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => props.id, load, { immediate: true })

async function run(action, done) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await action()
    if (result?.id) fill(result)
    if (done) message.value = done
    return result
  } catch (exception) {
    error.value = exception.message
    return null
  } finally {
    busy.value = false
  }
}

const numberOrNull = (value) => (String(value ?? '').trim() === '' ? null : Number(value))

function body() {
  return {
    name: form.name,
    slug: form.slug.trim() || null,
    description: form.description,
    // The field shows whole minutes. Once the start time is fixed, send it back exactly as it is stored.
    startsAt: drop.value && !beforeStart.value ? drop.value.startsAt : fromLocalInput(form.startsAt),
    endsAt: fromLocalInput(form.endsAt),
    maxPerCustomer: numberOrNull(form.maxPerCustomer),
    admitPerMinute: numberOrNull(form.admitPerMinute),
    leftover: form.leftover,
    requiresAccount: form.requiresAccount,
    showInHero: form.showInHero,
    heroColour: form.heroColour,
  }
}

async function save() {
  if (drop.value) {
    await run(() => adminDropApi.update(drop.value.id, body()), t('admin.drops.saved'))
    return
  }
  const created = await run(() => adminDropApi.create(body()))
  if (created) router.replace({ path: `/admin/drops/${created.id}`, query: { created: '1' } })
}

const publish = () => run(() => adminDropApi.publish(drop.value.id), t('admin.drops.published'))
const unpublish = () => run(() => adminDropApi.unpublish(drop.value.id), t('admin.drops.unpublished'))

async function remove() {
  if (!window.confirm(t('admin.drops.deleteConfirm'))) return
  busy.value = true
  try {
    await adminDropApi.remove(drop.value.id)
    router.push('/admin/drops')
  } catch (exception) {
    error.value = exception.message
    busy.value = false
  }
}

function uploadBanner(event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    error.value = t('admin.images.tooLarge')
    return
  }
  run(() => adminDropApi.uploadBanner(drop.value.id, file))
}

const removeBanner = () => run(() => adminDropApi.removeBanner(drop.value.id))

function productBody(row) {
  return {
    allocated: Number(row.allocated || 0),
    maxPerCustomer: numberOrNull(row.maxPerCustomer),
    priceCents: eurosToCents(row.price),
  }
}

const saveProduct = (product) =>
  run(() => adminDropApi.updateProduct(drop.value.id, product.id, productBody(rows[product.id])), t('admin.drops.saved'))

const removeProduct = (product) => run(() => adminDropApi.removeProduct(drop.value.id, product.id))

// Adding a product: search the shop's products, then add with a first allocation.
const query = ref('')
const results = ref([])
const searched = ref(false)
let searchTimer = null

watch(query, (value) => {
  clearTimeout(searchTimer)
  if (value.trim().length < 2) {
    results.value = []
    searched.value = false
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      const page = await adminShopApi.products({ q: value.trim(), size: 8 })
      const inDrop = new Set(drop.value?.products.map((product) => product.productId))
      results.value = page.items.filter((item) => !inDrop.has(item.id) && item.status !== 'ARCHIVED')
      searched.value = true
    } catch (exception) {
      error.value = exception.message
    }
  }, SEARCH_DELAY_MS)
})

async function addProduct(item) {
  const added = await run(() =>
    adminDropApi.addProduct(drop.value.id, { productId: item.id, allocated: Math.max(0, item.available), maxPerCustomer: 1 }),
  )
  if (added) {
    query.value = ''
    results.value = []
  }
}

if (router.currentRoute.value.query.created) message.value = t('admin.drops.created')
</script>

<template>
  <router-link class="back" to="/admin/drops">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ t('admin.drops.back') }}
  </router-link>

  <div class="head">
    <div>
      <h1>{{ drop ? drop.name : t('admin.drops.newTitle') }}</h1>
      <p v-if="drop" class="state">
        <span class="status" :class="status.toLowerCase()">{{ t(`drops.status.${status}`) }}</span>
        <span v-if="drop.endReason" class="admin-hint">{{ t('admin.drops.endReason', { reason: drop.endReason }) }}</span>
        <span v-if="drop.reminderCount" class="admin-hint">{{ t('admin.drops.reminders', { count: drop.reminderCount }) }}</span>
      </p>
    </div>
    <div v-if="drop" class="head-actions">
      <a class="btn btn-secondary btn-small" :href="`/drops/${drop.slug}`" target="_blank" rel="noopener">
        <ExternalLink :size="16" aria-hidden="true" />
        {{ t('admin.drops.preview') }}
      </a>
      <router-link v-if="status !== 'DRAFT'" class="btn btn-secondary btn-small" :to="`/admin/drops/${drop.id}/live`">
        {{ ended ? t('admin.drops.report') : t('admin.drops.liveView') }}
      </router-link>
      <button v-if="status === 'DRAFT'" class="btn btn-primary btn-small" type="button" :disabled="busy" @click="publish">
        {{ t('admin.drops.publish') }}
      </button>
      <button v-if="status === 'SCHEDULED'" class="btn btn-secondary btn-small" type="button" :disabled="busy" @click="unpublish">
        {{ t('admin.drops.unpublish') }}
      </button>
      <button v-if="status === 'DRAFT'" class="link-button danger" type="button" :disabled="busy" @click="remove">
        {{ t('admin.drops.delete') }}
      </button>
    </div>
  </div>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <FormMessage v-if="status === 'LIVE'" type="success" class="message">{{ t('admin.drops.lockedLive') }}</FormMessage>
  <FormMessage v-if="ended" type="success" class="message">{{ t('admin.drops.lockedEnded') }}</FormMessage>

  <form class="admin-panel" @submit.prevent="save">
    <h2>{{ t('admin.drops.details') }}</h2>
    <div class="admin-fields">
      <div class="admin-field wide">
        <label class="admin-label" :for="field('name')">{{ t('admin.drops.name') }}</label>
        <input :id="field('name')" v-model="form.name" class="admin-input" required maxlength="120" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('slug')">{{ t('admin.drops.slug') }}</label>
        <input :id="field('slug')" v-model="form.slug" class="admin-input" maxlength="140" :disabled="!beforeStart" autocomplete="off" />
        <p class="admin-hint">{{ t('admin.drops.slugHint') }}</p>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('description')">{{ t('admin.drops.description') }}</label>
        <textarea :id="field('description')" v-model="form.description" class="admin-input" maxlength="5000"></textarea>
        <p class="admin-hint">{{ t('admin.drops.descriptionHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('starts')">{{ t('admin.drops.startsAt') }}</label>
        <input :id="field('starts')" v-model="form.startsAt" class="admin-input" type="datetime-local" required :disabled="!beforeStart" />
        <p class="admin-hint">{{ t('admin.drops.startsAtHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('ends')">{{ t('admin.drops.endsAt') }}</label>
        <input :id="field('ends')" v-model="form.endsAt" class="admin-input" type="datetime-local" :disabled="ended" />
        <p class="admin-hint">{{ t('admin.drops.endsAtHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('max')">{{ t('admin.drops.maxPerCustomer') }}</label>
        <input :id="field('max')" v-model="form.maxPerCustomer" class="admin-input" type="number" min="1" max="99" :disabled="!beforeStart" />
        <p class="admin-hint">{{ t('admin.drops.maxPerCustomerHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('speed')">{{ t('admin.drops.admitPerMinute') }}</label>
        <input :id="field('speed')" v-model="form.admitPerMinute" class="admin-input" type="number" min="1" max="10000" :disabled="ended" />
        <p class="admin-hint">{{ t('admin.drops.admitPerMinuteHint') }}</p>
      </div>
      <fieldset class="admin-field wide choices" :disabled="ended">
        <legend class="admin-label">{{ t('admin.drops.leftover') }}</legend>
        <label class="admin-check">
          <input v-model="form.leftover" type="radio" value="RETURN_TO_STOCK" />
          {{ t('admin.drops.leftoverRETURN_TO_STOCK') }}
        </label>
        <label class="admin-check">
          <input v-model="form.leftover" type="radio" value="KEEP" />
          {{ t('admin.drops.leftoverKEEP') }}
        </label>
      </fieldset>
      <div class="admin-field wide">
        <label class="admin-check">
          <input v-model="form.requiresAccount" type="checkbox" :disabled="!beforeStart" />
          {{ t('admin.drops.requiresAccount') }}
        </label>
        <p class="admin-hint">{{ t('admin.drops.requiresAccountHint') }}</p>
        <label class="admin-check">
          <input v-model="form.showInHero" type="checkbox" :disabled="ended" />
          {{ t('admin.drops.showInHero') }}
        </label>
      </div>
      <fieldset class="admin-field wide choices" :disabled="ended">
        <legend class="admin-label">{{ t('admin.drops.heroColour') }}</legend>
        <div class="colours">
          <label v-for="colour in HERO_COLOURS" :key="colour" class="admin-check">
            <input v-model="form.heroColour" type="radio" :value="colour" />
            <span class="swatch" :data-drop-colour="colour.toLowerCase()" aria-hidden="true"></span>
            {{ t(`admin.drops.heroColour${colour}`) }}
          </label>
        </div>
        <p class="admin-hint">{{ t('admin.drops.heroColourHint') }}</p>
      </fieldset>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">
        {{ drop ? t('admin.drops.save') : t('admin.drops.create') }}
      </button>
    </div>
  </form>

  <section v-if="drop" class="admin-panel">
    <h2>{{ t('admin.drops.banner') }}</h2>
    <img v-if="drop.bannerUrl" class="banner" :src="drop.bannerUrl" alt="" />
    <p class="admin-hint">{{ t('admin.drops.bannerHint') }}</p>
    <div class="admin-actions">
      <label class="btn btn-secondary btn-small upload">
        <Upload :size="16" aria-hidden="true" />
        {{ t('admin.drops.chooseBanner') }}
        <input class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadBanner" />
      </label>
      <button v-if="drop.bannerUrl" class="link-button" type="button" :disabled="busy" @click="removeBanner">
        {{ t('admin.drops.removeBanner') }}
      </button>
    </div>
  </section>

  <section v-if="drop" class="admin-panel">
    <h2>{{ t('admin.drops.productsTitle') }}</h2>
    <p class="admin-hint">{{ t('admin.drops.productsIntro') }}</p>

    <div v-if="beforeStart" class="search">
      <label class="admin-label" :for="field('search')">{{ t('admin.drops.search') }}</label>
      <input :id="field('search')" v-model="query" class="admin-input" type="search" autocomplete="off" />
      <p class="admin-hint">{{ t('admin.drops.searchHint') }}</p>
      <ul v-if="results.length" class="results">
        <li v-for="item in results" :key="item.id" class="result">
          <span class="result-name">
            {{ item.name }}
            <span class="admin-hint">
              {{ statusLabel(item.status) }} · {{ t('admin.drops.allocatedHint', { count: item.available }) }}
              · {{ item.priceCents != null ? formatMoney(item.salePriceCents ?? item.priceCents, 'EUR') : t('admin.drops.noShopPrice') }}
            </span>
          </span>
          <button class="btn btn-secondary btn-small" type="button" :disabled="busy" @click="addProduct(item)">
            <Plus :size="16" aria-hidden="true" />
            {{ t('admin.drops.addProduct') }}
          </button>
        </li>
      </ul>
      <p v-else-if="searched" class="admin-hint">{{ t('admin.drops.noResults') }}</p>
    </div>

    <p v-if="!drop.products.length" class="admin-intro">{{ t('admin.drops.noProducts') }}</p>
    <ul v-else class="products">
      <li v-for="product in drop.products" :key="product.id" class="product">
        <div class="thumb">
          <ProductImage :src="product.imageUrl" :is-card="product.imageIsCard" :set-logo="product.setLogoUrl" :type="product.type" />
        </div>
        <div class="product-body">
          <p class="product-name">
            <router-link :to="`/admin/producten/${product.productId}`">{{ product.name }}</router-link>
            <span class="admin-hint">{{ statusLabel(product.productStatus) }}</span>
          </p>
          <div class="product-fields">
            <label class="mini">
              <span class="admin-label">{{ t('admin.drops.allocated') }}</span>
              <input v-model="rows[product.id].allocated" class="admin-input" type="number" min="0" :disabled="ended" />
              <span class="admin-hint">{{ t('admin.drops.allocatedHint', { count: product.freeStock }) }}</span>
            </label>
            <label class="mini">
              <span class="admin-label">{{ t('admin.drops.productMax') }}</span>
              <input v-model="rows[product.id].maxPerCustomer" class="admin-input" type="number" min="1" max="99" :disabled="!beforeStart" />
            </label>
            <label class="mini">
              <span class="admin-label">{{ t('admin.drops.dropPrice') }}</span>
              <input v-model="rows[product.id].price" class="admin-input" inputmode="decimal" :disabled="!beforeStart" />
              <span class="admin-hint">
                {{ t('admin.drops.dropPriceHint', { price: product.shopPriceCents != null ? formatMoney(product.shopPriceCents, 'EUR') : t('admin.drops.noShopPrice') }) }}
              </span>
            </label>
            <dl class="numbers">
              <div><dt>{{ t('admin.drops.reserved') }}</dt><dd>{{ product.reserved }}</dd></div>
              <div><dt>{{ t('admin.drops.sold') }}</dt><dd>{{ product.sold }}</dd></div>
              <div><dt>{{ t('admin.drops.available') }}</dt><dd>{{ product.available }}</dd></div>
            </dl>
          </div>
          <div v-if="!ended" class="product-actions">
            <button class="btn btn-secondary btn-small" type="button" :disabled="busy" @click="saveProduct(product)">
              {{ t('admin.drops.saveProduct') }}
            </button>
            <button v-if="beforeStart" class="link-button danger" type="button" :disabled="busy" @click="removeProduct(product)">
              {{ t('admin.drops.removeProduct') }}
            </button>
          </div>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.status {
  display: inline-block;
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-muted-soft);
  font-size: var(--text-xs);
  font-weight: 700;
}

.status.scheduled {
  background: var(--color-tint-strong);
  color: var(--color-brand-deep);
}

.status.live {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.status.ended {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.message {
  margin-bottom: var(--space-3);
}

.choices {
  border: 0;
  padding: 0;
}

.colours {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-5);
}

.swatch {
  width: 1.75rem;
  height: 1.25rem;
  border-radius: var(--radius-xs);
  background: var(--drop-gradient);
  box-shadow: inset 0 0 0 1px var(--color-line);
}

.banner {
  width: 100%;
  max-width: 32rem;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-md);
}

.upload {
  cursor: pointer;
}

.search {
  display: grid;
  gap: var(--space-1);
  max-width: 40rem;
  margin: var(--space-3) 0 var(--space-4);
}

.results {
  display: grid;
  gap: var(--space-1);
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
}

.result {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
}

.result-name {
  display: grid;
}

.products {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.product {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.product-name {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  font-weight: 650;
}

.product-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.mini {
  display: grid;
  gap: var(--space-1);
  width: 9rem;
}

.numbers {
  display: flex;
  gap: var(--space-4);
  margin: 0;
}

.numbers dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.numbers dd {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 700;
}

.product-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
}
</style>
