// تسريع التنقل (يُحقن قبل سكربتات الصفحة):
// 1) جلب مسبق لروابط نفس الموقع عند لمس الرابط أكثر من 90ms (يُلغى مع أي سحب/تمرير).
// 2) إيقاف الفيديوهات الصامتة (خلفيات متحركة) خارج الشاشة لتوفير المعالج والبطارية والحرارة.
(function () {
  try {
    if (window.__novaBoost) return;
    window.__novaBoost = 1;
    if (!/^https?:$/.test(location.protocol)) return;
    // يوتيوب/جوجل: تدير تحميلها ومشغّلها بنفسها
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var cn = navigator.connection || {};
    var slow = !!cn.saveData || /(^|-)2g$/.test(cn.effectiveType || '');

    // ---------- 1) الجلب المسبق ----------
    var seen = {}, count = 0, timer = 0, target = null;
    var SKIP_FILE = /\.(zip|apk|pdf|exe|mp4|mp3|mkv|rar|7z|iso|dmg|torrent)$/i;
    var SKIP_PATH = /(logout|signout|sign-out|log-out|delete|remove|unsubscribe|checkout|cart|payment|\/pay\b|confirm|activate|verify)/i;

    function prefetch(a) {
      try {
        if (slow || count >= 12 || !a || !a.href) return;
        var u = new URL(a.href, location.href);
        if (u.origin !== location.origin) return;                       // نفس الموقع فقط: لا تسريب لمواقع أخرى
        if (u.href.split('#')[0] === location.href.split('#')[0]) return;
        if (a.target && a.target !== '_self') return;
        if (a.hasAttribute('download') || SKIP_FILE.test(u.pathname) || SKIP_PATH.test(u.pathname + u.search)) return;
        if (seen[u.href]) return;
        seen[u.href] = 1; count++;
        var l = document.createElement('link');
        l.rel = 'prefetch'; l.href = u.href; l.as = 'document';
        (document.head || document.documentElement).appendChild(l);
      } catch (e) { }
    }

    document.addEventListener('touchstart', function (e) {
      var t = e.target;
      var a = t && t.closest ? t.closest('a[href]') : null;
      if (!a) return;
      target = a;
      clearTimeout(timer);
      timer = setTimeout(function () { prefetch(target); }, 90);
    }, { passive: true, capture: true });
    ['touchmove', 'touchend', 'touchcancel'].forEach(function (n) {
      document.addEventListener(n, function () { clearTimeout(timer); }, { passive: true, capture: true });
    });

    // ---------- 2) إيقاف الفيديو الصامت خارج الشاشة ----------
    if ('IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (en) {
          var v = en.target;
          try {
            if (!en.isIntersecting) {
              if (!v.paused && v.muted && !v.controls && !v.ended) { v.__nbp = 1; v.pause(); }
            } else if (v.__nbp) {
              v.__nbp = 0;
              var p = v.play();
              if (p && p.catch) p.catch(function () { });
            }
          } catch (e) { }
        });
      }, { rootMargin: '150px' });

      var watch = function () {
        try {
          var l = document.getElementsByTagName('video');
          for (var i = 0; i < l.length && i < 40; i++) {
            if (!l[i].__nbw) { l[i].__nbw = 1; io.observe(l[i]); }
          }
        } catch (e) { }
      };
      document.addEventListener('DOMContentLoaded', watch);
      window.addEventListener('load', function () { setTimeout(watch, 400); setTimeout(watch, 3000); });
    }
  } catch (e) { }
})();
