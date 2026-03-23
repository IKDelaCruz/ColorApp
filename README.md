# ColorSplash

An Android coloring app for kids ages 6-12. The app loads [color.itdcsystems.com](https://color.itdcsystems.com/) in a WebView where kids can pick a category (Animals, Dinosaurs, Vehicles, Nature, Fantasy, Space) and color AI-generated drawings.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 15)
- **Build:** Gradle with Kotlin DSL, Version Catalog

## Project Structure

```
app/src/main/
├── java/com/itdcsystems/color/
│   ├── MainActivity.kt          # WebView setup, download handling, image saving
│   └── ui/theme/
│       ├── Color.kt              # App color palette
│       ├── Theme.kt              # Material 3 theme (light/dark)
│       └── Type.kt               # Typography
├── res/
│   ├── drawable/                 # Adaptive icon layers (foreground, background, monochrome)
│   ├── mipmap-*/                 # Launcher icons at all densities
│   ├── values/                   # Colors, strings, themes
│   └── xml/                      # Backup rules
└── AndroidManifest.xml
```

## Key Features

- **WebView** loads `https://color.itdcsystems.com/` full-screen
- **Download to Gallery** — images from the coloring page save to `Pictures/ColorSplash/` via MediaStore. Handles blob URLs, data URLs, and regular HTTP downloads
- **Back navigation** works within the WebView (navigates back through coloring pages)
- **Progress bar** shows at the top while pages load
- **Edge-to-edge** layout with system bar insets
- **Adaptive icon** with color wheel PNG and monochrome variant for themed icons

## How It Works

`MainActivity.kt` contains everything:

| Component | Purpose |
|---|---|
| `ColorSplashApp` | Root composable — WebView + progress bar |
| `ColorSplashWebView` | Configures WebView with JS enabled, injects download interceptor JS |
| `ImageSaver` | `@JavascriptInterface` bridge — receives base64 image data from JS and saves to gallery via `MediaStore` |

### Download Flow

The web app creates images from a `<canvas>` element. Since WebView doesn't natively handle blob/data URL downloads, we:

1. Inject JavaScript on page load that intercepts `document.createElement('a')` clicks with `download` attribute
2. Convert blob/data URLs to base64 via `FileReader`
3. Pass base64 data to `AndroidImageSaver.saveBase64Image()` (Kotlin `@JavascriptInterface`)
4. Save to gallery using `MediaStore` with `IS_PENDING` flag for safe writes

A `DownloadListener` is also set as a fallback for regular HTTP downloads.

## Prerequisites

- **Android Studio** Meerkat or later
- **JDK 11+** (bundled with Android Studio at `jbr/`)
- **Android SDK 36**

## Build

### From Android Studio

**Build > Build Bundle(s) / APK(s) > Build APK(s)**

### From Command Line

```bash
# Set JAVA_HOME if not configured
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on Device

1. Transfer `app-debug.apk` to your Android phone
2. Enable **Install from unknown sources** if prompted
3. Tap the APK to install

## Changing the App Icon

Replace the PNG icon source and regenerate all sizes:

```python
# Requires: pip install Pillow
python -c "
from PIL import Image
import os

src = 'path/to/your-icon.png'  # 512x512 recommended
base = 'app/src/main/res'
img = Image.open(src).convert('RGBA')

sizes = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96, 'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
for folder, size in sizes.items():
    resized = img.resize((size, size), Image.LANCZOS)
    for name in ['ic_launcher.png', 'ic_launcher_round.png']:
        resized.save(os.path.join(base, folder, name), 'PNG')

# Adaptive foreground (padded for safe zone)
fg = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
icon = img.resize((368, 368), Image.LANCZOS)
fg.paste(icon, (72, 72), icon)
fg.save(os.path.join(base, 'drawable', 'ic_launcher_foreground.png'), 'PNG')
"
```

The adaptive icon background color is set in `res/drawable/ic_launcher_background.xml`.

## Changing the Web URL

Edit the constant in `MainActivity.kt`:

```kotlin
private const val COLOR_URL = "https://color.itdcsystems.com/"
```

## License

Proprietary - ITDC Systems Development Services
