<script setup>
import BinderPage from '../components/BinderPage.vue'
import { useSession } from '../stores/session'
import { t } from '../utils/i18n'

const session = useSession()
</script>

<template>
  <section class="hero">
    <div class="container hero-grid">
      <div>
        <h1 class="hero-title">{{ t('home.title') }}</h1>
        <p class="hero-intro">{{ t('home.intro') }}</p>
        <router-link v-if="session.isLoggedIn.value" class="btn btn-primary hero-action" to="/account">
          {{ t('home.toAccount') }}
        </router-link>
        <router-link v-else class="btn btn-primary hero-action" to="/registreren">
          {{ t('home.createAccount') }}
        </router-link>
      </div>
      <BinderPage class="hero-binder" />
    </div>
  </section>
</template>

<style scoped>
.hero {
  padding-block: var(--space-6) var(--space-7);
  background: var(--color-binder);
  color: var(--color-on-binder);
}

.hero-grid {
  display: grid;
  gap: var(--space-6);
}

.hero-title {
  font-size: var(--text-hero);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 0.98;
}

.hero-intro {
  max-width: 34rem;
  margin-top: var(--space-4);
  color: var(--color-on-binder-soft);
  font-size: var(--text-lg);
}

.hero-action {
  margin-top: var(--space-5);
}

.hero :focus-visible {
  outline-color: var(--color-focus-on-binder);
}

.hero-binder {
  max-width: 24rem;
}

@media (min-width: 960px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
    align-items: center;
    gap: var(--space-8);
  }

  .hero {
    padding-block: var(--space-7);
  }

  .hero-binder {
    justify-self: end;
    width: 100%;
  }
}
</style>
