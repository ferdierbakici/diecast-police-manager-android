package app.diecast.police.museum.ui.components

/**
 * Cloudinary URL'ine mobil icin boyut/format donusumu ekler.
 * Kaynak URL: https://res.cloudinary.com/i40ukiay/image/upload/v1792589140/vehicles/model_5938.webp
 * Donusturulmus: https://res.cloudinary.com/i40ukiay/image/upload/f_webp,q_auto,w_800/v1792589140/...
 *
 * Gerekce: mobilde liste ekranlarinda tam cozunurlukte gorsel indirmek gereksiz bantgenisligi harcar.
 */
fun cloudinaryUrl(url: String?, width: Int): String? {
    if (url.isNullOrBlank()) return null
    if (!url.contains("/image/upload/")) return url
    // Zaten bir donusum uygulanmissa dokunma.
    val afterUpload = url.substringAfter("/image/upload/")
    if (afterUpload.startsWith("f_") || afterUpload.startsWith("c_") || afterUpload.startsWith("q_")) {
        return url
    }
    return url.replace(
        "/image/upload/",
        "/image/upload/f_webp,q_auto,w_$width/"
    )
}
