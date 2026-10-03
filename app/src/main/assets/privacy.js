(function () {
  'use strict';
  if (window.__nv) return;
  try { Object.defineProperty(window, '__nv', { value: 1 }); } catch (e) {}
  var seed = __SEED__;
  function hash(s) { var x = 2166136261; for (var i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; }
  var st = (hash(String(location.hostname)) ^ seed) >>> 0;
  function rnd() { st = (Math.imul(st, 1664525) + 1013904223) >>> 0; return st / 4294967296; }
  function def(o, p, v) { try { Object.defineProperty(o, p, { get: function () { return v; }, configurable: true }); } catch (e) {} }

  // Navigator: قيم موحّدة بدل القيم الفريدة
  var N = Navigator.prototype;
  def(N, 'hardwareConcurrency', 4);
  def(N, 'deviceMemory', 4);
  def(N, 'doNotTrack', '1');
  def(N, 'globalPrivacyControl', true);
  def(N, 'getBattery', undefined);
  def(N, 'connection', undefined);
  try { var lang = navigator.language || 'en-US'; def(N, 'languages', Object.freeze([lang])); } catch (e) {}
  try {
    var u = navigator.userAgentData;
    if (u) {
      var P = Object.getPrototypeOf(u);
      P.getHighEntropyValues = function () { return Promise.resolve({ brands: this.brands, mobile: this.mobile, platform: this.platform }); };
    }
  } catch (e) {}

  // Canvas: ضجيج طفيف (±1) ثابت داخل الجلسة لكل موقع
  var ctxMap = new WeakMap();
  var getCtx = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (t) {
    var c = getCtx.apply(this, arguments);
    if (t === '2d' && c) ctxMap.set(this, c);
    return c;
  };
  var gid = CanvasRenderingContext2D.prototype.getImageData;
  var pid = CanvasRenderingContext2D.prototype.putImageData;
  function jitter(data) {
    var n = Math.min(data.length, 8192);
    for (var i = 0; i < n; i += 4) { if (rnd() < 0.12) data[i + ((rnd() * 3) | 0)] ^= 1; }
  }
  CanvasRenderingContext2D.prototype.getImageData = function () {
    var d = gid.apply(this, arguments); jitter(d.data); return d;
  };
  function dirty(cv) {
    try {
      var c = ctxMap.get(cv);
      if (!c || !cv.width || !cv.height) return;
      var w = Math.min(cv.width, 32), h = Math.min(cv.height, 32);
      var d = gid.call(c, 0, 0, w, h); jitter(d.data); pid.call(c, d, 0, 0);
    } catch (e) {}
  }
  var tdu = HTMLCanvasElement.prototype.toDataURL;
  HTMLCanvasElement.prototype.toDataURL = function () { dirty(this); return tdu.apply(this, arguments); };
  var tbl = HTMLCanvasElement.prototype.toBlob;
  HTMLCanvasElement.prototype.toBlob = function () { dirty(this); return tbl.apply(this, arguments); };

  // الخطوط: تغيير بالغ الصغر في قياس النص يعطّل بصمة الخطوط
  var mt = CanvasRenderingContext2D.prototype.measureText;
  CanvasRenderingContext2D.prototype.measureText = function () {
    var r = mt.apply(this, arguments);
    try {
      var w = r.width * (1 + (rnd() - 0.5) * 0.0002);
      return new Proxy(r, { get: function (t, p) { if (p === 'width') return w; var v = t[p]; return typeof v === 'function' ? v.bind(t) : v; } });
    } catch (e) { return r; }
  };

  // WebGL: إخفاء اسم المعالج الرسومي
  [window.WebGLRenderingContext, window.WebGL2RenderingContext].forEach(function (C) {
    if (!C) return;
    var gp = C.prototype.getParameter;
    C.prototype.getParameter = function (p) {
      if (p === 37445) return 'Google Inc.';
      if (p === 37446) return 'ANGLE (Generic Renderer)';
      return gp.apply(this, arguments);
    };
  });

  // الصوت: ضجيج دقيق جداً
  if (window.AudioBuffer) {
    var gcd = AudioBuffer.prototype.getChannelData;
    var seen = new WeakSet();
    AudioBuffer.prototype.getChannelData = function () {
      var d = gcd.apply(this, arguments);
      if (!seen.has(d)) { seen.add(d); var n = Math.min(d.length, 20000); for (var i = 0; i < n; i += 97) d[i] += (rnd() - 0.5) * 1e-7; }
      return d;
    };
  }
})();
