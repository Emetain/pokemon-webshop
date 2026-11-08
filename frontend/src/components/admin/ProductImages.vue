<script setup>
import { ref, useId } from 'vue'
import { ArrowDown, ArrowUp, Trash2, Upload } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { adminShopApi } from '../../services/api'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024

/** The photos of a product: add, put in order, remove. Every change answers with the product, which is emitted. */
const props = defineProps({
  product: { type: Object, required: true },
})

const emit = defineEmits(['change'])

const id = useId()
const busy = ref(false)
const error = ref('')

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    emit('change', await action())
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function upload(event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    error.value = t('admin.images.tooLarge')
    return
  }
  run(() => adminShopApi.addImage(props.product.id, file))
}

function move(index, offset) {
  const urls = [...props.product.images]
  const [url] = urls.splice(index, 1)
  urls.splice(index + offset, 0, url)
  run(() => adminShopApi.reorderImages(props.product.id, urls))
}

function remove(index) {
  if (!window.confirm(t('admin.productForm.removeConfirm'))) return
  run(() => adminShopApi.removeImage(props.product.id, index))
}
</script>

<template>
  <section class="admin-panel" :aria-labelledby="`${id}-title`">
    <h2 :id="`${id}-title`">{{ t('admin.productForm.images') }}</h2>
    <p class="admin-hint">{{ t('admin.productForm.imagesHint') }}</p>
    <p v-if="product.card && !product.images.length" class="admin-hint">{{ t('admin.productForm.imagesCard') }}</p>

    <ol v-if="product.images.length" class="images">
      <li v-for="(url, index) in product.images" :key="url" class="image">
        <img :src="url" alt="" loading="lazy" decoding="async" />
        <div class="image-actions">
          <button
            class="icon-button"
            type="button"
            :disabled="busy || index === 0"
            :aria-label="t('admin.productForm.moveUpLabel', { number: index + 1 })"
            @click="move(index, -1)"
          >
            <ArrowUp :size="16" aria-hidden="true" />
          </button>
          <button
            class="icon-button"
            type="button"
            :disabled="busy || index === product.images.length - 1"
            :aria-label="t('admin.productForm.moveDownLabel', { number: index + 1 })"
            @click="move(index, 1)"
          >
            <ArrowDown :size="16" aria-hidden="true" />
          </button>
          <button
            class="icon-button danger"
            type="button"
            :disabled="busy"
            :aria-label="t('admin.productForm.removeLabel', { number: index + 1 })"
            @click="remove(index)"
          >
            <Trash2 :size="16" aria-hidden="true" />
          </button>
        </div>
      </li>
    </ol>

    <div class="admin-actions">
      <label class="btn btn-secondary btn-small upload" :class="{ busy }">
        <Upload :size="18" aria-hidden="true" />
        {{ t('admin.productForm.upload') }}
        <input class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy" @change="upload" />
      </label>
    </div>
    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  </section>
</template>

<style scoped>
.images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(8rem, 1fr));
  gap: var(--space-3);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.image {
  display: grid;
  gap: var(--space-1);
}

.image img {
  width: 100%;
  aspect-ratio: 1;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  object-fit: contain;
}

.image-actions {
  display: flex;
  gap: var(--space-1);
}

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
}

.icon-button:hover:not(:disabled) {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.icon-button.danger:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.upload {
  cursor: pointer;
}

.upload:focus-within {
  outline: 3px solid var(--color-focus);
  outline-offset: 2px;
}

.upload.busy {
  cursor: progress;
  opacity: 0.6;
}

.message {
  margin-top: var(--space-3);
}
</style>
