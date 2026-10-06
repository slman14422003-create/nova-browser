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
