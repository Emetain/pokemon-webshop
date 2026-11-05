import { createRouter, createWebHistory } from 'vue-router'
import { footerInfoLinks, footerShopLinks, navDestinations } from './data/navigation'
import { useSession } from './stores/session'

const ShopLayout = () => import('./components/layout/ShopLayout.vue')
const AdminLayout = () => import('./components/layout/AdminLayout.vue')
const PlaceholderView = () => import('./views/PlaceholderView.vue')

const placeholder = (path, titleKey, meta = {}) => ({
  path,
  component: PlaceholderView,
  meta: { titleKey, ...meta },
})

/** Menu and footer destinations that have no real page yet. Later phases replace these. */
function placeholderPages() {
  const pages = [
    ...navDestinations(),
    ...footerInfoLinks,
    ...footerShopLinks,
    { to: '/winkelwagen', labelKey: 'layout.cart' },
  ]
  return pages.map(({ to, labelKey, game }) => placeholder(to.slice(1), labelKey, { game }))
}

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

        ...placeholderPages(),
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
