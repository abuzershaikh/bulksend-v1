# WhatsApp Contact Extractor

## Overview
WhatsApp Contact Extractor automatically extracts unsaved contact numbers from WhatsApp chats using Android Accessibility Service.

## Features
- ✅ Real-time extraction of phone numbers from WhatsApp
- ✅ Supports both WhatsApp and WhatsApp Business
- ✅ Extracts only numbers with country codes (starting with +)
- ✅ Automatic duplicate removal
- ✅ Export to VCF (vCard) format
- ✅ Export to CSV format
- ✅ Copy all numbers to clipboard
- ✅ Custom series naming for contacts
- ✅ Privacy-focused - all data stays on device

## How to Use

### Step 1: Enable Accessibility Service
1. Open the app and navigate to "Grab Unsaved Chat Contact"
2. Tap on "Enable Accessibility Service" button
3. Find "WhatsApp Contact Extractor" in the accessibility settings
4. Enable the service
5. Accept the privacy notice

### Step 2: Configure Contact Series
1. Enter a "Series Name" (e.g., "Customer", "Lead", "Client")
2. Set "Start From" number (default is 1)
3. Contacts will be named as: "Customer 1", "Customer 2", etc.

### Step 3: Extract Contacts
1. Open WhatsApp
2. Navigate to any chat or group
3. Scroll through messages containing phone numbers
4. The service will automatically extract numbers with country codes
5. Return to the app to see extracted contacts

### Step 4: Export Contacts
- **Copy All**: Copy all numbers to clipboard
- **Save VCF**: Export as vCard file (can be imported to phone contacts)
- **Save CSV**: Export as CSV file (for spreadsheet use)
- **Clear**: Clear all extracted contacts

## Technical Details

### Permissions Required
- `BIND_ACCESSIBILITY_SERVICE`: To read WhatsApp screen content
- `READ_EXTERNAL_STORAGE`: To save exported files (Android 12 and below)

### Package Detection
The service monitors these packages:
- `com.whatsapp` (WhatsApp)
- `com.whatsapp.w4b` (WhatsApp Business)

### Number Format
- Only extracts numbers starting with `+` (country code required)
- Minimum length: 8 digits
- Removes spaces, hyphens, and parentheses automatically
- Example valid formats:
  - +1234567890
  - +91 98765 43210
  - +44 (20) 1234-5678

### Data Storage
- Numbers are stored in SharedPreferences locally
- No data is sent to any server
- Data persists until manually cleared

## Privacy & Security
- ✅ All data stays on your device
- ✅ No internet connection required for extraction
- ✅ No data collection or analytics on extracted numbers
- ✅ Service only reads WhatsApp content when active
- ✅ Can be disabled anytime from accessibility settings

## Files Structure
```
waextract/
├── TextAccessibilityService.kt    # Main accessibility service
├── TextExtractActivity.kt         # UI for viewing/exporting contacts
└── README.md                       # This file
```

## Troubleshooting

### Service Not Working
1. Ensure accessibility service is enabled
2. Check if WhatsApp is installed
3. Restart the app and try again

### Numbers Not Extracting
1. Ensure numbers have country code (+)
2. Scroll slowly through WhatsApp chats
3. Check if service is still enabled in settings

### Export Not Working
1. Grant storage permissions if prompted
2. Check available storage space
3. Try a different export format

## Support
For issues or questions, contact support through the app's Support section.
