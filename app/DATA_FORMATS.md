# DATA_FORMATS.md

## CSV Import Format

Encoding
- UTF-8

Required columns (case-insensitive)
- timestamp
- distance_cm
- illumination_lux

Timestamp parsing
- Integer epoch seconds or epoch millis (values <= 10_000_000_000 are treated as seconds)
- ISO_LOCAL_DATE_TIME (e.g., 2024-01-02T03:04:05)
- "yyyy-MM-dd HH:mm:ss" (e.g., 2024-01-02 03:04:05)
- ISO_OFFSET_DATE_TIME (e.g., 2024-01-02T03:04:05+02:00)
- Local date-time formats are interpreted as UTC (ZoneOffset.UTC)

Units and validation
- distance_cm: centimeters, accepted range 10.0..200.0 (otherwise rejected)
- illumination_lux: lux, accepted range 0.0..50_000.0 (otherwise rejected)

## localDay, Timezone, and UTC Contract

Storage
- Measurement timestamps are stored as epoch millis UTC.
- localDay is stored as an ISO-8601 date string (YYYY-MM-DD).

Timezone rules
- localDay is derived by converting timestampEpochMillis to the profile's timezoneId.
- Profile timezoneId is stored in profiles.timezoneId.
- Import resolves timezoneId from the profile; invalid values fall back to the system default.
- Migration backfill uses per-profile timezoneId; invalid values fall back to UTC.

Grouping and ranges
- Day grouping and filtering uses localDay, not SQL date math on epoch.
- Date ranges use inclusive lexical comparison on localDay (ISO dates).

UTC contract
- Exported timestamps are ISO-8601 in UTC.
