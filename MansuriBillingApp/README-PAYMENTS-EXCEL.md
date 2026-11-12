# Mansuri Billing – Payments, Excel and Sharing update

## What's included
- Existing bill/customer/khata/reports UI is retained.
- Saving a bill refreshes `Downloads/Mansuri Billing/Mansuri_Bills.xlsx`.
- Saving or deleting a customer payment refreshes the same workbook.
- Workbook tabs: `Bills`, `Payments`, and `Customer Summary`.
- Customer ledger can be shared as text through Android's share sheet.
- The Excel workbook can be shared as an `.xlsx` attachment through Android's share sheet.
- Data remains local on the phone; no cloud database is introduced.

## Build in Android Studio
1. Extract this ZIP.
2. Open the `MansuriBillingApp` folder in Android Studio.
3. Let Gradle sync finish.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Install `app/build/outputs/apk/debug/app-debug.apk`.

Install over the existing app (same application ID) rather than uninstalling it, so Android can preserve the app's local data. Still, back up important data before updating.

## Notes
- The SheetJS library is currently loaded from its CDN, so the app needs internet the first time it loads that library. The generated workbook itself is saved locally.
- This source update has passed JavaScript syntax checking, but it has not been compiled or tested on a physical Android device in this environment.


## Bill design update
The generated bill preview now follows the supplied Mansuri Fruit Supplier receipt reference: maroon supplier header, customer/date band, ruled weight/rate table, expense and total columns, signature area, and thank-you footer. Existing bill calculations and Excel/payment features are retained.
