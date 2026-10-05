// تحسين عرض صفحات الويب على الجوال (JavaScript + CSS). يُحقن قبل سكربتات الصفحة.
// لا يعمل في وضع سطح المكتب (لأن UA لا يحتوي Android) ولا يغيّر تخطيط المواقع المتجاوبة أصلاً.
(function () {
  try {
    if (window.__novaRender) return;
    window.__novaRender = 1;
    if (!/Android/i.test(navigator.userAgent)) return;

    var css =
      'img,video{max-width:100%}' +                                   // صور/فيديو لا تتجاوز عرض الشاشة
      'pre{white-space:pre-wrap;overflow-wrap:anywhere}' +             // الأكواد الطويلة تلتف بدل التمرير الأفقي
      'html{-webkit-text-size-adjust:100%;text-size-adjust:100%}';     // منع تضخيم الخط التلقائي

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
  } catch (e) { }
})();
