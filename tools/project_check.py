#!/usr/bin/env python3
"""فحص سريع للمشروع قبل البناء: توازن الأقواس، وجود المكونات في الـ Manifest، واستخدام Prefs.
الاستخدام:  python3 tools/project_check.py
"""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/nova/browser"
errors = []
warnings = []

def strip(code: str) -> str:
    code = re.sub(r'"""[\s\S]*?"""', '""', code)          # نصوص ثلاثية
    code = re.sub(r"/\*[\s\S]*?\*/", "", code)            # تعليقات كتلية
    code = re.sub(r"//[^\n]*", "", code)                  # تعليقات سطرية
    code = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', code)     # نصوص عادية
    code = re.sub(r"'(?:\\.|[^'\\\n])'", "''", code)      # محارف
    return code

# 1) توازن الأقواس
for f in sorted(SRC.glob("*.*")):
    if f.suffix not in (".kt", ".java"):
        continue
    c = strip(f.read_text(encoding="utf-8"))
    for a, b in ("{}", "()", "[]"):
        if c.count(a) != c.count(b):
            warnings.append(f"{f.name}: تحذير (قد يكون إيجابياً كاذباً) عدم توازن {a}{b} ({c.count(a)} مقابل {c.count(b)})")

# 2) مكونات الـ Manifest موجودة
mf = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for name in re.findall(r'android:name="\.(\w+)"', mf):
    if not (SRC / f"{name}.kt").exists() and not (SRC / f"{name}.java").exists():
        errors.append(f"Manifest يشير إلى .{name} ولا يوجد ملف له")

# 2.5) تدقيق أمني للـ Manifest
if 'android:allowBackup="false"' not in mf: warnings.append("أمان: allowBackup غير معطّل (قد تُسرَّب بيانات عبر النسخ الاحتياطي)")
if 'android:debuggable="true"' in mf: errors.append("أمان: debuggable=true في Manifest")
if "EnableSafeBrowsing" not in mf: warnings.append("أمان: لم يُفعَّل Safe Browsing في Manifest")
for m in re.finditer(r"<(?:service|receiver|provider)[^>]*android:exported=\"true\"", mf):
    errors.append("أمان: مكوّن مكشوف exported=true: " + m.group(0)[:60])
if re.search(r"setJavaScriptEnabled|addJavascriptInterface", "".join(f.read_text(encoding="utf-8") for f in SRC.glob("*.kt"))) and "addJavascriptInterface" in "".join(f.read_text(encoding="utf-8") for f in SRC.glob("*.kt")):
    warnings.append("أمان: addJavascriptInterface مستخدم — تأكد من حصره بمصادر موثوقة")

# 2.7) ملفات assets المطلوبة
AS = ROOT / "app/src/main/assets"
for need in ("blocklist.txt", "privacy.js"):
    if not (AS / need).exists(): errors.append(f"assets/{need} مفقود")
bl = AS / "blocklist.txt"
if bl.exists():
    doms = [l.strip().lower() for l in bl.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    for d in doms:
        if not re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", d): errors.append(f"blocklist: نطاق غير صالح: {d}")
    if len(doms) != len(set(doms)): warnings.append("blocklist: نطاقات مكررة")
    for must in ("trackersimulator.org", "eviltracker.net"):
        if must not in doms: warnings.append(f"blocklist: {must} غير موجود")
if (AS / "privacy.js").exists() and "__SEED__" not in (AS / "privacy.js").read_text(encoding="utf-8"):
    errors.append("privacy.js: العلامة __SEED__ مفقودة")

# 3) كل Prefs.xxx المستخدمة معرّفة
prefs = (SRC / "Settings.kt").read_text(encoding="utf-8")
defined = set(re.findall(r"(?:va[lr])\s+(\w+)\s+by\s+mutable|fun\s+(\w+)\(", prefs))
defined = {x for t in defined for x in t if x}
for f in SRC.glob("*.kt"):
    for m in set(re.findall(r"\bPrefs\.(\w+)", f.read_text(encoding="utf-8"))):
        if m not in defined and m != "engines":
            errors.append(f"{f.name}: Prefs.{m} غير معرّف")

print("\n".join(warnings + errors) if (warnings or errors) else "OK: لا أخطاء ظاهرة")
if not errors and warnings:
    print("لا أخطاء مانعة")
sys.exit(1 if errors else 0)
