# App ID Migration

Changing `applicationId` from `app.kairo.reader` to `com.kairo.reader` makes Android treat Kairo as a different app. The old app data is not deleted automatically, but the renamed app starts with a fresh private data directory.

For a local debug device, migrate the data with:

```bash
./scripts/migrate-app-id-data.sh export
./gradlew :app:installDebug
./scripts/migrate-app-id-data.sh restore
```

Or, after both package ids are installed:

```bash
./scripts/migrate-app-id-data.sh migrate
```

The script copies the old package's private `databases/`, `files/`, and `shared_prefs/` directories into the new package. That covers:

- Room database: `kairo.db`
- DataStore preferences: `files/datastore/user_prefs.preferences_pb`
- Imported EPUB/MOBI assets under `files/kairo_epub_assets/` and `files/kairo_mobi_assets/`

This uses `adb run-as`, so both old and new packages must be debuggable builds. A production migration cannot directly read another package's sandbox; it needs a transitional export/import flow where the old app writes a user-visible backup and the new app imports it.

## Fresh Demo Install

For screenshots, demos, and promotional capture, preserve the current app data outside the repo and reset the device app:

```bash
./scripts/clean-demo-install.sh --yes
```

That script:

- backs up `com.kairo.reader` private data to `~/Library/Application Support/Kairo/android-device-backups`
- writes a latest-backup marker for easy restore
- uninstalls `com.kairo.reader`, so the next Android Studio run is a clean install

If you want the script to reinstall the debug APK after uninstalling:

```bash
./scripts/clean-demo-install.sh --yes --install
```

After screenshots and demos are done, install the app if it is not currently installed, then restore the saved data:

```bash
./scripts/reimport-demo-data.sh --yes
```

To inspect available backups:

```bash
./scripts/reimport-demo-data.sh --list
```

To restore a specific backup:

```bash
./scripts/reimport-demo-data.sh --yes --backup-file "$HOME/Library/Application Support/Kairo/android-device-backups/com.kairo.reader-YYYYMMDD-HHMMSS.tar"
```

Do not commit backup tars. They are app-private data snapshots and should stay in the external backup folder.
