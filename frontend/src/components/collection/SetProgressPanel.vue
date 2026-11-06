<script setup>
import { computed } from 'vue'
import { ListChecks } from 'lucide-vue-next'
import ProgressBar from './ProgressBar.vue'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** `stats` holds `progress`, `masterProgress` and `value`. */
const props = defineProps({
  stats: { type: Object, required: true },
  master: { type: Boolean, default: false },
  setName: { type: String, required: true },
  missingLink: { type: Object, required: true },
})

defineEmits(['mode'])

const shown = computed(() => (props.master ? props.stats.masterProgress : props.stats.progress))
</script>

<template>
  <section class="panel" :aria-label="t('collection.set.title')">
    <div class="head">
      <h2 class="title">{{ t('collection.set.title') }}</h2>
      <div class="modes" role="group" :aria-label="t('collection.set.mode')">
        <button class="mode" type="button" :aria-pressed="!master" @click="$emit('mode', false)">
          {{ t('collection.set.normal') }}
        </button>
        <button class="mode" type="button" :aria-pressed="master" @click="$emit('mode', true)">
          {{ t('collection.set.master') }}
        </button>
      </div>
    </div>

    <ProgressBar :progress="shown" :name="setName" />
    <p class="hint">{{ master ? t('collection.set.masterHint') : t('collection.set.normalHint') }}</p>

    <div class="foot">
      <p class="value">
        {{ t('collection.set.value') }}
        <strong>{{ formatMoney(stats.value.cents, stats.value.currency) }}</strong>
      </p>
      <router-link class="missing-link" :to="missingLink">
        <ListChecks :size="18" aria-hidden="true" />
        {{ t('collection.set.missingList') }}
      </router-link>
    </div>
  </section>
</template>

<style scoped>
.panel {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.title {
  font-size: var(--text-lg);
}

.modes {
  display: inline-flex;
  padding: var(--space-half);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
}

.mode {
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.mode[aria-pressed='true'] {
  background: var(--accent);
  color: var(--accent-ink);
}

.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-4);
}

.value {
  font-size: var(--text-sm);
}

.value strong {
  margin-left: var(--space-1);
  font-size: var(--text-md);
}

.missing-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  font-size: var(--text-sm);
  font-weight: 600;
}
</style>
