/* Nova AI bridge — يحوّل موقع الذكاء الاصطناعي إلى «واجهة برمجية» للواجهة الأصلية للتطبيق.
 * الإعدادات (المحدّدات) في ai-sites.json؛ عند تغيّر تصميم موقع يكفي تعديل ذلك الملف دون لمس الكود.
 * native → Kotlin:  window.NovaAi.postMessage(JSON)   {t:'snap'|'diag'|'up'}
 * Kotlin → native:  window.__novaAi.send / stop / newChat / diag / fc / fe / attach
 */
(function () {
  'use strict';
  try {
    if (window.__novaAi || window.top !== window) return;
    var H = location.hostname.replace(/^www\./, '');
    var ALL = __CFG__;
    var S = null;
    for (var k in ALL) { if (H === k || H.slice(-(k.length + 1)) === '.' + k) { S = ALL[k]; break; } }
    if (!S) return;

    function post(o) { try { window.NovaAi.postMessage(JSON.stringify(o)); } catch (e) {} }
    function qa(sel, root) { if (!sel) return []; try { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); } catch (e) { return []; } }
    function vis(e) {
      if (!e || !e.getBoundingClientRect) return false;
      var r = e.getBoundingClientRect(), cs = getComputedStyle(e);
      return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none';
    }
    function firstVis(sel) { var l = qa(sel); for (var i = 0; i < l.length; i++) if (vis(l[i])) return l[i]; return null; }

    // ───────── الإدخال والإرسال ─────────
    function findInput() {
      var e = firstVis(S.input);
      if (e) return e;
      var c = qa('textarea,[contenteditable=""],[contenteditable="true"],[role=textbox]').filter(vis);
      c.sort(function (a, b) { return b.getBoundingClientRect().bottom - a.getBoundingClientRect().bottom; });
      return c[0] || null;
    }
    function setText(el, t) {
      el.focus();
      if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
        var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, t);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        return;
      }
      var sel = window.getSelection(), r = document.createRange();
      r.selectNodeContents(el); sel.removeAllRanges(); sel.addRange(r);
      var ok = false;
      try { ok = document.execCommand('insertText', false, t); } catch (e) {}
      if (!ok) { el.textContent = t; el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: t })); }
    }
    var SEND_RE = /send|submit|إرسال|ارسال/i;
    function enabled(x) { return vis(x) && !x.disabled && x.getAttribute('aria-disabled') !== 'true'; }
    function findSend(inp) {
      var b = firstVis(S.send);
      if (b && enabled(b)) return b;
      var p = inp;
      for (var d = 0; p && d < 7; d++, p = p.parentElement) {
        var bs = qa('button,[role=button]', p).filter(enabled);
        for (var i = bs.length - 1; i >= 0; i--) {
          var x = bs[i];
          var lab = (x.getAttribute('aria-label') || '') + ' ' + (x.getAttribute('data-testid') || '') + ' ' + (x.title || '');
          if (SEND_RE.test(lab)) return x;
        }
      }
      return null;
    }
    function enter(inp) {
      ['keydown', 'keypress', 'keyup'].forEach(function (n) {
        inp.dispatchEvent(new KeyboardEvent(n, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
      });
    }
    function doSend(text) {
      var inp = findInput();
      if (!inp) { post({ t: 'err', x: 'noinput' }); return; }
      if (text) setText(inp, text);
      var n = 0;
      (function tryClick() {
        var b = findSend(inp);
        if (b) { b.click(); setTimeout(snap, 400); return; }
        if (++n < 8) { setTimeout(tryClick, 150); return; }
        enter(inp);
      })();
    }
    function stopBtn() { return firstVis(S.stop) || firstVis('button[aria-label*="Stop" i],[data-testid*="stop" i]'); }

    // ───────── قراءة الرسائل (DOM → Markdown) ─────────
    var SKIP = /^(script|style|button|svg|noscript|mat-icon|textarea)$/i;
    function md(root) {
      function kids(e, ctx) { var s = ''; for (var c = e.firstChild; c; c = c.nextSibling) s += node(c, ctx); return s; }
      function node(n, ctx) {
        if (n.nodeType === 3) return n.nodeValue.replace(/\s+/g, ' ');
        if (n.nodeType !== 1 || SKIP.test(n.tagName)) return '';
        if (n.classList && n.classList.contains('sr-only')) return '';
        var t = n.tagName.toLowerCase(), s;
        switch (t) {
          case 'br': return '\n';
          case 'hr': return '\n---\n\n';
          case 'p': case 'div': case 'section': case 'article': s = kids(n, ctx).trim(); return s ? s + '\n\n' : '';
          case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6':
            return '\n' + new Array(Math.min(3, +t.charAt(1)) + 1).join('#') + ' ' + kids(n, ctx).trim() + '\n\n';
          case 'strong': case 'b': s = kids(n, ctx).trim(); return s ? '**' + s + '**' : '';
          case 'em': case 'i': s = kids(n, ctx).trim(); return s ? '*' + s + '*' : '';
          case 'code': return (n.parentElement && n.parentElement.tagName === 'PRE') ? n.textContent : '`' + n.textContent + '`';
          case 'pre':
            var cd = n.querySelector('code'), m = cd && cd.className.match(/language-([\w+-]+)/);
            return '\n```' + (m ? m[1] : '') + '\n' + (cd || n).textContent.replace(/\n$/, '') + '\n```\n\n';
          case 'a': s = kids(n, ctx).trim(); return (n.href && s) ? '[' + s + '](' + n.href + ')' : s;
          case 'blockquote': return '> ' + kids(n, ctx).trim().replace(/\n/g, '\n> ') + '\n\n';
          case 'ul': case 'ol':
            var i = 0, r = '';
            for (var li = n.firstElementChild; li; li = li.nextElementSibling) {
              if (li.tagName === 'LI') { i++; r += ctx + (t === 'ol' ? i + '. ' : '- ') + kids(li, ctx + '  ').trim() + '\n'; }
            }
            return r + '\n';
          case 'tr':
            var cells = []; for (var c = n.firstElementChild; c; c = c.nextElementSibling) cells.push(kids(c, ctx).trim().replace(/\n+/g, ' '));
            return '| ' + cells.join(' | ') + ' |\n';
          default: return kids(n, ctx);
        }
      }
      return node(root, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    }
    var cache = typeof WeakMap === 'function' ? new WeakMap() : null;
    function conv(e, user) {
      var len = (e.textContent || '').length, c = cache && cache.get(e);
      if (c && c.len === len) return c.x;
      var x = user ? (e.innerText || '').trim() : md(e);
      if (cache) cache.set(e, { len: len, x: x });
      return x;
    }
    function outer(list) { return list.filter(function (e) { return !list.some(function (o) { return o !== e && o.contains(e); }); }); }
    function collect() {
      var us = S.user, bs = S.bot, both = !!(us && bs);
      var els = outer(qa(both ? us + ',' + bs : '[data-message-author-role]'));
      var out = [];
      for (var i = 0; i < els.length; i++) {
        var e = els[i];
        var u = both ? e.matches(us) : e.getAttribute('data-message-author-role') === 'user';
        var x = conv(e, u);
        if (x) out.push({ r: u ? 'u' : 'a', x: x });
      }
      return out;
    }

    var last = '', timer = 0;
    function snap() {
      timer = 0;
      var o = { t: 'snap', input: !!findInput(), busy: !!stopBtn(), msgs: collect() };
      var j = JSON.stringify(o);
      if (j === last) return;
      last = j; post(o);
    }
    function sched() { if (!timer) timer = setTimeout(snap, 250); }
    new MutationObserver(sched).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    setInterval(snap, 1500);

    // ───────── رفع الملفات ─────────
    var bufs = {};
    function fileInput() {
      var l = qa(S.file || 'input[type=file]').filter(function (x) { return !x.disabled && !x.webkitdirectory; });
      return l[0] || null;
    }
    function attachBtn() {
      var b = firstVis(S.attach);
      if (b) return b;
      var inp = findInput(), p = inp;
      for (var d = 0; p && d < 7; d++, p = p.parentElement) {
        var bs = qa('button,[role=button]', p).filter(enabled);
        for (var i = 0; i < bs.length; i++) {
          var lab = (bs[i].getAttribute('aria-label') || '') + ' ' + (bs[i].getAttribute('data-testid') || '');
          if (/attach|upload|file|plus|إرفاق|رفع|إضافة/i.test(lab)) return bs[i];
        }
      }
      return null;
    }

    window.__novaAi = {
      send: doSend,
      stop: function () { var b = stopBtn(); if (b) b.click(); },
      newChat: function () {
        var b = firstVis(S.newChat);
        if (b) b.click(); else if (S.home) location.href = S.home;
        setTimeout(snap, 600);
      },
      attach: function () { var b = attachBtn(); if (b) b.click(); },
      fc: function (id, chunk) { (bufs[id] = bufs[id] || []).push(chunk); },
      fe: function (id, name, mime) {
        try {
          var bin = atob((bufs[id] || []).join('')); delete bufs[id];
          var u8 = new Uint8Array(bin.length);
          for (var i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
          var f = new File([u8], name, { type: mime || 'application/octet-stream' });
          var inp = fileInput();
          if (!inp) { post({ t: 'up', ok: false, x: 'noinput' }); return; }
          var dt = new DataTransfer(); dt.items.add(f); inp.files = dt.files;
          inp.dispatchEvent(new Event('input', { bubbles: true }));
          inp.dispatchEvent(new Event('change', { bubbles: true }));
          post({ t: 'up', ok: true });
        } catch (e) { post({ t: 'up', ok: false, x: String(e) }); }
      },
      diag: function () {
        function d(el) { return el ? (el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.className && typeof el.className === 'string' ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.') : '')) : 'NOT FOUND'; }
        var inp = findInput(), m = collect();
        post({ t: 'diag', x: [
          'host: ' + H,
          'input: ' + d(inp),
          'send: ' + d(inp && findSend(inp)) + ' (يظهر غالباً بعد كتابة نص)',
          'stop: ' + d(stopBtn()),
          'attach: ' + d(attachBtn()),
          'file input: ' + d(fileInput()),
          'user sel matches: ' + qa(S.user).length,
          'bot sel matches: ' + qa(S.bot).length,
          'messages read: ' + m.length,
          'last: ' + (m.length ? m[m.length - 1].r + ' | ' + m[m.length - 1].x.slice(0, 80) : '-')
        ].join('\n') });
      }
    };
    snap();
  } catch (e) {}
})();
