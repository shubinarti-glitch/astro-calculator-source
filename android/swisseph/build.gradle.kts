// Java-порт Swiss Ephemeris 2.01.00 (Thomas Mack, th-mack.de).
// Источники сгенерированы авторским Precompile (java Precompile -qfc -iswesrc).
// Лицензия — Swiss Ephemeris (GPL/AGPL-вариант), см. LICENSE.
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    // Код 2001 года: глушим предупреждения устаревших API, они не наши.
    options.compilerArgs.add("-nowarn")
}
