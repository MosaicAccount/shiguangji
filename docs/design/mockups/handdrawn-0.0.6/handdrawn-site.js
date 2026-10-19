/* 手绘站设计图的小工具：主题切换、导航选中、浮层开合。
 * 产品实现里这些由 Vue / Pinia 接管，本文件只服务设计图。 */
(function () {
  var d = document, root = d.documentElement;
  var params = new URLSearchParams(location.search);

  function label(btn, dark) {
    btn.setAttribute('aria-label', dark ? '切到日间' : '切到夜间');
    btn.dataset.dark = dark ? '1' : '';
  }
  function setTheme(theme, persist) {
    var dark = theme === 'dark';
    root.classList.toggle('dark', dark);
    d.querySelectorAll('[data-theme-btn]').forEach(function (b) { label(b, dark); });
    if (persist) { try { localStorage.setItem('hd-theme', dark ? 'dark' : 'light'); } catch (e) {} }
  }
  setTheme(params.get('theme') || (function () { try { return localStorage.getItem('hd-theme'); } catch (e) { return null; } })() || 'light', false);
  d.querySelectorAll('[data-theme-btn]').forEach(function (b) {
    b.addEventListener('click', function () { setTheme(root.classList.contains('dark') ? 'light' : 'dark', true); });
  });

  /* 浮层：按钮 data-open 指向选择器，点遮罩或 [data-close] 关闭 */
  d.querySelectorAll('[data-open]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      var el = d.querySelector(btn.dataset.open);
      if (!el) return;
      e.preventDefault();
      el.hidden = false;
    });
  });
  d.querySelectorAll('.hd-scrim').forEach(function (scrim) {
    scrim.addEventListener('click', function (e) { if (e.target === scrim) scrim.hidden = true; });
    scrim.querySelectorAll('[data-close]').forEach(function (b) {
      b.addEventListener('click', function () { scrim.hidden = true; });
    });
  });
  d.querySelectorAll('[data-close]').forEach(function (b) {
    if (b.closest('.hd-scrim')) return;
    b.addEventListener('click', function () { var s = b.closest('.hd-scrim'); if (s) s.hidden = true; });
  });

  /* 导航选中：<body data-page="home"> 对应 [data-nav="home"] */
  var page = d.body.dataset.page;
  if (page) {
    d.querySelectorAll('[data-nav]').forEach(function (a) {
      a.classList.toggle('on', a.dataset.nav === page);
      if (a.dataset.nav === page) a.setAttribute('aria-current', 'page');
    });
  }
})();
