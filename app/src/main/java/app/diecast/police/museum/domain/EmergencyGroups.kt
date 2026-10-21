package app.diecast.police.museum.domain

/**
 * Web uygulamasindaki `getEmergencyServiceGroup` fonksiyonunun birebir karsiligi.
 * Kaynak: src/app/page.tsx:61-87
 */
object EmergencyGroups {

    val GROUPS = listOf("Police", "Fire", "Ambulance", "Gendarmerie", "Other")

    private val POLICE = Regex(
        "(police|policia|polizia|sheriff|patrol|constabulary|milic|highway patrol|" +
            "law enforcement|municipal police|customs|border|zoll|federal police|" +
            "military police|national police|state police|local police|douane|" +
            "guardia di finanza|straz graniczna|police municipale)"
    )

    private val FIRE = Regex(
        "(fire|firefighter|fire service|itfaiye|pompier|bomberos|feuerwehr|hasiči|" +
            "vigili del fuoco|пожарная|shōbō|xiāofáng)"
    )

    private val AMBULANCE = Regex(
        "(ambulance|medical|hospital|paramedic|red cross|rescue|medic|ambulans|" +
            "ambulanza|drk|ems|samu|notarzt|rettungsdienst|скорая)"
    )

    private val GENDARMERIE = Regex(
        "(gendarmerie|gendarmeria|guardia civil|jandarma|carabinieri|carabineros|marechaussee)"
    )

    fun groupOf(service: String?): String {
        val normalized = service?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return "Other"
        if (POLICE.containsMatchIn(normalized)) return "Police"
        if (FIRE.containsMatchIn(normalized)) return "Fire"
        if (AMBULANCE.containsMatchIn(normalized)) return "Ambulance"
        if (GENDARMERIE.containsMatchIn(normalized)) return "Gendarmerie"
        return "Other"
    }
}
