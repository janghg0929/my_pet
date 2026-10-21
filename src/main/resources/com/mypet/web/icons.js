// 버튼 그림 (글씨 대신). data-icon="이름" 이 붙은 버튼 앞에 색이 있는 작은 그림을 넣는다.
// 그림만 있는 버튼은 title(마우스를 올리면 나오는 이름)과 aria-label 로 이름을 알려 준다.
// 이모지는 앱 안 웹 엔진에서 흑백으로 나오거나 □ 로 깨져서, 이모지처럼 알아보기 쉬운 그림을 직접 그린다.
(function () {
  "use strict";

  var INK = "#26221e";
  // 16×16 칸 기준 SVG 내용
  var ICONS = {
    // 붓 (그리기): 나무 손잡이 + 쇠테 + 빨간 물감 붓털
    brush:
      '<path d="M14.5 1.5 L9.6 6.4" stroke="#8d5524" stroke-width="2.6" stroke-linecap="round"/>' +
      '<path d="M10.2 5.8 L8.2 7.8" stroke="#9e9e9e" stroke-width="3.2"/>' +
      '<path d="M8.6 7.4 Q5.4 7 4.2 9.6 Q3 12.1 1.5 14.5 Q4.6 13.4 6.6 11.9 Q9.2 10 8.6 7.4 Z" fill="#e53935" stroke="' + INK + '" stroke-width="0.9"/>',
    // 연필 (펜): 노란 몸통 + 나무 끝 + 분홍 지우개
    pen:
      '<path d="M3 13 L3 10 L10 3 L13 6 L6 13 Z" fill="#fdd835" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M3 13 L3 10 L6 13 Z" fill="#ffcc80" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M3 13 L3 11.6 L4.4 13 Z" fill="' + INK + '"/>' +
      '<path d="M10 3 L13 6 L14.3 4.7 L11.3 1.7 Z" fill="#f48fb1" stroke="' + INK + '" stroke-width="1"/>',
    // 지우개: 분홍·파랑 지우개
    eraser:
      '<path d="M1.8 10 L7.8 4 L14 10.2 L10 14 L5.8 14 Z" fill="#f48fb1" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M4.9 6.9 L7.8 4 L14 10.2 L11 13 Z" fill="#4fc3f7" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M10 15 L15 15" stroke="#9e9e9e" stroke-width="1.2" stroke-linecap="round"/>',
    // 채우기: 파란 물감이 흘러나오는 통
    fill:
      '<path d="M2.4 7.6 L7 3 L11.8 7.8 L7.4 12.2 Z" fill="#c8c2b8" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M2.4 7.6 L11.8 7.8" stroke="#1e88e5" stroke-width="1.8"/>' +
      '<path d="M11.8 7.8 Q14.8 9.2 14.3 12.2 Q13.8 14.4 12.9 12.4 Q12.3 10.6 11.8 7.8 Z" fill="#1e88e5"/>',
    // 스포이트: 빨간 고무 + 물감 든 유리관
    picker:
      '<path d="M10.2 1.8 L14.2 5.8 L12.4 7.6 L8.4 3.6 Z" fill="#e53935" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M9.2 4.8 L11.2 6.8" stroke="' + INK + '" stroke-width="1.4"/>' +
      '<path d="M9.6 6.4 L3.6 12.4 L3 14 L4.6 13.4 L10.6 7.4" fill="#e1f5fe" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M6.4 9.6 L3.6 12.4 L3 14 L4.6 13.4 L7.4 10.6 Z" fill="#1e88e5"/>',
    // 되돌리기 / 다시 하기: 파란 화살표
    undo: '<path d="M5 2.8 L1.8 6 L5 9.2 M1.8 6 L10 6 Q14.2 6 14.2 10 Q14.2 14 10 14 L6 14" fill="none" stroke="#1e88e5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
    redo: '<path d="M11 2.8 L14.2 6 L11 9.2 M14.2 6 L6 6 Q1.8 6 1.8 10 Q1.8 14 6 14 L10 14" fill="none" stroke="#1e88e5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
    // 모두 지우기: 휴지통
    trash:
      '<path d="M6 3.4 L6 1.8 L10 1.8 L10 3.4" fill="none" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M3.6 4.4 L4.7 14.4 L11.3 14.4 L12.4 4.4 Z" fill="#c8c2b8" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M1.8 3.8 L14.2 3.8" stroke="' + INK + '" stroke-width="1.6" stroke-linecap="round"/>' +
      '<path d="M6.6 6.8 L6.9 12 M9.4 6.8 L9.1 12" stroke="' + INK + '" stroke-width="1"/>',
    // 사진: 카메라
    camera:
      '<path d="M1.6 5 L4.8 5 L6 2.8 L10 2.8 L11.2 5 L14.4 5 L14.4 13.4 L1.6 13.4 Z" fill="#546e7a" stroke="' + INK + '" stroke-width="1"/>' +
      '<circle cx="8" cy="9" r="3" fill="#4fc3f7" stroke="' + INK + '" stroke-width="1"/>' +
      '<circle cx="8" cy="9" r="1.1" fill="#e1f5fe"/>' +
      '<rect x="11.8" y="6.2" width="1.6" height="1.2" fill="#fdd835"/>',
    // 사진 빼기: 카메라 + 빨간 빗금
    "camera-off":
      '<path d="M1.6 5 L4.8 5 L6 2.8 L10 2.8 L11.2 5 L14.4 5 L14.4 13.4 L1.6 13.4 Z" fill="#546e7a" stroke="' + INK + '" stroke-width="1"/>' +
      '<circle cx="8" cy="9" r="3" fill="#4fc3f7" stroke="' + INK + '" stroke-width="1"/>' +
      '<path d="M1.5 15 L14.5 1" stroke="#ffffff" stroke-width="3.4" stroke-linecap="round"/>' +
      '<path d="M1.5 15 L14.5 1" stroke="#e53935" stroke-width="2" stroke-linecap="round"/>'
  };

  function svg(name) {
    var body = ICONS[name];
    if (!body) {
      return null;
    }
    var markup = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="18" height="18" class="icon" aria-hidden="true">'
      + body + "</svg>";
    var parsed = new DOMParser().parseFromString(markup, "image/svg+xml").documentElement;
    return document.importNode(parsed, true);
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
