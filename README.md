# RUVO

RUVO is a minimal, offline-first Android expense tracker. It has no account, network permission, ads, cloud sync, analytics, or export feature.

## Features
- Fast expense and income entry with editable/deletable transactions
- Built-in and custom categories with icons and colours
- Daily, weekly, monthly and yearly summaries
- Monthly spending calendar heatmap with per-day totals
- Monthly daily average (including zero-spend days)
- Category breakdown, monthly comparison, budgets and spending insights
- Search and filter transaction history
- Confirm-before-delete flow with an Undo action
- Edit, delete and reorder custom categories; existing entries are reassigned safely when a category is deleted
- Optional local PIN app lock
- Compact home-screen widget showing today's spend and a quick-add action
- Forest-green, sage and warm-ivory visual system; dark theme support
- Animated navigation, charts, calendar transitions and gesture interactions
- Exact paise arithmetic, decimal currency display and editable transaction dates
- All data stored on-device using Room

## Build
Open the project in Android Studio (Giraffe or newer) and run the `app` configuration. JDK 17 and Android SDK 35 are used.

The GitHub Actions workflow builds a debug APK on pushes and pull requests.

## Privacy
The manifest intentionally does not request the INTERNET permission. RUVO does not implement exporting, cloud backup, or online services. Uninstalling the app can remove its local data; keep this in mind before uninstalling.

## Project
- Kotlin
- Jetpack Compose + Material 3
- Room database
- Android AppWidget / RemoteViews
