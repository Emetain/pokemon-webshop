<script setup>
import { computed, onMounted, ref } from 'vue'
import NewsletterForm from '../content/NewsletterForm.vue'
import { footerInfoLinks, footerShopLinks } from '../../data/navigation'
import { shopApi } from '../../services/api'
import { useAds } from '../../stores/ads'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

// The KVK number and the rest, once the owner has filled them in on the server. Nothing shows until then.
const company = ref(null)

const companyParts = computed(() => {
  const details = company.value
  if (!details || !(details.address || details.kvkNumber || details.vatNumber)) return []
  return [
    details.legalName ?? t('brand.name'),
    details.address,
    details.kvkNumber && t('footer.company.kvk', { number: details.kvkNumber }),
    details.vatNumber && t('footer.company.vat', { number: details.vatNumber }),
  ].filter(Boolean)
})

// With ads on: a way back to Google's consent message, to change the choice. Not for Premium, who sees no ads.
const ads = useAds()
const session = useSession()

onMounted(async () => {
  ads.load()
  try {
    company.value = await shopApi.company()
  } catch {
    // The footer works without it.
  }
})
</script>

<template>
  <footer class="site-footer">
    <div class="container newsletter-band">
      <div>
        <h2 class="column-title">{{ t('newsletter.title') }}</h2>
        <p>{{ t('newsletter.intro') }}</p>
      </div>
      <NewsletterForm on-brand />
    </div>
    <div class="container columns">
      <section>
        <h2 class="column-title">{{ t('footer.infoTitle') }}</h2>
        <ul class="link-list">
          <li v-for="link in footerInfoLinks" :key="link.to">
            <router-link :to="link.to">{{ t(link.labelKey) }}</router-link>
          </li>
        </ul>
      </section>

      <section>
        <h2 class="column-title">{{ t('footer.shopTitle') }}</h2>
        <ul class="link-list">
          <li v-for="link in footerShopLinks" :key="link.to">
            <router-link :to="link.to">{{ t(link.labelKey) }}</router-link>
          </li>
        </ul>
      </section>

      <section>
        <h2 class="column-title">{{ t('footer.paymentTitle') }}</h2>
        <ul class="payment-list">
          <li v-for="method in t('footer.paymentMethods')" :key="method" class="payment-method">{{ method }}</li>
        </ul>
      </section>
    </div>

    <div class="container">
      <p v-if="companyParts.length" class="company">
        <span v-for="part in companyParts" :key="part" class="company-part">{{ part }}</span>
      </p>
      <p v-if="ads.state.enabled && !session.isPremium.value">
        <button class="ad-choices" type="button" @click="ads.showChoices()">{{ t('ads.choices') }}</button>
      </p>
      <div class="attribution">
        <p v-for="line in t('footer.attribution')" :key="line">{{ line }}</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: var(--space-7);
  padding-block: var(--space-6) var(--space-5);
  background: var(--color-brand-deep);
  color: var(--color-on-brand-soft);
  font-size: var(--text-sm);
}

.site-footer :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.newsletter-band {
  display: grid;
  gap: var(--space-3) var(--space-6);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-brand-raised);
}

@media (min-width: 768px) {
  .newsletter-band {
    grid-template-columns: minmax(0, 1fr) minmax(0, 26rem);
    align-items: center;
  }
}

.columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5) var(--space-4);
}

.columns > :last-child {
  grid-column: 1 / -1;
}

.column-title {
  margin-bottom: var(--space-2);
  color: var(--color-on-brand);
  font-size: var(--text-md);
}

.link-list,
.payment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.link-list a {
  display: inline-block;
  padding-block: var(--space-1);
  color: var(--color-on-brand-soft);
  text-decoration: none;
}

@media (hover: hover) {
  .link-list a:hover {
    color: var(--color-on-brand);
    text-decoration: underline;
  }
}

.payment-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.payment-method {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-brand-raised);
  color: var(--color-on-brand);
  font-size: var(--text-xs);
  font-weight: 600;
}

.ad-choices {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.company {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
  margin: var(--space-5) 0 0;
  color: var(--color-on-brand);
}

.company-part:not(:last-child)::after {
  content: '·';
  margin-left: var(--space-3);
  color: var(--color-on-brand-soft);
}

.attribution {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-brand-raised);
  font-size: var(--text-xs);
}

.attribution {
  display: grid;
  gap: var(--space-2);
}

.attribution p {
  max-width: 80ch;
}

@media (min-width: 768px) {
  .columns {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .columns > :last-child {
    grid-column: auto;
  }
}
</style>
