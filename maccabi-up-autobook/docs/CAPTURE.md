# Step 1: Map the upapp API

Goal: record the requests upapp makes when you log in and book one class, so
`MaccabiApiClient.kt` can make the same calls. You need a computer and
about one evening.

## 0. First, check the web

Log in at maccabi4u.co.il from a desktop browser and look for upapp/Upbody
booking. If booking works on the web, use the browser's DevTools → Network tab
instead of everything below. It's much easier.

## 1. Set up interception (Android emulator)

Newer Android apps don't trust user-installed certificates, so use an emulator
image you can root:

1. Install Android Studio. Create an emulator with a **"Google APIs"** image
   (not "Google Play"), API 30–33.
2. Install mitmproxy on the computer: `pipx install mitmproxy`, then run `mitmweb`.
3. Install the mitmproxy CA as a **system** certificate in the emulator. Follow
   the mitmproxy docs: "Install system CA on Android emulator".
4. Point the emulator's proxy at `10.0.2.2:8080` (Settings → Network → Proxy).
5. Install upapp in the emulator by copying the APK from your phone, or
   download it from a site you trust.

If upapp refuses to connect (certificate pinning), run it with Frida plus a
generic SSL-unpinning script, or use HTTP Toolkit's "Android device via ADB"
mode, which handles most cases automatically.

## 2. Record these flows

Keep `mitmweb` recording. In upapp:

1. **Log in**: enter your ID number and wait for the SMS on your real phone.
   The code is sent to your phone number, not the emulator; that's fine. Enter it.
2. **Browse to Friday Pilates** at your studio.
3. **Book one class** (or book and then cancel within the free-cancellation window).
4. **Open "my bookings".**

## 3. Note for each request

| Flow | What to record |
|---|---|
| Request SMS | URL, method, headers (especially app version and device ID), body with the ID number |
| Verify code | URL, body, and **which response field is the token** (and its expiry) |
| List classes | URL, how the date/studio/activity is filtered, the JSON shape of one class (id, name, studio, start time, spots) |
| Book | URL, body (class ID?), and the response for **success**, **full**, **not open yet** |
| My bookings | URL and response shape |

Also note:
- Any fixed headers the app always sends (`User-Agent`, `x-app-version`, device ID).
- Whether the login asks for anything besides the ID number (date of birth, a second ID).
- The exact text and sender of the SMS. This goes into the SMS keyword setting.
- When booking opens for Friday classes (how many days before, at what time).

Export the flows from mitmweb (File → Save) and **remove your ID number,
codes and tokens** before sharing them with anyone, including when asking
Claude to fill in `MaccabiApiClient.kt`.

## 4. Fill in the client

Implement the five methods in `MaccabiApiClient.kt` with OkHttp, mapping
responses into `GymClass` and `BookResult`. Then test with **Dry run** first.
After that, do one real run with a class you're happy to attend.
