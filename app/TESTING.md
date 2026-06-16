# Testing

## Unit tests

Run the local JVM suite:

```bash
./gradlew :app:testDebugUnitTest
```

## Instrumentation / Compose UI tests

Run the connected device or emulator suite:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Coverage

Kover is configured for local unit-test coverage on the `debug` variant.

Generate reports:

```bash
./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug
```

Verify the minimum coverage rule:

```bash
./gradlew :app:koverVerifyDebug
```

The HTML report is written under:

```text
app/build/reports/kover/htmlDebug/
```

The XML report is written under:

```text
app/build/reports/kover/reportDebug.xml
```
