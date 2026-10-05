// وضع التطبيق (PWA) ليوتيوب ومواقع الذكاء الاصطناعي. يُحقن قبل سكربتات الصفحة، فقط على النطاقات المسموح بها من Pwa.kt.
(function () {
  try {
    if (window.__novaPwa || window.top !== window) return;
    var h = location.hostname;
    var yt = /(^|\.)youtube\.com$/.test(h);
    // google.com: نفعّل فقط على وضع الذكاء الاصطناعي (udm=50) وليس على البحث العادي
    if (/(^|\.)google\.[a-z.]+$/.test(h) && !/[?&]udm=50(&|$)/.test(location.search) && !/^\/ai(\/|$)/.test(location.pathname) && !/^gemini\./.test(h)) return;
    window.__novaPwa = 1;

    // 1) محاكاة التثبيت: المواقع ترى display-mode: standalone فتخفي لافتات «افتح في التطبيق/ثبّت»
    try {
      var mm = window.matchMedia.bind(window);
      window.matchMedia = function (q) {
        if (/display-mode\s*:\s*(standalone|minimal-ui)/i.test(String(q))) {
          return { matches: true, media: String(q), onchange: null,
            addListener: function () {}, removeListener: function () {},
            addEventListener: function () {}, removeEventListener: function () {}, dispatchEvent: function () { return false; } };
        }
        return mm(q);
      };
      Object.defineProperty(navigator, 'standalone', { get: function () { return true; }, configurable: true });
    } catch (e) {}

    // 2) مظهر التطبيق: بلا وميض لمس ولا أشرطة تمرير، ومنع ارتداد التمرير الذي يتعارض مع واجهات الدردشة
    var css =
      '*{-webkit-tap-highlight-color:transparent}' +
      'html,body{overscroll-behavior-y:none;-webkit-font-smoothing:antialiased}' +
      '::-webkit-scrollbar{display:none}' +
      (yt ? 'ytm-open-app-promo-renderer,ytm-mealbar-promo-renderer,ytm-app-install-promo-renderer{display:none!important}' : '');
    try {
      var sh = new CSSStyleSheet();
      sh.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
    } catch (e) {}

    // 3) يوتيوب: إخفاء زر «Open App / فتح التطبيق» (التطبيق نفسه هو المتصفح)
    if (yt) {
      var re = /^(open app|open in app|فتح التطبيق|افتح التطبيق|ouvrir l.application|abrir app|app öffnen)$/i;
      var t = 0;
      var sweep = function () {
        t = 0;
        try {
          var l = document.querySelectorAll('a,button,ytm-button-renderer,yt-button-shape');
          for (var i = 0; i < l.length; i++) {
            var e = l[i];
            if (e.__novaHid) continue;
            var s = (e.innerText || e.getAttribute('aria-label') || '').trim();
            if (s && s.length < 24 && re.test(s)) {
              var box = e.closest('ytm-button-renderer') || e;
              box.style.setProperty('display', 'none', 'important');
              e.__novaHid = 1;
            }
          }
        } catch (er) {}
      };
      var start = function () {
        sweep();
        new MutationObserver(function () { if (!t) t = setTimeout(sweep, 400); })
          .observe(document.documentElement, { childList: true, subtree: true });
      };
      if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
    }
  } catch (e) {}
})();
