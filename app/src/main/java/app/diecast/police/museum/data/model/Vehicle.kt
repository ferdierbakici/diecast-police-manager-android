package app.diecast.police.museum.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CountryRef(
    val name: String? = null,
    @SerialName("flag_emoji") val flagEmoji: String? = null
)

@Serializable
data class NameRef(
    val name: String? = null
)

/**
 * `vehicles` tablosunun mobil uygulama icin gereken kolonlari.
 * Foreign key'ler PostgREST `embed` ile gomulur (countries/manufacturers/vehicle_brands/series).
 */
@Serializable
data class Vehicle(
    val id: Int,

    @SerialName("model_name") val modelName: String? = null,
    @SerialName("model_year") @Serializable(with = LenientInt::class) val modelYear: Int? = null,
    val scale: String? = null,
    @SerialName("availability_status") val availabilityStatus: String? = null,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("status_changed_at") val statusChangedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,

    @SerialName("emergency_service") val emergencyService: String? = null,
    @SerialName("model_code") val modelCode: String? = null,
    val color: String? = null,
    val material: String? = null,
    @SerialName("model_length") @Serializable(with = LenientDouble::class) val modelLength: Double? = null,
    @SerialName("model_length_unit") val modelLengthUnit: String? = null,
    @Serializable(with = LenientInt::class) val rating: Int? = null,

    @SerialName("acquisition_date") val acquisitionDate: String? = null,
    @SerialName("market_value") @Serializable(with = LenientDouble::class) val marketValue: Double? = null,
    val currency: String? = null,
    @SerialName("acquired_from") val acquiredFrom: String? = null,

    @SerialName("showcase_num") @Serializable(with = LenientString::class) val showcaseNum: String? = null,
    @SerialName("shelf_num") @Serializable(with = LenientString::class) val shelfNum: String? = null,
    @SerialName("box_num") @Serializable(with = LenientString::class) val boxNum: String? = null,
    @SerialName("exhibition_type") val exhibitionType: String? = null,

    @SerialName("is_customized") @Serializable(with = LenientBool::class) val isCustomized: Boolean? = null,
    @SerialName("customizer_name") val customizerName: String? = null,
    val notes: String? = null,
    @SerialName("instagram_url") val instagramUrl: String? = null,
    @SerialName("website_url") val websiteUrl: String? = null,

    @SerialName("model_image_cdn") val modelImageCdn: String? = null,
    @SerialName("model_image") val modelImage: String? = null,

    @SerialName("country_id") val countryId: Int? = null,
    @SerialName("manufacturer_id") val manufacturerId: Int? = null,
    @SerialName("vehicle_brand_id") val vehicleBrandId: Int? = null,
    @SerialName("series_id") val seriesId: Int? = null,

    val countries: CountryRef? = null,
    val manufacturers: NameRef? = null,
    @SerialName("vehicle_brands") val vehicleBrands: NameRef? = null,
    val series: NameRef? = null
) {
    val countryName: String? get() = countries?.name
    val countryFlag: String? get() = countries?.flagEmoji
    val manufacturerName: String? get() = manufacturers?.name
    val brandName: String? get() = vehicleBrands?.name
    val seriesName: String? get() = series?.name

    /** Gorsel onceligi: Cloudinary (CDN) > yerel dosya yolu. */
    val imageUrl: String?
        get() = modelImageCdn?.takeIf { it.isNotBlank() }
            ?: modelImage?.takeIf { it.isNotBlank() && it.startsWith("http") }
}
