# PhoneBackup — Android backup & restore from Windows

Back up and restore: selected apps (installable APK files), contacts, SMS, photos / videos / music.
Created by Mostafa Mandegari (مصطفی ماندگاری).

## Build (no programming needed)
1. Upload all files to a **public** GitHub repository (include the `.github` folder).
2. Settings → Actions → General → Workflow permissions → **Read and write permissions** → Save.
3. Every push builds automatically and publishes a new **Release** (right side of the repo page) containing `PhoneBackup.exe`.
   You can also run it manually from the Actions tab → Run workflow.

## Versioning
The file `VERSION` holds the major.minor number (e.g. `1.4`). Every build appends the Actions run number, so the Release is `v1.4.<run>` and the file is named `PhoneBackup_v1.4.<run>.exe`. Edit `VERSION` to start a new minor/major version.

## Use
1. On the phone enable **USB debugging** (Xiaomi/Poco: also *Install via USB*).
2. Connect the cable and accept the Allow prompt.
3. Run `PhoneBackup.exe`. Language: English by default, Persian from the top-right box.
4. Tick the media folders you want (the list is read from the phone; Android/media/... entries include WhatsApp media).
5. Press *Load app list from phone*, tick the apps you want, choose a folder, press *Back up*.
6. *Restore* opens a window where you choose which apps and media folders to put back. *Cancel* stops any running job.

## More features
- App list: tick *User apps* / *System apps* to show them separately (Type column). *Uninstall ticked apps* removes the ticked apps from the phone (with confirmation; a few critical packages are protected).
- Backup folders are named `PhoneBackup_<PhoneModel>_<date>_<time>`; *Open backup folder* opens it in Explorer.
- SMS restore sets the helper as the default SMS app through ADB automatically and puts your previous SMS app back at the end.

- *Phone info* shows model, Android / One UI version, security patch, Samsung firmware (AP/BL/CP/CSC, bit, date), hardware, battery and SIM details, and can copy or save them. IMEI is read automatically when the phone allows it; otherwise use *Read IMEI from phone screen*.

## Backup layout
```
PhoneBackup_YYYYMMDD_HHMM/
  apps/        Telegram.apk, Instagram.apks ...   (one file per app, named by app name)
  media/       DCIM, Pictures, Movies, Music
  contacts.json
  sms.json
```
- Apps with a single APK become `Name.apk` (installable directly).
- Apps with split APKs become `Name.apks` (a zip of the splits; this tool installs it, and so do split-installer apps such as SAI).
- To skip an app during restore, just delete its file from the `apps` folder.

## Updates
The *Check for updates* button reads the latest GitHub Release, downloads it and restarts the app (the repository must be public).
