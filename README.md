# RUVO

RUVO is a minimal, offline-first Android expense tracker. It has no account, network permission, ads, cloud sync, analytics, or export feature.

## Features
- Fast expense and income entry with editable/deletable transactions
- Optional transaction notes, shown in history and searchable
- Built-in and custom categories with icons and colours
- Daily, weekly, monthly and yearly summaries
- Monthly spending calendar heatmap with per-day totals
- Monthly daily average, category breakdown, monthly comparison and spending insights
- Search and filter transaction history by type, category and date range
- Quick date presets: Today, 7 days, 30 days, This month, Last month and All time
- History sorting by newest, oldest, highest amount and lowest amount
- Date-grouped transaction rows with matching totals
- Confirm-before-delete flow with an Undo action
- Edit, delete and reorder custom categories; existing entries are reassigned safely when a category is deleted
- Monthly spending budget
- Optional local PIN app lock
- Minimal 1×1 home-screen quick-add widget
- Dark crystal-glass visual system, translucent panels and refined transitions
- Exact paise arithmetic, decimal currency display and editable transaction dates
- All transaction and category data stored on-device using Room

## Build
Open the project in Android Studio (Giraffe or newer) and run the app configuration. JDK 17 and Android SDK 35 are used.

The GitHub Actions workflow runs release unit tests, assembles an optimized release APK and uploads it as a workflow artifact.

## Privacy
The manifest intentionally does not request the INTERNET permission. RUVO does not implement exporting, cloud backup, or online services. Uninstalling the app can remove its local data; keep this in mind before uninstalling.

## Project
- Kotlin
- Jetpack Compose + Material 3
- Room database
- Android AppWidget / RemoteViews
