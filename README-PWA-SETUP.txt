MANSURI BILLING — LOCAL PWA PACKAGE

WHAT THIS IS
- PWA-Website/ is the ready-to-host browser version.
- MansuriBillingApp/ is the original Android Studio project, with PWA support added to its HTML.
- No Firebase/database/cloud storage is used for customer data. Each device stores its own data in browser localStorage.

IMPORTANT LIMITATION
A PWA needs to be served from an HTTPS website to be installable and to use offline caching. Hosting these static files does NOT mean customer billing data is stored in the cloud.
PWA browser security does not allow a website to silently overwrite an Excel file in Downloads. Bills and payments are saved locally automatically; use Export Excel to download the latest workbook. The Android APK's native bridge can save/replace the workbook in Downloads automatically.

PUBLISH THE PWA
1. Upload the CONTENTS of PWA-Website/ to any HTTPS static website host (for example, GitHub Pages or Firebase Hosting).
2. Open the resulting HTTPS link in Chrome on Android.
3. Use the browser menu (⋮) > Install app / Add to Home screen.
4. Open the Mansuri icon. After the first successful load, the app shell is cached for offline opening.

DATA / BACKUP
- Data is device-specific and stored in that browser's local storage.
- Do not clear browser site data or uninstall/clear the app before exporting a backup.
- Export Excel regularly and share/copy it to a safe place. Excel export includes Bills, Payments and Customer Summary.
- A PWA's local browser data is not automatically shared with the Android APK or another phone.

TESTING
This package is source-only and has not been deployed to an HTTPS host or tested across physical phones. Test bill save, payment save, Excel export and offline reopening before using it for shop transactions.
