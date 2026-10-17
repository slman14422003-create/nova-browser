(function () {
  try {
    if (window.__novaPb) return;
    window.__novaPb = 1;
    if (window.top !== window) return;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|google\.[a-z.]+)$/.test(location.hostname)) return;

    var idle = window.requestIdleCallback || function (f) { return setTimeout(f, 200); };

    function hints() {
      try {
        var seen = {}, n = 0, here = location.origin;
        var els = document.querySelectorAll('script[src],link[href][rel=stylesheet],img[src]');
        for (var i = 0; i < els.length && i < 60 && n < 4; i++) {
          var u = els[i].src || els[i].href;
          if (!u) continue;
          var o;
          try { o = new URL(u, here).origin; } catch (e) { continue; }
          if (o === here || seen[o] || !/^https:/.test(o)) continue;
          seen[o] = 1; n++;
          var l = document.createElement('link');
          l.rel = 'preconnect'; l.href = o; l.crossOrigin = 'anonymous';
          document.head.appendChild(l);
        }
      } catch (e) { }
    }

    function frames() {
      try {
        var vh = window.innerHeight * 2, f = document.getElementsByTagName('iframe');
        for (var i = 0; i < f.length && i < 40; i++) {
          var x = f[i];
          if (x.hasAttribute('loading') || x.__nvl) continue;
          x.__nvl = 1;
          if (x.getBoundingClientRect().top > vh) x.loading = 'lazy';
        }
      } catch (e) { }
    }

    function hero() {
      try {
        var vh = window.innerHeight, im = document.images;
        for (var i = 0; i < im.length && i < 12; i++) {
          var r = im[i].getBoundingClientRect();
          if (r.top < vh && r.width >= 200 && r.height >= 120) {
            if (!im[i].hasAttribute('fetchpriority')) im[i].setAttribute('fetchpriority', 'high');
            if (!im[i].hasAttribute('loading')) im[i].loading = 'eager';
            break;
          }
        }
      } catch (e) { }
    }

    function run() { idle(function () { hero(); hints(); frames(); }); }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, { once: true });
    else run();
    window.addEventListener('load', function () { setTimeout(frames, 600); }, { once: true });
  } catch (e) { }
})();
