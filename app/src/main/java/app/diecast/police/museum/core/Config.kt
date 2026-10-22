package app.diecast.police.museum.core

/**
 * There is no server-side API; the app talks to Supabase PostgREST directly.
 * The key is a "publishable" key (the modern form of the old anon key) - designed for client-side use.
 */
object Config {
    const val PROJECT_URL = "https://eojfccviyaambajvlsqu.supabase.co"
    const val REST_URL = "$PROJECT_URL/rest/v1"
    const val AUTH_URL = "$PROJECT_URL/auth/v1"

    /** Publishable key (public) - read only, plus writes with the user's JWT. */
    const val ANON_KEY = "sb_publishable_ffun9Q-34eCT-Iuuqyr1pQ_doJa_ZcR"

    const val CLOUDINARY_CLOUD = "i40ukiay"

    /** Deep link for the OAuth return. Must be added to "Redirect URLs" in the Supabase dashboard. */
    const val REDIRECT_URI = "diecastpolice://auth-callback"

    const val SITE_URL = "https://diecastpolice.vercel.app"

    /**
     * The columns shown on the vehicle detail screen. A narrowed list instead of `select=*`:
     * it is faster and does not carry all 48 columns.
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
     * Embedded (foreign key) version, used by the detail/list cards.
     *
     * CRITICAL: `countries`, `manufacturers`, `vehicle_brands` **must** use `!inner`.
     * PostgREST silently ignores a filter on a plain (non-inner) embed and returns
     * every row (verified against the live API: `countries.name=eq.France` returns
     * 5783 rows with a plain embed and 1494 with `!inner`).
     * The web app uses `!inner(*)` for the same reason.
     *
     * `series` is never used as a filter and `series_id` is NULL in 4495 rows,
     * so it stays a plain embed (with an inner join those records would disappear).
     */
    const val VEHICLE_SELECT =
        "$VEHICLE_COLUMNS,countries!inner(name,flag_emoji),manufacturers!inner(name)," +
            "vehicle_brands!inner(name),series(name)"
}
