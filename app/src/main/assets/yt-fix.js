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
    if (window.__novaYtFix || window.top !== window) return;
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
      'html:not([data-nova-ccpos]) .caption-window,html[data-nova-ccpos="b"] .caption-window{top:auto!important;bottom:calc(15% + var(--nova-cc-off,0)*1%)!important}' +
      'html:not([data-nova-ccpos]) .ytp-autohide .caption-window,html[data-nova-ccpos="b"] .ytp-autohide .caption-window{bottom:calc(6% + var(--nova-cc-off,0)*1%)!important}' +
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
})();
