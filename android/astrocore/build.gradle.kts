// Офлайн-движок расчёта карт: чистый Kotlin/JVM, без Android-зависимостей.
// Тесты (golden-сверка с API сайта) гоняются обычным Gradle-JVM-раннером.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":swisseph"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // Файл эфемерид Хирона лежит в assets приложения — отдаём путь тесту.
    systemProperty("ephe.dir", rootProject.projectDir.resolve("app/src/main/assets/ephe").absolutePath)
}

// Кириллический путь репозитория ломает classpath тестового воркера Gradle на Windows
// (ClassNotFoundException). Обход: run-tests.ps1 запускает JUnit напрямую,
// classpath берёт из этой задачи.
val testCp = sourceSets["test"].runtimeClasspath
tasks.register("printTestClasspath") {
    val path = testCp.elements
    doLast { println(path.get().joinToString(";") { it.asFile.absolutePath }) }
}
