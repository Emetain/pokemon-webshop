# Phase 1: Foundation

Accounts, roles, layout and navigation, so every later phase has a place to live.

## What was built

**Backend**

- `User` with BCrypt password hash, role (`CUSTOMER`, `ADMIN`) and country (default `NL`).
- Register, login, logout and who-am-I under `/api/v1/auth`. Sessions are random tokens stored in the database and sent as an `HttpOnly`, `SameSite=Lax` cookie, `Secure` outside the local profiles.
- `AuthInterceptor` loads the current user. `@RequiresLogin` and `@AdminOnly` guard endpoints, `@CurrentUser` injects the user.
- Password reset by email: single use, expires after 1 hour, ends all sessions of the user. HTML and text mail, sent in the background.
- `@RateLimited` with an in-memory limiter per IP on login, register, forgot password and reset password. Answers `429` with `Retry-After`.
- `GlobalExceptionHandler` returns `{ "error": "<code>", "message": "<Dutch text>" }` for every failure, including Spring's own errors.
- `Address` with CRUD under `/api/v1/account/addresses`. Postcodes are checked and normalized for NL and BE.
- `PATCH /api/v1/account` for name and country.
- `GET /api/v1/admin/status` as the first admin endpoint.
- A daily job removes expired sessions and used or expired reset tokens.
- `DevDataSeeder` creates the demo accounts, in the `dev` profile only. Products, stock and a drop follow in the phases that add those entities.
- Flyway migration `V1__accounts.sql`.

**Frontend**

- Shop layout: top bar, header with logo placeholder, search field (not working yet), account, wishlist and cart with count, main navigation, slide-in menu on mobile, footer with info links, payment labels and data attribution.
- Pages: login, register, forgot password, reset password, my account (profile, country, addresses).
- Admin layout with sidebar and an empty `/admin` page.
- `useSession()` and `useRegion()` stores, route guards for logged-in and admin routes, `services/api.js`.
- `t()` helper with `data/i18n/nl.js`. Design tokens in `main.css`, with an accent colour per game.
- Navigation and footer links lead to a placeholder page until their phase builds the real one.

**Design:** the four games are the index tabs of a binder, in their own colours. The active tab runs into the coloured edge under the header. A binder page with filled and empty pockets, in real card proportions, is the recurring visual and shows the idea behind the collection tracker.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/User.kt`, `UserSession.kt`, `PasswordResetToken.kt`, `Address.kt`, `Country.kt` |
| Auth flow | `controller/AuthController.kt`, `orchestration/AuthOrchestrator.kt`, `service/UserService.kt`, `SessionService.kt`, `PasswordResetService.kt` |
| Guards | `security/SecurityAnnotations.kt`, `AuthInterceptor.kt`, `RateLimitInterceptor.kt`, `config/WebConfig.kt` |
| Errors | `exception/ApiException.kt`, `GlobalExceptionHandler.kt` |
| Migration | `db/migration/V1__accounts.sql` |
| Layout | `components/layout/` (`ShopLayout`, `AppHeader`, `MobileMenu`, `AppFooter`, `AdminLayout`) |
| State and API | `stores/session.js`, `stores/region.js`, `services/api.js`, `router.js` |
| Text and tokens | `data/i18n/nl.js`, `utils/i18n.js`, `assets/css/main.css` |

## Demo logins (dev profile)

Logged on startup.

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@example.com` | `admin12345` |
| Customer | `klant@example.com` | `klant12345` |

## New environment variables

All are in `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `APP_BASE_URL` | Frontend address for links in mails | `http://localhost:5173` |
| `MAIL_FROM` | Sender of all mails | `noreply@example.com` |
| `AUTH_COOKIE_SECURE` | `Secure` flag on the session cookie | on, off in `dev`, `postgres` and `docker` |
| `FORWARD_HEADERS_STRATEGY` | Set to `native` behind a reverse proxy | `none` |

`MAIL_HOST` and `MAIL_PORT` already existed and are now documented in `.env.example` too.

## Checks

| Check | Result |
|-------|--------|
| `./gradlew test` | 66 tests, 0 failed |
| `npm run build` | Passes |
| Register, log in, log out in a real browser | Pass |
| Password reset, mail read from Mailpit's API | Pass: mail has text and HTML, the link is single use, the old password stops working |
| Customer on an admin endpoint | `403` with `{ "error": "forbidden", ... }` |
| Customer on `/admin` | Redirected to `/` |
| Logged-out visitor on `/admin` or `/account` | Redirected to login, and back after logging in |
| Layout at 360, 768, 1024 and 1280px | No horizontal scroll on the shop pages at any width, `/account` and `/admin` checked at 360px. Screenshots reviewed at 360 and 1280px, plus the header at 1024px |
| Slide-in menu at 360px | Opens, closes with Escape, the backdrop or a link, and returns focus |
| Keyboard | Skip link works, focus styles visible |
| Colour contrast | All text pairs at least 5.1:1, input borders and focus rings at least 3.6:1 |
| Browser console during all flows | No errors or warnings |
| `prod` profile on an empty Postgres database | Flyway applies V1, Hibernate validation passes, no demo data, cookie has `Secure`, the API flow works on Postgres |

The browser checks ran with a temporary Playwright script (40 checks) that is not part of the repo. `FlywayMigrationTest` repeats the migration and validation check on H2 in every `./gradlew test`, so the prod check against real Postgres stays a manual step per phase.

The browser run caught a real bug that the backend tests couldn't see: logging out crashed `AccountView` for a moment, because it rendered without a user before the route changed. That is fixed, along with a slide-in menu that stayed open when you tapped the current page, and the back button losing the scroll position.

## Decisions

- **Branch:** phases are committed on `feature/tcg-webshop` instead of `main`. It can't be called `phase-1` because that would clash with the tag.
- **401 or 403:** a logged-out visitor gets `401` on admin endpoints, a logged-in non-admin gets `403`. The prompt says 403 for non-admins, but the frontend needs the difference to send logged-out visitors to the login page.
- **Tokens are stored as SHA-256 hashes.** Someone who reads the database can't use a session or reset link from it.
- **`Secure` flag:** off in `postgres` and `docker` as well as `dev`, because those also run on plain http locally. `prod` always has it unless `AUTH_COOKIE_SECURE` says otherwise.
- **Rate limiting** also covers forgot password and reset password, because forgot password sends mail. Limit: 10 attempts per minute per IP, per endpoint.
- **Forgot password** answers the same for known and unknown addresses, and login takes equally long, so nobody can test who has an account.
- **Form validation** lives on the backend only. Browser validation bubbles follow the browser's language, not Dutch.
- **Countries:** NL and BE, matching the shipping start in phase 5. `User.country` is a plain code rather than an enum, because phase 5 makes shipping countries manageable in admin.
- **Fonts** (Bricolage Grotesque, Schibsted Grotesk) are self-hosted through npm, so visitors make no requests to Google.

## Changes after the first review

The first version was reviewed in the browser. These changes replace the design and the placeholder name described above.

- **Name:** the shop is now called Uithoorn TCG.
- **Colours:** white and blue, with a white page and a strong blue hero. Deep blue is used for the top bar, footer and admin sidebar. Pokémon uses the brand blue as its game accent, the other games keep their own.
- **Navigation:** the binder tabs are gone. The header now follows catchyourcards.nl: a white sticky header with a wide search field, and a row of text links with chevrons under it. Items turn blue on hover and open a full-width dropdown with columns, a "Bekijk alles" link and a feature tile. On mobile a full-height drawer has icon rows with a sub-menu level.
- **Dropdown behaviour:** in the reference, the dropdown opened on click only in our test. Here it opens on hover too, after 120 ms, and closes 220 ms after the mouse leaves, so passing over the menu doesn't make it flicker. A click keeps it open until the visitor closes it. It also works with the keyboard, and Escape closes it.
- **Pokémon elements:** a ball emblem in blue and white, drawn for this project, in the logo and behind the hero. The binder page became a Pokédex page with numbered slots, which previews the Pokédex in phase 3.
- **Menu contents:** the links in the dropdowns come from `data/navigation.js`, using the product types and accessory types of phase 4. They lead to placeholder pages for now. The router generates those pages from the same list, so every menu link works. The series under Pokémon are a fixed list until the card catalog of phase 2 can supply them.

Checks after these changes: `npm run build` passes, and the temporary browser script passes 71 of 71 checks (the earlier flows plus hover timing, click to keep open, keyboard use, closing, and all menu destinations). All colour pairs meet WCAG AA, with text at 4.6:1 or higher and input borders at 3.1:1. The backend didn't change.

## Open issues

- **Ball emblem:** it resembles the Poké Ball, which is a trademark of the Pokémon rights holders. Using it in the logo carries more risk than using it as decoration. Replace it with a real logo before going live, and have someone check how the shop uses Pokémon names and symbols.

- **Before deploying behind a proxy:** set `FORWARD_HEADERS_STRATEGY=native`, otherwise all visitors share one IP and one rate limit.
- **Real mail:** there are no SMTP username and password settings yet, only Mailpit. Needed before going live.
- **Swagger UI** is reachable in `prod`. Part of the phase 8 security review.
- **Attribution line** in the footer is provisional. Phase 2 reads each API's terms and adjusts it.
- **Rate limiter** counts per app instance and resets on restart. Fine for one instance, as the prompt allows.
- **Expired sessions:** the frontend only notices a session that expires during a visit on the next API call that needs a login, and the header still shows "Mijn account" until a reload.
- **Country select:** when saving fails, the error shows but the select keeps the unsaved value. Phase 8 error states.
- **`npm audit`:** 0 issues in production dependencies, 2 in dev tooling (`vite` and `esbuild`, from the skeleton). The fix is a major Vite upgrade, which is left out of this phase.
- **Account deletion** doesn't exist yet, so the foreign keys to `users` have no cascade rule.
