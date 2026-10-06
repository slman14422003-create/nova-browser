// تحسين عرض صفحات الويب على الجوال (JavaScript + CSS). يُحقن قبل سكربتات الصفحة.
// لا يعمل في وضع سطح المكتب (لأن UA لا يحتوي Android) ولا يغيّر تخطيط المواقع المتجاوبة أصلاً.
(function () {
  try {
    if (window.__novaRender) return;
    window.__novaRender = 1;
    if (!/Android/i.test(navigator.userAgent)) return;

    // يوتيوب/جوجل: صفحاتها تدير تخطيطها بنفسها (المشغّل والواجهة الأصلية)، فلا نضيف شيئاً
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var css =
      'img,video{max-width:100%}' +                                   // صور/فيديو لا تتجاوز عرض الشاشة
      'pre{white-space:pre-wrap;overflow-wrap:anywhere}' +             // الأكواد الطويلة تلتف بدل التمرير الأفقي
      'html{-webkit-text-size-adjust:100%;text-size-adjust:100%;-webkit-tap-highlight-color:transparent}' + // منع تضخيم الخط + إزالة وميض اللمس
      'body{-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility}' +  // خطوط أنعم
      'a,button,summary,label,input,select,[role=button]{touch-action:manipulation}' + // إلغاء تأخير اللمس المزدوج
      'h1,h2,h3{text-wrap:balance}p,li,blockquote{text-wrap:pretty}' +                // أسطر أجمل (CSS حديث)
      'img{image-orientation:from-image}' +
      ':focus-visible{outline:2px solid #4c8dff;outline-offset:2px;border-radius:4px}' +
      '::selection{background:rgba(76,141,255,.35)}' +
      'html{scrollbar-width:thin;overscroll-behavior-x:none}' +                       // لا سحب جانبي عرضي مزعج
      '@media (prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}';

    // ورقة أنماط مبنية برمجياً: لا تتأثر بسياسات CSP التي تمنع <style> المضمّن، وتعمل قبل وجود <head>
    try {
      var sh = new CSSStyleSheet();
      sh.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
    } catch (e) { /* متصفح قديم: نتجاهل */ }

    // حارس التجاوز الأفقي: يعمل فقط للصفحات المتجاوبة (viewport=device-width) التي كسر عنصر واحد عرضها
    function guard() {
      try {
        var m = document.querySelector('meta[name=viewport]');
        if (!m || !/device-width/i.test(m.content)) return;
        var de = document.documentElement;
        if (de.scrollWidth > window.innerWidth * 1.02) de.style.setProperty('overflow-x', 'hidden');
      } catch (e) { }
    }
    window.addEventListener('load', function () { setTimeout(guard, 400); });

    // الصور: فك الترميز بعيداً عن خيط الواجهة، وتأجيل ما هو بعيد أسفل الصفحة (مرة واحدة وبحدّ أعلى)
    function imgs() {
      try {
        var l = document.images, n = Math.min(l.length, 300), vh = window.innerHeight * 2;
        for (var i = 0; i < n; i++) {
          var im = l[i];
          if (im.__nv) continue; im.__nv = 1;
          if (!im.hasAttribute('decoding')) im.decoding = 'async';
          if (!im.hasAttribute('loading') && !im.complete && im.getBoundingClientRect().top > vh) im.loading = 'lazy';
        }
      } catch (e) { }
    }
    document.addEventListener('DOMContentLoaded', function () { setTimeout(imgs, 50); });
    window.addEventListener('load', function () { setTimeout(imgs, 300); });
  } catch (e) { }
})();
