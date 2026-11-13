<script setup>
import { computed } from 'vue'
import { PackageOpen } from 'lucide-vue-next'
import CardImage from '../catalog/CardImage.vue'
import { formatMoney, formatPercent } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * What is in the batch of a mystery box, and the chance of every kind of box. The bar gives every kind the width of
 * its chance, so the chances can be seen as well as read. Never how many boxes there are or are left.
 */
const props = defineProps({
  box: { type: Object, required: true },
})

/** Darkest for the most valuable box. The list from the server is sorted by value already. */
const SHADES = [100, 70, 46, 28, 17, 11, 7]

const prizes = computed(() =>
  props.box.prizes.map((prize, index) => ({ ...prize, shade: SHADES[Math.min(index, SHADES.length - 1)] })),
)
const left = computed(() => prizes.value.filter((prize) => !prize.gone))
</script>

<template>
  <section class="odds" aria-labelledby="mystery-odds">
    <div class="head">
      <PackageOpen class="icon" :size="26" aria-hidden="true" />
      <div>
        <h2 id="mystery-odds">{{ t('mystery.title') }}</h2>
        <p class="batch">{{ t('mystery.batch', { name: box.batchName }) }}</p>
      </div>
    </div>

    <div class="stack" role="img" :aria-label="t('mystery.stackLabel')">
      <span
        v-for="prize in left"
        :key="prize.name"
        class="segment"
        :style="{ flexGrow: prize.chance, '--shade': `${prize.shade}%` }"
        :title="`${prize.name}: ${formatPercent(prize.chance)}`"
      ></span>
    </div>

    <dl class="values">
      <div>
        <dt>{{ t('mystery.min') }}</dt>
        <dd>{{ formatMoney(box.minValueCents, 'EUR') }}</dd>
      </div>
      <div>
        <dt>{{ t('mystery.average') }}</dt>
        <dd>{{ formatMoney(box.averageValueCents, 'EUR') }}</dd>
      </div>
      <div>
        <dt>{{ t('mystery.max') }}</dt>
        <dd>{{ formatMoney(box.maxValueCents, 'EUR') }}</dd>
      </div>
    </dl>

    <table class="prizes">
      <caption class="visually-hidden">{{ t('mystery.tableCaption') }}</caption>
      <thead>
        <tr>
          <th scope="col">{{ t('mystery.contents') }}</th>
          <th scope="col" class="num value-col">{{ t('mystery.value') }}</th>
          <th scope="col" class="num">{{ t('mystery.chance') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="prize in prizes" :key="prize.name" :class="{ gone: prize.gone }">
          <th scope="row">
            <span class="prize">
              <span class="swatch" :style="{ '--shade': `${prize.shade}%` }" aria-hidden="true"></span>
              <CardImage v-if="prize.cardImageUrl" class="thumb" :src="prize.cardImageUrl" :alt="prize.name" />
              <span>
                {{ prize.name }}
                <span class="phone-value">{{ formatMoney(prize.valueCents, 'EUR') }}</span>
              </span>
            </span>
          </th>
          <td class="num value-col">{{ formatMoney(prize.valueCents, 'EUR') }}</td>
          <td class="num chance">{{ prize.gone ? t('mystery.gone') : formatPercent(prize.chance) }}</td>
        </tr>
      </tbody>
    </table>

    <p class="how">{{ t('mystery.how') }}</p>
  </section>
</template>

<style scoped>
.odds {
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.head {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.icon {
  flex: none;
  color: var(--accent);
}

.batch {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

/* The boxes that are left, side by side: a wider segment is a bigger chance. */
.stack {
  display: flex;
  gap: 2px;
  height: 1.75rem;
  margin-top: var(--space-4);
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--color-muted-soft);
}

.segment {
  flex-basis: 0;
  min-width: 3px;
  background: color-mix(in srgb, var(--accent) var(--shade), var(--color-surface));
}

.values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: var(--space-4) 0 0;
}

.values div {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
}

.values dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.values dd {
  margin: 0;
  font-weight: 700;
}

.prizes {
  width: 100%;
  margin-top: var(--space-4);
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.prizes th,
.prizes td {
  padding: var(--space-2) var(--space-1);
  border-bottom: 1px solid var(--color-line);
  text-align: left;
  vertical-align: middle;
}

.prizes thead th {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 600;
}

.prizes tbody th {
  font-weight: 600;
}

.prizes .num {
  text-align: right;
  white-space: nowrap;
}

/* On a phone the value goes under the name, so the name has room. */
.phone-value {
  display: block;
  color: var(--color-ink-soft);
  font-weight: 400;
}

.value-col {
  display: none;
}

@media (min-width: 560px) {
  .phone-value {
    display: none;
  }

  .value-col {
    display: table-cell;
  }
}

.chance {
  font-weight: 700;
}

.gone {
  color: var(--color-ink-soft);
  text-decoration: line-through;
}

.prize {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.swatch {
  flex: none;
  width: 0.875rem;
  height: 0.875rem;
  border-radius: var(--radius-xs);
  background: color-mix(in srgb, var(--accent) var(--shade), var(--color-surface));
  box-shadow: inset 0 0 0 1px var(--color-line);
}

.thumb {
  flex: none;
  width: 2rem;
}

.how {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
