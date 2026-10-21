# Diecast Police Museum — Android

Dünya genelindeki diecast polis araçları koleksiyonunun **native Android uygulaması**.

Kotlin + Jetpack Compose ile yazılmıştır. Backend yoktur; uygulama doğrudan
Supabase PostgREST + GoTrue API'lerine `publishable` anahtarıyla bağlanır.

Web sürümü: https://diecastpolice.vercel.app

---

## Özellikler (v1)

| Ekran | İçerik |
|---|---|
| **Ana Sayfa** | Arama kutusu, istatistikler (toplam araç / ülke / marka), *Koleksiyona son katılanlar*, *Son eklenen modeller* |
| **Arama** | Serbest metin araması + filtreler (ülke, üretici, marka, durum, acil servis grubu), sayfalı sonuç listesi |
| **Detay** | Büyük görsel, model bilgileri, durum, konum/karşılaştırma, koleksiyon, özel yapım, bağlantılar, sabit alt aksiyon çubuğu |
| **Hesap** | E-posta/şifre ile giriş & kayıt, Google OAuth, oturum yönetimi |
| **Koleksiyon** | Sekmeli liste: *Koleksiyonum* / *Wishlist*, boş durumlar, sayı rozetleri |

Koleksiyon ve wishlist kalıcıdır (Supabase `user_model_status` tablosu).

---

## Mimari

Kotlin, DI kütüphanesi ve veritabanı katmanı **yoktur** — bilinçli olarak basit tutulmuştur.

```
app/src/main/java/app/diecast/police/museum/
├── core/            Config (sabitler), Network (OkHttp)
├── data/
│   ├── model/       Vehicle, AuthModels, Lenient serializer'lar, Filters
│   ├── remote/      RestClient, Json
│   ├── repository/  MuseumRepository, AuthRepository
│   └── local/       SessionStore (DataStore: oturum)
├── domain/          Status, EmergencyGroups, Timestamps  (web portları)
├── ui/              home / search / detail / collection / auth / navigation / theme
└── MuseumApp.kt     AppContainer (manuel ServiceLocator)
```

**Ağ katmanı:** `RestClient` → OkHttp → `https://<ref>.supabase.co/rest/v1`
**Kimlik:** GoTrue (`/auth/v1`) → JWT `Authorization: Bearer` başlığı
**Oturum:** `DataStore` üzerinde saklanan access/refresh token

### Google OAuth: PKCE akışı

Google girişi **PKCE** (RFC 7636) ile yürütülür:

1. `Pkce.newCodeVerifier()` → 43–128 karakterlik rastgele verifier
2. Verifier **kalıcı olarak** `DataStore`'a yazılır, `code_challenge` (S256)
   `/authorize?flow_type=pkce&code_challenge=…` isteğine eklenir
3. Google → Supabase callback → `diecastpolice://auth-callback?code=…`
4. `MainActivity.handleOAuthRedirect()` → `OAuthRedirect.parse()` →
   `POST /token?grant_type=pkce` (`auth_code` + `code_verifier`) → oturum

> Verifier **bellekte tutulmaz.** OAuth tarayıcıda tamamlanırken uygulama
> arka planda kalır ve sistem tarafından öldürülebilir; bellekte tutulsaydı
> dönüşte "doğrulama verisi bulunamadı" hatası alınırdı.

`core/OAuthRedirect.kt` saf Kotlin'tir (Android'e bağımlı değil) ve dönüş
adresini ayrıştırır: query önceliklidir, fragment'taki eski (implicit) token
biçimi geriye dönük desteklenir.

> **Giriş olayı ≠ oturum durumu.** Oturum `DataStore`'da kalıcı olduğu için
> her ekran açılışında zaten vardır; bu yüzden "giris tamamlandı" bilgisi
> ayrı bir olay kanalından (`AuthRepository.signedInEvents`) yayınlanır.
> Aksi halde oturumu olan kullanıcı *Hesap* sekmesine her girdiğinde ekran
> otomatik kapanıyordu.

### Web uygulamasıyla uyum

Web'deki davranış birebir portlandı:

- `Status.filterValues` / `displayLabel` → `domain/Status.kt`
- `getEmergencyServiceGroup` → `domain/EmergencyGroups.kt`
- "Recently in Collection" sıralaması → `domain/Timestamps.kt`

> **Kritik PostgREST davranışı:** Gömülü (embedded) kolon üzerinde filtreleme
> mutlaka `!inner` gerektirir. Düz embed'de filtre **sessizce yok sayılır** ve
> tüm satırlar döner. `Config.VEHICLE_SELECT` bu yüzden `!inner` kullanır.

---

## Derleme

Gereksinimler: **JDK 17**, Android SDK (compileSdk 35).

```bash
# local.properties içinde SDK yolunu ayarla
sdk.dir=C\:\\Users\\<kullanici>\\AppData\\Local\\Android\\Sdk

./gradlew assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # birim testleri
```

| | |
|---|---|
| Gradle | 8.11.1 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| Compose BOM | 2024.12.01 |

---

## Testler

`app/src/test/resources/fixtures/` altındaki dosyalar **canlı Supabase API'sinden**
alınmış gerçek yanıtlardır. Testler internete bağımlı değildir; modellerin
gerçek veriyi doğru çözümlediğini cihaz olmadan kanıtlar.

```bash
./gradlew testDebugUnitTest
```

Kapsam:
- `LiveResponseParsingTest` — gerçek API yanıtlarının `Vehicle` modeline çözümü,
  durum filtreleri, "Recently in Collection" sıralaması (web referansıyla birebir)
- `VehicleFieldMappingTest` — her JSON anahtarı doğru alana yansıyor mu
  (`@SerialName` eksiklikleri sessizce `null` üretir — bu test bunu yakalar)
- `LenientSerializerTest` — değişken JSON tipleri (`"true"`, `"5"`, `null`)
- `PkceTest` — RFC 7636 test vektörü, verifier/challenge üretimi, authorize URL'i
- `OAuthRedirectTest` — dönüş adresi ayrıştırma (query/fragment, hata, yüzde kodlama)
- `AuthUserMappingTest` — gosterilen adın eşlemesi (`display_name` → `full_name` → e-posta)

---

## Yapılandırma

`core/Config.kt` içinde:

| Değer | Açıklama |
|---|---|
| `SUPABASE_URL` | Proje URL'si |
| `ANON_KEY` | **Publishable** anahtar (gizli değildir, istemciye konulabilir) |
| `CLOUDINARY_CLOUD` | Görsel CDN cloud adı |
| `REDIRECT_URI` | `diecastpolice://auth-callback` |

> Service-role anahtarı **hiçbir yerde** bulunmaz ve projeye dahil değildir.

### Google OAuth için gerekli kurulum

Supabase Dashboard → **Authentication → URL Configuration → Redirect URLs**:

```
diecastpolice://auth-callback
```

Bu olmadan Google ile giriş tarayıcıdan uygulamaya geri dönemez.

> Not: GoTrue'un `state` kaydının ömrü ~10 dakikadır. Google ekranında uzun
> beklenirse dönüş `error_code=bad_oauth_state` ile **Site URL**'e düşer ve
> uygulama açılmaz; bu durumda akış baştan başlatılmalıdır.

---

## Paket

`app.diecast.police.museum`
