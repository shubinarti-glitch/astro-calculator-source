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

// Editorial content is fetched at runtime; no private content build inputs.

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
    namespace = "ru.astrosmap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.astrosmap.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.7.4"
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
