# توقيع الإصدار (Release)

## لماذا؟
بدون مفتاح ثابت يُوقَّع كل بناء على GitHub بمفتاح debug مختلف، فلا يمكن تحديث التطبيق فوق نسخة سابقة (يلزم حذفها أولاً). مع مفتاح الإصدار الثابت تتحدّث النسخ فوق بعضها.

## توليد المفتاح (صلاحية 30 سنة)
```bash
bash tools/gen-keystore.sh      # ينشئ signing/nova-release.jks + keystore.properties + SECRETS.txt (RSA 4096، 10950 يوماً)
```

## إعداد GitHub (مرة واحدة)
في المستودع: **Settings → Secrets and variables → Actions → New repository secret**، أضف:

| الاسم | القيمة |
|---|---|
| `KEYSTORE_BASE64` | محتوى الملف مشفّراً base64 (موجود في `SECRETS.txt`) |
| `KEYSTORE_PASSWORD` | كلمة مرور المفتاح |
| `KEY_ALIAS` | `nova` |
| `KEY_PASSWORD` | نفس كلمة المرور |

## إصدار نسخة
```bash
git tag v1.5.0 && git push origin v1.5.0
```
يبني `release.yml` ملفي APK وAAB موقّعين، ويرفقهما مع `SHA256SUMS.txt` في GitHub Release.

## بناء محلي موقّع
انسخ `keystore.properties.example` إلى `keystore.properties` وضع ملف `.jks` في `signing/` ثم `gradle assembleRelease`.

## تحذيرات
- **انسخ ملف `.jks` احتياطياً في مكان آمن**: ضياعه يعني استحالة تحديث التطبيق على Google Play.
- لا ترفعه إلى git أبداً (`.gitignore` يمنع ذلك).
- النسخ المثبتة سابقاً (الموقّعة بمفتاح debug) تتطلب حذفها مرة واحدة قبل تثبيت النسخة الجديدة.

## التحديث من داخل التطبيق
- يفحص التطبيق `releases/latest` في مستودعك (يُؤخذ اسمه تلقائياً من `GITHUB_REPOSITORY` عند البناء على GitHub، أو ضع `updateRepo=owner/repo` في `gradle.properties` للبناء المحلي).
- شرط نجاح التثبيت: **نفس مفتاح التوقيع دائماً** + رمز إصدار أعلى (يُشتق من الوسم: `v1.7.0` ← 10700).
- يتحقق التطبيق من SHA-256 في `SHA256SUMS.txt` ومن اسم الحزمة قبل التثبيت.
