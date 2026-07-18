# דו״ח מסירה — גרסה 1.0.0

תאריך אימות: 16 ביולי 2026

## ארטיפקט

- חבילה: `com.yv.bbttracker`
- `versionName`: `1.0.0`
- `versionCode`: `1`
- מנוע ניתוח: `bbt-awareness-1.0.1`
- APK: `release/BBT-Fertility-Tracker-v1.0.0.apk`
- SHA-256: `05ecd1e563e7ea3200f4a597a18dc0e76784bec6ddb6490b4ad3e75e7e27057d`
- חתימה: APK Signature Scheme v2, מפתח RSA בגודל 4096 סיביות.

## אימות שבוצע

- 63 בדיקות JVM עברו.
- חמש בדיקות מכשיר עברו על אמולטור Android 16 / API 36, כולל Room ו־smoke test של RTL וגופן מוגדל.
- Android Lint ל־release עבר.
- R8 ומזעור משאבים הופעלו בבניית release.
- ה־APK עבר `apksigner verify --verbose --print-certs`.
- allowlist ההרשאות עבר; אין הרשאת אינטרנט, מיקום, Bluetooth, מצלמה, מיקרופון או אחסון רחב.
- ה־APK הסופי הותקן, הופעל בהפעלה קרה והציג את מסך ה־onboarding בעברית וב־RTL ללא קריסה.
- בגרסת debug הושלם ה־onboarding ונבדק גם מסך ״היום״ בעברית.

## מה עדיין דורש בדיקת מכשיר פיזי

- OnePlus Nord 3 5G / OxygenOS לא היה מחובר בעת המסירה.
- יש לבדוק על המכשיר בפועל ביומטריה, חסימת צילום מסך ו־recents, התראות לאחר reboot וניהול סוללה, מצב כהה, TalkBack ובוררי קבצים לייצוא/שחזור.
- לפני הפצה רחבה יש להשלים את `docs/RELEASE_CHECKLIST_HE.md` ולגבות את מפתח החתימה והסיסמה במקום מאובטח. אובדן המפתח ימנע התקנת עדכונים מעל גרסה זו.
