# Android device support

IRA is a private debug build. Support is defined by what the APK can run, not by brand:

- `arm64-v8a` or `armeabi-v7a` CPU
- Android API 26 or newer
- 4GB RAM recommended for the local 270M model
- USB debugging and sideloading allowed by the owner

IRA runs on ARM Android phones from any OEM. Samsung Galaxy, Xiaomi/Redmi/Poco, OnePlus/Oppo/Realme, and Motorola have dedicated setup notes below. Every other Android OEM uses the generic Android setup. Apple iOS is not supported. Very old 32-bit x86 devices, Android API 25 or older, and devices with a policy that blocks sideloading are out of scope.

The install script detects the manufacturer and prints the matching steps for known OEMs, or generic Android steps for every other OEM. The paths below are the common paths. Menu names vary by Android version, One UI, HyperOS, OxygenOS, ColorOS, and carrier build.

## Support matrix

| OEM | Install gate | Background gate | Recommended model |
| --- | --- | --- | --- |
| Samsung Galaxy | Auto Blocker, Install unknown apps | Battery > Background usage limits > Never sleeping apps | 270M on 4GB, 1.5B only on 6GB+ |
| Xiaomi, Redmi, Poco | Developer options, Install via USB, Install unknown apps | Autostart, App battery saver > No restrictions | 270M on 4GB, 1.5B only on 6GB+ |
| OnePlus, Oppo, Realme | Install unknown apps | Auto launch, Battery optimization > Don't optimize | 270M on 4GB, 1.5B only on 6GB+ |
| Motorola | Install unknown apps | Battery optimization, App launch | 270M on 4GB, 1.5B only on 6GB+ |
| Every other Android OEM | Install unknown apps | Battery optimization > Unrestricted, allow autostart if present | 270M on 4GB, 1.5B only on 6GB+ |

The 270M model is about 241 MB. The optional 1.5B model is about 940 MB and needs more free RAM and storage. Use 270M on 32-bit ARM devices; 1.5B is not recommended there. The app runs in cloud mode without either GGUF file.

The installer default is `--models auto`: it pushes 270M to every ARM phone and adds 1.5B only on 64-bit devices with 5.5GB or more RAM. Use `--models small`, `--models big`, `--models both`, or `--models none` to override. If both files are installed, the `Local model` row switches between them and applies after IRA restarts.

## Samsung Galaxy

### Install

1. Settings > About phone > Software information > tap `Build number` seven times if Developer options is hidden.
2. Settings > Security and privacy > Auto Blocker. Turn it off temporarily, or turn off `Maximum restrictions` if it is on.
3. Settings > Security and privacy > More security settings > Install unknown apps. Allow the source you use, such as Chrome or My Files.
4. Run the installer, then turn Auto Blocker back on after setup.

Samsung notes that Auto Blocker is on by default on Galaxy devices released with One UI 6.1.1 and later. It blocks apps from outside the Galaxy Store or Play Store and can block commands over USB. Turn it off only for the install, then restore it.

### Permissions

- Accessibility: Settings > Accessibility > Installed apps > IRA Assistant.
- Notification access: Settings > Notifications > Advanced settings > Notification access > IRA. If that path is missing, use Settings > Apps > Special access > Notification access.
- Overlay: Settings > Apps > three dots > Special access > Appear on top > IRA.
- Battery: Settings > Battery > Background usage limits > Never sleeping apps > add IRA. Also set Settings > Apps > IRA > Battery > Unrestricted.
- If Accessibility is greyed out: App info > IRA > three dots > Allow restricted settings, then try again.

## Xiaomi, Redmi, Poco

### Install

1. Settings > About phone > tap `OS` or `MIUI version` seven times.
2. Settings > Additional settings > Developer options.
3. Enable `USB debugging` and `Install via USB`.
4. If `Install via USB` is unavailable, sign in to a Mi account and insert a SIM, then retry. Do not use a bootloader unlock or a security bypass.
5. Settings > Privacy protection > Special permissions > Install unknown apps. Allow the source you use.

HyperOS and MIUI move labels between releases. If a path is missing, search Settings for `Install via USB`, `Install unknown apps`, or `Autostart`.

### Permissions

- Accessibility: Settings > Additional settings > Accessibility > Downloaded apps > IRA Assistant.
- Overlay: Settings > Apps > Manage apps > IRA > Other permissions > Display pop-up windows while running in background.
- Notification access: Settings > Apps > Special app access > Notification access > IRA. If missing, check Settings > Notifications & status bar.
- Autostart: Settings > Apps > Manage apps > IRA > Autostart. Turn it on.
- Battery: Settings > Battery > App battery saver > IRA > No restrictions.
- If Accessibility is greyed out: App info > IRA > three dots > Allow restricted settings.

## OnePlus, Oppo, Realme

### Install

1. Settings > Apps > Special app access > Install unknown apps. Allow the source you use.
2. Settings > About device > Version > tap `Build number` seven times if Developer options is hidden.
3. Settings > Additional settings > Developer options > enable `USB debugging`.
4. Run the installer. If the system shows a restricted-setting warning, open the app info and allow restricted settings only if you trust the APK.

### Permissions

- Accessibility: Settings > System settings > Accessibility > Downloaded apps > IRA Assistant. On some builds it is Settings > Additional settings > Accessibility.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Overlay: Settings > Apps > Special app access > Display over other apps > IRA.
- Auto launch: Settings > Apps > Auto launch > IRA. Turn it on.
- Battery: Settings > Battery > Battery optimization > IRA > Don't optimize. On ColorOS look for App battery management or Allow background activity.
- If Accessibility is greyed out: App info > IRA > three dots > Allow restricted settings.

## Motorola

### Install

1. Settings > Apps > Special app access > Install unknown apps. Allow the source you use.
2. Settings > About phone > tap `Build number` seven times if Developer options is hidden.
3. Settings > System > Developer options > enable `USB debugging`.
4. Run the installer.

### Permissions

- Accessibility: Settings > Accessibility > Downloaded apps > IRA Assistant.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Overlay: Settings > Apps > Special app access > Display over other apps > IRA.
- Battery: Settings > Battery > Battery optimization > IRA > Don't optimize.
- Auto-start: Settings > Battery > App launch, or Manage how apps use battery > Allow apps to start automatically > IRA.
- If Accessibility is greyed out: App info > IRA > three dots > Allow restricted settings.

## Other Android OEMs

### Install

1. Settings > Apps > Special app access > Install unknown apps. Allow the source you use.
2. Settings > About phone > tap `Build number` seven times if Developer options is hidden.
3. Settings > System or Additional settings > Developer options > enable `USB debugging`.
4. Run the installer.

### Permissions

- Accessibility: Settings > Accessibility > Downloaded apps > IRA Assistant. Some builds use Installed apps or Installed services.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Overlay: Settings > Apps > Special app access > Display over other apps > IRA.
- Battery: Settings > Battery > Battery optimization > IRA > Don't optimize, or set IRA to Unrestricted.
- Autostart: if the OEM has an Autostart or App launch screen, allow IRA.
- If Accessibility is greyed out: App info > IRA > three dots > Allow restricted settings.

## Android 13 and newer restricted settings

Android 13 and newer can hide Accessibility and Notification access for apps installed outside a store. If the toggle is greyed out:

1. Settings > Apps > IRA.
2. Tap the three dots in the top-right.
3. Tap `Allow restricted settings`.
4. Return to Accessibility or Notification access and enable IRA.

This is a user action. Do not try to flip secure settings with adb.

## Adding OEM-specific notes

The installer already accepts every Android manufacturer and falls back to the generic steps above. Add a named branch only when you have verified paths for that OEM:

1. Add its `ro.product.manufacturer` values to the `case` in `scripts/agent-install.sh`.
2. Add a row to the matrix above.
3. Add install, accessibility, notification access, overlay, autostart, and battery paths.
4. Keep the OEM-specific output descriptive, because menu names change between OS releases.

Do not block installation just because a manufacturer is not named in the script.

## Sources

- Samsung Auto Blocker: https://www.samsung.com/us/support/answer/ANS10003636/
- Samsung install unknown apps: https://www.samsung.com/ae/support/mobile-devices/how-to-enable-permission-to-install-apps-from-unknown-source-on-my-samsung-phone/
- Samsung accessibility apps: https://www.samsung.com/us/support/answer/ANS10001906/
- Samsung screen overlay: https://www.samsung.com/us/support/troubleshoot/TSG10004868/
- Xiaomi developer options: https://www.mi.com/global/support/faq/details/KA-168765/
- Xiaomi accessibility: https://www.mi.com/uk/support/faq/details/KA-728047/
- Xiaomi background and pop-up permissions: https://www.mi.com/global/support/faq/details/KA-717221/
- OnePlus OxygenOS 14 manual: https://service.oneplus.com/content/dam/support/user-manuals/common/OxygenOS_14.0_User_Manual.pdf
- OnePlus install unknown apps: https://www.oneplus.com/tr/support/answer/detail/op450
- Motorola battery and app launch: https://help.motorola.com/hc/3505/14/global/en-us/CG2007980805.html
- Motorola special app access: https://help.motorola.com/hc/7166/15/na/en-us/T1809172408sS3.html
- Motorola install unknown apps: https://en-in.support.motorola.com/app/answers/detail/a_id/108560/p/30,
