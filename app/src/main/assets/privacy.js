(function () {
  'use strict';
  if (window.__nv) return;
  try { Object.defineProperty(window, '__nv', { value: 1 }); } catch (e) {}
  var seed = __SEED__;
  var SITE_LANG = '__LANG__';
  function safe(f) { try { f(); } catch (e) {} }
  // مواقع التحقق (Cloudflare Turnstile وhCaptcha وreCAPTCHA وDataDome…): أي تعديل في بصمة المتصفح يجعل التحقق يفشل أو يدور بلا نهاية.
  // لذلك لا نتدخل فيها إطلاقاً (إطاراتها تُحمَّل من نطاقات معروفة).
  if (/(^|\.)(challenges\.cloudflare\.com|hcaptcha\.com|recaptcha\.net|captcha-delivery\.com|datadome\.co|arkoselabs\.com|funcaptcha\.com|geetest\.com|perimeterx\.net|px-cdn\.net|px-client\.net|friendlycaptcha\.com|mtcaptcha\.com)$/.test(String(location.hostname))) return;
  function hash(s) { var x = 2166136261; for (var i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; }
  var rv = 0;
  safe(function () { var a = new Uint32Array(2); crypto.getRandomValues(a); rv = a[0] ^ a[1]; });
  // حالة المولّد عشوائية في كل سياق (صفحة أو إطار): نفس الاستدعاء في سياقين مختلفين يعطي نتيجتين مختلفتين
  var st = (hash(String(location.hostname)) ^ seed ^ rv ^ ((Math.random() * 4294967296) >>> 0)) >>> 0;
  // المولّد يتقدّم مع كل استدعاء: كل قراءة (Canvas/WebGL/صوت) تعطي نتيجة مختلفة قليلاً
  function rnd() { st = (Math.imul(st, 1664525) + 1013904223) >>> 0; return st / 4294967296; }
  // مواقع جوجل/يوتيوب: لا نعدّل Canvas/WebGL/الصوت/قياس الخطوط فيها. هذه الخطافات تُبطئ صفحاتها الثقيلة (وضع الذكاء الاصطناعي، المشغّل)
  // ولا فائدة منها هناك أصلاً لأن جوجل تعرفك عبر حسابك. تبقى حماية الـ Navigator الخفيفة.
  var LIGHT = /(^|\.)(google\.[a-z.]+|youtube\.com|gstatic\.com|googleusercontent\.com|ytimg\.com|googlevideo\.com|github\.com|githubusercontent\.com|github\.dev|gitlab\.com|bitbucket\.org|vscode\.dev|stackblitz\.com|codesandbox\.io|codepen\.io|replit\.com|jsfiddle\.net|huggingface\.co)$/.test(String(location.hostname));
  function def(o, p, v) { try { Object.defineProperty(o, p, { get: function () { return v; }, configurable: true }); } catch (e) {} }
  // صفحة التحقق نفسها (تأتي من نطاق الموقع): تُكتشف ببصمات واضحة في صفحتها، وعندها تُعاد القيم الحقيقية كلها (يُفحص بتقييد زمني ثم يُثبَّت)
  var chl = false, chlAt = 0;
  function inChallenge() {
    if (chl) return true;
    var n = Date.now();
    if (chlAt && n - chlAt < 300) return false;
    chlAt = n;
    try {
      var t = document.title || '';
      if (window._cf_chl_opt || window._cf_chl_ctx || /^(just a moment|checking your browser|attention required|verifying|verify you are human|one more step|security check|ddos-guard|لحظة|جارٍ التحقق)/i.test(t) ||
          document.querySelector('script[src*="challenge-platform"],script[src*="challenges.cloudflare.com"],iframe[src*="challenges.cloudflare.com"],script[src*="hcaptcha.com"],iframe[src*="hcaptcha.com"],script[src*="captcha-delivery.com"],iframe[src*="captcha-delivery.com"]')) chl = true;
    } catch (e) {}
    return chl;
  }
  // خاصية Navigator معدّلة تعيد القيمة الحقيقية داخل صفحات التحقق
  function defN(o, p, v) {
    try {
      var d = Object.getOwnPropertyDescriptor(o, p), og = d && d.get, ov = d && ('value' in d) ? d.value : undefined;
      Object.defineProperty(o, p, { get: function () { return inChallenge() ? (og ? og.call(this) : ov) : v; }, configurable: true });
    } catch (e) {}
  }
  // الخطافات الثقيلة تُطبَّق كاملة على أول استدعاءات (وهي ما تقيسه برامج البصمة)، ثم على استدعاء من كل N فقط:
  // صفحات الأكواد والمحررات والألعاب تستدعيها آلاف المرات فكانت تُثقل الخيط الرئيسي
  var cnt = {};
  function tick(k, free, every) { var n = (cnt[k] = (cnt[k] || 0) + 1); return n <= free || (n % every) === 0; }
  function noisePx(a) {
    var n = a.length, flips = 0, first = -1;
    for (var i = 0; i < n; i += 4) {
      if (a[i + 3] === 0) continue;
      if (first < 0) first = i;
      if (rnd() < 0.015) { a[i + ((rnd() * 3) | 0)] ^= 1; flips++; }
    }
    if (!flips && first >= 0) a[first] ^= 1;   // نضمن تغييراً واحداً على الأقل
  }
  function jitter(a) { var n = Math.min(a.length, 8192); for (var i = 0; i < n; i += 4) { if (rnd() < 0.12) a[i + ((rnd() * 3) | 0)] ^= 1; } }

  // ---------- Navigator ----------
  safe(function () {
    var N = Navigator.prototype;
    defN(N, 'hardwareConcurrency', 4);
    defN(N, 'deviceMemory', 4);
    defN(N, 'doNotTrack', '1');
    defN(N, 'globalPrivacyControl', true);
    if (!LIGHT) { defN(N, 'getBattery', undefined); defN(N, 'connection', undefined); }
    var lang = SITE_LANG || navigator.language || 'en-US';
    if (SITE_LANG) defN(N, 'language', lang);
    defN(N, 'languages', Object.freeze([lang]));
  });
  safe(function () {
    var u = navigator.userAgentData;
    if (u) {
      var maj = ((navigator.userAgent.match(/Chrome\/(\d+)/) || [])[1]) || '120';
      var oh = Object.getPrototypeOf(u).getHighEntropyValues;
      Object.getPrototypeOf(u).getHighEntropyValues = function () {
        if (inChallenge()) return oh.apply(this, arguments);
        var b = this.brands || [];
        return Promise.resolve({
          brands: b, mobile: this.mobile, platform: this.platform, architecture: '', bitness: '', model: '',
          platformVersion: '10.0.0', uaFullVersion: maj + '.0.0.0', wow64: false,
          fullVersionList: b.map(function (x) { return { brand: x.brand, version: maj + '.0.0.0' }; })
        });
      };
    }
  });

  if (LIGHT) return;

  // ---------- Canvas 2D ----------
  var kind = new WeakMap();
  var getCtx = HTMLCanvasElement.prototype.getContext;
  var gid = CanvasRenderingContext2D.prototype.getImageData;
  var pid = CanvasRenderingContext2D.prototype.putImageData;
  var tdu = HTMLCanvasElement.prototype.toDataURL;
  var tbl = HTMLCanvasElement.prototype.toBlob;
  safe(function () {
    HTMLCanvasElement.prototype.getContext = function (t) {
      var c = getCtx.apply(this, arguments);
      if (c) kind.set(this, String(t));
      return c;
    };
    CanvasRenderingContext2D.prototype.getImageData = function () { var d = gid.apply(this, arguments); if (!inChallenge() && tick('gid', 300, 16)) jitter(d.data); return d; };
  });
  // نسخة منقّحة من أي Canvas (2D أو WebGL) تُستخدم عند التصدير
  function noisyCopy(cv) {
    try {
      var w = cv.width, h = cv.height;
      if (!w || !h) return null;
      var t = document.createElement('canvas'); t.width = w; t.height = h;
      var c = getCtx.call(t, '2d'); c.drawImage(cv, 0, 0);
      var rw = w, rh = h;
      if (w * h > 600000) { rw = Math.min(w, 512); rh = Math.min(h, 512); }   // لوحات كبيرة: جزء فقط للسرعة
      var d = gid.call(c, 0, 0, rw, rh); noisePx(d.data); pid.call(c, d, 0, 0);
      return t;
    } catch (e) { return null; }
  }
  function isKnown(cv) { return kind.has(cv); }
  safe(function () {
    HTMLCanvasElement.prototype.toDataURL = function () {
      var t = (isKnown(this) && !inChallenge() && tick('cv', 40, 8)) ? noisyCopy(this) : null;
      return tdu.apply(t || this, arguments);
    };
    HTMLCanvasElement.prototype.toBlob = function () {
      var t = (isKnown(this) && !inChallenge() && tick('cv', 40, 8)) ? noisyCopy(this) : null;
      return tbl.apply(t || this, arguments);
    };
  });
  // OffscreenCanvas على الخيط الرئيسي
  safe(function () {
    if (!window.OffscreenCanvas) return;
    var ogid = OffscreenCanvasRenderingContext2D.prototype.getImageData;
    OffscreenCanvasRenderingContext2D.prototype.getImageData = function () { var d = ogid.apply(this, arguments); if (!inChallenge() && tick('gid', 300, 16)) jitter(d.data); return d; };
    var cb = OffscreenCanvas.prototype.convertToBlob;
    OffscreenCanvas.prototype.convertToBlob = function () {
      try { var c = this.getContext('2d'); if (c && !inChallenge() && tick('cv', 40, 8)) { var d = ogid.call(c, 0, 0, Math.min(this.width, 512), Math.min(this.height, 512)); noisePx(d.data); c.putImageData(d, 0, 0); } } catch (e) {}
      return cb.apply(this, arguments);
    };
  });

  // الخطوط: تغيير بالغ الصغر في قياس النص
  safe(function () {
    var mt = CanvasRenderingContext2D.prototype.measureText;
    CanvasRenderingContext2D.prototype.measureText = function () {
      var r = mt.apply(this, arguments);
      if (inChallenge() || !tick('mt', 600, 64)) return r;
      try {
        var w = r.width * (1 + (rnd() - 0.5) * 0.0002);
        // خاصية مباشرة على الكائن بدل Proxy: measureText تُستدعى آلاف المرات في الصفحات الكثيفة النص (محررات، مخططات، ألعاب)،
        // وإنشاء Proxy مع bind لكل خاصية في كل استدعاء كان يستهلك الخيط الرئيسي ويسبب تقطيعاً
        Object.defineProperty(r, 'width', { value: w, configurable: true });
        return r;
      } catch (e) { return r; }
    };
  });

  // ---------- WebGL ----------
  [window.WebGLRenderingContext, window.WebGL2RenderingContext].forEach(function (C) {
    if (!C) return;
    safe(function () {
      var gp = C.prototype.getParameter;
      C.prototype.getParameter = function (p) {
        if ((p === 37445 || p === 37446) && !inChallenge()) return p === 37445 ? 'Google Inc.' : 'ANGLE (Generic Renderer)';
        return gp.apply(this, arguments);
      };
      var rp = C.prototype.readPixels;
      C.prototype.readPixels = function () {
        var r = rp.apply(this, arguments);
        if (inChallenge() || !tick('rp', 100, 16)) return r;
        for (var i = 0; i < arguments.length; i++) {
          var a = arguments[i];
          if (a && a.BYTES_PER_ELEMENT === 1 && a.length) { jitter(a); break; }
        }
        return r;
      };
    });
  });

  // ---------- الصوت ----------
  safe(function () {
    if (!window.AudioBuffer) return;
    function audioNoise(d) { if (inChallenge()) return; var n = Math.min(d.length, 20000); for (var i = 0; i < n; i += 97) d[i] += (rnd() - 0.5) * 1e-7; }
    var gcd = AudioBuffer.prototype.getChannelData;
    var seen = new WeakSet();
    AudioBuffer.prototype.getChannelData = function () {
      var d = gcd.apply(this, arguments);
      if (!seen.has(d)) { seen.add(d); audioNoise(d); }
      return d;
    };
    var cfc = AudioBuffer.prototype.copyFromChannel;
    AudioBuffer.prototype.copyFromChannel = function (dest) { var r = cfc.apply(this, arguments); audioNoise(dest); return r; };
  });
  safe(function () {
    if (!window.AnalyserNode) return;
    var gff = AnalyserNode.prototype.getFloatFrequencyData;
    AnalyserNode.prototype.getFloatFrequencyData = function (a) { var r = gff.apply(this, arguments); if (inChallenge()) return r; for (var i = 0; i < a.length; i += 7) a[i] += (rnd() - 0.5) * 1e-4; return r; };
    var gft = AnalyserNode.prototype.getFloatTimeDomainData;
    AnalyserNode.prototype.getFloatTimeDomainData = function (a) { var r = gft.apply(this, arguments); if (inChallenge()) return r; for (var i = 0; i < a.length; i += 7) a[i] += (rnd() - 0.5) * 1e-7; return r; };
  });
})();
