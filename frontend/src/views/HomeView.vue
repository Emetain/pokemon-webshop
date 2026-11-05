<script setup>
import BallMark from '../components/BallMark.vue'
import BinderPage from '../components/BinderPage.vue'
import { useSession } from '../stores/session'
import { t } from '../utils/i18n'

const session = useSession()
</script>

<template>
  <section class="hero">
    <BallMark class="hero-ball" outline />
    <div class="container hero-grid">
      <div>
        <h1 class="hero-title">{{ t('home.title') }}</h1>
        <p class="hero-intro">{{ t('home.intro') }}</p>
        <div class="hero-actions">
          <router-link v-if="session.isLoggedIn.value" class="btn btn-on-brand" to="/account">
            {{ t('home.toAccount') }}
          </router-link>
          <router-link v-else class="btn btn-on-brand" to="/registreren">
            {{ t('home.createAccount') }}
          </router-link>
          <router-link class="btn btn-outline-on-brand" to="/pokemon">{{ t('home.browsePokemon') }}</router-link>
        </div>
      </div>
      <BinderPage class="hero-binder" />
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
  padding-block: var(--space-6) var(--space-7);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.hero :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.hero-ball {
  position: absolute;
  top: -9rem;
  right: -19rem;
  width: 30rem;
  height: 30rem;
  color: var(--color-on-brand);
  opacity: 0.16;
  transform: rotate(-18deg);
}

.hero-grid {
  position: relative;
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
  color: var(--color-on-brand-soft);
  font-size: var(--text-lg);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.hero-binder {
  max-width: 24rem;
}

@media (min-width: 960px) {
  .hero {
    padding-block: var(--space-7);
  }

  .hero-ball {
    top: 50%;
    right: -9rem;
    width: 50rem;
    height: 50rem;
    transform: translateY(-50%) rotate(-18deg);
  }

  .hero-grid {
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
    align-items: center;
    gap: var(--space-8);
  }

  .hero-binder {
    justify-self: end;
    width: 100%;
  }
}
</style>
