// 마우스를 올리면 뜨는 이름표. 앱 안 웹 엔진(JavaFX WebView)은 title 을 띄워 주지 않아서 직접 띄운다.
// title 이 붙은 요소에 잠깐(0.35초) 올려 두면 아래(자리가 없으면 위)에 작은 이름표가 뜬다.
// 코드가 나중에 title 을 바꿔도 올릴 때마다 다시 읽는다 (title 은 data-tip 으로 옮겨서 브라우저 기본 풍선과 겹치지 않게).
(function () {
  "use strict";

  var DELAY_MS = 350;
  var tip = null;
  var timer = null;
  var current = null;

  function element() {
    if (!tip) {
      tip = document.createElement("div");
      tip.className = "tooltip";
      tip.setAttribute("role", "tooltip");
      tip.hidden = true;
      document.body.appendChild(tip);
    }
    return tip;
  }

  function textOf(el) {
    if (el.getAttribute("title")) {
      el.setAttribute("data-tip", el.getAttribute("title"));
      el.removeAttribute("title");
    }
    return el.getAttribute("data-tip") || "";
  }

  function hide() {
    clearTimeout(timer);
    timer = null;
    current = null;
    if (tip) {
      tip.hidden = true;
    }
  }

  function show(el) {
    var text = textOf(el);
    if (!text || !document.body.contains(el)) {
      return;
    }
    var t = element();
    t.textContent = text;
    t.hidden = false;
    var r = el.getBoundingClientRect();
    var w = t.offsetWidth, h = t.offsetHeight;
    var vw = document.documentElement.clientWidth, vh = document.documentElement.clientHeight;
    var left = Math.max(4, Math.min(vw - w - 4, r.left + r.width / 2 - w / 2));
    var top = r.bottom + 6;
    if (top + h > vh - 4) {
      top = Math.max(4, r.top - h - 6); // 아래에 자리가 없으면 위에
    }
    t.style.left = Math.round(left) + "px";
    t.style.top = Math.round(top) + "px";
  }

  document.addEventListener("mouseover", function (event) {
    var el = event.target.closest ? event.target.closest("[title], [data-tip]") : null;
    if (el === current) {
      return;
    }
    hide();
    if (!el) {
      return;
    }
    textOf(el); // title → data-tip
    current = el;
    timer = setTimeout(function () { show(el); }, DELAY_MS);
  });
  document.addEventListener("mouseout", function (event) {
    if (current && (!event.relatedTarget || !current.contains(event.relatedTarget))) {
      hide();
    }
  });
  // 누르거나, 키를 치거나, 굴리면 바로 숨긴다
  ["mousedown", "keydown", "wheel"].forEach(function (type) {
    document.addEventListener(type, hide, true);
  });

  window.BitTooltip = { hide: hide };
})();
