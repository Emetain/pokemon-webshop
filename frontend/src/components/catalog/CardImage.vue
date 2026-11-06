<script setup>
import { ref, watch } from 'vue'
import { ImageOff } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

const props = defineProps({
  src: { type: String, default: null },
  alt: { type: String, default: '' },
  eager: { type: Boolean, default: false },
})

const emit = defineEmits(['failed'])

const failed = ref(false)
const loaded = ref(false)

function onError() {
  failed.value = true
  emit('failed')
}

watch(
  () => props.src,
  () => {
    failed.value = false
    loaded.value = false
  },
)
</script>

<!--
  Scryfall's terms forbid covering, cropping, distorting or tinting card images. So: `contain` instead of
  `cover`, no rounded corners on the image, and never anything on top of it. Prices and labels go below.
-->
<template>
  <div class="card-image" :class="{ waiting: src && !failed && !loaded }">
    <img
      v-if="src && !failed"
      class="picture"
      :src="src"
      :alt="alt"
      :loading="eager ? 'eager' : 'lazy'"
      decoding="async"
      @load="loaded = true"
      @error="onError"
    />
    <div v-else class="missing">
      <ImageOff :size="28" aria-hidden="true" />
      <span class="missing-text">{{ t('catalog.noImage') }}</span>
    </div>
  </div>
</template>

<style scoped>
.card-image {
  aspect-ratio: 63 / 88;
}

/* Sits behind the image and goes away when it arrives, so it never shows through transparent corners. */
.waiting {
  border-radius: var(--radius-card);
  background: var(--color-tint);
}

.picture {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.missing {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  height: 100%;
  padding: var(--space-3);
  border: 1.5px dashed var(--color-line-strong);
  border-radius: var(--radius-card);
  background: var(--color-tint);
  color: var(--color-ink-soft);
  text-align: center;
}

.missing-text {
  font-size: var(--text-xs);
}
</style>
