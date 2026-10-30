# Laundry POS

Offline laundry / dry-cleaning counter app for the SUNMI D3 Mini (Android 13). Kotlin + Jetpack Compose, Room (SQLite), SUNMI built-in printer.

## Features

- **New order**: phone lookup (returning customers auto-fill), tap-to-add price grid by service, per-piece and per-kg items, discount, stain/damage notes, ready-by date.
- **GST invoice / receipt**: sequential invoice numbers per financial year (`INV/26-27/0001`), CGST + SGST split, prices inclusive or exclusive of GST, round-off, 58 mm or 80 mm paper, optional customer + shop copies. Without GST it prints a plain receipt.
- **Order tracking**: Received → Ready → Delivered (or Cancelled; the invoice number is kept).
- **WhatsApp updates (tap-to-send)**: order received, ready for pickup (sent automatically on "Mark ready & notify"), delivered, dues reminder. Opens WhatsApp / WhatsApp Business with the message filled in. If neither is installed, it shows a QR code so the shop phone can scan and send.
- **Customers & dues**: history, outstanding balance, record payments (UPI / Cash / Card).
- **Daily report**: orders, billed, collected by mode, delivered, ready-not-collected, total dues. Printable.
- **Data**: JSON backup and restore, invoices CSV export for the accountant.

Payment collection itself is out of scope; the shop's UPI QR scanner handles it.

## Build

Requires JDK 17+ and the Android SDK (here `D:\dev\android-sdk`, set in `local.properties`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install on the D3 Mini

SUNMI devices bound to a partner account can block apps that aren't from the SUNMI App Store. Try USB debugging first:

1. On the device: Settings → About → tap *Build number* 7 times, then Developer options → enable *USB debugging*.
2. Connect a USB cable and accept the prompt on the device.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`

If the install is rejected because of an install restriction, the device must be unbound from the previous owner's SUNMI partner account (via SUNMI support or the reseller, with proof of purchase and the serial number).

## First run

1. Settings: shop name, address, phone, GST details (if registered), paper size. Press *Test print*.
2. Prices: replace the sample price list with your own.
3. Save a backup from Settings regularly; all data lives only on the device.
