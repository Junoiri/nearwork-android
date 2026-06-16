# nearwork-android

Android application constituting the mobile software layer of the **HowFar** wearable system (Wrocław University of Science and Technology). HowFar is a spectacle-mounted device that simultaneously records working distance and ambient illuminance for objective nearwork exposure monitoring in children.

Developed as a bachelor's thesis at the Faculty of Fundamental Problems of Technology, Wrocław University of Science and Technology, 2026.

---

## Overview

The app implements a complete data pipeline from raw HowFar sensor output to clinically interpretable exposure metrics:

* **Import** — parses CSV files exported from the HowFar device via Android's Storage Access Framework
* **Storage** — validates and stores measurements locally in a SQLite database; no data leaves the device
* **Metrics** — computes two exposure metrics per session:

  * **Diopter-hours (D-h)** — cumulative accommodative demand, integrated via the trapezoidal rule
  * **Nearwork Risk Score (NRS)** — composite metric combining vergence demand, distance zone weighting, and an illuminance-dependent multiplier
* **Visualisation** — multi-screen dashboard with per-session, daily, weekly, and monthly breakdowns across multiple child profiles
* **Export** — Results Pack ZIP containing `daily.csv`, `sessions.csv`, `import_quality.csv`, and `manifest.json` for offline analysis
* **Demo mode** — bundled sample datasets based on real HowFar measurements for exploring the app without a physical device

---

## Architecture

Single-activity Jetpack Compose app with MVVM and clean architecture layering:
UI → ViewModel → Use Cases → Repository → Room (SQLite) / DataStore

Timestamps are stored in UTC; `localDay` grouping is derived from the active profile's `timezoneId`. No UI layer has direct access to Room, DataStore, or USB APIs.

---

## Requirements

* Android 8.0 (API 26) or higher
* A HowFar device for importing new measurements
* Alternatively, use the demo mode described below to explore the app with bundled sample data

---

## Demo/testing without a HowFar device

For testing or demonstration purposes, the app can be used without a physical HowFar device. After launching the app, navigate to:

**Settings → General → Create a fresh demo profile and load data there**

This creates a separate demo profile and loads bundled sample data based on real HowFar measurements collected during development. It allows the dashboard, metrics, visualisations, and export workflow to be explored without importing new data from the wearable device.

---

## Building

Open the `app/` directory (not the repo root) in Android Studio. This generates `local.properties` with your SDK path, which Gradle requires.

Then build from the terminal:

```bash
cd app
./gradlew assembleDebug
```

Gradle wrapper is included. Java 17 is required.

---

## Repository structure

```text
nearwork-android/
├── app/                              # Android project root
│   ├── app/                          # Android application module
│   ├── gradle/                       # Gradle wrapper and version catalog
│   ├── scripts/                      # Local build helper scripts
│   ├── DATA_FORMATS.md               # CSV import contract and timestamp rules
│   ├── METHODS.md                    # Preprocessing and metric computation spec
│   └── RESULTS_PACK_SPEC.md          # Export ZIP format specification
├── colab/                            # Google Colab analysis notebooks
│   ├── ALS_Validation_2026_06_04.ipynb
│   ├── HowFar_All_Measurements_2026_05_25.ipynb
│   └── Nearwork_Analysis_HowFar_2026_06_07.ipynb
├── .github/
│   └── workflows/android.yml         # CI — assemble, test, lint
└── README.md
```

---

## Documentation

* `app/DATA_FORMATS.md` — CSV column spec, timestamp format, valid ranges
* `app/METHODS.md` — preprocessing pipeline, metric formulas, session segmentation
* `app/RESULTS_PACK_SPEC.md` — Results Pack ZIP structure and manifest schema

---

## Project context

This repository contains the software layer only. The HowFar hardware (spectacle-mounted ToF + ALS sensor module with RTC) was developed independently. The thesis evaluating this system — *Mobile Application for Monitoring Nearwork and Supporting Myopia Prevention Based on Wearable Sensor Data* (M. Sadłowska, 2026) — includes cross-validation of the ambient light sensor against two independent reference instruments and a field evaluation across 233 nearwork sessions.

---

## Contact

**Author:** Marta Sadłowska · [github.com/Junoiri](https://github.com/Junoiri)
**Supervisor:** Prof. D. Robert Iskander · Faculty of Fundamental Problems of Technology, Wrocław University of Science and Technology

---

## Licence

Academic project — not licensed for redistribution.