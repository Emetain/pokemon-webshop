# Phase 6: Drops

Limited releases at a set time, with a waiting room that puts everyone who came on time in a random order, lets people
in in batches, and holds every limit on the server. The admin sets drops up, steers them while they run, and reads the
report afterwards.

## Before you try it

1. **Restart the backend on 8080** with `SPRING_PROFILES_ACTIVE=postgres`. A backend from before this phase doesn't
   know the drop endpoints, while Vite already shows the new pages. Hibernate adds five tables (`drops`,
   `drop_products`, `drop_queue_entries`, `drop_cart_items`, `drop_reminders`) and four columns to `orders`.
2. Make a drop at `/admin/drops`: add a product with stock, allocate some, set a start a few minutes ahead and publish.
   Open `/drops/<webadres>` in a second browser as a customer and go into the waiting room.
3. Paying needs the Stripe test key from phase 5. Without it, everything up to the payment works and the checkout says
   online payment isn't set up yet.

## What was built

**Backend**

- `Drop`: name, slug, description, banner, start and optional end, status (`DRAFT`, `SCHEDULED`, `LIVE`, `ENDED`),
  account required (on by default), a limit for the whole drop, what happens to the leftover (`RETURN_TO_STOCK` or
  `KEEP`), a hero slide on or off, and the speed of the waiting room.
- `DropProduct`: the product, how many are allocated, held and sold, a limit per customer and an optional drop price.
  **Allocating moves stock out of the normal stock** with a `DROP_ALLOCATION` movement. Less allocation, the leftover
  at the end and stock released after that go back the same way, with a plus. A sale in the drop is counted in the
  drop: the allocation already took it off the shelf.
- **The scheduler** (`DropJobs`, every second) starts drops at their start time, lets the next batch through, ends
  drops at their end time or when everything is sold, and sends the reminders.
- **Waiting room** (`DropQueueService`): it opens 30 minutes before the start. At the start everyone in it gets a
  random place from `SecureRandom`, later arrivals the next place at the back. Every 10 seconds the shop moves a
  "through up to place N" mark forward by as many people as the speed allows (60 a minute by default, exact also at
  slow speeds). Only people whose page asked in the last 75 seconds count towards a batch, so places of people who left
  don't use it up. They keep their place and get in when they come back.
- **Access token**: once your place is through, the next ping hands out a random token in an `HttpOnly` cookie on the
  path of that drop only, valid for 10 minutes. The drop cart and checkout need it, together with the same account (or
  the `drop_visitor` cookie for a drop without account). When the time runs out you join at the back again, and the
  drop cart stays.
- **Drop cart and checkout**: the drop cart holds no stock, like the shop cart. Starting the payment holds the drop
  stock for 10 minutes (`app.drops.reservation-ttl`) and reuses the phase 5 checkout: Stripe, the webhook, the
  confirmation mail, the success and cancel pages. A paid drop order sells from the drop. One that is paid after its
  reservation ran out is always refunded, because that stock already went to the next person in line.
- **Limits** per product and for the whole drop count paid orders and checkouts that hold stock, by account, email key,
  address key and waiting room entry together. The email key drops `+tags`, and for Gmail the dots. The address key is
  country, postcode and house number written one way, plus the street in Belgium. The limits are checked when
  something goes in the drop cart, and again under the lock on the drop when the payment starts, with everyone else's
  checkouts already counted.
- **One lock per drop** (`DropLocks`): joining, starting, each batch, checkouts, payments and releases of a drop all
  take a row lock on it first, and read the row again once they have it. So two buyers can never both take the last
  item or both pass a limit.
- **Bot protection**: account required by default. Rate limits per IP address for every drop endpoint, and per account
  for joining, pinging, the cart and the checkout (`app.rate-limit.buckets`). A hidden `website` field on joining, the
  reminder and the checkout turns away bots that fill in every field. Repeat orders are blocked by the limits above.
- **"Herinner mij"**: one mail 15 minutes before the start (`app.drops.reminder-before`), on the account's address or an
  address a guest fills in.
- **Hero**: every upcoming and live drop with the slide switched on gets a slide by itself, first in the carousel, live
  drops before upcoming ones. It goes away when the drop ends. Its background is a colour chosen per drop, black by
  default or red, yellow or green, each with a slight gradient, so it stands apart from the blue hero of the shop. It
  shows the banner when the drop has one, and otherwise the main pictures of up to three of its products, fanned out.
  The colour came with `V9__drop_hero_colour.sql`, after the owner saw the first version.
- **Product page**: a product in an upcoming or live drop says so and links to the drop, so a product whose stock all
  went to the drop doesn't just look sold out.
- **Report**: queue (joined, in the waiting room at the start, still waiting, through, shopping now), and per product
  allocated, held, sold, left, revenue and how long it took to sell out, with the average. Also orders, buyers (by
  email key), cancelled or refunded orders and reminder signups.
- The dev seeder adds one upcoming drop two hours after startup, with a product only sold in the drop and two booster
  boxes from the normal stock.

**Frontend**

- `/drops`: live, upcoming with a countdown, and the last 12 drops that ended.
- `/drops/{slug}`: banner, status, countdown in blocks, the products with drop price, limit and a "nog 12 over" bar
  that refreshes every 5 seconds, "Zo werkt het", and the waiting room panel. That panel shows each step: log in, the
  waiting room opening time with "Herinner mij", joining, the waiting room with how many wait, your place with how many
  are before you and an estimate, your turn with the time left and the drop cart, your time ran out, and the end. The
  page asks again when the shop says to, sooner close to the start or the front of the line, with a little randomness
  so a full waiting room doesn't ask all at once. Countdowns use the server's clock.
- `/drops/{slug}/afrekenen`: the checkout from phase 5 in drop mode, with the time left to start the payment.
- The hero slide of a drop has its countdown and turns into "Nu live". A "LIVE" badge next to Drops in the menu and in
  the mobile menu while a drop runs.
- Admin: `/admin/drops`, the edit page (details, banner, products with allocation, limit and drop price, publish, back
  to draft, delete a draft, preview), and `/admin/drops/{id}/live` with the live numbers, speed, pause and "end now",
  which is the report once the drop ended.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/Drop.kt`, `DropProduct.kt`, `DropQueueEntry.kt` (with `DropCartItem`), `DropReminder.kt`, `DropStatus.kt` (with `DropLeftover`), `Order.kt` (drop, entry, email and address key) |
| Logic | `service/DropRules.kt` (shuffle, batches, waits, limits, email and address keys), `DropQueueService.kt`, `DropStockService.kt`, `DropCartService.kt`, `DropCheckoutService.kt`, `DropService.kt`, `DropReminderService.kt` (with `DropMails`), `DropJobs.kt`, `DropLocks.kt`, `DropCookieService.kt`, `StockService.allocateToDrop` and `returnFromDrop`, `RateLimitService` (buckets and per account) |
| Shared with phase 5 | `CheckoutService.party` and `newOrder`, `OrderPaymentService.markPaid` and `CheckoutService.releaseReservation` know drop orders, `CartOrchestrator.dto(lines)`, `CheckoutOrchestrator.paymentRequest` |
| API | `controller/DropController.kt`, `AdminDropController.kt`, `orchestration/DropOrchestrator.kt`, `DropShopOrchestrator.kt`, `AdminDropOrchestrator.kt`, `HeroSlideOrchestrator.kt`, `ProductOrchestrator.kt` |
| Migration | `db/migration/V8__drops.sql` |
| Pages | `views/drops/DropsView.vue`, `DropView.vue`, `views/checkout/CheckoutView.vue` (drop mode), `views/admin/AdminDropsView.vue`, `AdminDropEditView.vue`, `AdminDropLiveView.vue` |
| Building blocks | `components/drops/` (`DropCard`, `DropCountdown`, `DropProductCard`, `DropQueuePanel`, `DropReminderForm`, `LiveBadge`), `stores/drops.js`, `components/shop/HeroCarousel.vue` |
| Tests | `integration/DropIntegrationTest.kt` (17), `DropLimitsIntegrationTest.kt` (8), `DropConcurrencyIntegrationTest.kt` (2), `DropTestSupport.kt`, unit tests `DropRulesTest`, `DropMailsTest`, `RateLimitServiceTest` |

## New environment variables

None. The settings are under `app.drops` in `application.yml`: `tick-cron` (every second), `admit-per-minute` (60 for
a new drop), `admit-interval` (10s), `access-ttl` (10m), `reservation-ttl` (10m), `active-window` (75s),
`waiting-room-opens` (30m) and `reminder-before` (15m). The drop limits per IP and per account are under
`app.rate-limit.buckets`.

## Checks

Checks with a running app used throwaway databases and ports 8095 to 8097 with a second Vite on 5174. All of them were
dropped or stopped afterwards. Nothing was running on 8080 or 5173, and the real database was only read, to copy it.

| Check | Result |
|-------|--------|
| `./gradlew test` | 657 tests, 0 failed |
| `npm run build` | Passes |
| Done when: a drop two minutes ahead goes live by itself, the countdown matches, the waiting room lets people in in batches | Run three times on the `prod` profile on Postgres, with the real scheduler and 16 people in the waiting room (15 through the API, one in Chromium). The drop went live 0.14, 0.91 and 0.57 seconds after its start time. The countdown in the browser was 0 to 1 second from the real time. At 12 a minute, people got through in twos about every 10 seconds (0.6, 10.1, 21.6 … seconds), and all 15 after 71 seconds. The places after the shuffle didn't follow the order people joined, such as 7, 13, 1, 6, 11 |
| Done when: limits can't be bypassed by calling the API | `DropLimitsIntegrationTest`: no cart or checkout before your turn, with someone else's token, logged out or after your time ran out. Past the limit per product or per drop. After buying the limit, also after getting back in line. A second account with the same address written differently ("12 A", "1421 AB"). A guest with the same Gmail with dots, a `+tag` or `googlemail.com`. Two checkouts of one person at the same moment. A product of another drop. The honeypot. Buying allocated stock through the normal cart |
| Done when: many simulated buyers never sell more than allocated | 40 buyers start the payment at the same moment for 7 allocated: exactly 7 get a checkout, 33 hear it's gone, and paying all 7 at the same moment sells 7. 12 buyers adding and checking out at once for 5 allocated hold at most 5. On H2 and on Postgres |
| Drop, checkout and stock tests on Postgres | The 58 tests of the drops, the checkout and the stock races pass on a fresh Postgres database |
| `prod` on an empty Postgres database | Flyway applies V1 to V8, Hibernate validation passes |
| Copy of the real database, `postgres` profile | Hibernate adds the five tables and four columns. Allocating 3 of a real product writes `DROP_ALLOCATION -3` to the stock log, the product page shows the drop, and deleting the draft gives the stock back |
| `dev` profile | The seeder makes the "Prismatic Evolutions drop" two hours ahead, on the quarter hour, and it is the first hero slide |
| Browser | Countdown, waiting room, place in line, the turn with the time left, + stopping at the limit, the drop checkout, the hero slide with countdown, the LIVE badge, `/drops`, and the three admin pages, also saving a live drop. 360, 768 and 1280 px: no horizontal scroll. The only console message is the 401 of "who am I" for visitors who aren't logged in |

Review, tests and screenshots found these bugs, all fixed:

- Joining read the drop by its slug before locking it in the same transaction, so the lock gave the numbers from
  before, such as the next free place. `DropLocks` reads the row again once it holds the lock.
- The admin orchestrator read a drop after a change by calling itself, which skips the transaction.
- The shuffle only took people who joined before the start time. A join after the start time starts the drop first,
  so everyone without a place when it starts was in the waiting room: they all count now.
- The live dot of a drop slide had the class of the carousel's dots, and would have restyled them.
- Saving a live drop from admin sent its start time rounded to the minute, which the shop refuses as a change.

## Decisions

- **A drop has its own stock.** The allocation takes it off the normal stock at once, also for a draft, so it can't be
  sold twice. Stock that isn't allocated stays in the shop, also during the drop: allocate everything to sell only in
  the drop.
- **Going back uses `DROP_ALLOCATION` with a plus**, not a new reason. Hibernate's `update` doesn't widen the check on
  the reasons of `stock_movements` in an existing database, so a new value would fail there.
- **One lock per drop** keeps every check exact. It is held for milliseconds, which is plenty for a shop this size.
  Polling never takes it.
- **The limits are checked when something goes in the drop cart and when the payment starts.** The prompt says "at
  payment": the check runs under the lock right before the stock is held, with every running checkout counted, so the
  webhook has nothing left to check. A payment that comes after the reservation ran out is refunded.
- **Account, email, address and waiting room entry count together.** Any of them matching counts towards the same
  limit. Two households in one building with their own house numbers are two addresses.
- **The reminder comes 15 minutes before the start, not at the start.** At the start the order is random among
  everyone already waiting, so a mail at the start would always be too late.
- **People who left are passed over, not removed.** They keep their place and get in once they're back. 75 seconds
  covers a browser tab in the background, which runs its timers only once a minute.
- **A drop ends by itself** at its end time, or when everything is sold and nothing is held. With leftover `KEEP`, the
  admin gives it back from the report page.
- **A paid drop order that is cancelled** goes back to the normal stock, like any order. The report counts it under
  cancelled or refunded.
- **A drop can sell a draft product**, a product only for the drop. Its name links to the product page only when the
  product is active.
- **Prices, limits, the start time, the account rule and the products are fixed once the drop is live.** The
  allocation, the end time, the speed, the text and the hero slide can still change.
- **Per IP the limits are generous** (300 pings a minute), because a phone network can put many people behind one
  address. Per account they are tight.

## Open issues

- **The privacy policy for the legal check:** reminder signups, the waiting room (account or visitor cookie, when you
  joined and were last seen) and the email and address keys that drop orders keep for the limits. The `drop_visitor`
  and `drop_access` cookies are functional, for the cookie notice of phase 7.
- **The rate limits and the waiting room run in one backend.** The limiter counts in memory, so a second backend would
  count separately. The locks are in the database and stay safe with two.
- **A real load test** belongs on the server of phase 9. The checks here had 16 people in the waiting room and 40
  buyers at once in the tests.
- **The Stripe test payment of phase 5** is still to do with the owner's test key, also for a drop order.

## Notes for later phases

- **Phase 7:** discount codes "never on drop prices unless enabled": drop orders have `Order.drop`, and their lines are
  priced in `DropCheckoutService`. Bundles and tiered prices don't apply there. Drop slides come before the AI slides
  and the release calendar slides in the hero.
- **Phase 8:** an Open Graph image for drops can use the banner. `FORWARD_HEADERS_STRATEGY=native` matters for drops
  too, or every visitor shares the proxy's address for the limits.
- **Phase 9:** run one backend, or move the rate limiter to the database or Redis first.
