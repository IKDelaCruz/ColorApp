# ColorSplash iOS — Developer Specification

This document describes the ColorSplash Android app in full detail so an iOS/Swift developer can build an identical iOS version.

## Overview

ColorSplash is a WebView-based coloring app for kids ages 6–12. It loads `https://color.itdcsystems.com/` where kids pick a category (Animals, Dinosaurs, Vehicles, Nature, Fantasy, Space) and color AI-generated drawings. The app has download-to-gallery support, offline handling, and a kid-friendly UI.

**The iOS equivalent uses:** `WKWebView`, `UIKit` or `SwiftUI`, `Photos` framework.

---

## App Behavior

### 1. Launch Flow

1. App launches → system splash screen shows (app icon)
2. WebView begins loading `https://color.itdcsystems.com/`
3. A thin progress bar appears at the top of the screen during loading
4. Once the page finishes loading, the progress bar fades out
5. User interacts with the coloring web app full-screen

### 2. Navigation

- The WebView handles all in-app navigation (category selection, coloring pages, etc.)
- **Back navigation**: The device back button navigates backward within the WebView history (Android `BackHandler` → iOS: custom back gesture or navigation)
- **URL restriction**: Only URLs starting with `https://color.itdcsystems.com` are allowed within the WebView. External URLs are blocked.

### 3. Coloring Page URLs

- Home: `https://color.itdcsystems.com/`
- New drawing: `https://color.itdcsystems.com/coloring/new/{category}`
- Categories: `animals`, `dinosaurs`, `vehicles`, `nature`, `fantasy`, `space`

---

## Feature 1: WebView Configuration

The WebView must be configured with these settings:

| Setting | Value | iOS Equivalent |
|---------|-------|----------------|
| JavaScript | Enabled | `WKWebViewConfiguration` → `preferences.javaScriptEnabled` (default true) |
| DOM Storage | Enabled | Enabled by default in WKWebView |
| Media playback without user gesture | Yes | `configuration.mediaTypesRequiringUserActionForPlayback = []` |
| Content access | Allowed | Default in WKWebView |

### Edge-to-Edge Layout

The WebView fills the entire screen but respects the system safe areas (status bar, home indicator). On iOS, use `edgesIgnoringSafeArea` selectively or respect `safeAreaInsets`.

---

## Feature 2: Download to Photo Gallery

This is the most complex feature. The coloring web app has a download button that creates images from an HTML `<canvas>` element. The download mechanism uses **blob URLs** or **data URLs** — not regular HTTP file downloads.

### How the Web App Download Works

1. User taps "Download" on the coloring page
2. The JavaScript creates a canvas-based image
3. It creates a blob URL via `URL.createObjectURL(blob)` or a data URL via `canvas.toDataURL()`
4. It creates an `<a>` element with a `download` attribute and triggers a click

### Why This Needs Special Handling

Standard WebView (both Android and iOS) does NOT handle blob/data URL downloads natively. You must intercept them.

### Implementation Strategy for iOS

**Option A: JavaScript Message Handler (Recommended)**

Inject JavaScript that intercepts download attempts and sends the image data to Swift via `WKScriptMessageHandler`.

**JavaScript to inject on page load:**

```javascript
(function() {
    if (window._colorSplashDownloadPatched) return;
    window._colorSplashDownloadPatched = true;

    // Intercept dynamically created <a> elements with download attribute
    var origCreateElement = document.createElement.bind(document);
    document.createElement = function(tag) {
        var el = origCreateElement(tag);
        if (tag.toLowerCase() === 'a') {
            var origClick = el.click.bind(el);
            el.click = function() {
                if (el.href && el.hasAttribute('download')) {
                    var href = el.href;
                    if (href.startsWith('blob:')) {
                        fetch(href)
                            .then(function(r) { return r.blob(); })
                            .then(function(blob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    // Send base64 data to Swift
                                    window.webkit.messageHandlers.imageSaver.postMessage({
                                        data: reader.result,
                                        mimeType: blob.type || 'image/png'
                                    });
                                };
                                reader.readAsDataURL(blob);
                            });
                        return;
                    } else if (href.startsWith('data:')) {
                        var mimeMatch = href.match(/^data:([^;,]+)/);
                        var mime = mimeMatch ? mimeMatch[1] : 'image/png';
                        window.webkit.messageHandlers.imageSaver.postMessage({
                            data: href,
                            mimeType: mime
                        });
                        return;
                    }
                }
                origClick();
            };
        }
        return el;
    };

    // Intercept window.open for blob URLs
    var origOpen = window.open;
    window.open = function(url) {
        if (url && url.startsWith('blob:')) {
            fetch(url)
                .then(function(r) { return r.blob(); })
                .then(function(blob) {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        window.webkit.messageHandlers.imageSaver.postMessage({
                            data: reader.result,
                            mimeType: blob.type || 'image/png'
                        });
                    };
                    reader.readAsDataURL(blob);
                });
            return null;
        }
        return origOpen.apply(this, arguments);
    };
})();
```

**Swift handler pseudocode:**

```swift
// 1. Register message handler
let config = WKWebViewConfiguration()
let contentController = WKUserContentController()
contentController.add(self, name: "imageSaver")
config.userContentController = contentController

// 2. Inject JavaScript as WKUserScript (runs on documentEnd)

// 3. Implement WKScriptMessageHandler
func userContentController(_ controller: WKUserContentController,
                           didReceive message: WKScriptMessage) {
    guard let body = message.body as? [String: String],
          let base64String = body["data"],
          let mimeType = body["mimeType"] else { return }

    // Strip "data:image/png;base64," prefix
    let pureBase64 = base64String.components(separatedBy: ",").last ?? base64String
    guard let data = Data(base64Encoded: pureBase64),
          let image = UIImage(data: data) else { return }

    // Save to Photos
    UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
    // Or use PHPhotoLibrary for album-specific saving
}
```

### Saving to a Specific Album

On Android, images save to `Pictures/ColorSplash/`. On iOS, create a "ColorSplash" album:

```swift
import Photos

func saveToColorSplashAlbum(image: UIImage) {
    let albumName = "ColorSplash"

    // Find or create album
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "title = %@", albumName)
    var album = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions).firstObject

    if album == nil {
        PHPhotoLibrary.shared().performChanges({
            PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
        }) { success, error in
            if success {
                album = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions).firstObject
                self.addImageToAlbum(image: image, album: album!)
            }
        }
    } else {
        addImageToAlbum(image: image, album: album!)
    }
}

func addImageToAlbum(image: UIImage, album: PHAssetCollection) {
    PHPhotoLibrary.shared().performChanges({
        let request = PHAssetChangeRequest.creationRequestForAsset(from: image)
        let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
        let placeholder = request.placeholderForCreatedAsset!
        albumChangeRequest?.addAssets([placeholder] as NSArray)
    }) { success, _ in
        DispatchQueue.main.async {
            // Show success/failure feedback
        }
    }
}
```

### Required Permission

Add to `Info.plist`:
```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>ColorSplash needs access to save your coloring pages to your photo gallery.</string>
```

---

## Feature 3: Offline Screen

When the device has no internet connection, show a kid-friendly offline screen instead of the default WebView error.

### Detection

- **iOS:** Implement `WKNavigationDelegate` → `webView(_:didFailProvisionalNavigation:withError:)` for initial load failures and `webView(_:didFail:withError:)` for in-page failures
- Check for `NSURLErrorNotConnectedToInternet`, `NSURLErrorNetworkConnectionLost`, `NSURLErrorTimedOut`
- Alternatively, use `NWPathMonitor` from `Network` framework for proactive connectivity monitoring

### Offline Screen UI

```
┌─────────────────────────────┐
│                             │
│                             │
│            🎨               │  ← 72pt emoji
│                             │
│     Oops! No Internet       │  ← Bold, 28pt, primary purple (#7C4DFF)
│                             │
│   We need the internet to   │  ← Regular, 16pt, 60% opacity text
│   load your coloring pages. │
│   Check your Wi-Fi and      │
│   try again!                │
│                             │
│      ┌──────────────┐       │
│      │   Try Again   │       │  ← Rounded button, primary purple bg, white text
│      └──────────────┘       │
│                             │
└─────────────────────────────┘
```

### Behavior

- Offline screen overlays the WebView with full background (not transparent)
- Animated fade-in when going offline
- "Try Again" button calls `webView.reload()`
- When a real page starts loading successfully, the offline screen fades out
- Only trigger offline screen for main frame errors, NOT subresource failures (images, CSS, JS)

---

## Feature 4: Progress Bar

- Thin `LinearProgressIndicator` at the very top of the screen
- Tracks WebView loading progress (0–100%)
- Uses `WKWebView`'s `estimatedProgress` property (KVO observable)
- Animated fade-out when loading completes
- Hidden when offline screen is visible
- Color: primary purple `#7C4DFF` with 15% opacity track

---

## Color Palette

### Light Theme
| Token | Hex | Usage |
|-------|-----|-------|
| Primary | `#7C4DFF` | Buttons, progress bar, headings |
| Secondary | `#FF4081` | Accent elements |
| Tertiary | `#FFD740` | Highlight elements |
| Teal | `#00E5FF` | Decorative |
| Green | `#69F0AE` | Decorative |
| Orange | `#FFAB40` | Decorative |
| Background | `#F8F5FF` | Screen background (light lavender) |
| Surface | `#FFFFFF` | Card/container backgrounds |
| On Background | `#2D2D3A` | Primary text color |

### Dark Theme
| Token | Hex | Usage |
|-------|-----|-------|
| Primary | `#B388FF` | Buttons, progress bar, headings |
| Secondary | `#FF80AB` | Accent elements |
| Background | `#1A1A2E` | Screen background (dark navy) |
| Surface | `#252542` | Card/container backgrounds |
| On Background | `#F0F0F5` | Primary text color |

### System Theme Detection

Follow the device system theme (light/dark mode). Set status bar style accordingly:
- Light theme → dark status bar icons
- Dark theme → light status bar icons

---

## Typography

| Style | Font | Weight | Size | Line Height |
|-------|------|--------|------|-------------|
| Headline Large | System Sans-Serif | Bold | 28pt | 36pt |
| Headline Medium | System Sans-Serif | Semibold | 22pt | 28pt |
| Body Large | System Sans-Serif | Regular | 16pt | 24pt |
| Label Large | System Sans-Serif | Medium | 14pt | 20pt |

On iOS, use the system font (`SF Pro`) — no custom fonts needed.

---

## App Icon

The app uses a **color wheel** PNG icon. The same source PNG (`color-wheel.png`) should be used to generate the iOS app icon set.

### iOS Icon Requirements

Generate from the source PNG at these sizes for the asset catalog:

| Size | Usage |
|------|-------|
| 1024x1024 | App Store |
| 180x180 | iPhone @3x |
| 120x120 | iPhone @2x |
| 167x167 | iPad Pro @2x |
| 152x152 | iPad @2x |

Background: White (the color wheel has a transparent background, iOS will show white).

---

## App Metadata

| Property | Value |
|----------|-------|
| App Name | ColorSplash |
| Bundle ID (suggested) | `com.itdcsystems.color` |
| Deployment Target | iOS 15.0+ (for modern WKWebView features) |
| Orientations | Portrait only (kid-friendly, matches web app layout) |
| Requires Internet | Yes (WebView-based) |

---

## Version Management

### Android Approach (for reference)
Uses a `version.properties` file with auto-incrementing `VERSION_CODE` and `VERSION_PATCH` on every build.

### Suggested iOS Approach
Use an Xcode build phase script to auto-increment the build number:

```bash
# Run Script Build Phase
buildNumber=$(/usr/libexec/PlistBuddy -c "Print CFBundleVersion" "$INFOPLIST_FILE")
buildNumber=$(($buildNumber + 1))
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $buildNumber" "$INFOPLIST_FILE"
```

Or use `agvtool` for build number management:
```bash
agvtool next-version -all
```

---

## Complete Screen Map

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  System      │────▶│  WebView         │────▶│  Coloring Page    │
│  Splash      │     │  (Home Page)     │     │  (Canvas + Tools) │
│  (App Icon)  │     │  Category Grid   │     │  Download Button  │
└─────────────┘     └─────────────────┘     └──────────────────┘
                           │                         │
                    (No Internet)              (Tap Download)
                           ▼                         ▼
                    ┌─────────────┐          ┌──────────────┐
                    │  Offline     │          │  Save to      │
                    │  Screen      │          │  Photo Gallery │
                    │  + Retry Btn │          │  + Toast/Alert │
                    └─────────────┘          └──────────────┘
```

---

## Testing Checklist

- [ ] WebView loads `https://color.itdcsystems.com/` correctly
- [ ] JavaScript works (category buttons, coloring tools respond)
- [ ] Back navigation works within WebView
- [ ] External URLs are blocked (stays within color.itdcsystems.com)
- [ ] Progress bar shows during page load and fades out
- [ ] Download button saves image to Photos / "ColorSplash" album
- [ ] Toast/alert shows "Saved to Gallery!" on successful download
- [ ] Offline screen shows when Wi-Fi is off (not the default WebView error)
- [ ] "Try Again" button reloads the page
- [ ] Offline screen hides when connection is restored and page loads
- [ ] Light/dark theme follows system setting
- [ ] App icon displays correctly (color wheel on white background)
- [ ] Status bar style matches theme (dark icons on light, light icons on dark)
- [ ] Safe area insets are respected (content not under status bar or home indicator)

---

## Source Repository

- **Android version:** https://github.com/IKDelaCruz/ColorApp
- Clone and reference the Android implementation for exact behavior details
