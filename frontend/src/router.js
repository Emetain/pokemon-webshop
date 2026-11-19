import { createRouter, createWebHistory } from 'vue-router'
import { GAMES } from './data/games'
import { navDestinations } from './data/navigation'
import { useSession } from './stores/session'
import { setRouteMeta, updateCanonical } from './utils/pageMeta'

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

const ShopListView = () => import('./views/shop/ShopListView.vue')
const SHOP_FILTERS = ['q', 'sort', 'language', 'min', 'max', 'stock', 'sale', 'preorder', 'page']

/** A list of products. `fixed` says which part of the shop it is, the URL adds the rest. */
const shopRoute = (path, fixed) => ({
  path,
  component: ShopListView,
  props: (route) => ({ ...fixed, ...withQuery(...SHOP_FILTERS)(route) }),
})

/** Per game: all products, a series, a set within it, and a kind of product such as /pokemon/booster-boxen. */
function gameShopRoutes() {
  return GAMES.flatMap(({ slug }) => [
    shopRoute(slug, { mode: 'game', game: slug }),
    shopRoute(`${slug}/serie/:series`, { mode: 'game', game: slug }),
    shopRoute(`${slug}/serie/:series/:setCode`, { mode: 'game', game: slug }),
    shopRoute(`${slug}/:typeSlug`, { mode: 'game', game: slug }),
  ])
}

// Leaves room for the sticky header above the element.
const HASH_SCROLL_OFFSET = 160

/** Menu destinations that have no real page yet. */
function placeholderPages() {
  return navDestinations().map(({ to, labelKey, game }) => placeholder(to.slice(1), labelKey, { game }))
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

        {
          path: 'inloggen',
          component: () => import('./views/LoginView.vue'),
          meta: { guestOnly: true, title: 'auth.login.title', noindex: true },
        },
        {
          path: 'registreren',
          component: () => import('./views/RegisterView.vue'),
          meta: { guestOnly: true, title: 'auth.register.title', noindex: true },
        },
        {
          path: 'wachtwoord-vergeten',
          component: () => import('./views/ForgotPasswordView.vue'),
          meta: { title: 'auth.forgot.title', noindex: true },
        },
        {
          path: 'wachtwoord-herstellen',
          component: () => import('./views/ResetPasswordView.vue'),
          meta: { title: 'auth.reset.title', noindex: true },
        },
        {
          path: 'account',
          component: () => import('./views/AccountView.vue'),
          meta: { requiresLogin: true, title: 'account.title', noindex: true },
        },
        {
          path: 'account/bestellingen',
          name: 'account-orders',
          component: () => import('./views/account/OrdersView.vue'),
          props: withQuery('page'),
          meta: { requiresLogin: true, title: 'orders.list.title', noindex: true },
        },
        {
          path: 'account/tickets',
          component: () => import('./views/account/TicketsView.vue'),
          meta: { requiresLogin: true, title: 'support.mine.title', noindex: true },
        },
        {
          path: 'klantenservice/ticket/:token',
          component: () => import('./views/support/TicketView.vue'),
          props: true,
          meta: { noindex: true },
        },
        {
          path: 'account/bestellingen/:number',
          name: 'account-order',
          component: () => import('./views/account/OrderView.vue'),
          props: true,
          meta: { requiresLogin: true, title: 'orders.list.title', noindex: true },
        },

        {
          path: 'winkelwagen',
          name: 'cart',
          component: () => import('./views/checkout/CartView.vue'),
          meta: { title: 'cart.title', noindex: true },
        },
        {
          path: 'afrekenen',
          name: 'checkout',
          component: () => import('./views/checkout/CheckoutView.vue'),
          meta: { title: 'checkout.title', noindex: true },
        },
        {
          path: 'bestelling/bedankt',
          name: 'order-placed',
          component: () => import('./views/checkout/OrderPlacedView.vue'),
          props: withQuery('order', 'token'),
          meta: { title: 'orders.placed.title', noindex: true },
        },
        {
          path: 'bestelling/geannuleerd',
          name: 'order-cancelled',
          component: () => import('./views/checkout/OrderCancelledView.vue'),
          props: withQuery('order', 'token', 'drop'),
          meta: { title: 'orders.cancelled.title', noindex: true },
        },
        {
          path: 'bestelling/:number',
          name: 'guest-order',
          component: () => import('./views/checkout/GuestOrderView.vue'),
          props: withQuery('token'),
          meta: { noindex: true },
        },

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
          path: 'kaarten/:game/:setCode/nog-niet-in-bezit',
          name: 'set-missing',
          component: () => import('./views/collection/SetMissingView.vue'),
          props: withQuery('sort', 'category', 'color', 'rarity', 'variant', 'priced', 'master', 'page'),
          meta: { requiresLogin: true, title: 'collection.missingList.title', noindex: true },
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
          meta: { title: 'search.title', noindex: true },
        },

        {
          path: 'collectie',
          name: 'collection',
          component: () => import('./views/collection/CollectionView.vue'),
          meta: { title: 'collection.title', description: 'meta.pages.collection' },
        },
        {
          path: 'collectie/pokedex',
          name: 'pokedex',
          component: () => import('./views/collection/PokedexView.vue'),
          props: withQuery('generatie'),
          meta: { title: 'pokedex.title', description: 'meta.pages.pokedex' },
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
          meta: { title: 'collection.title', noindex: true },
        },
        {
          path: 'verlanglijst',
          name: 'wishlist',
          component: () => import('./views/collection/WishlistView.vue'),
          props: withQuery('sort', 'priced', 'collection', 'page'),
          meta: { title: 'wishlist.title', noindex: true },
        },

        {
          path: 'drops',
          name: 'drops',
          component: () => import('./views/drops/DropsView.vue'),
          meta: { title: 'drops.title', description: 'meta.pages.drops' },
        },
        { path: 'drops/:slug', name: 'drop', component: () => import('./views/drops/DropView.vue'), props: true },
        {
          path: 'drops/:slug/afrekenen',
          name: 'drop-checkout',
          component: () => import('./views/checkout/CheckoutView.vue'),
          props: (route) => ({ dropSlug: route.params.slug }),
          meta: { title: 'checkout.title', noindex: true },
        },

        ...gameShopRoutes(),
        shopRoute('accessoires', { mode: 'accessories' }),
        shopRoute('accessoires/:accessorySlug', { mode: 'accessories' }),
        {
          path: 'pre-order',
          name: 'pre-orders',
          component: () => import('./views/shop/PreOrderView.vue'),
          props: withQuery('game'),
          meta: { title: 'preOrders.title', description: 'meta.pages.preOrders' },
        },
        {
          path: 'releasekalender',
          name: 'release-calendar',
          component: () => import('./views/shop/ReleaseCalendarView.vue'),
          props: withQuery('game'),
          meta: { title: 'releases.title', description: 'meta.pages.releases' },
        },
        {
          path: 'nieuws',
          name: 'news',
          component: () => import('./views/content/NewsView.vue'),
          props: withQuery('game', 'page'),
          meta: { title: 'news.title', description: 'meta.pages.news' },
        },
        {
          path: 'nieuws/:slug',
          name: 'news-article',
          component: () => import('./views/content/NewsArticleView.vue'),
          props: true,
        },
        {
          path: 'info/:slug',
          name: 'info',
          component: () => import('./views/content/InfoPageView.vue'),
          props: true,
        },
        {
          path: 'nieuwsbrief/bevestigen',
          component: () => import('./views/content/NewsletterLinkView.vue'),
          props: (route) => ({ mode: 'confirm', ...withQuery('token')(route) }),
          meta: { title: 'newsletter.confirmTitle', noindex: true },
        },
        {
          path: 'nieuwsbrief/afmelden',
          component: () => import('./views/content/NewsletterLinkView.vue'),
          props: (route) => ({ mode: 'unsubscribe', ...withQuery('token')(route) }),
          meta: { title: 'newsletter.unsubscribeTitle', noindex: true },
        },
        shopRoute('producten', { mode: 'all' }),
        {
          path: 'product/:slug',
          name: 'product',
          component: () => import('./views/shop/ProductView.vue'),
          props: true,
        },

        ...placeholderPages(),

        // Anything else: a page that says so, instead of a second copy of the home page.
        { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('./views/NotFoundView.vue') },
      ],
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAdmin: true, title: 'admin.title', noindex: true },
      children: [
        { path: '', component: () => import('./views/admin/AdminDashboardView.vue') },
        {
          path: 'producten',
          component: () => import('./views/admin/AdminProductsView.vue'),
          props: withQuery('q', 'game', 'type', 'status', 'stock', 'source', 'price', 'sort', 'size', 'page'),
        },
        { path: 'producten/nieuw', component: () => import('./views/admin/AdminProductEditView.vue') },
        { path: 'producten/losse-kaart', component: () => import('./views/admin/AdminSingleView.vue') },
        { path: 'producten/import', component: () => import('./views/admin/AdminImportView.vue') },
        {
          path: 'producten/:id(\\d+)',
          component: () => import('./views/admin/AdminProductEditView.vue'),
          props: true,
        },
        { path: 'voorraad', component: () => import('./views/admin/AdminStockView.vue') },
        {
          path: 'bestellingen',
          component: () => import('./views/admin/AdminOrdersView.vue'),
          props: withQuery('q', 'status', 'page'),
        },
        { path: 'bestellingen/:number', component: () => import('./views/admin/AdminOrderView.vue'), props: true },
        { path: 'verzending', component: () => import('./views/admin/AdminShippingView.vue') },
        { path: 'hero', component: () => import('./views/admin/AdminSlidesView.vue') },
        { path: 'kortingscodes', component: () => import('./views/admin/AdminDiscountCodesView.vue') },
        {
          path: 'tickets',
          component: () => import('./views/admin/AdminTicketsView.vue'),
          props: withQuery('status', 'page'),
        },
        { path: 'tickets/:number', component: () => import('./views/admin/AdminTicketView.vue'), props: true },
        {
          path: 'reviews',
          component: () => import('./views/admin/AdminReviewsView.vue'),
          props: withQuery('status', 'page'),
        },
        { path: 'releasekalender', component: () => import('./views/admin/AdminReleasesView.vue') },
        { path: 'nieuws', component: () => import('./views/admin/AdminNewsView.vue') },
        { path: 'paginas', component: () => import('./views/admin/AdminPagesView.vue') },
        {
          path: 'nieuwsbrief',
          component: () => import('./views/admin/AdminNewsletterView.vue'),
          props: withQuery('status', 'page'),
        },
        { path: 'drops', component: () => import('./views/admin/AdminDropsView.vue') },
        { path: 'drops/nieuw', component: () => import('./views/admin/AdminDropEditView.vue') },
        { path: 'drops/:id(\\d+)', component: () => import('./views/admin/AdminDropEditView.vue'), props: true },
        { path: 'drops/:id(\\d+)/live', component: () => import('./views/admin/AdminDropLiveView.vue'), props: true },
        { path: 'sync', component: () => import('./views/admin/AdminSyncView.vue') },
        {
          path: 'afbeeldingen',
          component: () => import('./views/admin/AdminImagesView.vue'),
          props: withQuery('kind', 'q', 'game', 'set', 'image', 'sort', 'size', 'page'),
        },
        { path: 'afbeeldingen/zoeken', component: () => import('./views/admin/AdminPictureSearchView.vue') },
        {
          path: 'prijskoppelingen',
          component: () => import('./views/admin/AdminPriceLinksView.vue'),
          props: withQuery('status', 'page'),
        },
      ],
    },
    {
      // Outside the admin layout, so only the packing slip itself prints.
      path: '/admin/bestellingen/:number/pakbon',
      component: () => import('./views/admin/AdminPackingSlipView.vue'),
      props: true,
      meta: { requiresAdmin: true, title: 'admin.title', noindex: true },
    },
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

router.afterEach((to, from) => {
  if (to.path !== from.path || from.matched.length === 0) setRouteMeta(to)
  else updateCanonical()
})

export default router
