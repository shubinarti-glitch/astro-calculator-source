# Запуск golden-тестов astrocore в обход тестового воркера Gradle:
# кириллический путь репозитория ломает его classpath на Windows (ClassNotFoundException),
# поэтому компилируем через Gradle, а JUnit запускаем напрямую.
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
Set-Location $PSScriptRoot

.\gradlew.bat :astrocore:testClasses -q
if ($LASTEXITCODE -ne 0) { exit 1 }

$cp = (.\gradlew.bat :astrocore:printTestClasspath -q | Select-Object -Last 1)
$ephe = Join-Path $PSScriptRoot "app\src\main\assets\ephe"

& "$env:JAVA_HOME\bin\java.exe" -cp $cp "-Dephe.dir=$ephe" org.junit.runner.JUnitCore ru.astrosmap.app.astro.GoldenTest
exit $LASTEXITCODE
