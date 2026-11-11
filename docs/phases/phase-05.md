# Phase 5: Cart, checkout and orders

Customers put products in a cart, pay with iDEAL, Bancontact or a card at Stripe, and follow their order. The admin
handles orders, shipping rates and refunds, and sees revenue on the dashboard.

## Before you try it

1. **Restart the backend on 8080** with `SPRING_PROFILES_ACTIVE=postgres`. The running phase 4 build doesn't know the
   cart, checkout or order endpoints, while Vite on 5173 already shows the new pages. Hibernate adds six tables, and
   startup creates the shipping rates for the Netherlands and Belgium.
2. **Add a Stripe test key** to `.env`: `STRIPE_SECRET_KEY=sk_test_...` from the Stripe dashboard (test mode). Without
   it the shop works, but checkout says online payment isn't set up yet.
3. **Forward webhooks with the Stripe CLI** (see "Testing a payment" below) and put the `whsec_...` it prints in `.env`
   as `STRIPE_WEBHOOK_SECRET`, then restart the backend once more.

## What was built

**Backend**

- `Cart` and `CartItem`: one cart per account, or per guest behind an `HttpOnly` `cart` cookie of which only the hash is
  stored. A guest cart merges into the account cart on login and registration. A cart holds no stock. Quantities stay
  within what can be bought, and a line that sold out can still be lowered or removed. Guest carts that haven't
  changed for 60 days are deleted at night.
- `ShippingRate` per country: price, free-shipping threshold and delivery time. Startup creates NL (€4,95, free from
  €75, 1 tot 2 werkdagen) and BE (€8,95, free from €125, 2 tot 3 werkdagen) when they don't exist. The admin changes
  them at `/admin/verzending`.
- `Order`, `OrderLine` and `OrderStatusChange`. Lines copy the name, price, image and pre-order date at the time of
  purchase. Amounts are euro cents with the VAT in them stored separately. The order copies the profile name of a
  customer, or the name a guest fills in. The status history drives the customer's timeline and the admin's log.
- **Checkout reserves the stock for 15 minutes** (`app.checkout.reservation-ttl`) in one transaction: if one line has
  too few left, nothing is reserved and the customer hears which product changed. Starting checkout again for the same
  cart first closes the earlier attempt, so going back from the payment page never blocks your own stock.
- **Stripe Checkout** in `service/payment/`: `PaymentGateway` with `StripePaymentGateway`, and a fake in the tests.
  iDEAL and card for the Netherlands, Bancontact and card for Belgium. Dutch payment page, the order number in the
  metadata, an idempotency key per order.
- **An order only becomes paid through the webhook** (`POST /api/v1/payments/stripe/webhook`). The signature is checked
  over the raw body, with Stripe's five-minute limit against replays. The shop then asks Stripe for the session itself
  and only marks the order paid when Stripe says `paid`. Every change to an order goes through a row lock, so two
  deliveries of the same event at the same moment sell the stock once. The pages the customer returns to only read.
- **Releasing unpaid checkouts**: a job every minute closes the Stripe page of an order whose reservation ran out, and
  only then gives the stock back. When Stripe says it was paid just before, the stock stays and the webhook converts
  it. The "terug" link on Stripe's page brings the customer to a page that releases the stock right away.
- Mails in HTML and text: order confirmation, payment received, shipped with track & trace, cancelled and refunded. They
  go after the change is saved.
- Guest checkout, and a guest can make an account from the success page for a day after paying, with the order in it.
- Admin: order list with search and status tabs, order detail, status changes, track & trace (sends the mail), cancel
  and refund through Stripe with the stock going back, a printable packing slip, shipping rates, and a dashboard with
  revenue today, this week and this month, orders to ship, the latest orders and low stock.
- **"Populair" and the bestsellers now start with what sold here** in the last 90 days (`app.shop.bestseller-period`),
  then the popular sets of phase 4, then the newest sets.
- The dev seeder adds two paid orders for the demo customer, one to ship with a pre-order in it and one shipped.

**Frontend**

- Cart button in the header with the real count. It opens a cart drawer from the right, which also opens after adding
  a product. The drawer and the cart page show a free-shipping bar with a Poké Ball rolling towards "gratis
  verzending".
- Cart buttons work on product tiles and the product page, with a quantity on the product page.
- `/winkelwagen`: lines with quantities, a country choice for the shipping estimate, totals with VAT, payment methods
  for the country.
- `/afrekenen`: four numbered steps (details, address, shipping, check and pay). A logged-in customer picks a saved
  address or enters one and can save it. An order with both a pre-order and stock asks whether to ship together or send
  what is in stock first. Terms checkbox, then off to Stripe.
- `/bestelling/bedankt`: asks every two seconds until the webhook marked the order paid, then thanks the customer and
  takes the bought products out of the cart. `/bestelling/geannuleerd`: releases the stock. `/bestelling/{nummer}`: a
  guest's order from the link in the mail.
- `/account/bestellingen` and `/account/bestellingen/{nummer}`: order history and detail with the status timeline.
- Admin: `/admin/bestellingen`, the order detail with actions, `/admin/bestellingen/{nummer}/pakbon`, `/admin/verzending`
  and the new dashboard.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/Cart.kt` (with `CartItem`), `ShippingRate.kt`, `Order.kt`, `OrderLine.kt`, `OrderStatusChange.kt`, `OrderStatus.kt` (with `OrderStockState`, `ShipmentPreference`, `Carrier`) |
| Logic | `service/OrderMath.kt` (totals, free shipping, VAT), `OrderRules.kt` (status changes, order numbers, tracking links, payment methods), `CartService.kt` (with `CartRules`), `CheckoutService.kt`, `OrderPaymentService.kt`, `OrderService.kt`, `OrderMailService.kt` (with `OrderMails`), `CheckoutJobs.kt`, `ShippingRateService.kt`, `StockService.sellReserved` |
| Stripe | `service/payment/PaymentGateway.kt`, `StripePaymentGateway.kt`, `StripeWebhookVerifier.kt` |
| API | `controller/CartController.kt`, `CheckoutController.kt`, `OrderController.kt` (with `AccountOrderController`), `PaymentWebhookController.kt`, `AdminOrderController.kt` and their orchestrators |
| Popular | `repository/ProductSpecifications.kt` (`sold`), `ProductOrchestrator.kt` |
| Migration | `db/migration/V7__cart_checkout_and_orders.sql` |
| Pages | `views/checkout/` (`CartView`, `CheckoutView`, `OrderPlacedView`, `OrderCancelledView`, `GuestOrderView`), `views/account/` (`OrdersView`, `OrderView`), `views/admin/AdminOrdersView.vue`, `AdminOrderView.vue`, `AdminPackingSlipView.vue`, `AdminShippingView.vue`, `AdminDashboardView.vue` |
| Building blocks | `components/cart/` (`CartDrawer`, `CartLine`, `CartTotals`, `FreeShippingBar`), `components/order/` (`OrderDetails`, `OrderTimeline`, `OrderStatusBadge`), `stores/cart.js` |
| Tests | `integration/CheckoutIntegrationTest.kt` (27 tests), `FakePaymentGateway.kt`, unit tests `OrderMathTest`, `OrderRulesTest`, `CartRulesTest`, `OrderMailsTest`, `StripeWebhookVerifierTest`, `StockServiceTest` |

## New environment variables

In `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `STRIPE_SECRET_KEY` | Stripe's secret key. `sk_test_...` locally. Empty switches online payment off | empty |
| `STRIPE_WEBHOOK_SECRET` | Proves a webhook comes from Stripe. From `stripe listen` locally, from the webhook endpoint in production | empty |

New settings in `application.yml`, without environment variables: `app.checkout.reservation-ttl` (15m),
`release-cron` (every minute), `guest-cart-ttl` (60d), `cart-cleanup-cron`, `tracking-urls`, `default-rates`, and
`app.shop.bestseller-period` (90d).

## Testing a payment

```sh
brew install stripe/stripe-cli/stripe
stripe login
stripe listen --forward-to localhost:8080/api/v1/payments/stripe/webhook
```

Put the `whsec_...` that `stripe listen` prints in `.env` as `STRIPE_WEBHOOK_SECRET` and restart the backend. Order
something, and pay on Stripe's test page with card `4242 4242 4242 4242`, any future date and any CVC, or choose iDEAL
and "Authorize". The order turns paid within a few seconds, and the confirmation is in Mailpit at `localhost:8025`.

When Homebrew refuses because the Command Line Tools are too old, download `stripe_<version>_mac-os_arm64.tar.gz` from
[Stripe's releases on GitHub](https://github.com/stripe/stripe-cli/releases), check it against
`stripe-mac-checksums.txt`, and put the `stripe` file in a folder on your `PATH`, such as `~/.local/bin`.

The CLI login and the key in `.env` must belong to the same Stripe environment (test mode or a sandbox), or the webhooks
never arrive. `stripe listen --api-key <the key from .env>` makes sure of that.

**Managed Payments must be off.** Newer Stripe accounts have it on by default (Settings > Managed payments). It makes
Stripe the seller, is meant for digital products, and refuses the payment methods the shop asks for: checkout then says
"We konden de betaalpagina niet openen" (502), and the backend log shows "Unsupported parameter:
payment_method_types". Switching it off needs no restart.

## Checks

Checks with real data ran on throwaway databases and on ports 8095 to 8097 with a second Vite on 5174. All of them were
dropped or stopped afterwards. The backend on 8080 and Vite on 5173 were left alone.

| Check | Result |
|-------|--------|
| `./gradlew test` | 617 tests, 0 failed (see the note on the sync test below) |
| `npm run build` | Passes |
| Done when: a full test payment with the Stripe CLI and the mail in Mailpit | Done on 2026-11-11 with the owner, in a Stripe sandbox with `stripe listen`. Two card payments, for one and for two Elite Trainer Boxes: each webhook got 200, the order turned paid within a second, the stock went from reserved to sold (3 to 0 in the stock log), and the confirmation mail was in Mailpit. Processing, shipping and delivery afterwards worked, with the shipping mail. The first five tries got 502 because Managed Payments was on in the account (see "Testing a payment"); each released its stock right away. An expired session the shop didn't know got 200 and changed nothing |
| Done when: an abandoned checkout releases its stock | Tests: the job leaves the reservation alone before 15 minutes, then closes the Stripe page and gives the stock back. A checkout that Stripe reports as paid keeps its stock, and the webhook then sells it. Going back from Stripe releases it at once |
| Done when: refreshing the success page creates nothing twice | Tests: the success page only reads. The same webhook three times, also as `async_payment_succeeded`, sells the stock once and sends one mail. Two deliveries of the same event at the same moment sell it once |
| Webhook security | A wrong secret, no signature, an old timestamp or a changed body gets 400 and changes nothing. An event for a session that isn't paid changes nothing either |
| Checkout and stock tests on Postgres | 27 checkout tests and the 4 stock race tests pass on a fresh Postgres database |
| `prod` on an empty Postgres database | Flyway applies V1 to V7, Hibernate validation passes, the shipping rates are created. The cart, popular sorting, the dashboard, the order list and saving a rate work. Checkout answers that payment isn't set up, as it should without a key |
| Copy of the real database | Hibernate adds the six tables. "Populair" still starts with 30th Celebration, because there are no orders yet, and takes about 20 ms after the first call, like the default order |
| Browser, `dev` profile | 20 checks pass: adding from a tile and from the product page, the drawer and Escape, cart page, shipping per country with Bancontact for Belgium, checkout without a key, guest cart merged on login, order history and detail, success and cancel pages, dashboard, order list and status filter, processing and shipping with the mail in Mailpit, packing slip, shipping rates |
| Browser at 360, 768 and 1280px | 8 pages and the drawer at each width: no horizontal scroll. The only console messages are the 401 of "who am I" for visitors who aren't logged in, which every page has had since phase 1 |

Checks and screenshots found these bugs, all fixed:

- Reading an order right after an admin action ran outside a transaction, because the call stayed inside one class.
  The same for recording a cancel or refund and holding a paid reservation a little longer.
- The dev seeder read product photos outside a transaction and stopped the startup.
- Every status tab on the admin order list said it was the current page, because a router link ignores the query.
- On a wide screen the checkout summary stretched to the bottom of the page and couldn't stay in view. On a phone it
  came after the pay button.
- Product thumbnails in the cart and order lists cut off the kind of product. They show only the icon now.
- The admin order page squeezed the order into two narrow columns next to the actions.

**The sync test:** `CatalogSyncIntegrationTest` failed once in the full suite: it waited at most five seconds for a
sync in the background, which took longer while the whole suite ran. It passed on its own both before and after. It
now waits up to 15 seconds with Awaitility.

## Decisions

- **The order is made when checkout starts,** as "wacht op betaling", with the stock reserved. Paid only comes from the
  webhook, so the success page never changes anything and refreshing it is harmless.
- **15 minutes for the stock, 31 for Stripe's page.** Stripe's payment page lasts at least 30 minutes. When the 15
  minutes run out the shop closes the page at Stripe first and only then lets go of the stock, so nobody can pay for
  stock that went to someone else. Should Stripe still report a payment for an order whose stock went back, the shop
  sells it if the stock is still there, and otherwise pays it back automatically.
- **One mail for an iDEAL, Bancontact or card payment.** All three report at once whether the payment succeeded, so the
  order confirmation says the payment came in. The separate "betaling ontvangen" mail is only for a payment that comes
  in after the checkout finished, which none of these three methods do. Abandoned checkouts get no mail.
- **The webhook trusts Stripe, not the event.** After checking the signature it reads only the type and the session id,
  and asks Stripe for the session. This also makes the webhook independent of the API version of the event: stripe-java
  29.2.0 speaks `2025-05-28.basil`.
- **Cart merge adds up**, up to what can be bought, and never lowers what the account cart had.
- **Shipping costs follow the address.** The cart estimates with the region setting, the checkout uses the country of
  the chosen address without changing the profile.
- **Sending what is in stock first costs nothing extra.** The owner may want a second shipping fee for the pre-order
  part. That is a small change in `OrderMath`.
- **A paid order is cancelled in full** and its stock goes back. After shipping the admin refunds an amount instead,
  and chooses whether the products go back on the stock, once.
- **Track & trace links:** PostNL's link is built from the code, the postcode and the country, in the format most Dutch
  shop plugins use. DHL, bpost and DPD have no deep link we could confirm, so the admin pastes the link from the
  shipping tool. Only `https` links go in a mail.
- **Order numbers are random,** such as `UT-7K3M9Q`, so they say nothing about how much the shop sells.
- **The key of a guest order is stored as it is,** not hashed. The order behind it sits in the same row, so a hash
  protects nothing, and the mails need the key to link to the order.
- **The customer's account only shows paid orders,** not checkouts that were given up. The admin sees those under
  "Niet betaald".
- **Shipping rates are created at startup when missing,** also in `prod`. Without a rate nobody can order, and this is
  configuration, not demo data.
- **"Populair" counts the last 90 days of sales,** so a set that sold well a year ago doesn't stay on top.
- **The customer-facing name is "iDEAL | Wero",** as Stripe asks shops to do from 2026.

## Source notes: Stripe, checked on 2026-11-09

- `docs.stripe.com/checkout/fulfillment`: fulfil from `checkout.session.completed` and
  `checkout.session.async_payment_succeeded`, safe to run twice or at the same time, retrieve the session and check
  `payment_status`. Webhooks are required because a customer can pay and never come back.
- `docs.stripe.com/api/checkout/sessions/create`: `expires_at` is 30 minutes to 24 hours after creation. `locale: nl`,
  `payment_method_types` with `ideal`, `bancontact` and `card`.
- `docs.stripe.com/payments/ideal`: "iDEAL | Wero", immediate notification, EUR, refunds up to 180 days. **Dutch shops
  must show their KVK number on the website** under the iDEAL scheme rules. iDEAL moves to Wero during 2026 and 2027.
- `docs.stripe.com/payments/bancontact`: immediate, EUR, minimum €0,50, refunds up to two years and paid out as a bank
  transfer, so the customer should hear about it. The refund mail does that.

## Open issues

- **The end-to-end payment with the Stripe CLI still has to be done** with the owner's test key, see "Before you try it".
- **Legal check additions:** the KVK number on the website (iDEAL rule), the terms the checkbox links to
  (`/info/algemene-voorwaarden` is still a placeholder, phase 7), what Stripe processes in the privacy policy, and the
  14-day withdrawal right for orders. On the go-live list in the build prompt.
- **Wero:** Stripe asks shops to switch from iDEAL to Wero during 2026 and 2027. stripe-java 29.2.0 doesn't know `wero`
  as a payment method yet. Needs a library update when the switch comes.
- **Refunds made in the Stripe dashboard** aren't seen by the shop. Refund from the order page in admin.
- **Production webhook:** add an endpoint in the Stripe dashboard for `checkout.session.completed`,
  `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`
  pointing at `https://<domein>/api/v1/payments/stripe/webhook`, and use its signing secret.
- **The 401 for "who am I"** shows in the browser console for visitors who aren't logged in, on every page since phase
  1. Harmless. Phase 8 could answer 204 instead.

## Notes for later phases

- **Phase 6:** `StockService.reserve` and `sellReserved` are what a drop checkout needs. Limits per account and product
  can count `OrderLine` rows of paid orders. A drop order can use the same checkout with its own prices, and the access
  token of the waiting room fits next to the cart.
- **Phase 7:** discount codes and bundles change the lines and totals in `OrderMath` and the Stripe line items in
  `CheckoutOrchestrator.paymentRequest`. Reviews can check `OrderLine` for "bought this product". The terms checkbox
  links to `/info/algemene-voorwaarden`.
- **Phase 8:** the KVK number in the footer, the production webhook, and the 401 of "who am I".
