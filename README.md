# Diecast Police Museum — Android

The **native Android app** for the worldwide collection of diecast police vehicles.

Written in Kotlin + Jetpack Compose. There is no backend; the app talks to the
Supabase PostgREST + GoTrue APIs directly using a `publishable` key.

Web version: https://diecastpolice.vercel.app

---

## Features (v1)

| Screen | Content |
|---|---|
| **Home** | Search box, statistics (total vehicles / countries / brands), *Recently in Collection*, *Latest Added Models* |
| **Search** | Free text search + filters (country, manufacturer, brand, series, status, emergency service group), paginated result list |
| **Detail** | Large image, model details, status, location/comparison, collection, customization, links, fixed bottom action bar |
| **Account** | Email/password sign in & sign up, Google OAuth, session management |
| **Collection** | Tabbed list: *My Collection* / *Wishlist*, empty states, count badges |

The collection and the wishlist are persistent (Supabase `user_model_status` table).

The whole user interface is in English.

### Tappable tags

Under the image of the detail screen the metadata is shown as **tappable tags**:
country, brand, manufacturer, series and emergency service. Tapping a tag switches
to the search screen and shows all models of that criterion – the same behaviour as
selecting the criterion in the filter sheet.

| Tag | Filter that is applied |
|---|---|
| Country (with the flag) | `countries.name` |
| Brand | `vehicle_brands.name` |
| Manufacturer | `manufacturers.name` |
| Series | `series_id` (the series name is resolved to its id) |
| Emergency service | `emergency_service` + the emergency service group |

The search screen starts with the filter of the tapped tag, so the result list is
correct from the first frame (no second search, no flash of an unfiltered list).

---

## Architecture

No DI library and no database layer — deliberately kept simple.

```
app/src/main/java/app/diecast/police/museum/
├── core/            Config (constants), Network (OkHttp)
├── data/
│   ├── model/       Vehicle, AuthModels, Lenient serializers, Filters
│   ├── remote/      RestClient, Json
│   ├── repository/  MuseumRepository, AuthRepository
│   └── local/       SessionStore (DataStore: session)
├── domain/          Status, EmergencyGroups, Timestamps  (ports of the web app)
├── ui/              home / search / detail / collection / auth / navigation / theme
└── MuseumApp.kt     AppContainer (manual ServiceLocator)
```

**Network layer:** `RestClient` → OkHttp → `https://<ref>.supabase.co/rest/v1`
**Auth:** GoTrue (`/auth/v1`) → JWT `Authorization: Bearer` header
**Session:** access/refresh tokens stored in `DataStore`

### Google OAuth: the PKCE flow

Google sign in runs through **PKCE** (RFC 7636):

1. `Pkce.newCodeVerifier()` → a random verifier of 43–128 characters
2. The verifier is written to `DataStore` **persistently**, and the `code_challenge`
   (S256) is added to the `/authorize?flow_type=pkce&code_challenge=…` request
3. Google → Supabase callback → `diecastpolice://auth-callback?code=…`
4. `MainActivity.handleOAuthRedirect()` → `OAuthRedirect.parse()` →
   `POST /token?grant_type=pkce` (`auth_code` + `code_verifier`) → session

> The verifier is **not kept in memory.** While OAuth completes in the browser
> the app stays in the background and the system may kill it; keeping the
> verifier in memory only would produce a "verification data not found" error
> on the return.

`core/OAuthRedirect.kt` is plain Kotlin (no Android dependency) and parses the
return URL: the query takes priority, and the old (implicit) token format in the
fragment is still supported for backward compatibility.

> **Sign-in event ≠ session state.** Because the session is persisted in
> `DataStore` it already exists on every screen open, so the "sign-in completed"
> information is published on a separate event channel
> (`AuthRepository.signedInEvents`). Otherwise the screen closed automatically
> every time a signed-in user opened the *Account* tab.

### Compatibility with the web app

The behaviour of the web app was ported as-is:

- `Status.filterValues` / `displayLabel` → `domain/Status.kt`
- `getEmergencyServiceGroup` → `domain/EmergencyGroups.kt`
- "Recently in Collection" ordering → `domain/Timestamps.kt`

> **Critical PostgREST behaviour:** filtering on an embedded column **requires**
> `!inner`. With a plain embed the filter is **silently ignored** and every row
> is returned. That is why `Config.VEHICLE_SELECT` uses `!inner`.
>
> The **series** filter is the exception: `series` stays a plain embed (with an
> inner join the 4495 rows without a series would disappear), therefore the
> series is filtered on the `series_id` column. The name → id mapping comes from
> `FilterOptions.seriesIds`.

---

## Build

Requirements: **JDK 17**, Android SDK (compileSdk 35).

```bash
# set the SDK path in local.properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk

./gradlew assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # unit tests
```

| | |
|---|---|
| Gradle | 8.11.1 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| Compose BOM | 2024.12.01 |

### Release signing

Release builds are signed with the keystore described in `keystore.properties`
(file kept outside the repository and gitignored). When that file is missing the
release build falls back to the debug signing key, so CI stays green.

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

> Keep the keystore backed up: if it is lost, the app can no longer be updated
> on the devices that already have it installed.

---

## Tests

The files under `app/src/test/resources/fixtures/` are **real responses captured
from the live Supabase API**. The tests do not depend on the internet; they
prove that the models parse real data correctly without a device.

```bash
./gradlew testDebugUnitTest
```

Coverage:
- `LiveResponseParsingTest` — parsing of real API responses into the `Vehicle`
  model, status filters, the "Recently in Collection" ordering (identical to the
  web reference)
- `VehicleFieldMappingTest` — checks that every JSON key reaches the right field
  (a missing `@SerialName` silently produces `null` — this test catches it)
- `LenientSerializerTest` — variable JSON types (`"true"`, `"5"`, `null`)
- `RecentlyInCollectionOrderTest` — the ordering guarantee of the "Recently in
  Collection" section (regression guard for `order=id.desc`)
- `PkceTest` — the RFC 7636 test vector, verifier/challenge generation, the
  authorize URL
- `OAuthRedirectTest` — return URL parsing (query/fragment, errors, percent
  encoding)
- `AuthUserMappingTest` — the mapping of the displayed name (`display_name` →
  `full_name` → email)
- `TagFilterTest` — the tag filters: every criterion of a tapped tag builds the
  right PostgREST parameter, the other criteria are reset, the series name is
  resolved to its id

---

## Configuration

In `core/Config.kt`:

| Value | Description |
|---|---|
| `SUPABASE_URL` | The project URL |
| `ANON_KEY` | The **publishable** key (not a secret, safe to ship in a client) |
| `CLOUDINARY_CLOUD` | The image CDN cloud name |
| `REDIRECT_URI` | `diecastpolice://auth-callback` |

> The service-role key is **nowhere** in the repository and is not part of the
> project.

### Required setup for Google OAuth

Supabase Dashboard → **Authentication → URL Configuration → Redirect URLs**:

```
diecastpolice://auth-callback
```

Without this, Google sign in cannot return from the browser to the app.

> Note: the lifetime of GoTrue's `state` record is ~10 minutes. If you wait too
> long on the Google screen the return falls back to the **Site URL** with
> `error_code=bad_oauth_state` and the app does not open; in that case the flow
> has to be started from the beginning.

---

## Package

`app.diecast.police.museum`
