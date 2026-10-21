package app.diecast.police.museum.core

/**
 * Sunucu tarafinda hicbir API yok; uygulama dogrudan Supabase PostgREST'e konusur.
 * Anahtar "publishable" (eski anon key'in modern hali) - istemci tarafinda kullanmak icin tasarlandi.
 */
object Config {
    const val PROJECT_URL = "https://eojfccviyaambajvlsqu.supabase.co"
    const val REST_URL = "$PROJECT_URL/rest/v1"
    const val AUTH_URL = "$PROJECT_URL/auth/v1"

    /** Publishable key (public) - sadece okuma + kullanici JWT'si ile yazma. */
    const val ANON_KEY = "sb_publishable_ffun9Q-34eCT-Iuuqyr1pQ_doJa_ZcR"

    const val CLOUDINARY_CLOUD = "i40ukiay"

    /** OAuth donusu icin deep link. Supabase dashboard'unda "Redirect URLs"e eklenmelidir. */
    const val REDIRECT_URI = "diecastpolice://auth-callback"

    const val SITE_URL = "https://diecastpolice.vercel.app"

    /**
     * Arac detayinda gosterilecek kolonlar. `select=*` yerine daraltilmis liste:
     * hem daha hizli hem de 48 kolonun tamami tasinmaz.
     */
    const val VEHICLE_COLUMNS = "id,model_name,model_year,scale,availability_status," +
        "previous_status,status_changed_at,created_at,updated_at," +
        "emergency_service,model_code,color,material,model_length,model_length_unit,rating," +
        "acquisition_date,market_value,currency,acquired_from," +
        "showcase_num,shelf_num,box_num,exhibition_type," +
        "is_customized,customizer_name,notes,instagram_url,website_url," +
        "model_image_cdn,model_image," +
        "country_id,manufacturer_id,vehicle_brand_id,series_id"

    /**
     * Foreign key gomulmus hali (detay/liste kartlari icin).
     *
     * KRITIK: `countries`, `manufacturers`, `vehicle_brands` **!inner** olmak zorunda.
     * PostgREST duz (inner olmayan) embed uzerinde filtre uygulandiginda filtreyi
     * SESSIZCE yok sayar ve tum satirlari dondurur (canli API'de dogrulandi:
     * `countries.name=eq.France` duz embed ile 5783, !inner ile 1494).
     * Web uygulamasi da ayni sekilde `!inner(*)` kullaniyor.
     *
     * `series` filtre olarak kullanilmadigi ve `series_id` 4495 satirda NULL
     * oldugu icin duz embed kalir (inner olsa o kayitlar kaybolurdu).
     */
    const val VEHICLE_SELECT =
        "$VEHICLE_COLUMNS,countries!inner(name,flag_emoji),manufacturers!inner(name)," +
            "vehicle_brands!inner(name),series(name)"
}
