# Phase 4: Shop products and stock

A browsable shop for all four games and accessories, the admin tools to fill it, and a hero carousel on the home page.

## What was built

**Backend**

- `Product` with every field from the plan: game (empty for accessories that fit every game), type, accessory type, name, slug, description, language, set, card, card variant, condition, grading, price, sale price, images, status, pre-order fields, and the low-stock warning level. Imported products remember where they came from, and whether the admin edited them since.
- Stock in its own `ProductStock` row with a version, so two purchases of the last item can't both succeed, and editing a product never clashes with a purchase. `StockService` is the only way stock changes, and every change writes a `StockMovement` with the reason, the numbers after it, a note and the admin. It can adjust, set a counted number, reserve, release and sell. A lost race is tried once more with the new numbers, which then gives the real answer, such as "uitverkocht".
- Every product can be in the shop without stock: lists and its own page show "Uitverkocht". A product without a price is in the shop but can't be bought either.
- Public endpoints: `GET /api/v1/products` with filters (game, type, accessory, series, set, language, price range, in stock, pre-order, sale, search) and sorting (aanbevolen, new, price both ways, name, popular, release date), plus the filter options that exist in that part of the shop. Product detail by slug, new arrivals and bestsellers.
- Series and sets for the shop navigation: `GET /api/v1/shop/{game}/series` and `/series/{slug}`, from the card catalog, with product counts.
- Product wishlist (`/api/v1/wishlist/products`) and "mail me when back in stock" (`POST /api/v1/products/{slug}/stock-alerts`, rate limited). The mail goes once, after the change is saved, and also when a product with stock gets its price later.
- Card lists now say which cards the shop sells as a single in stock (`forSale`: cheapest price and number of offers), and the card detail lists the offers.
- **Product list import** from Cardmarket's public product list, per game, started by an admin. See Decisions and Source notes.
- **Products of one set**: the usual sealed products of a set in one go, without price or stock, skipping what the set already has.
- **Quick single**: a single from the card catalog with the market prices in euros as a suggestion. The shop price is never filled in from them.
- **CSV** export and import of products and stock in one file.
- **Hero slides** (`HeroSlide`): title, text, button with link, image, game colour, start and end date, on or off, order.
- Product images through `MediaStorage`. A duplicate shares the files of the original, and a file is only deleted when no product uses it any more.
- `DevDataSeeder` adds 20 demo products for all four games and accessories, with stock, a sale, a pre-order, sold-out products and three hero slides.

**Frontend**

- Home: the hero is a carousel. Slide 1 is the hero with the binder, then the slides from admin. It turns every 8 seconds, stops on hover and focus, while the tab is hidden or the hero is out of view, and for visitors who prefer reduced motion. It has buttons to go back, forward, to a slide and to pause. Below it: "Nieuw binnen", the games, "Populair" and the newest pre-orders.
- Shop pages per game (`/pokemon`), per kind of product (`/pokemon/booster-boxen`), per series (`/pokemon/serie/scarlet-violet`) and set (`/pokemon/serie/scarlet-violet/sv08`), accessories (`/accessoires`, `/accessoires/sleeves`), `/pre-order` and `/producten`. Kinds of products as chips in the band, filters and the series navigation in a sidebar, all filters and sorting in the URL.
- Product tiles with badges below the image ("Pre-order", "Uitverkocht", "−11%", "Laatste 2"), price with the old price struck through, "incl. btw", a wishlist heart and the cart button (disabled until phase 5).
- Product page `/product/{slug}`: gallery, price, stock status, pre-order ship date, the back-in-stock form when sold out, details, description, and for singles the card in the catalog with its market price and source. Other offers of the same card and related products from the set.
- The Pokémon series in the menu come from the catalog now: the newest series that have products.
- Card set pages show "Te koop vanaf €X" below cards the shop has as a single, never on the image. The list of missing cards has a column with a link to the single.
- `/verlanglijst` shows products above the cards. Search shows matching products above the cards.
- Admin: products table with search, filters and 25/50/100 per page; product form with card picker, photos and stock panel with the log; duplicate and archive; quick single; stock overview with "Bijna op" and the latest changes; import page (Cardmarket, one set, CSV); hero slides.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/Product.kt`, `ProductStock.kt`, `StockMovement.kt`, `ProductWishlistEntry.kt`, `StockAlert.kt`, `HeroSlide.kt` and the enums `ProductType`, `ProductLanguage`, `ProductStatus`, `AccessoryType`, `GradingCompany`, `StockReason`, `ProductImportSource` |
| Logic | `service/StockService.kt`, `ProductRules.kt` (prices, buyable, "Laatste", slugs), `ProductService.kt` (with `SetProductTemplates`), `ProductCsv.kt`, `ProductCsvService.kt`, `ProductImportService.kt`, `StockAlertService.kt`, `CardOfferService.kt`, `HeroSlideService.kt` |
| Source | `service/provider/CardmarketProductMapper.kt`, `CardmarketProductProvider.kt` |
| Queries | `repository/ProductSpecifications.kt`, `ProductRepository.kt` |
| API | `controller/ProductController.kt`, `ShopController.kt`, `ProductWishlistController.kt`, `AdminProductController.kt`, `AdminStockController.kt`, `AdminProductImportController.kt`, `AdminHeroSlideController.kt` and their orchestrators |
| Migration | `db/migration/V5__shop_products_and_stock.sql` |
| Pages | `views/shop/ShopListView.vue`, `ProductView.vue`, `views/HomeView.vue`, `views/admin/AdminProducts*`, `AdminSingleView.vue`, `AdminStockView.vue`, `AdminImportView.vue`, `AdminSlidesView.vue` |
| Building blocks | `components/shop/` (`ProductTile`, `ProductGrid`, `ProductImage`, `ProductBadges`, `PriceTag`, `ProductFilters`, `SeriesNav`, `ProductRow`, `HeroCarousel`, `StockAlertForm`, `WishlistToggle`), `components/admin/` (`CardPicker`, `ProductImages`, `StockPanel`), `data/shop.js`, `stores/shopNavigation.js` |

## New environment variables

In `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `CARDMARKET_PRODUCTS_BASE_URL` | Cardmarket's public product list, for the import in admin. Only read when an admin starts an import | `https://downloads.s3.cardmarket.com` |

## Upgrading a local database

Restart the backend: the running Phase 3 build doesn't know any of the new endpoints and answers 404 on all of them. Hibernate adds the new tables at startup. Then fill the shop at `/admin/producten/import`, per game.

## Checks

The checks with real data ran on a throwaway copy of the local database, with the new backend on port 8095 and a second Vite on 5174. The copy was dropped afterwards, and the backend on 8080 was left alone.

| Check | Result |
|-------|--------|
| `./gradlew test` | 543 tests, 0 failed |
| `npm run build` | Passes |
| Done when: every product type, images, stock in the log | An integration test creates all 14 types through the API, including a single, a slab and an accessory. In the browser: a new product through the form, a photo upload, a stock change that shows in the log at once, duplicate, archive |
| Done when: no stock means "Uitverkocht" and no cart | Lists and the product page show "Uitverkocht", `canBuy` is false, the cart button is disabled, and the back-in-stock form shows. The cart itself is phase 5 |
| Done when: filters and sorting | Integration tests for every filter, including the price range on the sale price, and every sort order. In the browser the filters and sort end up in the URL and give the right products |
| Done when: two purchases of the last item | One test makes two buyers read "1 left" at the same moment: exactly one succeeds, the other fails on the version, and the log has one sale. A second test lets 20 buyers go for 3 items: exactly 3 sold, stock 0. Both also passed on Postgres |
| Import on the real catalog | Pokémon: 5,109 products in the list, 1,456 imported. Magic: 5,159 and 1,451. Lorcana: 296 and 107. One Piece: 810 and 95. About a second per game. A second run made nothing new: every product unchanged |
| Import keeps edits and stock | Tests: a product the admin edited keeps its name and price, and stock stays as it was |
| `prod` on an empty Postgres database | Flyway applies V1 to V5, Hibernate validation passes. Creating a product, stock, a slide, the wishlist, popular sorting, the low-stock list and the CSV export all work on that schema |
| Product and stock tests on Postgres | 26 tests, 0 failed |
| Dev seeder | 20 products and 3 slides on a fresh `dev` start |
| Browser, 360, 768 and 1280px | 20 pages at each width: no horizontal scroll, no console errors or Vue warnings |
| Browser flows | 32 checks pass: the carousel and its controls, filters, series and set pages, sold out, back-in-stock signup, wishlist, "te koop" on a set page, products in search, unknown pages, product form, photo, stock, duplicate, quick single, set tool, CSV download, slides with an unsafe link refused |

The browser checks ran with temporary Playwright scripts that are not part of the repo.

Checks and screenshots found these bugs, all fixed:

- The product rows on the home page made the page 6,000 pixels wide on a phone: a grid child needed `min-width: 0`, and the hidden labels in the tiles escaped the scrolling row until it was positioned.
- Tabular figures put wide gaps in prices on this typeface, as with the percentages in phase 3.
- "Te koop" below a card pushed its price out of line with the row.
- The long series list hid the filters in the sidebar.
- On a phone, promo slides took the height of the binder slide and showed a big empty blue area.
- The Poké Ball showed on the Lorcana slide.
- The import didn't save updates when called on its own, outside the admin endpoint.
- The CSV export quoted every price because of the decimal comma.
- The query for filter options skipped products without a set.

## Decisions

- **The hero is a carousel,** the owner's idea during this phase. The hero with the binder stays slide 1, as phase 3 decided. Admin slides follow in their own order. Phase 6 adds slides for drops by itself, and phase 7 lets the admin have slides written by AI. Both are in the build prompt now.
- **From 768 pixels all slides share the height of the tallest,** so the page never jumps. On a phone the binder slide is far taller than the rest, so there the hero follows the current slide, and it only turns while it's on screen.
- **A product without a price can't be bought.** Imported products have no price, so they show "Uitverkocht" with "Prijs volgt" until the owner sets one. Stock without a price doesn't make a product buyable either.
- **Imported products are active,** because the owner wants every product in the shop from the start.
- **Cardmarket's list is the import source,** not tcgcsv. Cardmarket is the European market and its categories map well to our product types. tcgcsv mirrors TCGplayer, the US market, with US-only shop exclusives, and its images would be another legal question.
- **Products are only imported for sets in our catalog.** Cardmarket lists every edition in one file without a language. An expansion is linked to the set that most of its products start with, and products of expansions that can't be linked stay out. This keeps Japanese, Chinese and other editions out, as well as sets the card sync doesn't have yet. Running the import again after a sync adds those.
- **Names say what stays out:** Pokémon Center, Japanese, Chinese, Korean, Indonesian and Thai editions, US shop exclusives, cases and displays of other products, promos and prerelease packs. Coins, lots and complete card sets aren't a product type of the shop.
- **Imported products are English,** because our catalog holds the English sets.
- **Both the import and the set tool are built.** The plan has the set tool as a fallback without a usable source. Whether the source may be used is part of the legal check, so the owner has a way either way.
- **"Nieuw binnen" means just back in stock,** not just created. Otherwise an import of 3,000 products would fill the row with sold-out products. `Product.arrivedAt` is set when a product goes from sold out to in stock.
- **"Popular" means wanted until there are orders:** wishlists plus people waiting for stock, then the newest. In an empty shop that is the same as newest, as the plan says. Phase 5 can switch it to order lines.
- **Badges sit below the image for every product,** not only for card images, so tiles look the same everywhere.
- **"Laatste X" shows at the warning level or below,** 2 by default. The low-stock overview only lists products the shop has had in stock: an imported product that was never stocked isn't running out.
- **Singles have a card variant,** which the plan didn't list. The market price and the name depend on it, for example a holo next to a reverse holo.
- **Stock is its own row with the version.** A version on the product would make an edit in admin fail during a purchase.
- **The back-in-stock mail has no double opt-in.** It is one mail that the customer asked for, and the signup is rate limited per IP.
- **A product keeps its URL when its name changes,** so shared links keep working. The admin can change the slug by hand.
- **Hero slide links are a path in the shop or an https link,** never another scheme.
- **The home page has no drop block yet.** Drops don't exist until phase 6, which adds them as slides in the carousel.
- **One CSV for products and stock,** with semicolons, decimal commas and a byte order mark for Dutch Excel. Text that starts like a formula gets a quote in front.
- **Search shows products above the cards,** because a customer typing "Prismatic" probably wants the products.
- **The menu shows the newest series that have products,** up to 6. A shop without products gets the newest series with at least 3 sets.

## Source notes

### Cardmarket product list

- `downloads.s3.cardmarket.com/productCatalog/productList/products_nonsingles_{id}.json`, with id 6 for Pokémon, 1 Magic, 18 One Piece and 19 Lorcana. One JSON object with `version`, `createdAt` and `products`. Each product has `idProduct`, `name`, `idCategory`, `categoryName`, `idExpansion`, `idMetacard` and `dateAdded`. No language, no release date, no image.
- Categories for Pokémon: Box Set 1,121, Booster 796, Coins 695, Display 626, Tins 571, Theme Decks 560, Blisters 498, Elite Trainer Boxes 171, PCG Set 30, Lot 28, Trainer Kits 13.
- Names follow `<set> <product>` or `<set>: <detail> <product>`. Asian editions say so in the name or have their own expansion: "JP", "S-Ch", "Pokémon Card 151", "(Asia Region Legal)". Expansion 1645 is a mix of loose products without a set.
- Checked on 2026-11-08 in the build prompt: nothing about commercial use, so it is part of the legal check. The import never runs by itself.

## Open issues

- **The legal check decides whether the import may be used.** Cardmarket's list and the product names come from them. It is on the go-live list in phase 8.
- **Imported products have no photos.** The tiles show the set logo or the kind of product. The owner uploads photos per product.
- **Upcoming sets come in after the card sync has them.** Cardmarket already lists products of sets that TCGdex doesn't have yet. Until then the set tool or a manual product covers a pre-order.
- **Products without a set aren't imported,** such as premium collections in Cardmarket's loose expansion. The admin adds those by hand.
- **Old products are imported too,** such as Base Set booster boxes, as sold out. There is no bulk archive yet, only one product at a time.
- **Some imported names carry Cardmarket's own details,** such as "Booster Box (18 Boosters)" or "Fun Pack (3 Cards)".
- **"Populair" equals "Nieuw binnen" in a new shop,** until customers use the wishlist or the shop has orders.

## Notes for later phases

- **Phase 5:** `StockService.reserve`, `release` and `sell` are ready, and `retryingOnStockConflict` in `AdminProductOrchestrator` shows how to handle a lost race. Enable the cart buttons in `ProductTile.vue` and `ProductView.vue`. Use `ProductRules.canBuy` at add-to-cart. Switch the popular sort and bestsellers to order lines.
- **Phase 6:** `StockReason.DROP_ALLOCATION` exists. Add drop slides to the carousel: `HeroCarousel.vue` takes a list of slides after the binder.
- **Phase 7:** `/pre-order` lists pre-orders by release date; the timeline builds on it. The AI slides belong on `/admin/hero`.
- **Phase 8:** add product pages to the sitemap, and give products an Open Graph image. Consider a bulk archive in the admin table.
