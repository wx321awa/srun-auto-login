// ==UserScript==
// @name         校园网深澜自动登录
// @namespace    dsh.campus.srun
// @version      1.0.0
// @description  打开 10.33.0.2 认证页时自动填账号密码并点登录（不用算加密，页面改版也不怕）
// @match        http://10.33.0.2/*
// @match        https://10.33.0.2/*
// @match        http://10.33.0.2:*/*
// @match        https://10.33.0.2:*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  // ======== 只需要改这两行 ========
  const USERNAME = '你的账号';
  const PASSWORD = '你的密码';
  // ===============================

  // React / Vue 绑定需要走原生 setter，否则框架读不到值
  function setValue(el, value) {
    const proto = el instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype
      : HTMLInputElement.prototype;
    const desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) {
      desc.set.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  function findByHint(keywords) {
    const all = document.querySelectorAll('input, textarea');
    for (const kw of keywords) {
      for (const el of all) {
        const hint = ((el.id || '') + ' ' + (el.name || '') + ' ' + (el.placeholder || '') +
                      ' ' + (el.className || '')).toLowerCase();
        if (hint.includes(kw) && el.offsetParent !== null) return el;
      }
    }
    return null;
  }

  function pickFields() {
    let pwd = document.querySelector('input[type="password"]');
    if (!pwd) pwd = findByHint(['password', 'pwd', 'pass']);
    let user = findByHint(['username', 'user', 'account', 'loginname', 'name']);
    if (user === pwd) user = null;
    if (!user) {
      const cands = [...document.querySelectorAll('input[type="text"], input:not([type])')]
        .filter((e) => e.offsetParent !== null && e !== pwd);
      user = cands[0] || null;
    }
    return { user, pwd };
  }

  function clickLogin() {
    const texts = ['登录', '登录上网', '上网登录', '认证', '连接', 'login', 'LOGIN'];
    const nodes = [...document.querySelectorAll('button, a, input[type="submit"], input[type="button"], div[class*=btn]')];
    for (const n of nodes) {
      const t = (n.innerText || n.value || '').trim().toLowerCase();
      if (!t) continue;
      if (texts.some((k) => t.includes(k.toLowerCase())) && n.offsetParent !== null) {
        n.click();
        return 'clicked:' + t.slice(0, 12);
      }
    }
    if (typeof window.login === 'function') {
      try { window.login(); return 'window.login()'; } catch (e) { /* ignore */ }
    }
    return '';
  }

  let done = false;
  let tries = 0;

  const timer = setInterval(function () {
    if (done) return;
    tries += 1;
    if (tries > 40) { clearInterval(timer); return; }

    // 已经在线 / 不是登录页就别动
    const body = document.body ? document.body.innerText : '';
    if (/已在线|在线时长|注销|logout/i.test(body) && !document.querySelector('input[type="password"]')) {
      clearInterval(timer); done = true; return;
    }

    const { user, pwd } = pickFields();
    if (!user || !pwd) return;

    if (user.value !== USERNAME) setValue(user, USERNAME);
    if (pwd.value !== PASSWORD) setValue(pwd, PASSWORD);
    if (!user.value || !pwd.value) return;

    done = true;
    clearInterval(timer);
    setTimeout(function () {
      const r = clickLogin();
      console.log('[校园网] 已填好，' + (r || '没找到登录按钮，手动点一下'));
    }, 300);
  }, 500);
})();