<script setup>
import { footerInfoLinks, footerShopLinks } from '../../data/navigation'
import { t } from '../../utils/i18n'
</script>

<template>
  <footer class="site-footer">
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
      <div class="attribution">
        <p>{{ t('footer.attribution') }}</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: var(--space-7);
  padding-block: var(--space-6) var(--space-5);
  background: var(--color-binder);
  color: var(--color-on-binder-soft);
  font-size: var(--text-sm);
}

.site-footer :focus-visible {
  outline-color: var(--color-focus-on-binder);
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
  color: var(--color-on-binder);
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
  color: var(--color-on-binder-soft);
  text-decoration: none;
}

.link-list a:hover {
  color: var(--color-on-binder);
  text-decoration: underline;
}

.payment-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.payment-method {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-binder-raised);
  color: var(--color-on-binder);
  font-size: var(--text-xs);
  font-weight: 600;
}

.attribution {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-binder-raised);
  font-size: var(--text-xs);
}

.attribution p {
  max-width: 70ch;
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
