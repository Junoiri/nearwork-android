# METHODS.md

## Preprocessing

Source
- Implemented in PreprocessingPipeline (domain/analysis).

Steps (deterministic)
- Sort samples by timestamp.
- Dedupe identical timestamps (keeps the last sample for that timestamp).
- Reject invalid samples:
  - timestamp is not finite
  - distance out of range [10.0, 200.0] cm
  - lux out of range [0.0, 50_000.0]
- Smooth distance with a moving median window (timestamps unchanged).
  - Default window: 60-sample causal rolling mean, applied to distance only (illuminance is unsmoothed).

Gap detection (used for reporting)
- Import gap detection (SampleCsvParser):
  - Compute deltas between sorted timestamps.
  - Typical delta = median of deltas in [1s, 30min], or all deltas if none match.
  - Gap threshold = max(typical, 10s).
  - Report up to 10 largest gaps.
- Results pack daily gap detection (RoomMeasurementRepository):
  - Typical delta = median of deltas in [1ms, 30min], or all deltas if none match.
  - Gap threshold = max(typical * 5, 60s).

## Diopter-hours

Implementation
- Diopters = 1 / distance_meters.
- Integration uses trapezoidal rule between consecutive samples.
- Interval diopter-hours = average(dioptersA, dioptersB) * delta_hours.
- Non-positive time deltas are skipped.

## Low-light exposure

Implementation
- For each consecutive sample pair, if the earlier sample lux is below threshold,
  add the interval duration to low-light time.
- Results are reported as integer minutes (daily) or seconds (per-session).

## Session segmentation

Implementation (NearworkSessionDetector)
- A sample is "nearwork" if distance_cm <= nearwork_distance_threshold_cm.
- Sessions are contiguous runs of nearwork samples.
- Break a session when inactivity exceeds the configurable gap duration threshold
  (strict greater-than comparison on epoch millis deltas).
- Minimum session duration:
  - If only one sample, duration is treated as min_session_duration_seconds.
  - Otherwise duration = lastTimestamp - firstTimestamp (in seconds).
  - Sessions shorter than min_session_duration_seconds are discarded.
- Session metrics:
  - avgDistanceCm, minDistanceCm
  - diopterHoursInSession
  - lowLightSecondsInSession

## Risk rules

Flags used in results pack
- Very close: minDistanceCm <= very_close_threshold_cm
- Low light: lowLightSecondsInSession > 0
- High exposure: diopterHoursInSession >= (high_exposure_threshold_diopter_hours * high_exposure_session_fraction)

## Settings and defaults

SettingsStore values (defaults from SettingsDefaults)
- low_light_threshold_lux: 55
- high_exposure_threshold_diopter_hours: 8.0
- high_exposure_session_fraction: 0.2
- nearwork_distance_threshold_cm: 60
- break_gap_seconds: 60
- min_session_duration_seconds: 60
- very_close_threshold_cm: 33
- show_debug_overlay: false
- last_demo_profile_id: null (not stored when unset)
