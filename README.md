# RRT Neon Reader — Kotlin Android app

قارئ Android مستقل للمـانجا والمانهوا، يدعم المصادر المخصصة بما فيها تصنيفات 18+ بإعداد اختياري، وقارئ فصول وتنزيل صورها محليًا.

## Build exclusively on GitHub (without Android Studio)

1. Upload the complete contents of this folder to the **root** of `hald2020aa-hue/rrt` (not under a nested directory).
2. Navigate to **Actions** → **RRT Neon Reader - Build APK** → **Run workflow** (also runs on push to main).
3. After a successful job, open **Artifacts** and download `RRT-NeonReader-debug-APK`; unzip and install `app-debug.apk`.
4. The file is a **debug-signed APK** for testing only. To distribute publicly, configure secure release signing.

If using Termux and a GitHub token/login already configured:

```bash
pkg install -y git unzip
unzip rrt-neon-reader.zip -d $HOME/rrt-work
cd $HOME/rrt-work/rrt-neon-reader
git init
git branch -M main
git remote add origin https://github.com/hald2020aa-hue/rrt.git
git add .
git commit -m "Initial RRT Neon Reader Kotlin app"
git push -u origin main
```

Use GitHub CLI or git credential manager authentication; **never paste a token in chat or commit credentials into the repository**.

## Source configuration

Open `+ إضافة مصدر جديد`, customize the site URL and CSS selectors, or import a JSON file. `examples/madara-example.json` is a TEMPLATE with a placeholder URL, not a confirmed source. Real sites differ; no automatic guarantee of compatibility.

### Current features

- JSON source definitions; HTTPS only; configurable Madara-like CSS adapters.
- Optional 18+ source visibility (off by default); no blanket domain word blacklist.
- Catalog, search per source, global search, favorites, chapter list, vertical image reader.
- Offline chapter images; file directory is app-specific external storage.
- Arabic neon styling, supports right-to-left and Android foldables.

### Limitations

No CAPTCHA/Cloudflare bypass, no scripts-only or API-only extraction without dedicated adapters, no guaranteed compatibility with every website, no continuous background downloads. Check `docs/IMPLEMENTATION_AR.md`.

- Kotlin 2.0.21 / AGP 8.9.2 / Gradle 8.11.1 / Java 17 / Android SDK 35.
- minSdk 26, targetSdk 34, package `com.rrt.neonreader`.
- The original extracted APK code, proprietary icons, branding and licensing components are not bundled.
