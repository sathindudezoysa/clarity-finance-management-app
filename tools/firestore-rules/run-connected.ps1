param(
    [switch]$DirectAdb,
    [string]$TestClass = '',
    [string]$Jdk17 = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot',
    [string]$Adb = (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe')
)
$ErrorActionPreference = 'Stop'
node --test tools/firestore-rules/currency.test.cjs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$env:JAVA_HOME = $Jdk17
if (!$DirectAdb) {
    .\gradlew.bat :app:connectedDebugAndroidTest --console=plain
    exit $LASTEXITCODE
}
# Alternative for the old AGP/UTP protobuf host class-loader failure. Preserve device data.
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Adb install -r -t app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$instrumentArgs = @('shell', 'am', 'instrument', '-w', '-r')
if ($TestClass) { $instrumentArgs += @('-e', 'class', $TestClass) }
$instrumentArgs += 'com.example.clarity.test/androidx.test.runner.AndroidJUnitRunner'
$result = & $Adb @instrumentArgs
$result | ForEach-Object { Write-Output $_ }
if (($result -join "`n") -match 'OK \([0-9]+ tests?\)' -and ($result -join "`n") -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED') { exit 0 }
exit 1
