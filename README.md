# PeopleFace

Android app to remember people's names.

- **Groups**: where people are from (kid's school, club A, club B…).
- **Families** inside a group: father, mother, any number of kids, or other people (grandma, nanny…).
  A family can be a single person, and the family name is optional.
- **People** have a thumbnail and multiple photos. Each photo is a face cut from a larger picture:
  the app detects faces on-device (ML Kit, works offline), you tap the person you want and adjust the square if needed.
  With **Faces from a photo** on a family screen, you can cut every family member out of a single group picture.
- **Search** across names, families, groups and notes (ignores accents).
- **Quiz**: multiple choice or flashcards, per group or across all groups.
- **Backup**: everything, including photos, lives in one SQLite file (`peopleface.db`).
  Settings → Export / Import database.

Only the cropped faces are stored (512 px JPEG + 192 px thumbnail); original pictures are not kept.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

The GitHub Actions workflow (`Build APK`) only runs when triggered manually from the Actions tab and uploads the debug APK as an artifact.

## Structure

```
app/src/main/java/com/rrgmc/peopleface/
  data/db/        Room entities, DAOs, database (origin_groups, families, persons, photos)
  data/           Repository, BackupManager, AppContainer
  image/          FaceDetector (ML Kit), CropMath, ImageUtils
  ui/             Compose screens: groups, families, family, person, crop, search, quiz, settings
```
