package ru.astrosmap.app.astro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PackagedEphemerisTest {
    @Test fun sharedEngineSupportsConcurrentScreensAndWorkers() {
        val engine = AstroEngine(System.getProperty("ephe.dir"))
        val pool = Executors.newFixedThreadPool(4)
        try {
            val work = (0 until 80).map { i -> Callable {
                val input = BirthInput(2026, 9, 1 + i % 28, i % 24, 10, 0.0, 0.0, "UTC")
                val chart = engine.natal(input)
                assertTrue(chart.points.any { it.name == "Moon" && it.absPos.isFinite() })
            } }
            pool.invokeAll(work, 30, TimeUnit.SECONDS).forEach { it.get() }
        } finally { pool.shutdownNow() }
    }

    @Test fun moonIsAvailableWithoutPlanetaryEphemerisFiles() {
        val directory = Files.createTempDirectory("empty-ephemeris-").toFile()
        try {
            val engine = AstroEngine(directory.absolutePath)
            val input = BirthInput(2026, 9, 24, 1, 10, 0.0, 0.0, "UTC")
            val natal = engine.natal(input)
            assertTrue(natal.points.any { it.name == "Moon" && it.absPos.isFinite() })
            assertTrue(natal.lunarPhase.degreesBetween.isFinite())
            assertTrue(engine.transit(input, input).transitPoints.any { it.name == "Moon" })
        } finally { directory.delete() }
    }
}
