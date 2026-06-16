# RESULTS_PACK_SPEC.md

## Zip naming
- nearwork_results_pack_{startDay}_{endDay}.zip
- startDay/endDay are localDay strings (YYYY-MM-DD) from the selected export range.

## Zip contents
- manifest.json
- nearwork_daily_results.csv
- nearwork_sessions_results.csv
- nearwork_import_quality.csv

## nearwork_daily_results.csv
Header
- date,sampleCount,diopterHoursTotal,lowLightMinutes,longestSessionSeconds,riskySessionCount,gapCount,largestGapSeconds

Columns
- date: localDay (YYYY-MM-DD)
- sampleCount: number of samples used after preprocessing
- diopterHoursTotal: total diopter-hours for the day (D*h)
- lowLightMinutes: minutes where lux < low_light_threshold_lux
- longestSessionSeconds: duration of longest nearwork session
- riskySessionCount: count of sessions with any risk flag
- gapCount: count of detected gaps (blank if none)
- largestGapSeconds: largest detected gap in seconds (blank if none)

## nearwork_sessions_results.csv
Header
- date,sessionStartIsoUtc,sessionEndIsoUtc,durationSeconds,avgDistanceCm,minDistanceCm,diopterHoursInSession,lowLightSecondsInSession,flags_veryClose,flags_lowLight,flags_highExposure

Columns
- date: localDay (YYYY-MM-DD)
- sessionStartIsoUtc: ISO-8601 UTC timestamp
- sessionEndIsoUtc: ISO-8601 UTC timestamp
- durationSeconds: session duration in seconds
- avgDistanceCm: average distance in cm
- minDistanceCm: minimum distance in cm
- diopterHoursInSession: diopter-hours accumulated in the session (D*h)
- lowLightSecondsInSession: seconds with lux < low_light_threshold_lux
- flags_veryClose: 1 if minDistanceCm <= very_close_threshold_cm, else 0
- flags_lowLight: 1 if lowLightSecondsInSession > 0, else 0
- flags_highExposure: 1 if diopterHoursInSession >= (high_exposure_threshold_diopter_hours * high_exposure_session_fraction), else 0

## nearwork_import_quality.csv
Header
- importedAtIsoUtc,sourceType,filename,totalRows,insertedRows,rejectedRows,rejectedTimestampCount,rejectedDistanceCount,rejectedLuxCount,duplicatesRemovedCount,gapCount,largestGapSeconds,smoothingWindow,thresholds_lowLightLux,thresholds_highExposureDh,thresholds_nearworkCm,thresholds_breakGapSec,thresholds_minSessionSec,thresholds_veryCloseCm

Columns
- importedAtIsoUtc: ISO-8601 UTC timestamp for import session
- sourceType: import source enum name (e.g., USB)
- filename: original filename
- totalRows: rows in CSV
- insertedRows: rows inserted into DB
- rejectedRows: rows rejected during parse/validation
- rejectedTimestampCount: rows rejected for timestamp
- rejectedDistanceCount: rows rejected for distance
- rejectedLuxCount: rows rejected for lux
- duplicatesRemovedCount: duplicates removed (in-file + already-in-DB)
- gapCount: number of detected gaps in the import (top gaps only)
- largestGapSeconds: largest gap duration in seconds
- smoothingWindow: preprocessing smoothing window size (samples)
- thresholds_lowLightLux: low light threshold (lux)
- thresholds_highExposureDh: high exposure threshold (diopter-hours)
- thresholds_nearworkCm: nearwork distance threshold (cm)
- thresholds_breakGapSec: session break gap threshold (seconds)
- thresholds_minSessionSec: session minimum duration (seconds)
- thresholds_veryCloseCm: very close threshold (cm)

## manifest.json schema
Top-level
- exportCreatedAtIsoUtc: string (ISO-8601 UTC)
- appVersionName: string
- versionCode: integer
- profile: object
  - profileId: integer
  - profileName: string
- dateRange: object
  - startDay: string (YYYY-MM-DD)
  - endDay: string (YYYY-MM-DD)
- dataSources: array of strings ("daily", "sessions", "import_quality")
- preprocessing: object
  - smoothingWindow: integer
  - dedupeRule: string
  - outOfRangeRejection: object
    - distanceCmMin: number
    - distanceCmMax: number
    - luxMin: number
    - luxMax: number
  - gapDetectionThresholdSeconds: number
- diopterHours: object
  - dioptersFormula: string
  - integrationMethod: string
- sessionSegmentation: object
  - nearwork_distance_threshold_cm: integer
  - break_gap_seconds: integer
  - min_session_duration_seconds: integer
  - very_close_threshold_cm: integer
  - high_exposure_session_fraction: number
- exposureThresholds: object
  - low_light_threshold_lux: integer
  - high_exposure_threshold_diopter_hours: number
- timeHandling: object
  - timezoneId: string
  - dayGrouping: string ("measurements stored as epoch millis UTC; localDay derived in timezoneId")
  - storedTimestamps: string ("epoch millis UTC")
  - exports: string ("ISO UTC")

Reproducibility notes
- manifest.json reflects current SettingsStore values and PreprocessingPipeline config at export time.
- import_quality.csv threshold fields also reflect current settings at export time, not per-import snapshots.
- ImportSessionEntity stores settingsSnapshotJson and pipelineSnapshotJson, but those snapshots are not used in results pack generation.
