package app.diecast.police.museum.ui.components

/**
 * Adds a size/format transformation to the Cloudinary URL for mobile.
 * Source URL: https://res.cloudinary.com/i40ukiay/image/upload/v1792589140/vehicles/model_5938.webp
 * Transformed: https://res.cloudinary.com/i40ukiay/image/upload/f_webp,q_auto,w_800/v1792589140/...
 *
 * Rationale: downloading full resolution images on the list screens wastes bandwidth.
 */
fun cloudinaryUrl(url: String?, width: Int): String? {
    if (url.isNullOrBlank()) return null
    if (!url.contains("/image/upload/")) return url
    // Do not touch a URL that already has a transformation applied.
    val afterUpload = url.substringAfter("/image/upload/")
    if (afterUpload.startsWith("f_") || afterUpload.startsWith("c_") || afterUpload.startsWith("q_")) {
        return url
    }
    return url.replace(
        "/image/upload/",
        "/image/upload/f_webp,q_auto,w_$width/"
    )
}
