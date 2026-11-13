# Phase 8: Extras

The features that make the big Dutch TCG shops feel complete, plus what the owner added while it was built: Riftbound
as a fifth game, mystery boxes, customer service with a chatbot and tickets, pictures from TCGplayer for everything
without one, release calendar suggestions, and changes to drops after a test with a friend.

## Before you try it

1. **Restart the backend on 8080** with `SPRING_PROFILES_ACTIVE=postgres`. A backend from before this phase doesn't
   know the new endpoints, while Vite already shows the new pages. Hibernate adds the new tables and columns; a
   startup fix (`LocalEnumConstraintFix`) drops the old database rules that only allowed four games.
2. **Info pages:** the contact and privacy pages in your database keep their old text. Load the new default with the
   reset button at `/admin/paginas`, or edit them.
3. **Pictures:** `/admin/afbeeldingen/zoeken`, one click per game. Sure matches are filled in, the rest waits below.
4. **Chatbot:** needs `OPENAI_API_KEY` in `.env`. Without it the contact page goes straight from the FAQ to a ticket.
5. **Riftbound:** start a sync at `/admin/sync`, then import its products at `/admin/producten/import`.
6. Ticket mails go to Mailpit (http://localhost:8025) until the shop has a domain and a mailbox.

## What was built

**Kept from the plan**

- **Discount codes** (`/admin/kortingscodes`): percentage or amount, a minimum order, from and to dates, a maximum
  number of uses, once per customer, only for some games or product types, and drops only when "ook op drops" is on.
  Products only, never shipping; free shipping goes by the subtotal after the discount. Stripe gets a one-off coupon,
  so its page shows the same total. Uses are counted under a lock on the code.
- **Pre-order page** (`/pre-order`) and **release calendar** (`/releasekalender`), managed at
  `/admin/releasekalender`. A release links to its pre-orders and gets a hero slide in one click.
- **Hero slides written by AI** (`/admin/hero`): a prompt gives a title, text, button and optionally a picture, as a
  slide that is switched off until the admin approves it. OpenAI's Responses API with a JSON schema and `store:
  false`, `gpt-6.1-terra`, pictures with `gpt-image-2.6-flare`. Every call's cost comes from the tokens OpenAI reports;
  the shop stops at `OPENAI_MONTHLY_LIMIT_USD`. The picture prompt forbids characters, logos and card art.
- **News** (`/nieuws`, on the home page), the **newsletter** with double opt-in and a CSV export, **reviews** from
  buyers that an admin approves, **info pages** editable in admin (the legal ones as drafts), and a **cookie notice**
  for functional cookies only.

**Left out by the owner:** bundles, case deals and quantity discounts, and "kaarten verkopen".

**Added by the owner**

- **Riftbound** as a full fifth game: catalog, collection and shop. Data and prices from TCGplayer through
  tcgcsv.com; products from Cardmarket's list.
- **Mystery boxes**: the owner packs a batch by hand and enters it in admin. The product page shows the chance of
  every kind of box, from the boxes that are left. At payment the shop draws a random remaining box from the oldest
  batch, and the packing list says which one to send. The customer sees it when the parcel ships.
- **Drops, after a test with a friend** (2026-11-13):
  - Both countdowns on a drop page now show the same second: one shared clock (`stores/serverClock.js`) set from the
    quickest of the last 8 replies, ticking on the shop's whole seconds. The checkout's access timer was frozen and
    counts down now.
  - **Customers never see how many are left, anywhere**: not on the drop page, not in the shop ("Laatste stuks",
    "Bijna uitverkocht"), not in an API reply, not in an error message. Mystery boxes show chances only. The server
    still refuses more than the stock.
  - A drop's prices show only once it is your turn: they come with the drop cart, not with the public drop.
- **Customer service** (`/info/contact`): first FAQ links, then a chatbot, then a ticket.
  - The **chatbot** answers only from the published info pages and shipping costs, on `gpt-6-luna` ($0.10 / $0.50
    per million tokens; a real answer cost $0.0002) with its own limit `OPENAI_CHAT_MONTHLY_LIMIT_USD`. It never
    mentions stock or prices and suggests a ticket for anything about an order. The conversation stays in the
    browser. A chat button on every shop page opens it too.
  - **Tickets** get a number (`T-7K3M9Q`) and a secret link. The shop gets each new ticket and every reply by mail;
    the customer gets a confirmation and every answer. Customers with an account see them under "Mijn tickets". The
    shop answers at `/admin/tickets`, open ones first.
- **Pictures from TCGplayer** (`/admin/afbeeldingen/zoeken`): a search per game for cards and sealed products
  without a picture. Sure matches get it right away, doubtful ones show up to 4 candidates for the admin. A card's
  found picture comes after the source's own and after an upload, and the sync leaves it alone.
- **Release calendar suggestions**: upcoming sets from the card catalog and TCGplayer, refreshed every night. "Toevoegen"
  opens the form filled in, "Negeren" removes one for good.
- **"Kies je spel"** on the home page shows the five games only.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/DiscountCode.kt`, `MysteryBox.kt`, `ProductReview.kt`, `SetRelease.kt`, `Content.kt` (news, info pages, newsletter), `AiUsage.kt`, `SupportTicket.kt`, `PictureSuggestion.kt`, `ReleaseSuggestion.kt`, `Card.kt` (found picture), `HeroSlide.kt` (AI) |
| Logic | `service/DiscountRules.kt`, `DiscountCodeService.kt`, `MysteryBoxService.kt`, `ReviewService.kt`, `ReleaseService.kt`, `ReleaseSuggestionService.kt`, `NewsService.kt`, `NewsletterService.kt`, `InfoPageService.kt` (with `InfoPageDefaults`), `SupportTicketService.kt`, `ai/OpenAiClient.kt`, `ai/AiSlideService.kt`, `ai/SupportChatService.kt`, `pictures/PictureMatching.kt`, `pictures/PictureSearchService.kt`, `pictures/TcgplayerCatalog.kt`, `provider/RiftboundProvider.kt`, `RiftboundMapper.kt` |
| API | `controller/AdminDiscountCodeController.kt`, `AdminMysteryBoxController.kt`, `ContentController.kt`, `ReleaseController.kt`, `ReviewController.kt`, `SupportController.kt`, `AdminPictureController.kt` (with release suggestions), `orchestration/SupportOrchestrator.kt` and the others of the same names |
| Migrations | `db/migration/V10__extras.sql`, `V11__support_tickets.sql`, `V12__picture_and_release_suggestions.sql` |
| Pages | `views/content/` (news, info pages, newsletter links), `views/shop/PreOrderView.vue`, `ReleaseCalendarView.vue`, `views/support/TicketView.vue`, `views/account/TicketsView.vue`, `views/admin/AdminDiscountCodesView.vue`, `AdminReleasesView.vue`, `AdminNewsView.vue`, `AdminPagesView.vue`, `AdminNewsletterView.vue`, `AdminReviewsView.vue`, `AdminTicketsView.vue`, `AdminTicketView.vue`, `AdminPictureSearchView.vue` |
| Building blocks | `components/support/` (chat, chat button, ticket form and thread), `components/shop/MysteryBoxOdds.vue`, `components/reviews/`, `components/content/`, `components/layout/CookieNotice.vue`, `stores/serverClock.js`, `stores/supportChat.js` |
| Tests | `integration/ExtrasIntegrationTest.kt`, `SupportIntegrationTest.kt`, `PictureSearchIntegrationTest.kt`, `FakeAiClient.kt`, `FakeTcgplayerCatalog.kt`, unit tests `DiscountRulesTest`, `MysteryBoxMathTest`, `AiCostTest`, `RiftboundMapperTest`, `PictureMatchingTest`, `SupportRulesTest` |

## New environment variables

| Variable | Default | What it does |
|----------|---------|--------------|
| `SHOP_EMAIL` | empty (falls back to `MAIL_FROM`) | Where new tickets and customer replies are mailed. The shared shop mailbox once there is a domain |
| `OPENAI_API_KEY` | empty | Hero slides by AI and the chatbot. Empty switches both off |
| `OPENAI_MONTHLY_LIMIT_USD` | 5 | What hero slides may cost per month |
| `OPENAI_TEXT_MODEL`, `OPENAI_IMAGE_MODEL` | `gpt-6.1-terra`, `gpt-image-2.6-flare` | Models for hero slides |
| `OPENAI_CHAT_MODEL` | `gpt-6-luna` | The chatbot's model |
| `OPENAI_CHAT_MONTHLY_LIMIT_USD` | 3 | What the chatbot may cost per month |
| `TCGCSV_BASE_URL` | `https://tcgcsv.com` | TCGplayer's catalog, for Riftbound, the picture search and release suggestions |
| `RELEASE_SUGGESTION_CRON` | `0 45 4 * * *` | When the release suggestions refresh, Dutch time. `-` switches it off |

They are in `.env.example`, both compose files and `deploy/server/env.example`. On the server, add
`OPENAI_API_KEY` (and a limit in OpenAI's dashboard) to `/opt/poketopia/.env` when the owner wants the chatbot online.

## Checks

| Check | Result |
|-------|--------|
| `./gradlew test` | 952 tests, 0 failed (2026-11-14). An earlier run failed 2 ticket tests because tests read the developer's `.env` (an empty `SHOP_EMAIL`); the test settings now pin those values |
| `npm run build` | Passes |
| Upgrade on a copy of the real database (2026-11-12) | Works after column defaults for `orders.discount_cents` and `hero_slides.ai_generated` and the enum-check fix (it dropped 26 + 7 old checks) |
| `prod` on an empty Postgres | Flyway V1-V10 and validation pass; `FlywayMigrationTest` validates V1-V12 against the entities in every test run |
| Riftbound | 13 sets, 1,802 cards, 2,319 prices in about 20 s; a second sync creates nothing; 27 products imported |
| Drop timers | 40 readings at random moments: both countdowns showed the same time every time. The checkout timer counts down |
| Stock and prices | The public drop reply has no stock numbers and no prices; before your turn the page has no € sign and no "nog N over"; once admitted the price shows and + works |
| Customer service | In the browser: FAQ, chatbot (through a stand-in for OpenAI) with links in answers, the ticket suggestion, a ticket with the chat attached, the ticket page with a reply, the admin answer, and all five kinds of mail in Mailpit. One real chatbot answer with the owner's key: correct, $0.0002 |
| Picture search, copy of the real database, real TCGplayer (133 s) | Pokémon 1,151 of 1,762 cards and 831 of 1,470 products filled, 660 to review; Magic 931 of 1,451 products, 289 to review; One Piece 21 cards and 48 products, 46 to review, 25 non-English skipped; Lorcana 97 of 107 products, 3 to review. Screenshots of blisters, collection boxes and boxes: every automatic picture matched |
| Release suggestions on real data | 8 upcoming sets (One Piece, Riftbound, Lorcana, Magic); "Toevoegen" fills the form in |
| Browser | Every new page at 360 and 1280 px: no script errors, no sideways scrolling. Fixed along the way: the cookie notice covered the chat button on phones, the chat panel slid under the header, and review pictures were cut to a square |

## Decisions

- **Riftbound data from TCGplayer** through tcgcsv.com: Riftcodex didn't have the newest set yet.
- **Never show stock to customers**, also not as an exact limit: `maxQuantity` is 99, and asking for more than is
  left gives a message without the number. Someone could still find the stock by trying larger amounts; any shop
  that refuses to oversell has that.
- **Mystery box chances** are shown per kind of box, as a percentage of the boxes left, never as counts.
- **The chatbot on a small model** (`gpt-6-luna`), not gpt-4o: on OpenAI's pricing page gpt-4o costs more ($2.50 /
  $10) and OpenAI shuts it down on 2027-01-14. Its own limit, apart from the hero slides.
- **A ticket confirmation contains nothing the visitor typed**: the form takes any address, so the visitor's own text
  in that mail would let anyone send their words to anyone through the shop.
- **Found pictures are links to TCGplayer's CDN**, like Riftbound's card pictures, not copies: about 3,000 pictures
  would fill the disk of the Mac and the server.
- **Sure means sure**: a card needs the same set, number and name; a product needs a clear best score. Everything
  else goes to the admin, and "Geen van deze" keeps it out of later searches.
- **Release suggestions don't go on the calendar by themselves**: dates move, and some entries are tokens or extras.

## Open issues

- **The shop mailbox** needs a domain (phase 10). The advice was a hosted mailbox on the domain rather than a mail
  server on the VPS next to Minecraft.
- **Pictures not found**: about 1,400 products and 650 cards, mostly promos, trainer kits and older sets. They can
  still be uploaded at `/admin/afbeeldingen`.
- **Duplicate release suggestions** can appear under different names ("Commander: Marvel: Darkhold Destiny" and
  "Darkhold Destiny Commander"); the admin dismisses one.
- **Pokémon releases** mostly appear only just before release, so they stay manual.

## Notes for later phases

- **Phase 9 legal check**, added by this phase: TCGplayer's data and pictures (Riftbound, the picture search) and
  tcgcsv's "careful if you seek to profit", Riot's fan content policy, mystery boxes and the Kansspelwet, the
  newsletter, reviews and tickets in the privacy policy (and how long tickets are kept), OpenAI's terms for generated
  slides and the chatbot (a processor agreement, saying it's AI), and the draft legal pages, in particular the
  returns clause on opened sealed products.
- **Phase 9 polish**: tests read the developer's `.env` through `spring.config.import`; the test settings now pin the
  values tests depend on. A test profile without the import would be cleaner.
- **Phase 10**: a domain, a mailbox for `SHOP_EMAIL`, and `OPENAI_API_KEY` on the server if the chatbot goes live.
