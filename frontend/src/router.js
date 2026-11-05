import { createRouter, createWebHistory } from 'vue-router'
import { useSession } from './stores/session'

const ShopLayout = () => import('./components/layout/ShopLayout.vue')
const AdminLayout = () => import('./components/layout/AdminLayout.vue')
const PlaceholderView = () => import('./views/PlaceholderView.vue')

const placeholder = (path, titleKey, meta = {}) => ({
  path,
  component: PlaceholderView,
  meta: { titleKey, ...meta },
})

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (to, from, savedPosition) => savedPosition ?? { top: 0 },
  routes: [
    {
      path: '/',
      component: ShopLayout,
      children: [
        { path: '', component: () => import('./views/HomeView.vue') },

        { path: 'inloggen', component: () => import('./views/LoginView.vue'), meta: { guestOnly: true } },
        { path: 'registreren', component: () => import('./views/RegisterView.vue'), meta: { guestOnly: true } },
        { path: 'wachtwoord-vergeten', component: () => import('./views/ForgotPasswordView.vue') },
        { path: 'wachtwoord-herstellen', component: () => import('./views/ResetPasswordView.vue') },
        { path: 'account', component: () => import('./views/AccountView.vue'), meta: { requiresLogin: true } },

        placeholder('pokemon', 'nav.pokemon', { game: 'pokemon' }),
        placeholder('lorcana', 'nav.lorcana', { game: 'lorcana' }),
        placeholder('magic', 'nav.magic', { game: 'mtg' }),
        placeholder('one-piece', 'nav.onePiece', { game: 'one-piece' }),
        placeholder('accessoires', 'nav.accessories'),
        placeholder('pre-order', 'nav.preOrder'),
        placeholder('drops', 'nav.drops'),
        placeholder('collectie', 'nav.collection'),
        placeholder('verlanglijst', 'layout.wishlist'),
        placeholder('winkelwagen', 'layout.cart'),
        placeholder('info/verzending', 'footer.shipping'),
        placeholder('info/retourneren', 'footer.returns'),
        placeholder('info/veelgestelde-vragen', 'footer.faq'),
        placeholder('info/contact', 'footer.contact'),
        placeholder('info/over-ons', 'footer.about'),
        placeholder('info/algemene-voorwaarden', 'footer.terms'),
        placeholder('info/privacybeleid', 'footer.privacy'),
      ],
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAdmin: true },
      children: [{ path: '', component: () => import('./views/admin/AdminDashboardView.vue') }],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const session = useSession()
  await session.ensureLoaded()

  const needsLogin = to.meta.requiresLogin || to.meta.requiresAdmin
  if (needsLogin && !session.isLoggedIn.value) {
    return { path: '/inloggen', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !session.isAdmin.value) {
    return { path: '/' }
  }
  if (to.meta.guestOnly && session.isLoggedIn.value) {
    return { path: '/account' }
  }
  return true
})

export default router
