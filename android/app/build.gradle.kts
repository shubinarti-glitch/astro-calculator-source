import java.util.Properties
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** Build-only private data: no Context, I/O or asynchronous loading in the APK. */
abstract class GenerateAndroidEditorial : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val packageFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = packageFile.get().asFile
        if (!file.isFile) throw GradleException(
            "Missing private Android editorial package: $file. Restore data/editorial/android-v1.json " +
                "or supply -PandroidEditorialFile=<absolute path>. No production/demo fallback is allowed."
        )
        val data = JsonSlurper().parse(file, "UTF-8") as? Map<*, *>
            ?: throw GradleException("Invalid Android editorial package: expected object")
        require(data["schemaVersion"] == 1) { "Unsupported Android editorial schema" }
        require(data.keys == setOf("schemaVersion", "cards", "phaseAdvice", "moonMood"))
        fun rows(key: String, size: Int, width: Int): List<List<String>> {
            val values = data[key] as? List<*> ?: error("Missing editorial table: $key")
            require(values.size == size) { "$key must contain $size entries" }
            val result = values.map { row ->
                require(row is List<*> && row.size == width) { "Invalid $key row" }
                row.map { value ->
                    require(value is String && value.isNotBlank()) { "Missing $key text/ID" }
                    value
                }
            }
            require(result.map { it[0] }.distinct().size == size) { "Duplicate $key IDs" }
            return result
        }
        val cards = rows("cards", 78, 7)
        val idsHash = MessageDigest.getInstance("SHA-256")
            .digest(cards.joinToString("\n") { it[0] }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(idsHash == "25e07797a22b4977a67a24113c22c12d3a22aedc85636e605a4d51e49c8b5c53") {
            "Tarot IDs/order changed; saved readings and artwork require the original 78 IDs"
        }
        val phases = rows("phaseAdvice", 8, 3)
        val moods = rows("moonMood", 12, 3)
        require(phases.map { it[0] } == listOf("New Moon", "Waxing Crescent", "First Quarter",
            "Waxing Gibbous", "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent"))
        require(moods.map { it[0] } == listOf("Ari", "Tau", "Gem", "Can", "Leo", "Vir",
            "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis"))
        fun literal(value: String) = JsonOutput.toJson(value).replace("$", "\\$")
        val source = buildString {
            appendLine("// Generated from private editorial data. Do not publish this file.")
            appendLine("package ru.astrosmap.app.editorial")
            appendLine("import ru.astrosmap.app.ui.tarot.TarotCard")
            appendLine("internal object AndroidEditorial {")
            appendLine("    val cards: List<TarotCard> = listOf(")
            cards.forEach { appendLine("        TarotCard(${it.joinToString(", ") { v -> literal(v) }}),") }
            appendLine("    )")
            for ((name, entries) in listOf("phaseAdvice" to phases, "moonMood" to moods)) {
                appendLine("    val $name = mapOf(")
                entries.forEach { appendLine("        ${literal(it[0])} to (${literal(it[1])} to ${literal(it[2])}),") }
                appendLine("    )")
            }
            appendLine("}")
        }
        val target = outputDirectory.file("ru/astrosmap/app/editorial/AndroidEditorial.kt").get().asFile
        target.parentFile.mkdirs()
        target.writeText(source, Charsets.UTF_8)
    }
}

val generateAndroidEditorial = tasks.register<GenerateAndroidEditorial>("generateAndroidEditorial") {
    // InputFiles allows our actionable missing-package check, including after a prior build.
    val configured = providers.gradleProperty("androidEditorialFile")
    // Resolve at configuration time: deferred script closures cannot be stored
    // in Gradle's configuration cache.
    packageFile.set(configured.orNull?.let { file(it) }
        ?: rootProject.file("../data/editorial/android-v1.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/androidEditorial/kotlin"))
    // Private text must not enter a shared Gradle build cache.
    outputs.cacheIf { false }
}

tasks.named("preBuild") { dependsOn(generateAndroidEditorial) }

// Diagnostic runner support for Windows checkouts containing non-ASCII paths.
// Uses the exact unit-test classpath; does not alter APK packaging or test rules.
abstract class PrintUnitTestClasspath : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun printPath() { println(runtimeClasspath.asPath) }
}

afterEvaluate {
    for (variant in listOf("StandardDebug", "GoogleplayDebug")) {
        val unitTest = tasks.named<Test>("test${variant}UnitTest")
        tasks.register<PrintUnitTestClasspath>("print${variant}UnitTestClasspath") {
            runtimeClasspath.from(unitTest.get().classpath)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    sourceSets.getByName("main").java.srcDir(generateAndroidEditorial.flatMap { it.outputDirectory })
    namespace = "ru.astrosmap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.astrosmap.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.7.3"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Эмулятор ходит на локальный FastAPI хоста (10.0.2.2 = localhost хоста).
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://astrosmap.ru/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // До создания боевого ключа release подписывается debug-ключом (только для локальных проверок).
            // Боевая подпись — см. android/RELEASE.md (signing.properties, создаёт владелец).
            val props = Properties()
            val f = rootProject.file("signing.properties")
            if (f.exists()) {
                f.inputStream().use { props.load(it) }
                signingConfigs.create("release") {
                    storeFile = rootProject.file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    // Флейворы по магазину. googleplay-сборка прячет любую продажу/увод на оплату
    // (SHOW_EXTERNAL_PURCHASE_LINKS=false) — Google Play запрещает уводить на
    // внешнюю оплату цифровых функций. SHOW_BILLING зарезервирован под
    // нативную биллинг-интеграцию магазина.
    // standard — RuStore/AppGallery/сайт, где премиум оформляется на astrosmap.ru.
    flavorDimensions += "store"
    productFlavors {
        create("standard") {
            dimension = "store"
            buildConfigField("String", "STORE_ID", "\"standard\"")
            buildConfigField("String", "PAYMENT_PROVIDER", "\"external_web\"")
            buildConfigField("boolean", "SHOW_BILLING", "true")
            buildConfigField("boolean", "SHOW_EXTERNAL_PURCHASE_LINKS", "true")
        }
        create("googleplay") {
            dimension = "store"
            buildConfigField("String", "STORE_ID", "\"googleplay\"")
            buildConfigField("String", "PAYMENT_PROVIDER", "\"none\"")
            buildConfigField("boolean", "SHOW_BILLING", "false")
            buildConfigField("boolean", "SHOW_EXTERNAL_PURCHASE_LINKS", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":astrocore"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.browser)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
