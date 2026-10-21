// 버튼 그림 (글씨 대신). data-icon="이름" 이 붙은 버튼 앞에 16×16 선 그림을 넣는다.
// 그림만 있는 버튼은 title(마우스를 올리면 나오는 이름)과 aria-label 로 이름을 알려 준다.
// 이모지는 앱 안 웹 엔진에서 안 나오거나 흑백이라 직접 그린다.
(function () {
  "use strict";

  var PATHS = {
    pen: "M3 13 L3 10 L10 3 L13 6 L6 13 Z M9 4 L12 7",
    eraser: "M2 10 L8 4 L13 9 L8 14 L6 14 Z M5 7 L10 12 M9 14 L14 14",
    fill: "M2 8 L7 3 L12 8 L7 13 Z M2 8 L12 8 M14 10 L14 13",
    picker: "M10 2 L14 6 L12 8 L8 4 Z M9 7 L3 13 L3 14 L4 14 L10 8",
    undo: "M5 3 L2 6 L5 9 M2 6 L10 6 Q14 6 14 10 Q14 14 10 14 L6 14",
    redo: "M11 3 L14 6 L11 9 M14 6 L6 6 Q2 6 2 10 Q2 14 6 14 L10 14",
    trash: "M2 4 L14 4 M6 4 L6 2 L10 2 L10 4 M4 4 L5 14 L11 14 L12 4 M7 7 L7 11 M9 7 L9 11",
    camera: "M2 5 L5 5 L6 3 L10 3 L11 5 L14 5 L14 13 L2 13 Z M8 6.5 A2.5 2.5 0 1 0 8 11.5 A2.5 2.5 0 1 0 8 6.5",
    "camera-off": "M2 5 L5 5 L6 3 L10 3 L11 5 L14 5 L14 13 L2 13 Z M8 6.5 A2.5 2.5 0 1 0 8 11.5 A2.5 2.5 0 1 0 8 6.5 M1 15 L15 1"
  };

  function svg(name) {
    var path = PATHS[name];
    if (!path) {
      return null;
    }
    var ns = "http://www.w3.org/2000/svg";
    var el = document.createElementNS(ns, "svg");
    el.setAttribute("viewBox", "0 0 16 16");
    el.setAttribute("width", "16");
    el.setAttribute("height", "16");
    el.setAttribute("class", "icon");
    el.setAttribute("aria-hidden", "true");
    var p = document.createElementNS(ns, "path");
    p.setAttribute("d", path);
    p.setAttribute("fill", "none");
    p.setAttribute("stroke", "currentColor");
    p.setAttribute("stroke-width", "1.6");
    p.setAttribute("stroke-linejoin", "round");
    p.setAttribute("stroke-linecap", "round");
    el.appendChild(p);
    return el;
  }

  /** data-icon 이 붙은 요소에 그림을 넣는다 (이미 있으면 그대로). */
  function apply(root) {
    Array.prototype.forEach.call((root || document).querySelectorAll("[data-icon]"), function (el) {
      if (el.querySelector("svg.icon")) {
        return;
      }
      var icon = svg(el.getAttribute("data-icon"));
      if (!icon) {
        return;
      }
      el.insertBefore(icon, el.firstChild);
      if (!el.textContent.trim()) {
        el.classList.add("icon-only");
      }
      if (el.title && !el.getAttribute("aria-label")) {
        el.setAttribute("aria-label", el.title);
      }
    });
  }

  window.BitIcons = { svg: svg, apply: apply };
  apply();
})();
