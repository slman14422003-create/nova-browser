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
    var mode = !!CFG.on, cOpen = false, miniOn = false;

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
      'ytm-engagement-panel,ytm-bottom-sheet-renderer{opacity:0!important;pointer-events:none!important}' +
      '.ytp-pause-overlay,.ytp-pause-overlay-container,.ytp-ce-element{display:none!important}' +
      // لمسة جمالية لأزرار المشغّل: تدرّج خفيف يوضّح الأزرار فوق أي فيديو، وأزرار الوسط بخلفية زجاجية مستديرة
      '.player-controls-background{background:linear-gradient(180deg,rgba(0,0,0,.55) 0,rgba(0,0,0,0) 32%,rgba(0,0,0,0) 58%,rgba(0,0,0,.72) 100%)!important}' +
      '.player-controls-middle button{background:rgba(0,0,0,.38)!important;border-radius:50%!important;-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px)}' +
      '.player-controls-top,.player-controls-bottom{text-shadow:0 1px 3px rgba(0,0,0,.65)}';
    // الترجمة (CC): تصميمها ومديرها في yt-fix.js (ملف دعم يوتيوب)
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
      qa('a[href*="watch?v="]').forEach(function (a) {
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
      if (!out.length) out = fromData(curId);
      return out.slice(0, 160);
    }
    // احتياطي: بيانات الصفحة المضمّنة (ytInitialData) إن تغيّر شكل البطاقات في DOM
    function fromData(curId) {
      var out = [], seen = {}, n = 0;
      function txt(o) { return !o ? '' : (o.simpleText || (o.runs && o.runs.map(function (r) { return r.text; }).join('')) || (typeof o === 'string' ? o : '')); }
      function walk(o, d) {
        if (!o || typeof o !== 'object' || d > 14 || n++ > 40000 || out.length >= 120) return;
        if (Array.isArray(o)) { for (var i = 0; i < o.length; i++) walk(o[i], d + 1); return; }
        var id = o.videoId;
        if (typeof id === 'string' && /^[\w-]{11}$/.test(id) && id !== curId && !seen[id] && (o.title || o.headline)) {
          var t = txt(o.title) || txt(o.headline);
          if (t) {
            seen[id] = 1;
            out.push({ id: id, t: t, c: txt(o.shortBylineText || o.longBylineText || o.ownerText), m: txt(o.shortViewCountText || o.viewCountText) + (o.publishedTimeText ? ' • ' + txt(o.publishedTimeText) : ''), d: txt(o.lengthText), a: '' });
          }
        }
        for (var k in o) if (o.hasOwnProperty(k)) walk(o[k], d + 1);
      }
      try { walk(window.ytInitialData, 0); } catch (e) {}
      return out;
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

    // ───────── قوائم التشغيل ─────────
    function plTitle() {
      return pick(document, ['ytm-playlist-header-renderer h1', 'ytm-playlist-header-renderer .title', '.playlist-header h1', 'h1']) ||
        document.title.replace(/\s*-\s*YouTube$/, '');
    }
    // قوائم المستخدم في صفحة المكتبة: بطاقات روابطها /playlist?list=…
    function playlists() {
      var seen = {}, out = [];
      qa('a[href*="/playlist?"]').forEach(function (a) {
        var m = (a.getAttribute('href') || '').match(/[?&]list=([\w-]+)/);
        if (!m || seen[m[1]]) return;
        var c = cardOf(a);
        if (c.closest && c.closest('ytm-engagement-panel,ytm-mobile-topbar-renderer')) return;
        var ls = lines(c).filter(function (l) { return !DUR.test(l); });
        var t = pick(c, ['h3', 'h4', '[class*=headline]', '[class*=title]']) || ls[0] || '';
        if (!t) return;
        var th = '', im = qa('img', c);
        for (var i = 0; i < im.length && !th; i++) { var mm = (im[i].currentSrc || im[i].src || '').match(/\/vi(?:_webp)?\/([\w-]{11})\//); if (mm) th = mm[1]; }
        var meta = ls.filter(function (l) { return l !== t && /\d/.test(l) && l.length < 40; })[0] || '';
        seen[m[1]] = 1; out.push({ id: m[1], t: t, m: meta, th: th });
      });
      return out.slice(0, 40);
    }
    // قائمة التشغيل الجارية في صفحة المشاهدة (إن فُتح الفيديو من قائمة): العنوان والعناصر إن ظهرت في الصفحة
    function panel() {
      var lid = params().get('list'); if (!lid) return null;
      var root = q1('ytm-playlist-panel-renderer'), o = { id: lid, t: '', items: [] };
      if (root) {
        o.t = pick(root, ['h3', '.playlist-panel-title', '[class*=title]']);
        var seen = {};
        qa('a[href*="v="]', root).forEach(function (a) {
          var m = (a.getAttribute('href') || '').match(/[?&]v=([\w-]{11})/);
          if (!m || seen[m[1]]) return;
          var c = a.closest('ytm-playlist-panel-video-renderer,ytm-playlist-panel-video-wrapper-renderer') || cardOf(a);
          var it = parseCard(m[1], c); if (!it) return;
          seen[m[1]] = 1; o.items.push(it);
        });
      }
      return o;
    }

    // ───────── حفظ في قائمة تشغيل (يستخدم قائمة «حفظ» الأصلية في الصفحة ويعرضها الواجهة بشكل أصلي) ─────────
    var saveEls = [];
    function sheetEls() {
      var sh = q1('ytm-bottom-sheet-renderer'); if (!sh) return [];
      var els = qa('ytm-playlist-add-to-option-renderer', sh);
      if (!els.length) els = qa('[role=checkbox],[role=menuitemcheckbox]', sh);
      return els;
    }
    function saveList() {
      saveEls = sheetEls();
      post({
        t: 'save', ok: saveEls.length > 0,
        o: saveEls.map(function (e) {
          var ls = lines(e);
          var chk = e.getAttribute('aria-checked') === 'true' || !!e.querySelector('[aria-checked="true"],input:checked');
          return { n: ls[0] || '', p: ls[1] || '', c: chk };
        })
      });
    }

    // ───────── اللقطة ─────────
    var last = '', timer = 0;
    // تحليل بطاقات الفيديو مكلف: نعيد استخدام النتيجة ما دامت الصفحة وعدد الروابط لم يتغيّرا (حتى 6 ثوانٍ) فيبقى المشغّل والواجهة سلسَين
    var itKey = '', itCnt = -1, itAt = 0, itVal = [];
    function itemsCached(v) {
      var k = location.pathname + '?' + v + (params().get('search_query') || ''), n = document.getElementsByTagName('a').length, now = Date.now();
      if (k === itKey && n === itCnt && now - itAt < 6000 && itVal.length) return itVal;
      itKey = k; itCnt = n; itAt = now; return (itVal = items(v));
    }
    function snap() {
      timer = 0; applyMode();
      if (miniOn) return;   // المشغّل المصغّر لا يحتاج لقطات (يوفر المعالج)
      var p = location.pathname, v = params().get('v') || '';
      var o = {
        t: 'ys', href: location.href, on: mode,
        key: p + '?' + (v || params().get('search_query') || ''),
        items: itemsCached(v), chips: p === '/' ? chips() : []
      };
      if (p === '/' && !o.items.length) {
        var nd = q1('ytm-feed-nudge-renderer,ytm-feed-nudge');
        var bt = nd ? '' : ((q1('ytm-browse') || {}).textContent || '').slice(0, 600);
        o.nu = !!nd || /get started|ابدأ بالبحث|ابحث لتبدأ/i.test(bt);
      }
      if (p === '/watch') { o.w = watchData(); o.pp = panel(); if (cOpen) o.c = comments(); }
      else if (p === '/playlist') o.pt = plTitle();
      else if (p.indexOf('/feed/library') === 0 || p.indexOf('/feed/you') === 0 || p === '/feed/playlists') o.pls = playlists();
      var j = JSON.stringify(o);
      if (j === last) return;
      last = j; post(o);
    }
    function sched() { if (!timer) timer = setTimeout(snap, 500); }
    // تغيّرات داخل المشغّل (الوقت، نص الترجمة، شريط التقدّم) لا تغيّر القوائم ولا بيانات الصفحة: تجاهلها يمنع لقطة كل نصف ثانية أثناء التشغيل
    new MutationObserver(function (ms) {
      var pl = playerEl();
      for (var i = 0; i < ms.length; i++) {
        var t = ms[i].target;
        if (t && t.nodeType === 3) t = t.parentNode;
        if (!pl || !t || !pl.contains(t)) { sched(); return; }
      }
    }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    ['pushState', 'replaceState'].forEach(function (k) {
      var o = history[k];
      history[k] = function () { var r = o.apply(this, arguments); cOpen = false; sched(); return r; };
    });
    window.addEventListener('popstate', function () { cOpen = false; sched(); });
    setInterval(function () { if (!document.hidden) snap(); }, 2500);

    // ───────── المشغّل: إعدادات أصلية (جودة/سرعة/ترجمة) ─────────
    // قائمة الإعدادات الأصلية في يوتيوب مخفية خلف الواجهة الأصلية فلا يمكن لمسها، لذلك نلتقط ضغطة زر الترس
    // ونفتح بدلها قائمة التطبيق (تعمل عبر واجهة المشغّل نفسها: movie_player).
    function playerEl() { return document.getElementById('movie_player') || q1('.html5-video-player') || null; }
    function videoEl() { return q1('video'); }
    var GEAR_RE = /settings|الإعدادات|إعدادات/i, gearAt = 0;
    function gearHit(t) {
      try {
        var b = t && t.closest && t.closest('button,[role=button],.player-settings-icon,[class*=settings-icon]');
        if (!b || !b.closest('#player-container-id,.player-container,#player,.html5-video-player')) return false;
        return GEAR_RE.test((b.getAttribute('aria-label') || '') + ' ' + (b.className && b.className.baseVal === undefined ? b.className : ''));
      } catch (e) { return false; }
    }
    function onGear(e) {
      if (!mode || miniOn || location.pathname !== '/watch' || !gearHit(e.target)) return;
      e.preventDefault(); e.stopPropagation(); if (e.stopImmediatePropagation) e.stopImmediatePropagation();
      var n = Date.now(); if (n - gearAt < 450) return; gearAt = n;
      post({ t: 'gear' });
    }
    ['click', 'touchend'].forEach(function (n) { document.addEventListener(n, onGear, true); });
    function playerInfo() {
      var p = playerEl(), v = videoEl(), F = window.__novaYtFix;
      var o = { t: 'ps', rate: v ? v.playbackRate : 1, loop: v ? !!v.loop : false, q: [], cq: '', caps: [], trs: [], cc: '', tl: '', cr: false, cs: 'm', cb: 'glass', cp: 'b', co: 0 };
      try { if (p && p.getAvailableQualityLevels) { o.q = (p.getAvailableQualityLevels() || []).slice(0, 12); if (p.getPlaybackQuality) o.cq = p.getPlaybackQuality() || ''; } } catch (e) {}
      try {
        if (F && F.cc) {
          var ci = F.cc.info(), st = F.cc.styleOf();
          o.caps = ci.caps.slice(0, 60); o.trs = ci.trs.slice(0, 120); o.cc = ci.cc; o.tl = ci.tl; o.cr = !!ci.ready; o.cs = st.size; o.cb = st.bg; o.cp = st.pos; o.co = st.off;
        }
      } catch (e) {}
      post(o);
    }
    // فتح الإعدادات: لقطة فورية ثم لقطة ثانية بعد تحميل لغات الترجمة (القائمة تكون فارغة قبل تحميل وحدة الترجمة)
    function openPs() {
      playerInfo();
      var F = window.__novaYtFix;
      if (F && F.cc) F.cc.prepare(function () { playerInfo(); });
    }

    var MINI_ID = 'nova-yt-mini-style';
    var MINI_CSS = '.player-controls-top,.player-controls-bottom,.player-controls-middle,.player-controls-background,.ytp-chrome-top,.ytp-chrome-bottom,' +
      '.ytp-gradient-top,.ytp-gradient-bottom,.ytp-pause-overlay,.ytp-ce-element,.ytp-endscreen-content,ytm-player-endscreen-renderer,.ytp-spinner{display:none!important}' +
      // الفيديو يملأ الإطار دائماً (يوتيوب يضع مقاسات بكسل قديمة على العنصر بعد تغيّر حجم الصفحة)
      '.html5-video-container{width:100%!important;height:100%!important}' +
      'video.html5-main-video{width:100%!important;height:100%!important;left:0!important;top:0!important;object-fit:contain!important}';
    function applyMini(on) {
      miniOn = !!on;
      var s = document.getElementById(MINI_ID);
      if (on && !s) { s = document.createElement('style'); s.id = MINI_ID; s.textContent = MINI_CSS; (document.head || document.documentElement).appendChild(s); }
      else if (!on && s) s.remove();
    }

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
      ps: function () { openPs(); },
      rate: function (r) { var v = videoEl(); if (v && r > 0) v.playbackRate = r; setTimeout(playerInfo, 200); },
      quality: function (q) {
        var p = playerEl();
        try { if (p && p.setPlaybackQualityRange) p.setPlaybackQualityRange(q, q); if (p && p.setPlaybackQuality) p.setPlaybackQuality(q); } catch (e) {}
        setTimeout(playerInfo, 600);
      },
      caption: function (key, tl) {
        var F = window.__novaYtFix;
        try { if (F && F.cc) F.cc.set(key, tl || ''); } catch (e) {}
        setTimeout(playerInfo, 800); setTimeout(playerInfo, 1800);
      },
      ccStyle: function (size, bg, pos, off, quiet) {
        var F = window.__novaYtFix;
        try { if (F && F.cc) F.cc.style(size, bg, pos, off); } catch (e) {}
        if (!quiet) playerInfo();
      },
      loop: function (on) { var v = videoEl(); if (v) v.loop = !!on; setTimeout(playerInfo, 100); },
      mini: function (on) { applyMini(on); if (!on) sched(); },
      like: function () {
        var bar = q1('ytm-slim-video-action-bar-renderer');
        var b = bar && q1('button[aria-label*="like" i]:not([aria-label*="dislike" i])', bar);
        if (b) b.click(); setTimeout(snap, 500);
      },
      saveOpen: function () {
        var bar = q1('ytm-slim-video-action-bar-renderer');
        var b = (bar && btn(bar, /save|حفظ/i)) || btn(document, /save to playlist|حفظ في قائمة|^save$/i);
        if (!b) { post({ t: 'save', ok: false, fail: true, o: [] }); return; }
        b.click();
        var n = 0; (function poll() { saveList(); if (!sheetEls().length && ++n < 14) setTimeout(poll, 350); else if (!sheetEls().length) post({ t: 'save', ok: false, fail: true, o: [] }); })();
      },
      saveToggle: function (i) {
        var e = saveEls[i]; if (!e) return;
        (q1('[role=checkbox],input,button', e) || e).click();
        setTimeout(saveList, 500); setTimeout(saveList, 1200);
      },
      saveNew: function (name) {
        var sh = q1('ytm-bottom-sheet-renderer');
        var nb = sh && btn(sh, /new playlist|قائمة تشغيل جديدة|قائمة جديدة/i);
        if (!nb) { post({ t: 'save', ok: false, fail: true, o: [] }); return; }
        nb.click();
        var n = 0; (function wait() {
          var inp = q1('input[type=text],input:not([type]),textarea', q1('ytm-dialog, ytm-bottom-sheet-renderer, dialog, [role=dialog]') || document);
          if (!inp) { if (++n < 12) setTimeout(wait, 300); return; }
          try {
            var setter = Object.getOwnPropertyDescriptor(inp.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set;
            setter.call(inp, name); inp.dispatchEvent(new Event('input', { bubbles: true })); inp.dispatchEvent(new Event('change', { bubbles: true }));
          } catch (e) {}
          setTimeout(function () {
            var cb = btn(document, /^(create|إنشاء)$/i) || btn(document, /create|إنشاء/i);
            if (cb) cb.click();
            setTimeout(saveList, 900); setTimeout(saveList, 1800);
          }, 350);
        })();
      },
      saveClose: function () {
        var sh = q1('ytm-bottom-sheet-renderer');
        var b = sh && btn(sh, /close|إغلاق|cancel|إلغاء|done|تم/i);
        if (b) b.click();
        else {
          try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true })); } catch (e) {}
          var sc = q1('.mobile-topbar-scrim, c3-overlay, .dialog-scrim, .bottom-sheet-scrim'); if (sc) sc.click();
        }
        saveEls = [];
      },
      dislike: function () {
        var bar = q1('ytm-slim-video-action-bar-renderer');
        var b = bar && btn(bar, /dislike|لم يعجبني|لا يعجبني/i);
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
