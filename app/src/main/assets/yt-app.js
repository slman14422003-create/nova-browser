/* Nova YouTube app bridge — يحوّل m.youtube.com إلى مصدر بيانات للواجهة الأصلية (قوائم الفيديو، صفحة المشاهدة، التعليقات).
 * المشغّل نفسه يبقى مشغّل الصفحة الحقيقي (إعلانات وجودة وتحكم كما في الموقع)، والواجهة الأصلية تحيط به.
 * native → Kotlin:  window.NovaYtApp.postMessage(JSON)   {t:'ys'|'diag'}
 * Kotlin → native:  window.__novaYtApp.open/search/go/more/chip/mode/like/subscribe/comments/closeComments/expand/diag
 */
(function () {
  'use strict';
  try {
    if (window.__novaYtApp || window.top !== window || !window.NovaYtApp) return;
    var CFG = __CFG__;
    var mode = !!CFG.on, cOpen = false;

    function post(o) { try { window.NovaYtApp.postMessage(JSON.stringify(o)); } catch (e) {} }
    function qa(sel, root) { try { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); } catch (e) { return []; } }
    function q1(sel, root) { return qa(sel, root)[0] || null; }
    function tx(el) { return el ? (el.innerText || el.textContent || '').trim() : ''; }
    function lines(el) { return tx(el).split('\n').map(function (l) { return l.trim(); }).filter(Boolean); }
    function pick(root, sels) {
      if (!root) return '';
      for (var i = 0; i < sels.length; i++) { var t = tx(q1(sels[i], root)); if (t) return t; }
      return '';
    }
    function params() { return new URLSearchParams(location.search); }

    // ───────── وضع المشغّل: يظهر المشغّل وحده أعلى الصفحة ويُخفى الباقي (تعرضه الواجهة الأصلية) ─────────
    var STYLE_ID = 'nova-yt-app-style';
    var CSS =
      'html,body{overflow:hidden!important;background:#000!important}' +
      'ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer,ytm-mealbar-promo-renderer{display:none!important}' +
      '#player-container-id,.player-container{position:fixed!important;top:0!important;left:0!important;right:0!important;' +
      'width:100vw!important;height:56.25vw!important;max-height:56.25vw!important;z-index:2147483000!important;background:#000!important}' +
      'ytm-engagement-panel,ytm-bottom-sheet-renderer{opacity:0!important;pointer-events:none!important}';
    function applyMode() {
      var want = mode && location.pathname === '/watch';
      var s = document.getElementById(STYLE_ID);
      if (want && !s) {
        s = document.createElement('style'); s.id = STYLE_ID; s.textContent = CSS;
        (document.head || document.documentElement).appendChild(s);
      } else if (!want && s) s.remove();
    }

    // ───────── استخراج بطاقات الفيديو ─────────
    var CARD = 'ytm-rich-item-renderer,ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-media-item,ytm-playlist-video-renderer,ytm-video-card-renderer';
    var DUR = /^\d{1,2}:\d{2}(:\d{2})?$/;
    var BADGE = /^(LIVE|NEW|PREMIERE|Now playing|مباشر|جديد)$/i;
    var cache = typeof WeakMap === 'function' ? new WeakMap() : null;
    function cardOf(a) {
      var c = a.closest(CARD); if (c) return c;
      var p = a;
      for (var i = 0; i < 6 && p.parentElement; i++) { p = p.parentElement; if (p.querySelector('img') && tx(p).length > 6) return p; }
      return a;
    }
    // صورة القناة (أفاتار): تأتي من yt3.ggpht.com أو googleusercontent؛ نقبل https فقط
    function avOf(root) {
      if (!root) return '';
      var im = qa('img', root);
      for (var i = 0; i < im.length; i++) {
        var u = im[i].currentSrc || im[i].src || '';
        if (/^https:\/\/(yt3\.ggpht\.com|yt4\.ggpht\.com|[\w-]+\.googleusercontent\.com)\//.test(u)) return u;
      }
      return '';
    }
    function parseCard(id, c) {
      var ls = lines(c), dur = '', rest = [];
      ls.forEach(function (l) { if (!dur && DUR.test(l)) dur = l; else if (!BADGE.test(l)) rest.push(l); });
      var title = pick(c, ['h3', '.media-item-headline', '.compact-media-item-headline', 'h4', '[class*=headline]']) || rest[0] || '';
      if (!title) return null;
      var others = rest.filter(function (l) { return l !== title && title.indexOf(l) !== 0 && l.indexOf(title) !== 0; });
      var chan = '', info = [];
      others.slice(0, 3).join(' • ').split(/\s[•·]\s/).forEach(function (p) {
        p = p.trim(); if (!p) return;
        if (/\d/.test(p)) info.push(p); else if (!chan) chan = p;
      });
      return { id: id, t: title, c: chan, m: info.join(' • '), d: dur, a: avOf(c) };
    }
    function items(curId) {
      var seen = {}, out = [];
      qa('a[href*="/watch?v="]').forEach(function (a) {
        var m = (a.getAttribute('href') || '').match(/[?&]v=([\w-]{11})/);
        if (!m || m[1] === curId || seen[m[1]]) return;
        var c = cardOf(a);
        if (c.closest && c.closest('ytm-engagement-panel,ytm-mobile-topbar-renderer,ytm-comment-thread-renderer')) return;
        var len = (c.textContent || '').length, hit = cache && cache.get(c), o;
        if (hit && hit.len === len && hit.id === m[1]) o = hit.o;
        else { o = parseCard(m[1], c); if (cache) cache.set(c, { len: len, id: m[1], o: o }); }
        if (!o) return;
        if (!o.a) o.a = avOf(c);   // الصور تُحمَّل كسولاً: نكمّل الأفاتار لاحقاً دون إعادة تحليل البطاقة
        seen[m[1]] = 1; out.push(o);
      });
      return out.slice(0, 160);
    }

    var chipEls = [];
    function chips() {
      chipEls = qa('ytm-feed-filter-chip-bar-renderer ytm-chip-cloud-chip-renderer, ytm-feed-filter-chip-bar-renderer chip-shape');
      return chipEls.slice(0, 30).map(function (e) {
        var sel = e.getAttribute('aria-selected') === 'true' || !!e.querySelector('[aria-selected="true"]') ||
          /selected|active/i.test((e.className || '') + ' ' + ((e.firstElementChild && e.firstElementChild.className) || ''));
        return { x: tx(e), s: sel };
      }).filter(function (c) { return c.x; });
    }

    // ───────── صفحة المشاهدة ─────────
    function watchData() {
      var owner = q1('ytm-slim-owner-renderer');
      var bar = q1('ytm-slim-video-action-bar-renderer');
      var lb = bar && q1('button[aria-label*="like" i]:not([aria-label*="dislike" i])', bar);
      var sb = q1('ytm-slim-owner-renderer ytm-subscribe-button-renderer button, ytm-subscribe-button-renderer button');
      var ce = q1('ytm-comments-entry-point-header-renderer, ytm-comments-entry-point-teaser-renderer, ytm-comments-entry-point-renderer');
      var cl = lines(ce);
      var sbLab = sb ? ((sb.getAttribute('aria-label') || '') + ' ' + tx(sb)) : '';
      return {
        id: params().get('v') || '',
        title: pick(document, ['ytm-slim-video-metadata-section-renderer h2', '.slim-video-metadata-title', 'h2.slim-video-information-title', 'h1']) ||
          document.title.replace(/\s*-\s*YouTube$/, ''),
        chan: pick(owner, ['.slim-owner-channel-name', 'a[href^="/@"]', 'a[href*="/channel/"]', 'h3']),
        subs: pick(owner, ['.subhead', '.slim-owner-subscriber-count', '[class*=subscriber]']),
        oa: avOf(owner),
        info: pick(document, ['ytm-slim-video-metadata-section-renderer .secondary-text', '.slim-video-information-subtitle', '.slim-video-metadata-header .subhead']),
        likes: lb ? (tx(lb) || '') : '',
        liked: !!(lb && lb.getAttribute('aria-pressed') === 'true'),
        subbed: !!(sb && (sb.getAttribute('aria-pressed') === 'true' || /unsubscribe|subscribed|مشترك/i.test(sbLab))),
        desc: pick(document, ['ytm-expandable-video-description-body-renderer', '.slim-video-metadata-description', 'ytm-structured-description-content-renderer']),
        cl: cl[0] || '',
        cp: cl.slice(1).join(' ')
      };
    }
    function comments() {
      var els = qa('ytm-comment-thread-renderer');
      if (!els.length) els = qa('ytm-comment-renderer');
      return els.slice(0, 60).map(function (c) {
        var ls = lines(c), author = '', time = '', likes = '', text = '';
        ls.forEach(function (l) {
          if (!author && l.charAt(0) === '@') author = l;
          else if (!likes && /^[\d.,]+\s?[KMkمأ]*$/.test(l)) likes = l;
          else if (!time && l.length < 24 && /\d/.test(l)) time = l;
          else if (l.length > text.length) text = l;
        });
        if (!author) author = ls[0] || '';
        return { a: author, x: text, t: time, l: likes };
      }).filter(function (c) { return c.x; });
    }

    // ───────── اللقطة ─────────
    var last = '', timer = 0;
    function snap() {
      timer = 0; applyMode();
      var p = location.pathname, v = params().get('v') || '';
      var o = {
        t: 'ys', href: location.href, on: mode,
        key: p + '?' + (v || params().get('search_query') || ''),
        items: items(v), chips: p === '/' ? chips() : []
      };
      if (p === '/watch') { o.w = watchData(); if (cOpen) o.c = comments(); }
      var j = JSON.stringify(o);
      if (j === last) return;
      last = j; post(o);
    }
    function sched() { if (!timer) timer = setTimeout(snap, 500); }
    new MutationObserver(sched).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    ['pushState', 'replaceState'].forEach(function (k) {
      var o = history[k];
      history[k] = function () { var r = o.apply(this, arguments); cOpen = false; sched(); return r; };
    });
    window.addEventListener('popstate', function () { cOpen = false; sched(); });
    setInterval(snap, 2000);

    // ───────── الأوامر ─────────
    function btn(root, re) {
      var l = qa('button,[role=button]', root || document);
      for (var i = 0; i < l.length; i++) {
        var lab = (l[i].getAttribute('aria-label') || '') + ' ' + tx(l[i]);
        if (re.test(lab)) return l[i];
      }
      return null;
    }
    window.__novaYtApp = {
      open: function (path) {
        var m = path.match(/[?&]v=([\w-]{11})/), a = m && q1('a[href*="v=' + m[1] + '"]');
        if (a) {
          a.click();
          setTimeout(function () { if (location.href.indexOf(m[1]) < 0) location.assign(path); }, 900);   // احتياط إن لم يلتقط يوتيوب النقرة
        } else location.assign(path);
      },
      go: function (p) { location.assign(p); },
      search: function (q) { location.assign('/results?search_query=' + encodeURIComponent(q)); },
      more: function () { window.scrollTo(0, document.documentElement.scrollHeight); },
      chip: function (i) { var e = chipEls[i]; if (e) e.click(); },
      mode: function (on) { mode = !!on; applyMode(); sched(); },
      like: function () {
        var bar = q1('ytm-slim-video-action-bar-renderer');
        var b = bar && q1('button[aria-label*="like" i]:not([aria-label*="dislike" i])', bar);
        if (b) b.click(); setTimeout(snap, 500);
      },
      subscribe: function () {
        var b = q1('ytm-slim-owner-renderer ytm-subscribe-button-renderer button, ytm-subscribe-button-renderer button');
        if (b) b.click(); setTimeout(snap, 600);
      },
      comments: function () {
        cOpen = true;
        var e = q1('ytm-comments-entry-point-header-renderer, ytm-comments-entry-point-teaser-renderer, ytm-comments-entry-point-renderer');
        if (e) { var b = q1('button', e) || e; b.click(); }
        var n = 0; (function poll() { snap(); if (++n < 10) setTimeout(poll, 500); })();
      },
      closeComments: function () {
        cOpen = false;
        var b = btn(q1('ytm-engagement-panel') || document, /close|إغلاق/i);
        if (b) b.click();
      },
      expand: function () {
        var h = q1('ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytm-slim-video-metadata-section-renderer h2');
        if (h) h.click(); setTimeout(snap, 600);
      },
      diag: function () {
        var v = params().get('v') || '', w = location.pathname === '/watch';
        function n(sel) { return qa(sel).length; }
        post({ t: 'diag', x: [
          'path: ' + location.pathname + ' mode=' + mode,
          'video anchors: ' + n('a[href*="/watch?v="]') + ' → cards: ' + items(v).length,
          'chips: ' + chips().length,
          'player container: ' + (q1('#player-container-id,.player-container') ? 'found' : 'NOT FOUND'),
          'owner: ' + n('ytm-slim-owner-renderer') + ' | action bar: ' + n('ytm-slim-video-action-bar-renderer'),
          'subscribe btn: ' + n('ytm-subscribe-button-renderer button'),
          'comments entry: ' + n('ytm-comments-entry-point-header-renderer,ytm-comments-entry-point-teaser-renderer,ytm-comments-entry-point-renderer'),
          'comment threads: ' + n('ytm-comment-thread-renderer'),
          'watch: ' + (w ? JSON.stringify(watchData()).slice(0, 260) : '-'),
          'first card: ' + JSON.stringify(items(v)[0] || null).slice(0, 200)
        ].join('\n') });
      }
    };
    snap();
  } catch (e) {}
})();
