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

/** Passes the route params and the named query values to a view as props. A repeated value counts once. */
const withQuery = (...names) => (route) => ({
  ...route.params,
  ...Object.fromEntries(names.map((name) => [name, [route.query[name]].flat()[0] ?? undefined])),
})

const CARD_FILTERS = ['sort', 'category', 'color', 'rarity', 'variant', 'priced', 'collection']

// Leaves room for the sticky header above the element.
const HASH_SCROLL_OFFSET = 160

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
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return to.hash ? { el: to.hash, top: HASH_SCROLL_OFFSET } : { top: 0 }
  },
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

        {
          path: 'kaarten/:game',
          name: 'sets',
          component: () => import('./views/catalog/SetListView.vue'),
          props: true,
        },
        {
          path: 'kaarten/:game/:setCode',
          name: 'set',
          component: () => import('./views/catalog/SetView.vue'),
          props: withQuery(...CARD_FILTERS, 'master', 'page'),
        },
        {
          path: 'kaarten/:game/:setCode/gemist',
          name: 'set-missing',
          component: () => import('./views/collection/SetMissingView.vue'),
          props: withQuery('sort', 'category', 'color', 'rarity', 'variant', 'priced', 'master', 'page'),
          meta: { requiresLogin: true },
        },
        {
          path: 'kaarten/:game/:setCode/:cardId',
          name: 'card',
          component: () => import('./views/catalog/CardView.vue'),
          props: true,
        },
        {
          path: 'zoeken',
          name: 'search',
          component: () => import('./views/SearchView.vue'),
          props: withQuery('q', 'game', ...CARD_FILTERS, 'page'),
        },

        {
          path: 'collectie',
          name: 'collection',
          component: () => import('./views/collection/CollectionView.vue'),
        },
        {
          path: 'collectie/pokedex',
          name: 'pokedex',
          component: () => import('./views/collection/PokedexView.vue'),
          props: withQuery('generatie'),
        },
        {
          path: 'collectie/pokedex/:dexNumber',
          name: 'pokedex-entry',
          component: () => import('./views/collection/PokedexEntryView.vue'),
          props: withQuery(...CARD_FILTERS, 'page'),
        },
        {
          path: 'collectie/gedeeld/:slug',
          name: 'shared-collection',
          component: () => import('./views/collection/SharedCollectionView.vue'),
          props: true,
        },
        {
          path: 'verlanglijst',
          name: 'wishlist',
          component: () => import('./views/collection/WishlistView.vue'),
          props: withQuery('sort', 'priced', 'collection', 'page'),
        },

        ...placeholderPages(),
      ],
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAdmin: true },
      children: [
        { path: '', component: () => import('./views/admin/AdminDashboardView.vue') },
        { path: 'sync', component: () => import('./views/admin/AdminSyncView.vue') },
        {
          path: 'afbeeldingen',
          component: () => import('./views/admin/AdminImagesView.vue'),
          props: withQuery('kind', 'q', 'game', 'set', 'image', 'sort', 'page'),
        },
      ],
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
