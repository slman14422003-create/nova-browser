(function () {
  if (window.__novaYt || window.top !== window || !window.NovaYt) return;
  window.__novaYt = 1;
  var BG = __BG__;

  function v() { return document.querySelector('video'); }
  function send(o) { try { window.NovaYt.postMessage(JSON.stringify(o)); } catch (e) {} }
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
  setInterval(function () { var e = v(); if (e && !e.paused) state(); }, 5000);

  // تشغيل في الخلفية: إخفاء تغيّر الرؤية عن الصفحة حتى لا يوقف يوتيوب التشغيل
  if (BG) {
    try {
      Object.defineProperty(document, 'hidden', { get: function () { return false; }, configurable: true });
      Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; }, configurable: true });
      document.addEventListener('visibilitychange', function (e) { e.stopImmediatePropagation(); }, true);
    } catch (e) {}
  }

  // الخلفية/النافذة المنبثقة: إن أوقف يوتيوب أو المتصفح الفيديو من تلقاء نفسه نستأنفه (ما لم يطلب المستخدم الإيقاف)
  var bg = false, userPaused = false, lastResume = 0;
  window.__novaBg = function (b) { bg = !!b; };
  document.addEventListener('play', function () { userPaused = false; }, true);
  document.addEventListener('pause', function (ev) {
    var e = v();
    if (!BG || !bg || userPaused || !e || ev.target !== e || e.ended) return;
    var now = Date.now();
    if (now - lastResume < 400) return;
    lastResume = now;
    setTimeout(function () { if (bg && !userPaused && e.paused) { var p = e.play(); if (p && p.catch) p.catch(function () {}); } }, 120);
  }, true);
  function clickPlay() {
    var b = document.querySelector('.ytp-play-button,button.player-control-play-pause-icon,[aria-label="Play"]');
    if (b) b.click();
  }

  // أوامر من إشعار الوسائط
  window.__novaYtCtl = function (a) {
    var e = v(); if (!e) return;
    if (a === 'play') {
      userPaused = false;
      var p = e.play();
      if (p && p.catch) p.catch(function () { clickPlay(); });
    }
    else if (a === 'pause') { userPaused = true; e.pause(); }
    else if (a === 'fwd') e.currentTime = Math.min(e.duration || 1e9, e.currentTime + 10);
    else if (a === 'back') e.currentTime = Math.max(0, e.currentTime - 10);
    else if (a.indexOf('seek:') === 0) e.currentTime = parseFloat(a.slice(5)) / 1000;
    setTimeout(state, 50);
  };

  // النافذة المنبثقة: يملأ الفيديو الشاشة ويُخفى كل ما عداه
  window.__novaPip = function (on) {
    var id = '__nova_pip_css', old = document.getElementById(id), e = v();
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
