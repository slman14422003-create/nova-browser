/* Nova YouTube bundle (yt-all.js) — ملف واحد لكل سكربتات يوتيوب، يُحقن مرة واحدة مبكراً (YtHub.kt).
 * الأقسام (كلٌّ في IIFE مستقل بحارس خاص، فخطأ في قسم لا يوقف البقية):
 *   1) APP    : جسر الواجهة الأصلية — DOM الصفحة كمصدر بيانات (NovaYtApp)      — العنصر __CFG__
 *   2) FIX    : طبقات الإصلاح + مدير الترجمة (CC)                              — بلا جسر
 *   3) MEDIA  : حالة التشغيل لإشعار الوسائط والخلفية والمشغّل المصغّر (NovaYt)  — العنصر __BG__
 * لا يلمس الإعلانات ولا يستخرج روابط البث. التفاصيل في docs/YOUTUBE.md.
 */

/* ═════════ 1) APP ═════════ */
try {
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
} catch (e) {}

/* ═════════ 2) FIX ═════════ */
try {
/* Nova YouTube support layer (yt-fix.js) — ملف دعم يوتيوب: طبقات إصلاحية وتحسينات تُحقن مبكراً في كل صفحات يوتيوب
 *  1) مدير الترجمة (CC): قراءة اللغات المتاحة بشكل موثوق (تحميل وحدة الترجمة أولاً ثم الانتظار)، تغيير اللغة،
 *     الترجمة التلقائية إلى لغة أخرى، وتذكّر اللغة المختارة للفيديوهات التالية.
 *  2) شكل الترجمة: في منتصف أسفل المشغّل، خلفية زجاجية/غامقة/بلا خلفية، وأحجام متعددة، بدعم RTL.
 *  3) طبقات إصلاحية: إخفاء لافتات «افتح التطبيق»، منع وميض الضغط، تثبيت قياس الفيديو بعد الدوران.
 * لا يلمس الإعلانات ولا يستخرج روابط البث. يعمل بلا الجسور (NovaYtApp) فيفيد عرض الموقع أيضاً.
 * API:  window.__novaYtFix.cc.{info(), prepare(cb), set(key, translateTo), style(size, bg), styleOf()}
 */
(function () {
  'use strict';
  try {
    if (window.__novaYtFix || window.top !== window || location.hostname === 'music.youtube.com') return;   // لا تعدّل YouTube Music
    var FIX = window.__novaYtFix = { v: 2 };

    // ───────── تخزين آمن (قد يرفض الوصول في بعض الحالات) ─────────
    var K = { lang: 'nova_cc_lang', tr: 'nova_cc_tr', size: 'nova_cc_size', bg: 'nova_cc_bg', pos: 'nova_cc_pos', off: 'nova_cc_off' };
    function sget(k, d) { try { var v = localStorage.getItem(k); return v === null ? d : v; } catch (e) { return d; } }
    function sset(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function qa(sel, root) { try { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); } catch (e) { return []; } }
    function root() { return document.documentElement; }

    // ───────── 1) CSS: الترجمة + الطبقات الإصلاحية ─────────
    var CSS =
      // مقاسات الترجمة (تُضرب في الحجم المتجاوب)
      ':root{--nova-cc-k:1;--nova-cc-bg:rgba(14,14,18,.68);--nova-cc-blur:8px;--nova-cc-sh:none}' +
      'html[data-nova-ccsize="s"]{--nova-cc-k:.82}html[data-nova-ccsize="l"]{--nova-cc-k:1.22}html[data-nova-ccsize="xl"]{--nova-cc-k:1.5}' +
      'html[data-nova-ccbg="solid"]{--nova-cc-bg:rgba(0,0,0,.92);--nova-cc-blur:0px}' +
      'html[data-nova-ccbg="none"]{--nova-cc-bg:transparent;--nova-cc-blur:0px;' +
      '--nova-cc-sh:0 0 3px #000,0 0 3px #000,0 1px 6px rgba(0,0,0,.95),0 0 10px rgba(0,0,0,.8)}' +
      // الحاوية: لا تلتقط اللمس فتصل الضغطات للمشغّل
      '.ytp-caption-window-container{pointer-events:none!important}' +
      // النافذة: وسط أفقي دائماً بغضّ النظر عن اتجاه لغة الترجمة، وترتفع قليلاً عند ظهور أزرار التحكم
      '.ytp-caption-window-container .caption-window,.caption-window{position:absolute!important;left:50%!important;right:auto!important;' +
      'transform:translateX(-50%)!important;margin:0!important;width:auto!important;max-width:90%!important;' +
      'text-align:center!important;display:flex!important;flex-direction:column!important;align-items:center!important;' +
      'transition:bottom .22s cubic-bezier(.2,0,0,1),top .22s cubic-bezier(.2,0,0,1)!important;contain:layout style}' +
      // موضع الترجمة (يختاره المستخدم): b أسفل (فوق أزرار التحكم) • m وسط • t أعلى. --nova-cc-off نسبة مئوية للإزاحة الدقيقة نحو الداخل
      'html:not([data-nova-ccpos]) .caption-window,html[data-nova-ccpos="b"] .caption-window{top:auto!important;bottom:calc(var(--nova-cc-base,27%) + var(--nova-cc-off,0)*1%)!important}' +
      'html[data-nova-ccpos="m"] .caption-window{bottom:auto!important;top:calc(50% - var(--nova-cc-off,0)*1%)!important;transform:translate(-50%,-50%)!important}' +
      'html[data-nova-ccpos="t"] .caption-window{bottom:auto!important;top:calc(7% + var(--nova-cc-off,0)*1%)!important}' +
      '.ytp-caption-window-container .caption-visual-line{display:block!important;text-align:center!important;margin:2px 0!important}' +
      // النص: حجم متجاوب مع عرض الشاشة، خط مقروء، زوايا مدوّرة، يلتف السطر ولا يقتصّ
      '.ytp-caption-segment{background:var(--nova-cc-bg)!important;color:#fff!important;' +
      'font-family:Roboto,"Segoe UI","Noto Naskh Arabic","Noto Sans Arabic",system-ui,sans-serif!important;' +
      'font-size:calc(clamp(13px,3.9vw,26px)*var(--nova-cc-k))!important;line-height:1.55!important;' +
      'padding:.12em .6em!important;border-radius:10px!important;text-shadow:var(--nova-cc-sh)!important;text-align:center!important;' +
      'unicode-bidi:plaintext;white-space:pre-wrap!important;word-break:break-word;' +
      '-webkit-box-decoration-break:clone;box-decoration-break:clone;' +
      '-webkit-backdrop-filter:blur(var(--nova-cc-blur));backdrop-filter:blur(var(--nova-cc-blur))}' +
      // ── طبقات إصلاحية عامة ──
      // لا وميض رمادي عند اللمس (يعطي إحساس تطبيق أصلي)
      '*{-webkit-tap-highlight-color:transparent}' +
      // لافتات الدعوة لتثبيت تطبيق يوتيوب (لا تمسّ الإعلانات)
      'ytm-mealbar-promo-renderer,ytm-upsell-dialog-renderer,ytm-app-banner-renderer,.open-app-banner,ytm-open-app-banner{display:none!important}' +
      // يمنع ارتداد الصفحة (overscroll) الذي يقطع تمرير القوائم داخل WebView
      'html,body{overscroll-behavior-y:none}';
    var ST = document.createElement('style');
    ST.id = 'nova-yt-fix-style'; ST.textContent = CSS;
    (document.head || root()).appendChild(ST);
    if (!ST.isConnected && root()) root().appendChild(ST);

    function applyStyle() {
      var r = root(); if (!r) return;
      r.setAttribute('data-nova-ccsize', sget(K.size, 'm'));
      r.setAttribute('data-nova-ccbg', sget(K.bg, 'glass'));
      r.setAttribute('data-nova-ccpos', sget(K.pos, 'b'));
      var o = parseInt(sget(K.off, '0'), 10); if (!(o >= 0 && o <= 40)) o = 0;
      r.style.setProperty('--nova-cc-off', String(o));
    }
    applyStyle();

    // ───────── 2) مدير الترجمة ─────────
    function player() { return document.getElementById('movie_player') || document.querySelector('.html5-video-player'); }
    function txt(o) { return !o ? '' : (typeof o === 'string' ? o : (o.simpleText || (o.runs && o.runs.map(function (r) { return r.text; }).join('')) || '')); }
    function keyOf(t) { return t.vss_id || t.vssId || t.languageCode || ''; }
    function isAuto(t) { return t.kind === 'asr' || /^a\./.test(keyOf(t)); }
    function nameOf(t) {
      return String(t.displayName || txt(t.languageName) || txt(t.name) || t.languageCode || '').replace(/\s*\(.*?(auto|تلقائ).*?\)\s*$/i, '');
    }
    function playerResponse(p) {
      try { var r = p && p.getPlayerResponse && p.getPlayerResponse(); if (r) return r; } catch (e) {}
      return window.ytInitialPlayerResponse || null;
    }
    function renderer(p) {
      var pr = playerResponse(p);
      return (pr && pr.captions && pr.captions.playerCaptionsTracklistRenderer) || null;
    }
    // قائمة المسارات: من وحدة المشغّل إن حُمّلت، وإلا من بيانات الفيديو المضمّنة (تصل فوراً)
    function trackList(p) {
      var out = [], seen = {};
      function add(t, fromPR) {
        if (!t || !t.languageCode) return;
        var k = keyOf(t); if (!k || seen[k]) return; seen[k] = 1;
        out.push({ k: k, lc: t.languageCode, n: nameOf(t), a: isAuto(t) });
      }
      try { if (p && p.getOption) (p.getOption('captions', 'tracklist') || []).forEach(function (t) { add(t); }); } catch (e) {}
      if (!out.length) { var r = renderer(p); if (r && r.captionTracks) r.captionTracks.forEach(function (t) { add(t, true); }); }
      // اليدوية أولاً ثم التلقائية، مع الحفاظ على الترتيب داخل كل مجموعة
      return out.filter(function (x) { return !x.a; }).concat(out.filter(function (x) { return x.a; }));
    }
    function transList(p) {
      var out = [], seen = {};
      function add(code, name) { if (!code || seen[code]) return; seen[code] = 1; out.push({ c: code, n: name || code }); }
      try { if (p && p.getOption) (p.getOption('captions', 'translationLanguages') || []).forEach(function (t) { add(t.languageCode, txt(t.languageName) || t.languageName); }); } catch (e) {}
      if (!out.length) { var r = renderer(p); if (r && r.translationLanguages) r.translationLanguages.forEach(function (t) { add(t.languageCode, txt(t.languageName)); }); }
      return out;
    }
    function current(p, list) {
      var cur = null; try { cur = p && p.getOption && p.getOption('captions', 'track'); } catch (e) {}
      if (!cur || !cur.languageCode) return { k: '', tr: '' };
      var k = keyOf(cur), tr = (cur.translationLanguage && cur.translationLanguage.languageCode) || '';
      var found = list.some(function (x) { return x.k === k; });
      if (!found) { for (var i = 0; i < list.length; i++) if (list[i].lc === cur.languageCode) { k = list[i].k; break; } }
      return { k: k, tr: tr };
    }
    var prepared = false;
    function ensureModule(p) { try { if (p && p.loadModule) p.loadModule('captions'); } catch (e) {} }

    FIX.cc = {
      style: function (size, bg, pos, off) {
        if (size) sset(K.size, size);
        if (bg) sset(K.bg, bg);
        if (pos === 'b' || pos === 'm' || pos === 't') sset(K.pos, pos);
        if (off !== undefined && off !== null && off !== '') sset(K.off, String(Math.max(0, Math.min(40, Math.round(Number(off) || 0)))));
        applyStyle();
      },
      styleOf: function () { return { size: sget(K.size, 'm'), bg: sget(K.bg, 'glass'), pos: sget(K.pos, 'b'), off: parseInt(sget(K.off, '0'), 10) || 0 }; },
      info: function () {
        var p = player(), list = trackList(p), c = current(p, list), tr = transList(p);
        return {
          caps: list.map(function (x) { return { c: x.k, n: x.n, a: x.a ? 1 : 0 }; }),
          trs: tr, cc: c.k, tl: c.tr, ready: prepared || list.length > 0
        };
      },
      // يحمّل وحدة الترجمة (بدونها تكون القائمة فارغة) وينتظر وصول المسارات ثم يستدعي cb
      prepare: function (cb) {
        var p = player(), n = 0; prepared = false;
        ensureModule(p);
        (function poll() {
          var ok = trackList(p).length > 0 || !renderer(p) && n > 4;
          if (ok || ++n > 12) { prepared = true; cb && cb(); return; }
          if (n === 3) ensureModule(p);
          setTimeout(poll, 300);
        })();
      },
      // key: معرّف المسار (vss_id)، فارغ لإيقاف الترجمة. translateTo: رمز لغة الترجمة التلقائية (اختياري)
      set: function (key, translateTo) {
        var p = player(); if (!p) return;
        if (!key) { try { p.unloadModule && p.unloadModule('captions'); } catch (e) {} sset(K.lang, ''); sset(K.tr, ''); return; }
        ensureModule(p);
        var n = 0;
        (function go() {
          var raw = []; try { raw = p.getOption('captions', 'tracklist') || []; } catch (e) {}
          var tr = null;
          for (var i = 0; i < raw.length; i++) if (keyOf(raw[i]) === key) { tr = raw[i]; break; }
          if (!tr) for (var j = 0; j < raw.length; j++) if (raw[j].languageCode === key) { tr = raw[j]; break; }
          if (!tr && ++n < 10) { setTimeout(go, 300); return; }   // الوحدة لم تُحمَّل بعد
          var o = {}; if (tr) for (var x in tr) if (Object.prototype.hasOwnProperty.call(tr, x)) o[x] = tr[x];
          if (!tr) o.languageCode = String(key).replace(/^a?\./, '');
          if (translateTo && translateTo !== o.languageCode) o.translationLanguage = { languageCode: translateTo, languageName: translateTo };
          else delete o.translationLanguage;
          try { p.setOption('captions', 'track', o); } catch (e) {}
          // تحقّق: بعض الإصدارات لا تقبل الكائن الكامل فنعيد المحاولة بالرمز فقط
          setTimeout(function () {
            var c = null; try { c = p.getOption('captions', 'track'); } catch (e) {}
            if (!c || !c.languageCode) { try { p.setOption('captions', 'track', translateTo ? { languageCode: o.languageCode, translationLanguage: { languageCode: translateTo } } : { languageCode: o.languageCode }); } catch (e) {} }
          }, 700);
          sset(K.lang, key); sset(K.tr, translateTo || '');
        })();
      }
    };

    // تذكّر اللغة: عند بدء فيديو جديد يُطبَّق آخر اختيار إن كان متاحاً له
    var appliedFor = '';
    function vid() { try { return new URLSearchParams(location.search).get('v') || ''; } catch (e) { return ''; } }
    function autoApply() {
      var want = sget(K.lang, ''); if (!want || location.pathname !== '/watch') return;
      var id = vid(); if (!id || appliedFor === id) return;
      var p = player(); if (!p) return;
      var n = 0;
      (function wait() {
        var list = trackList(p);
        if (!list.length) { if (++n < 10) setTimeout(wait, 500); return; }
        appliedFor = id;
        var hit = list.filter(function (x) { return x.k === want; })[0] || list.filter(function (x) { return x.lc === want.replace(/^a?\./, ''); })[0];
        if (hit) FIX.cc.set(hit.k, sget(K.tr, ''));
      })();
    }
    document.addEventListener('loadedmetadata', function (e) { if (e.target && e.target.tagName === 'VIDEO') setTimeout(autoApply, 700); }, true);
    window.addEventListener('yt-navigate-finish', function () { appliedFor = ''; setTimeout(autoApply, 900); });

    // ───────── 3) طبقات إصلاحية: قياس الفيديو بعد الدوران ─────────
    // يوتيوب يترك مقاسات بكسل قديمة على عنصر الفيديو بعد تدوير الشاشة فيظهر مقصوصاً/بشريط؛ نُجبره على إعادة الحساب مرة واحدة
    var rz = 0, fromUs = false, land = window.innerWidth > window.innerHeight;
    function refit() {
      rz = 0;
      var v = document.querySelector('video.html5-main-video'); if (!v) return;
      v.style.removeProperty('width'); v.style.removeProperty('height'); v.style.removeProperty('left'); v.style.removeProperty('top');
      fromUs = true; try { window.dispatchEvent(new Event('resize')); } catch (e) {} fromUs = false;
    }
    // فقط عند انقلاب الاتجاه (طولي ↔ عرضي)، فلا يتأثر التمرير عند ظهور/اختفاء شريط المتصفح
    window.addEventListener('resize', function () {
      if (fromUs) return;
      var l = window.innerWidth > window.innerHeight;
      if (l === land) return;
      land = l; clearTimeout(rz); rz = setTimeout(refit, 400);
    });
  } catch (e) {}

  // ───────── موضع الترجمة السفلي يتبع أزرار التحكم: فوقها وهي ظاهرة، وقريب من الحافة حين تختفي ─────────
  (function () {
    var last = '';
    function shown() {
      var c = document.querySelector('.player-controls-bottom,.ytp-chrome-bottom');
      if (!c) return false;
      var cs = getComputedStyle(c);
      return cs.display !== 'none' && cs.visibility !== 'hidden' && parseFloat(cs.opacity) > 0.05;
    }
    function tick() {
      if (document.hidden || location.pathname !== '/watch') return;
      var v = shown() ? '27%' : '9%';
      if (v !== last) { last = v; document.documentElement.style.setProperty('--nova-cc-base', v); }
    }
    setInterval(tick, 400);
    ['touchend', 'click'].forEach(function (e) { document.addEventListener(e, function () { setTimeout(tick, 80); }, true); });
  })();
})();
} catch (e) {}

/* ═════════ 2b) PLAY — تشغيل أذكى: استئناف + تذكّر السرعة + ضغط مطوّل ×2 ═════════ */
try {
(function () {
  'use strict';
  try {
    if (window.__novaYtPlay || window.top !== window || location.hostname === 'music.youtube.com') return;
    window.__novaYtPlay = 1;
    var P = __PLAY__;
    var KP = 'nova.yt.pos', KR = 'nova.yt.rate';
    function sget(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function sset(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function vid() { try { return new URLSearchParams(location.search).get('v') || ''; } catch (e) { return ''; } }
    function video() { return document.querySelector('video.html5-main-video') || document.querySelector('video'); }
    function player() { return document.getElementById('movie_player') || document.querySelector('.html5-video-player'); }
    function ad() { var p = player(); return !!(p && p.classList && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting'))); }
    function watch() { return location.pathname === '/watch'; }

    // ───────── استئناف من حيث توقفت ─────────
    function posMap() { try { return JSON.parse(sget(KP) || '{}') || {}; } catch (e) { return {}; } }
    function savePos(id, sec, drop) {
      var m = posMap();
      if (drop) delete m[id]; else m[id] = [Math.floor(sec), Date.now()];
      var ks = Object.keys(m);
      if (ks.length > 80) { ks.sort(function (a, b) { return m[a][1] - m[b][1]; }); for (var i = 0; i < ks.length - 80; i++) delete m[ks[i]]; }
      sset(KP, JSON.stringify(m));
    }
    var resumedFor = '', lastSave = 0, ours = false, holding = false;
    function tryResume(v) {
      if (!P.resume || !watch() || ad()) return;
      var id = vid(); if (!id || resumedFor === id || !(v.duration > 60)) return;
      resumedFor = id;
      if (/[?&]t=/.test(location.search)) return;               // رابط بوقت محدد يغلب الاستئناف
      var e = posMap()[id]; if (!e) return;
      var s = e[0]; if (s > 15 && v.duration - s > 20) { try { v.currentTime = s; } catch (x) {} }
    }
    function track(v) {
      if (!P.resume || !watch() || ad() || !(v.duration > 60) || holding) return;
      var now = Date.now(); if (now - lastSave < 5000) return; lastSave = now;
      var id = vid(); if (!id) return;
      if (v.duration - v.currentTime <= 20) savePos(id, 0, true);
      else if (v.currentTime > 15) savePos(id, v.currentTime, false);
    }

    // ───────── تذكّر السرعة ─────────
    function applyRate(v) {
      if (!P.keep || ad()) return;
      var r = parseFloat(sget(KR) || '1');
      if (r > 0 && r <= 2 && Math.abs(v.playbackRate - r) > 0.01) { ours = true; try { v.playbackRate = r; } catch (e) {} ours = false; }
    }

    document.addEventListener('loadedmetadata', function (e) {
      var v = e.target; if (!v || v.tagName !== 'VIDEO') return;
      setTimeout(function () { tryResume(v); applyRate(v); }, 500);
    }, true);
    document.addEventListener('timeupdate', function (e) { var v = e.target; if (v && v.tagName === 'VIDEO') track(v); }, true);
    document.addEventListener('ratechange', function (e) {
      var v = e.target; if (!v || v.tagName !== 'VIDEO' || !P.keep || ours || holding || ad() || !watch()) return;
      if (v.playbackRate > 0 && v.playbackRate <= 2) sset(KR, String(v.playbackRate));
    }, true);
    window.addEventListener('yt-navigate-finish', function () { resumedFor = ''; });
    document.addEventListener('visibilitychange', function () {
      var v = video(), id = vid(); if (!P.resume || !v || !id || !watch() || ad() || !(v.duration > 60)) return;
      if (v.duration - v.currentTime > 20 && v.currentTime > 15) savePos(id, v.currentTime, false);
    }, true);

    // ───────── ضغط مطوّل على المشغّل = ×2 مؤقتاً (يعود عند الرفع) ─────────
    if (P.hold) (function () {
      var timer = 0, sx = 0, sy = 0, prev = 1, badge = null;
      var css = document.createElement('style');
      css.textContent = '#nova-hold-badge{position:absolute;top:9%;left:50%;transform:translateX(-50%);z-index:99;padding:6px 14px;border-radius:999px;' +
        'background:rgba(0,0,0,.55);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);color:#fff;font:600 13px/1.2 system-ui,sans-serif;pointer-events:none;direction:ltr}';
      (document.head || document.documentElement).appendChild(css);
      function inPlayer(t) {
        if (!t || !t.closest) return false;
        if (t.closest('button,[role=slider],[role=button],.ytp-chrome-bottom,.player-controls-bottom,.ytp-settings-menu,.ytp-ce-element')) return false;
        return !!t.closest('#movie_player,.html5-video-player,#player-container-id,.player-container');
      }
      function stop() {
        clearTimeout(timer); timer = 0;
        if (!holding) return;
        var v = video(); if (v) { ours = true; try { v.playbackRate = prev; } catch (e) {} ours = false; }
        holding = false; if (badge && badge.parentNode) badge.parentNode.removeChild(badge); badge = null;
      }
      document.addEventListener('touchstart', function (e) {
        if (!watch() || ad() || !e.touches || e.touches.length !== 1 || !inPlayer(e.target)) return;
        sx = e.touches[0].clientX; sy = e.touches[0].clientY;
        clearTimeout(timer);
        timer = setTimeout(function () {
          var v = video(); if (!v || v.paused) return;
          prev = v.playbackRate || 1; holding = true;
          ours = true; try { v.playbackRate = 2; } catch (x) {} ours = false;
          var host = player() || document.body;
          badge = document.createElement('div'); badge.id = 'nova-hold-badge'; badge.textContent = '2x  ▶▶'; host.appendChild(badge);
          try { if (navigator.vibrate) navigator.vibrate(12); } catch (x) {}
        }, 450);
      }, { passive: true, capture: true });
      document.addEventListener('touchmove', function (e) {
        if (!timer || holding || !e.touches || !e.touches[0]) return;
        if (Math.abs(e.touches[0].clientX - sx) > 12 || Math.abs(e.touches[0].clientY - sy) > 12) { clearTimeout(timer); timer = 0; }
      }, { passive: true, capture: true });
      ['touchend', 'touchcancel'].forEach(function (n) { document.addEventListener(n, stop, { passive: true, capture: true }); });
      document.addEventListener('contextmenu', function (e) { if (holding || timer) e.preventDefault(); }, true);
    })();
  } catch (e) {}
})();
} catch (e) {}

/* ═════════ 3) MEDIA ═════════ */
try {
(function () {
  if (window.__novaYt || window.top !== window || !window.NovaYt) return;
  window.__novaYt = 1;
  var BG = __BG__;
  var bg = false, mini = false, userPaused = false, lastResume = 0, nlog = 0;
  // الحماية من إيقاف الصفحة للفيديو: أثناء الخلفية (إن فُعّلت) وأثناء المشغّل المصغّر دائماً
  function guard() { return (BG && bg) || mini; }

  function send(o) { try { window.NovaYt.postMessage(JSON.stringify(o)); } catch (e) {} }
  function log(m) { if (nlog++ < 150) send({ t: 'log', m: String(m).slice(0, 400) }); }
  function v() { return document.querySelector('video'); }

  // القيمة الحقيقية لرؤية الصفحة (قبل التمويه) للتشخيص
  var realVis = function () { try { return Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState').get.call(document); } catch (e) { return '?'; } };
  log('script loaded ' + location.pathname + ' BG=' + BG);

  function meta() {
    var m = navigator.mediaSession && navigator.mediaSession.metadata;
    var t = (m && m.title) || document.title.replace(/ - YouTube$/, '');
    var a = (m && m.artist) || '';
    var art = '';
    if (m && m.artwork && m.artwork.length) art = m.artwork[m.artwork.length - 1].src;
    if (!art) { var og = document.querySelector('meta[property="og:image"]'); if (og) art = og.content; }
    return { title: t, artist: a, art: art };
  }
  function state() {
    var e = v(); if (!e) return;
    var m = meta();
    send({
      t: 'state', playing: !e.paused && !e.ended,
      pos: Math.floor(e.currentTime * 1000),
      dur: isFinite(e.duration) ? Math.floor(e.duration * 1000) : 0,
      title: m.title, artist: m.artist, art: m.art
    });
  }
  ['play', 'pause', 'ended', 'seeked', 'loadedmetadata'].forEach(function (n) {
    document.addEventListener(n, function () { setTimeout(state, 50); }, true);
  });
  ['play', 'pause', 'waiting', 'stalled', 'ended', 'error', 'emptied'].forEach(function (n) {
    document.addEventListener(n, function (ev) {
      if (ev.target && ev.target.tagName === 'VIDEO')
        log('event ' + n + ' t=' + Math.round(ev.target.currentTime) + ' vis=' + realVis() + ' bg=' + bg);
    }, true);
  });
  setInterval(function () { var e = v(); if (e && !e.paused) state(); }, 5000);

  // إخفاء تغيّر الرؤية عن الصفحة حتى لا يوقف يوتيوب التشغيل (يعمل فقط عند وجود الحماية: خلفية أو مشغّل مصغّر)
  try {
    var dsc = function (n) { try { return Object.getOwnPropertyDescriptor(Document.prototype, n); } catch (e) { return null; } };
    var dh = dsc('hidden'), dv = dsc('visibilityState');
    Object.defineProperty(document, 'hidden', { get: function () { return guard() ? false : (dh && dh.get ? dh.get.call(document) : false); }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function () { return guard() ? 'visible' : (dv && dv.get ? dv.get.call(document) : 'visible'); }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return guard() ? false : (dh && dh.get ? dh.get.call(document) : false); }, configurable: true });
    Object.defineProperty(document, 'webkitVisibilityState', { get: function () { return guard() ? 'visible' : (dv && dv.get ? dv.get.call(document) : 'visible'); }, configurable: true });
    ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze', 'blur'].forEach(function (n) {
      window.addEventListener(n, function (e) { if (guard()) e.stopImmediatePropagation(); }, true);
      document.addEventListener(n, function (e) { if (guard()) e.stopImmediatePropagation(); }, true);
    });
  } catch (e) {}

  // يمنع كود يوتيوب نفسه من إيقاف الفيديو أثناء الحماية إلا إذا طلب المستخدم الإيقاف
  try {
    var origPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function () {
      if (guard() && !userPaused && !this.ended) {
        log('blocked page pause');
        return;
      }
      return origPause.apply(this, arguments);
    };
    window.__novaOrigPause = origPause;
  } catch (e) {}

  window.__novaBg = function (b) { bg = !!b; log('bg=' + bg); };
  window.__novaMini = function (on) { mini = !!on; if (mini) userPaused = false; log('mini=' + mini); };

  // إيقاف جاء من خارج الصفحة (نظام/WebView): نستأنف ما لم يطلب المستخدم الإيقاف
  document.addEventListener('play', function () { userPaused = false; }, true);
  document.addEventListener('pause', function (ev) {
    var e = v();
    if (!guard() || userPaused || !e || ev.target !== e || e.ended) return;
    var now = Date.now();
    if (now - lastResume < 400) return;
    lastResume = now;
    log('resuming after external pause vis=' + realVis());
    setTimeout(function () { if (guard() && !userPaused && e.paused) { var p = e.play(); if (p && p.catch) p.catch(function (x) { log('play rejected ' + x); }); } }, 120);
  }, true);
  function clickPlay() {
    var b = document.querySelector('.ytp-play-button,button.player-control-play-pause-icon,[aria-label="Play"]');
    if (b) b.click();
  }

  // أوامر من إشعار الوسائط
  window.__novaYtCtl = function (a) {
    var e = v(); if (!e) { log('ctl ' + a + ' but no video'); return; }
    log('ctl ' + a);
    if (a === 'play') {
      userPaused = false;
      // واجهة المشغّل نفسها أولاً (تتجاوز منطق الإيقاف الداخلي)، ثم play() ثم نقر الزر كاحتياط
      try { var mp = document.getElementById('movie_player'); if (mp && mp.playVideo) mp.playVideo(); } catch (x) {}
      var p = e.play();
      if (p && p.catch) p.catch(function (x) { log('ctl play rejected ' + x); clickPlay(); });
      setTimeout(function () { if (e.paused && !userPaused) { clickPlay(); setTimeout(function () { if (e.paused && !userPaused) { try { e.click(); } catch (x) {} } }, 400); } }, 450);
    }
    else if (a === 'pause') { userPaused = true; (window.__novaOrigPause || e.pause).call(e); }
    else if (a === 'fwd') e.currentTime = Math.min(e.duration || 1e9, e.currentTime + 10);
    else if (a === 'back') e.currentTime = Math.max(0, e.currentTime - 10);
    else if (a.indexOf('seek:') === 0) e.currentTime = parseFloat(a.slice(5)) / 1000;
    setTimeout(state, 50);
  };

  // النافذة المنبثقة (بديل عند عدم استخدام ملء الشاشة): يملأ الفيديو الشاشة ويُخفى كل ما عداه
  window.__novaPip = function (on) {
    var id = '__nova_pip_css', old = document.getElementById(id), e = v();
    log('pip css ' + on);
    if (!on) {
      if (old) old.remove();
      if (e) e.classList.remove('__nova_v');
      (window.__novaPipEls || []).forEach(function (p) { p[0].style.cssText = p[1]; });
      window.__novaPipEls = [];
      return;
    }
    if (!e || old) return;
    var st = document.createElement('style'); st.id = id;
    st.textContent = 'html,body{background:#000!important;overflow:hidden!important}' +
      'body *{visibility:hidden!important}' +
      'video.__nova_v{visibility:visible!important;position:fixed!important;left:0!important;top:0!important;' +
      'width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;' +
      'z-index:2147483647!important;object-fit:contain!important;background:#000!important;transform:none!important}';
    document.head.appendChild(st);
    e.classList.add('__nova_v');
    window.__novaPipEls = [];
    for (var p = e.parentElement; p && p !== document.documentElement; p = p.parentElement) {
      window.__novaPipEls.push([p, p.style.cssText]);
      ['transform', 'filter', 'contain', 'perspective', 'will-change'].forEach(function (k) {
        p.style.setProperty(k, k === 'will-change' ? 'auto' : 'none', 'important');
      });
      p.style.setProperty('overflow', 'visible', 'important');
    }
  };
})();
} catch (e) {}
