package ru.astrosmap.app.astro

/** Порядок знаков — 3-буквенные коды, как в Kerykeion и на сайте. */
val SIGNS = listOf("Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis")

/** Данные рождения. Время — местное, tzId — IANA-зона (например "Europe/Moscow"). */
data class BirthInput(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val lat: Double,
    val lng: Double,
    val tzId: String,
    val housesSystem: Char = 'P',
)

/** Планета, узел или угол карты. */
data class ChartPoint(
    val name: String,       // имя Kerykeion: "Sun", "Mean_North_Lunar_Node", "Ascendant"…
    val absPos: Double,     // эклиптическая долгота 0–360
    val speed: Double,      // °/сутки (< 0 — ретроградность)
    val retrograde: Boolean,
    val houseNum: Int,      // 1–12
) {
    val sign: String get() = SIGNS[((absPos % 360.0) / 30.0).toInt()]
    val position: Double get() = absPos % 30.0
}

data class HouseCusp(val num: Int, val absPos: Double) {
    val sign: String get() = SIGNS[((absPos % 360.0) / 30.0).toInt()]
}

data class AspectHit(
    val p1: String,
    val p2: String,
    val aspect: String,       // "conjunction" | "sextile" | "quintile" | "square" | "trine" | "opposition"
    val orbit: Double,        // отклонение от точного аспекта, °
    val aspectDegrees: Int,
    val movement: String,     // "Applying" | "Separating" | "Static"
)

data class LunarPhase(
    val phase: Int,           // 1–28
    val name: String,         // "New Moon" … "Waning Crescent" (как в Kerykeion)
    val degreesBetween: Double,
)

data class NatalChart(
    val utcJulianDay: Double,
    val points: List<ChartPoint>,   // 15 точек в порядке сайта (PLANET_ORDER)
    val angles: List<ChartPoint>,   // ASC, MC, DSC, IC
    val houses: List<HouseCusp>,    // 12 куспидов
    val aspects: List<AspectHit>,
    val lunarPhase: LunarPhase,
)

data class TransitChart(
    val transitPoints: List<ChartPoint>,
    val aspects: List<AspectHit>,   // p1 — натальная точка, p2 — транзитная
    val lunarPhase: LunarPhase,
)
