/* Nova scrollguard.js — يمنع «السحب للتحديث» من خطف التمرير الداخلي.
 * المشكلة: التطبيق يضع الـ WebView داخل SwipeRefreshLayout الذي لا يرى إلا تمرير الصفحة الرئيسي (scrollY).
 * أي صفحة تتمرّر داخل عنصر (body بتمرير خاص، قائمة، دردشة، نافذة منبثقة، إطار iframe) يبقى scrollY فيها صفراً،
 * فكان السحب لأسفل لرجوع المحتوى للأعلى يُلتقط كسحب للتحديث: يتوقف التمرير ويتحرك المؤشر ويُعاد تحميل الصفحة.
 * الحل: عند بدء كل لمسة نفحص سلسلة العناصر تحت الإصبع، فإن كان فيها عنصر تمرّر لأسفل (scrollTop>0) نُعلم التطبيق
 * كي يعطّل السحب للتحديث في هذه اللمسة فقط. القيمة تُرسل مع كل لمسة (0 أو 1) فلا تبقى حالة قديمة أبداً.
 * يعمل في كل الإطارات؛ كل شيء داخل try/catch وأي فشل = السلوك السابق (تفعيل السحب للتحديث).
 */
(function () {
  'use strict';
  try {
    if (window.__novaSg) return;
    window.__novaSg = 1;
    if (!window.NovaScroll) return;

    function inner(e) {
      try {
        var p = e.composedPath ? e.composedPath() : null;
        if (!p) { var t = e.target, a = []; while (t && a.length < 60) { a.push(t); t = t.parentNode; } p = a; }
        for (var i = 0; i < p.length && i < 80; i++) {
          var el = p[i];
          if (el && el.nodeType === 1 && el.scrollTop > 0) return 1;
        }
      } catch (x) { }
      return 0;
    }

    document.addEventListener('touchstart', function (e) {
      try {
        if (e.touches && e.touches.length > 1) return;        // لمسة ثانية (تكبير): لا نغيّر الحالة
        window.NovaScroll.postMessage(inner(e) ? '1' : '0');
      } catch (x) { }
    }, { capture: true, passive: true });
  } catch (e) { }
})();
