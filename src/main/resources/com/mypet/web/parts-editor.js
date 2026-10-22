// 모습 꾸미기 편집기: 부위(몸통, 양팔, 양다리, 눈)를 고르고, 끌어서 옮기고, 모서리로 크기를 바꾸고, 위쪽 동그라미로 돌린다.
// [모양·사진]: 숫자 입력, 부위 사진 넣기/빼기(눈은 표정별).
// [움직임]: 상황(항상/쉴 때/걸을 때/공중/잡힐 때)마다 자세(끌거나 돌려서) + 반복 움직임, 부위 중심점.
// 좌우 맞추기, 기본 비트로 되돌리기, Ctrl+Z / Ctrl+Y (되돌리기 / 다시 하기), 마우스 휠 확대 + Shift+끌기로 화면 옮기기.
// 여러 부위 고르기·그룹·그룹 대칭·부위 하나 더는 parts-select.js.
// 바뀐 배치는 send("setParts", JSON) 으로 Java 에 보내 화면 비트에 바로 적용하고 저장한다.
(function () {
  "use strict";

  // 부위 고르기 순서 (만든 부위는 뒤에 붙는다)
  var PART_ORDER = ["body", "eyes", "leftArm", "rightArm", "leftLeg", "rightLeg"];
  var MAX_CUSTOM = 40; // PartLayout.MAX_CUSTOM
  var CUSTOM_SIZE = 24; // 새로 만든 부위의 크기 (PartLayout.CUSTOM_SIZE)
  var MAX_RESOLUTION = 256; // 그림판 해상도 한쪽 칸 수 (PartLayout.MAX_RESOLUTION)
  var EXPRESSIONS = [{ key: "neutral", label: "평소" }, { key: "grab", label: "잡혔을 때" }, { key: "fall", label: "떨어질 때" }];

  var MIRROR = { leftArm: "rightArm", rightArm: "leftArm", leftLeg: "rightLeg", rightLeg: "leftLeg" };
  var HANDLE = 9; // 크기 조절 손잡이 (화면 px)
  var ROTATE_GAP = 16; // 회전 손잡이가 부위 위로 떨어진 거리 (화면 px)
  var ROTATE_RADIUS = 5;
  var ROTATE_SNAP = 15, ROTATE_SNAP_RANGE = 4; // 15도 단위 근처에서는 딱 맞춘다
  // 크기·자리 한계 (사실상 없음, PartLayout / PartAnimation 과 같다)
  var MIN_SIZE = 2, MAX_SIZE = 2000, MAX_OFFSET = 2000, MAX_POSE_OFFSET = 2000;
  var FIELDS = ["x", "y", "w", "h", "rotation"];
  var STATE_LABELS = { always: "항상", idle: "쉴 때", walk: "걸을 때", air: "공중", grab: "잡힐 때" };
  // 같은 움직임도 부위에 따라 이름을 다르게 (몸통 흔들기 = 기우뚱, 눈 좌우 = 두리번)
  var MOTION_LABELS = { none: "없음", swing: "흔들기", bob: "둥실", sway: "좌우", breathe: "숨쉬기", blink: "깜빡" };
  var MOTION_LABELS_FOR = { body: { swing: "기우뚱" }, eyes: { sway: "두리번" } };
  var PIVOTS = [
    { key: "center", label: "가운데" }, { key: "top", label: "위" }, { key: "bottom", label: "아래" },
    { key: "left", label: "왼쪽" }, { key: "right", label: "오른쪽" }
  ];
  var TICK_MS = 1000 / 64;
  var HISTORY_LIMIT = 100;
  var ZOOM_MIN = 1, ZOOM_MAX = 16, ZOOM_STEP = 1.2;

  /** 키 순서와 상관없이 같은 배치면 같은 글자 (되돌리기 기록에서 같은 배치인지 볼 때). */
  function canonical(value) {
    if (Array.isArray(value)) {
      return "[" + value.map(canonical).join(",") + "]";
    }
    if (value && typeof value === "object") {
      return "{" + Object.keys(value).sort().map(function (key) {
        return JSON.stringify(key) + ":" + canonical(value[key]);
      }).join(",") + "}";
    }
    return JSON.stringify(value === undefined ? null : value);
  }

  /** 각도를 -179..180 도로 맞춘다 (PartLayout.normalizeRotation 과 같다). */
  function normalizeRotation(degrees) {
    var r = ((Math.round(degrees) % 360) + 360) % 360;
    return r > 180 ? r - 360 : r;
  }

  /** (x, y) 를 기준점 (cx, cy) 둘레로 angle(라디안) 만큼 돌린 점. */
  function rotatePoint(x, y, cx, cy, angle) {
    var cos = Math.cos(angle), sin = Math.sin(angle);
    var dx = x - cx, dy = y - cy;
    return { x: cx + dx * cos - dy * sin, y: cy + dx * sin + dy * cos };
  }

  function clamp(v, limit) {
    return Math.max(-limit, Math.min(limit, v));
  }

  function PartsEditor(options) {
    this.send = options.send;
    this.getColor = options.getColor;
    this.canvas = document.getElementById("parts-canvas");
    this.layout = window.BitView.copyLayout();
    this.selected = "body";
    this.expression = "neutral";
    this.drag = null;
    this.view = null; // { k, ox, oy, w, h } (화면 px)
    this.commitTimer = null;
    this.resetArmed = false;
    this.level = "basic"; // basic = 일반, advanced = 고급(시간 막대: timeline-editor.js)
    this.mode = "shape"; // shape = 모양·사진, motion = 움직임
    this.animState = "always"; // 움직임에서 고른 상황
    this.clock = new window.BitView.MotionClock();
    this.animating = false;
    this.visible = false;
    // 되돌리기: 저장된(Java 에 보낸) 배치들. committed = 지금 저장된 배치
    this.undoStack = [];
    this.redoStack = [];
    this.committed = null;
    this.committedKey = null;
    // 마우스 휠로 확대했으면 자동으로 다시 맞추지 않는다
    this.zoomed = false;
    this.painter = window.PixelPainter ? new window.PixelPainter() : null;
    this.build();
    this.buildMotion();
    if (this.initTimeline) {
      this.initTimeline();
    }
  }

  // ---------------------------------------------------------------- 화면 구성

  PartsEditor.prototype.build = function () {
    var self = this;
    this.buildPartManager();
    var eyeTabs = document.getElementById("parts-eye-tabs");
    EXPRESSIONS.forEach(function (expr) {
      var button = document.createElement("button");
      button.className = "seg";
      button.textContent = expr.label;
      button.setAttribute("data-expr", expr.key);
      button.addEventListener("click", function () {
        self.expression = expr.key;
        self.refreshControls();
        self.draw();
      });
      eyeTabs.appendChild(button);
    });

    FIELDS.forEach(function (field) {
      var input = document.getElementById("parts-" + field);
      input.addEventListener("input", function () { self.onNumber(field, input.value); });
      input.addEventListener("change", function () { self.refreshControls(); });
    });

    document.getElementById("parts-symmetric").addEventListener("change", function (event) {
      self.layout.symmetric = event.target.checked;
      if (self.layout.symmetric) {
        self.mirrorFrom("leftArm");
        self.mirrorFrom("leftLeg");
      }
      self.refreshControls();
      self.draw();
      self.commit(0);
    });
    document.getElementById("parts-photo").addEventListener("click", function () {
      self.send("pickPartImage", self.selected, self.expression);
    });
    document.getElementById("parts-photo-remove").addEventListener("click", function () {
      self.send("removePartImage", self.selected, self.expression);
    });
    document.getElementById("parts-draw").addEventListener("click", function () { self.openPainter("part"); });
    var reset = document.getElementById("parts-reset");
    reset.addEventListener("click", function () {
      if (!self.resetArmed) {
        self.resetArmed = true;
        reset.textContent = "한 번 더 누르면 되돌려요";
        setTimeout(function () {
          self.resetArmed = false;
          reset.textContent = "기본 비트로";
        }, 3000);
        return;
      }
      self.resetArmed = false;
      reset.textContent = "기본 비트로";
      self.send("resetParts");
    });

    this.buildPartMenu();
    this.canvas.addEventListener("mousedown", function (event) { self.onMouseDown(event); });
    this.canvas.addEventListener("mousemove", function (event) { self.onHover(event); });
    document.addEventListener("mousemove", function (event) { self.onMouseMove(event); });
    document.addEventListener("mouseup", function () { self.onMouseUp(); });
    // 창 너비가 바뀌면 다시 맞춰 그린다
    window.addEventListener("resize", function () {
      if (!self.drag) {
        self.zoomed = false;
        self.view = null;
        self.draw();
      }
    });
    // Ctrl+Z 되돌리기, Ctrl+Y / Ctrl+Shift+Z 다시 하기 (모습 꾸미기 화면에서만)
    document.addEventListener("keydown", function (event) {
      if (!self.visible) {
        return;
      }
      if (self.partMenuOpen()) {
        if (event.keyCode === 27 || event.key === "Escape") {
          self.closePartMenu();
        } else if (event.keyCode === 13 || event.key === "Enter") {
          self.applyPartMenu();
        }
        return;
      }
      // 그림판이 열려 있으면 그림판 키 (Ctrl+Z/Y 는 그림판 되돌리기, Esc 는 닫기)
      if (self.painter && self.painter.isOpen()) {
        if (self.painter.handleKey(event)) {
          event.preventDefault();
        }
        return;
      }
      if (!(event.ctrlKey || event.metaKey)) {
        if (self.advanced() && self.timelineKey) {
          self.timelineKey(event);
        }
        return;
      }
      var key = (event.key || "").toLowerCase();
      var z = key === "z" || event.keyCode === 90;
      var y = key === "y" || event.keyCode === 89;
      if (!z && !y) {
        return;
      }
      event.preventDefault();
      if (document.activeElement && document.activeElement.blur) {
        document.activeElement.blur(); // 숫자 칸의 자체 되돌리기 대신 편집기 되돌리기
      }
      if (y || event.shiftKey) {
        self.redo();
      } else {
        self.undo();
      }
    });
    // 마우스 휠: 마우스 자리를 기준으로 확대/축소. 더블클릭(빈 곳): 원래 크기로
    // (앱 안의 웹 엔진은 wheel 과 예전 mousewheel 을 둘 다 보낼 수 있어서, wheel 을 받았으면 mousewheel 은 무시)
    var sawWheel = false;
    function onWheel(event) {
      if (event.type === "wheel") {
        sawWheel = true;
      } else if (sawWheel) {
        return;
      }
      event.preventDefault();
      var delta = event.type === "wheel" ? event.deltaY : -event.wheelDelta;
      if (delta) {
        self.zoomAt(self.pointer(event), delta < 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
      }
    }
    this.canvas.addEventListener("wheel", onWheel);
    this.canvas.addEventListener("mousewheel", onWheel);
    this.canvas.addEventListener("dblclick", function (event) {
      var p = self.pointer(event);
      if (!self.hit(p.x, p.y)) {
        self.resetZoom();
      }
    });
  };

  // ---------------------------------------------------------------- 도트 그림판

  /**
   * 고른 부위를 그림판으로 그린다. mode "part" = 부위 사진(눈은 고른 표정), "key" = 고급 시간 막대의 지금 시간 키.
   * 지금 그림(사진)이 있으면 그걸로, 없으면 지금 모습(몸 색 네모/검은 점)으로 시작한다.
   */
  PartsEditor.prototype.openPainter = function (mode) {
    var self = this;
    if (!this.painter) {
      return;
    }
    var key = this.selected;
    var part = this.part();
    var name;
    var title = "그리기 · " + window.BitView.partName(this.layout, key);
    var pending = { mode: mode, key: key, expression: this.expression };
    if (mode === "key") {
      var photo = window.BitView.clipPhotoAt(this.clip(), key, this.tl.playhead);
      name = photo && photo.name !== window.BitView.BASE_PHOTO ? photo.name : null;
      pending.state = this.tl.state;
      pending.t = this.tl.playhead;
      title += " · " + (this.tl.playhead / 1000).toFixed(2) + "초";
    }
    if (!name) {
      name = key === "eyes" ? (part.images && part.images[mode === "key" ? "neutral" : this.expression]) : part.image;
    }
    if (key === "eyes" && mode === "part") {
      title += " (" + { neutral: "평소", grab: "잡혔을 때", fall: "떨어질 때" }[this.expression] + ")";
    }
    var image = name ? window.BitView.images[name] : null;
    document.getElementById("parts-add-panel").hidden = true;
    this.closePartMenu();
    this.painter.open({
      title: title,
      width: part.w,
      height: part.h,
      gridW: part.resW,
      gridH: part.resH,
      image: image && image.complete && image.naturalWidth > 0 ? image : null,
      base: key === "eyes" ? "none" : (part.shape === "dot" ? "dot" : "rect"),
      bodyColor: this.getColor,
      onSave: function (url, gridW, gridH, pixels) {
        pending.url = url;
        pending.pixels = pixels;
        pending.gridW = gridW;
        pending.gridH = gridH;
        self.pendingDrawing = pending;
        self.send("saveDrawing", url);
      }
    });
  };

  /** (Java 가 부름) 그림을 parts 폴더에 저장했다: 부위 사진 또는 시간 막대 키에 넣는다. 실패면 null. */
  PartsEditor.prototype.onDrawingSaved = function (name) {
    var pending = this.pendingDrawing;
    this.pendingDrawing = null;
    if (!pending || !name) {
      return;
    }
    // Java 에서 다시 받지 않아도 바로 보이게 그린 그림을 넣어 둔다
    var image = new Image();
    image.pixelArt = true;
    image.pixels = pending.pixels; // 그린 칸 색 그대로 (캔버스로 다시 읽으면 흐려진다)
    image.src = pending.url;
    window.BitView.images[name] = image;
    var self = this;
    image.onload = function () { self.draw(); };
    var drawn = this.layout[pending.key];
    if (drawn && pending.gridW > 0) {
      this.setResolution(drawn, pending.gridW, pending.gridH);
    }
    if (pending.mode === "key") {
      this.tl.pendingPhoto = { state: pending.state, part: pending.key, t: pending.t };
      this.onPickedImage(name);
      return;
    }
    var part = this.layout[pending.key];
    if (!part) {
      return;
    }
    if (pending.key === "eyes") {
      part.images = part.images || {};
      part.images[pending.expression] = name;
    } else {
      part.image = name;
      part.flip = false;
    }
    this.afterChange(pending.key);
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  // ---------------------------------------------------------------- 우클릭 메뉴: 그림판 해상도

  /** 부위의 그림판 해상도 (칸 수): 정해 둔 값, 없으면 부위 크기. */
  PartsEditor.prototype.resolutionOf = function (part) {
    return part.resW > 0 && part.resH > 0 ? { w: part.resW, h: part.resH } : { w: part.w, h: part.h };
  };

  /** 해상도를 부위에 기억한다 (부위 크기와 같으면 0: 부위 크기를 바꾸면 따라간다). */
  PartsEditor.prototype.setResolution = function (part, w, h) {
    w = Math.max(1, Math.min(MAX_RESOLUTION, Math.round(w)));
    h = Math.max(1, Math.min(MAX_RESOLUTION, Math.round(h)));
    var same = w === part.w && h === part.h;
    part.resW = same ? 0 : w;
    part.resH = same ? 0 : h;
  };

  PartsEditor.prototype.buildPartMenu = function () {
    var self = this;
    var menu = document.getElementById("part-menu");
    if (!menu) {
      return;
    }
    // 오른쪽 버튼을 뗄 때 연다 (앱 안 웹 엔진은 기본 우클릭 메뉴를 꺼 두면 contextmenu 를 안 보낸다)
    this.canvas.addEventListener("mouseup", function (event) {
      if (event.button !== 2) {
        return;
      }
      var p = self.pointer(event);
      var target = self.hit(p.x, p.y);
      if (target) {
        self.openPartMenu(target.key, event);
      }
    });
    var tabs = document.getElementById("parts-tabs");
    tabs.addEventListener("mouseup", function (event) {
      var button = event.button === 2 && event.target.closest ? event.target.closest("[data-part]") : null;
      if (button) {
        self.openPartMenu(button.getAttribute("data-part"), event);
      }
    });
    [this.canvas, tabs].forEach(function (element) {
      element.addEventListener("contextmenu", function (event) { event.preventDefault(); });
    });
    ["part-menu-w", "part-menu-h"].forEach(function (id) {
      document.getElementById(id).addEventListener("input", function () { self.refreshPartMenuHint(); });
    });
    document.getElementById("part-menu-fit").addEventListener("click", function () {
      var part = self.layout[self.menuKey];
      if (part) {
        document.getElementById("part-menu-w").value = part.w;
        document.getElementById("part-menu-h").value = part.h;
        self.refreshPartMenuHint();
      }
    });
    document.getElementById("part-menu-apply").addEventListener("click", function () { self.applyPartMenu(); });
    document.getElementById("part-menu-draw").addEventListener("click", function () {
      var key = self.menuKey;
      if (self.applyPartMenu() && key) {
        self.openPainter(self.advanced() ? "key" : "part");
      }
    });
    // 만든 부위: 저장 (지금 모습을 바로 저장) / 다른 이름으로 저장 (복사본을 부위 목록에 보관)
    document.getElementById("part-menu-save-now").addEventListener("click", function () {
      var key = self.menuKey;
      self.closePartMenu();
      if (self.layout[key]) {
        self.commit(0);
        self.setStatus("'" + window.BitView.partName(self.layout, key) + "' 부위를 저장했어요");
      }
    });
    document.getElementById("part-menu-save-as").addEventListener("click", function () {
      var key = self.menuKey;
      self.closePartMenu();
      if (!self.layout[key]) {
        return;
      }
      if (window.BitView.customKeys(self.layout).length >= MAX_CUSTOM) {
        self.setStatus("만든 부위는 " + MAX_CUSTOM + "개까지 보관할 수 있어요");
        return;
      }
      self.pendingText = { action: "saveAs", key: key };
      self.send("askText", "다른 이름으로 저장", window.BitView.partName(self.layout, key) + " 2");
    });
    // 메뉴 밖을 누르면 닫는다
    document.addEventListener("mousedown", function (event) {
      if (self.partMenuOpen() && !menu.contains(event.target)) {
        self.closePartMenu();
      }
    }, true);
  };

  PartsEditor.prototype.partMenuOpen = function () {
    var menu = document.getElementById("part-menu");
    return !!menu && !menu.hidden;
  };

  /** 부위를 고르고 마우스 자리에 해상도 메뉴를 띄운다. */
  PartsEditor.prototype.openPartMenu = function (key, event) {
    var part = this.layout[key];
    if (!part) {
      return;
    }
    if (this.selectedKeys().indexOf(key) < 0) {
      this.selectKeys(this.groupMembers(key), key); // 안 고른 부위면 그 부위(그룹째)만
    } else if (key !== this.selected) {
      this.select(key, true); // 여러 개 고른 채로 우클릭: 고른 것은 그대로
    }
    this.menuKey = key;
    this.renderGroupMenu(key);
    var res = this.resolutionOf(part);
    document.getElementById("part-menu-name").textContent = window.BitView.partName(this.layout, key);
    document.getElementById("part-menu-save").hidden = !window.BitView.isCustom(key);
    document.getElementById("part-menu-w").value = res.w;
    document.getElementById("part-menu-h").value = res.h;
    this.refreshPartMenuHint();
    var menu = document.getElementById("part-menu");
    var view = menu.offsetParent || document.body;
    var box = view.getBoundingClientRect();
    menu.hidden = false;
    // 창 밖으로 나가지 않게
    var x = Math.min(event.clientX - box.left, view.clientWidth - menu.offsetWidth - 8);
    var y = Math.min(event.clientY - box.top, view.clientHeight - menu.offsetHeight - 8);
    menu.style.left = Math.max(4, x) + "px";
    menu.style.top = Math.max(4, y) + "px";
    var input = document.getElementById("part-menu-w");
    input.focus();
    input.select();
  };

  PartsEditor.prototype.closePartMenu = function () {
    var menu = document.getElementById("part-menu");
    if (menu) {
      menu.hidden = true;
    }
    this.menuKey = null;
  };

  /** 메뉴 칸의 값 (틀리면 null). */
  PartsEditor.prototype.partMenuValue = function () {
    var w = parseInt(document.getElementById("part-menu-w").value, 10);
    var h = parseInt(document.getElementById("part-menu-h").value, 10);
    if (!(w >= 1 && h >= 1)) {
      return null;
    }
    return { w: Math.min(MAX_RESOLUTION, w), h: Math.min(MAX_RESOLUTION, h) };
  };

  PartsEditor.prototype.refreshPartMenuHint = function () {
    var part = this.layout[this.menuKey];
    if (!part) {
      return;
    }
    var value = this.partMenuValue();
    var text = "부위 " + part.w + "×" + part.h + "px";
    if (!value) {
      text += " · 1~" + MAX_RESOLUTION + " 사이로 써 주세요";
    } else if (value.w !== part.w || value.h !== part.h) {
      var k = value.w / part.w;
      text += " · 1칸 = " + (k >= 1 ? "1/" + (Math.round(k * 10) / 10) : (Math.round(10 / k) / 10)) + "px";
      if (value.w > part.w || value.h > part.h) {
        text += " (부위보다 칸이 많으면 줄여서 보여요. 부위를 키우면 그대로)";
      }
    }
    document.getElementById("part-menu-hint").textContent = text;
    document.getElementById("part-menu-apply").disabled = !value;
    document.getElementById("part-menu-draw").disabled = !value;
  };

  /** 메뉴의 해상도를 부위에 넣는다. 다음에 그림판을 열면 이 칸 수로 그린다. */
  PartsEditor.prototype.applyPartMenu = function () {
    var part = this.layout[this.menuKey];
    var value = this.partMenuValue();
    if (!part || !value) {
      return false;
    }
    var before = this.resolutionOf(part);
    this.setResolution(part, value.w, value.h);
    this.closePartMenu();
    if (before.w !== value.w || before.h !== value.h) {
      this.commit(0);
    }
    return true;
  };

  // ---------------------------------------------------------------- 부위 추가/빼기

  /** 부위 고르기 순서의 화면에 있는 부위들 (기본 부위 → 만든 부위). */
  PartsEditor.prototype.pickerKeys = function () {
    var layout = this.layout;
    return PART_ORDER.concat(window.BitView.customKeys(layout)).filter(function (key) {
      return layout[key] && layout[key].visible !== false;
    });
  };

  /** 눌렀을 때 고르는 순서: 위에 그려진 부위부터 (팔 → 만든 부위 → 눈 → 다리 → 몸통). */
  PartsEditor.prototype.hitOrder = function () {
    var layout = this.layout;
    return ["rightArm", "leftArm"].concat(window.BitView.customKeys(layout).reverse(), ["eyes", "rightLeg", "leftLeg", "body"])
      .filter(function (key) { return layout[key] && layout[key].visible !== false; });
  };

  PartsEditor.prototype.buildPartManager = function () {
    var self = this;
    // 부위 탭이 넘치면 휠로 옆으로 넘긴다 (스크롤 막대는 숨김)
    var tabs = document.getElementById("parts-tabs");
    var onWheel = function (event) {
      var delta = event.type === "wheel" ? (event.deltaY || event.deltaX) : -event.wheelDelta;
      if (delta && tabs.scrollWidth > tabs.clientWidth) {
        event.preventDefault();
        tabs.scrollLeft += delta > 0 ? 40 : -40;
      }
    };
    tabs.addEventListener("wheel", onWheel);
    var panel = document.getElementById("parts-add-panel");
    document.getElementById("parts-add").addEventListener("click", function () {
      panel.hidden = !panel.hidden;
      self.openMenu = null;
      self.renderLibrary();
    });
    document.getElementById("parts-add-close").addEventListener("click", function () {
      panel.hidden = true;
    });
    document.getElementById("parts-remove").addEventListener("click", function () {
      var keys = self.selectedKeys();
      if (!keys.length) {
        return;
      }
      keys.forEach(function (key) {
        self.layout[key].visible = false; // 설정은 남겨 둔다: 가져오기로 다시 넣으면 그대로
      });
      self.selection = [];
      self.selected = self.pickerKeys()[0] || "body";
      self.afterPartsChanged();
    });
    document.getElementById("parts-new").addEventListener("click", function () {
      if (window.BitView.customKeys(self.layout).length >= MAX_CUSTOM) {
        return;
      }
      self.pendingText = { action: "new" };
      self.send("askText", "새 부위 만들기", "부위 " + (window.BitView.customKeys(self.layout).length + 1));
    });
  };

  /** 화면의 부위 탭: 부위가 바뀔 때만 새로 만든다 (누르는 중인 버튼이 사라지지 않게). */
  PartsEditor.prototype.renderPartTabs = function () {
    var self = this;
    var keys = this.pickerKeys();
    if (keys.indexOf(this.selected) < 0 && keys.length) {
      this.selected = keys[0];
    }
    var tabs = document.getElementById("parts-tabs");
    var signature = JSON.stringify(keys.map(function (key) { return [key, window.BitView.partName(self.layout, key)]; }));
    if (tabs.getAttribute("data-parts") !== signature) {
      tabs.setAttribute("data-parts", signature);
      tabs.innerHTML = "";
      keys.forEach(function (key) {
        var button = document.createElement("button");
        button.className = "seg";
        button.textContent = window.BitView.partName(self.layout, key);
        button.setAttribute("data-part", key);
        button.addEventListener("click", function () { self.select(key); });
        tabs.appendChild(button);
      });
    }
    Array.prototype.forEach.call(tabs.querySelectorAll(".seg"), function (button) {
      var selected = button.getAttribute("data-part") === self.selected;
      button.classList.toggle("selected", selected);
      if (selected && button.scrollIntoView && (button.offsetLeft < tabs.scrollLeft
        || button.offsetLeft + button.offsetWidth > tabs.scrollLeft + tabs.clientWidth)) {
        tabs.scrollLeft = button.offsetLeft - 4;
      }
    });
    document.getElementById("parts-remove").disabled = !keys.length;
    var panel = document.getElementById("parts-add-panel");
    if (!panel.hidden) {
      this.renderLibrary();
    }
  };

  /** 가져오기 목록: 기본 부위 + 만든 부위, 화면에 없으면 [가져오기], ⋮ 로 이름 수정·삭제. */
  PartsEditor.prototype.renderLibrary = function () {
    var self = this;
    var list = document.getElementById("parts-library");
    var keys = PART_ORDER.concat(window.BitView.customKeys(this.layout));
    var signature = JSON.stringify([keys.map(function (key) {
      return [key, window.BitView.partName(self.layout, key), self.layout[key].visible !== false];
    }), this.openMenu, this.armedDelete]);
    if (list.getAttribute("data-library") === signature) {
      return;
    }
    list.setAttribute("data-library", signature);
    list.innerHTML = "";
    keys.forEach(function (key) {
      var part = self.layout[key];
      var row = document.createElement("div");
      row.className = "add-row";
      var name = document.createElement("span");
      name.className = "add-name";
      name.textContent = window.BitView.partName(self.layout, key);
      row.appendChild(name);
      if (part.visible !== false) {
        // 화면에 있으면 같은 부위를 하나 더 (눈은 하나만)
        var another = document.createElement("button");
        another.className = "btn btn-small add-another";
        another.textContent = "하나 더";
        another.title = key === "eyes" ? "눈은 하나만 넣을 수 있어요" : "같은 부위를 하나 더 넣기";
        another.disabled = key === "eyes" || window.BitView.customKeys(self.layout).length >= MAX_CUSTOM;
        another.addEventListener("click", function () {
          document.getElementById("parts-add-panel").hidden = true;
          self.addAnother(key);
        });
        row.appendChild(another);
      } else {
        var bring = document.createElement("button");
        bring.className = "btn btn-small btn-primary";
        bring.textContent = "가져오기";
        bring.addEventListener("click", function () {
          part.visible = true;
          self.selected = key;
          document.getElementById("parts-add-panel").hidden = true;
          self.afterPartsChanged();
        });
        row.appendChild(bring);
      }
      var more = document.createElement("button");
      more.className = "btn btn-small add-more";
      more.title = "메뉴";
      more.addEventListener("click", function () {
        self.openMenu = self.openMenu === key ? null : key;
        self.armedDelete = null;
        self.renderLibrary();
      });
      row.appendChild(more);
      if (self.openMenu === key) {
        var menu = document.createElement("div");
        menu.className = "add-menu";
        var rename = document.createElement("button");
        rename.className = "btn btn-small";
        rename.textContent = "이름 수정";
        rename.addEventListener("click", function () {
          self.pendingText = { action: "rename", key: key };
          self.openMenu = null;
          self.send("askText", "부위 이름 수정", window.BitView.partName(self.layout, key));
          self.renderLibrary();
        });
        menu.appendChild(rename);
        var remove = document.createElement("button");
        remove.className = "btn btn-small btn-danger";
        var custom = window.BitView.isCustom(key);
        remove.textContent = self.armedDelete === key ? "정말 삭제?" : "삭제";
        remove.disabled = !custom;
        remove.title = custom ? "" : "기본 부위는 못 지워요";
        remove.addEventListener("click", function () {
          if (self.armedDelete !== key) {
            self.armedDelete = key;
            self.renderLibrary();
            return;
          }
          self.deletePart(key);
        });
        menu.appendChild(remove);
        row.appendChild(menu);
      }
      list.appendChild(row);
    });
    document.getElementById("parts-new").disabled = window.BitView.customKeys(this.layout).length >= MAX_CUSTOM;
  };

  /** 만든 부위를 완전히 지운다 (시간 막대 키까지). */
  PartsEditor.prototype.deletePart = function (key) {
    delete this.layout[key];
    this.removeFromGroups(key);
    var timeline = this.layout.timeline;
    window.BitView.STATES.forEach(function (state) {
      if (timeline && timeline[state]) {
        delete timeline[state].tracks[key];
      }
    });
    this.openMenu = null;
    this.armedDelete = null;
    if (this.selected === key) {
      this.selected = this.pickerKeys()[0] || "body";
    }
    this.afterPartsChanged();
  };

  /** (Java 가 부름) 이름 입력 창의 대답: 새 부위 만들기 / 이름 수정. 취소면 null. */
  PartsEditor.prototype.onTextAnswer = function (text) {
    var pending = this.pendingText;
    this.pendingText = null;
    var name = text == null ? "" : String(text).replace(/\s+/g, " ").trim().slice(0, 20);
    if (!pending || !name) {
      return;
    }
    if (pending.action === "new") {
      var key;
      do {
        key = "c_" + Math.random().toString(36).slice(2, 10).replace(/[^a-z0-9]/g, "a");
      } while (this.layout[key]);
      var body = this.layout.body;
      this.layout[key] = window.BitView.normalizePart(key, {
        x: Math.round(body.x + body.w / 2 - CUSTOM_SIZE / 2), y: Math.round(body.y + body.h / 2 - CUSTOM_SIZE / 2),
        w: CUSTOM_SIZE, h: CUSTOM_SIZE,
        shape: "dot", name: name, image: null, flip: false
      });
      this.selected = key;
      document.getElementById("parts-add-panel").hidden = true;
    } else if (pending.action === "rename" && this.layout[pending.key]) {
      this.layout[pending.key].name = name;
    } else if (pending.action === "saveAs" && this.layout[pending.key]) {
      this.duplicatePart(pending.key, name);
      return;
    }
    this.afterPartsChanged();
  };

  /**
   * 다른 이름으로 저장: 부위를 통째로(그림·크기·움직임·시간 막대 키까지) 새 이름으로 복사해서 부위 목록에 보관한다.
   * 복사본은 화면에는 넣지 않는다 (가져오기에서 꺼낸다).
   */
  PartsEditor.prototype.duplicatePart = function (key, name) {
    if (window.BitView.customKeys(this.layout).length >= MAX_CUSTOM) {
      this.setStatus("만든 부위는 " + MAX_CUSTOM + "개까지 보관할 수 있어요");
      return null;
    }
    var copyKey;
    do {
      copyKey = "c_" + Math.random().toString(36).slice(2, 10).replace(/[^a-z0-9]/g, "a");
    } while (this.layout[copyKey]);
    var copy = JSON.parse(JSON.stringify(this.layout[key]));
    copy.name = name;
    copy.visible = false;
    this.layout[copyKey] = window.BitView.normalizePart(copyKey, copy);
    var timeline = this.layout.timeline;
    window.BitView.STATES.forEach(function (state) {
      var clip = timeline && timeline[state];
      if (clip && clip.tracks[key] && clip.tracks[key].length) {
        clip.tracks[copyKey] = JSON.parse(JSON.stringify(clip.tracks[key]));
      }
    });
    this.afterPartsChanged();
    this.setStatus("'" + name + "' 이름으로 부위 목록에 저장했어요 (＋ 부위 → 가져오기)");
    return copyKey;
  };

  /** 모습 꾸미기 아래 알림 줄. */
  PartsEditor.prototype.setStatus = function (text) {
    var status = document.getElementById("parts-status");
    if (status) {
      status.textContent = text;
    }
  };

  /** 부위를 넣고 빼고 지운 뒤: 화면을 다시 그리고 저장. */
  PartsEditor.prototype.afterPartsChanged = function () {
    this.view = this.zoomed ? this.view : null;
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  // ---------------------------------------------------------------- 확대

  PartsEditor.prototype.zoomAt = function (point, factor) {
    if (!this.view || this.drag) {
      return;
    }
    var v = this.view;
    var k = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, v.k * factor));
    if (k === v.k) {
      return;
    }
    // 마우스 아래 점이 그대로 있도록
    v.ox = point.x - (point.x - v.ox) * (k / v.k);
    v.oy = point.y - (point.y - v.oy) * (k / v.k);
    v.k = k;
    this.zoomed = true;
    this.draw();
  };

  PartsEditor.prototype.resetZoom = function () {
    this.zoomed = false;
    this.view = null;
    this.draw();
  };

  // ---------------------------------------------------------------- 되돌리기

  /** Java 가 보낸(또는 내가 보낸) 저장된 배치를 기록한다. 바뀌었으면 이전 배치를 되돌리기 목록에 넣는다. */
  PartsEditor.prototype.remember = function (layout) {
    var key = canonical(layout);
    if (this.committedKey !== null && key !== this.committedKey) {
      this.undoStack.push(this.committed);
      if (this.undoStack.length > HISTORY_LIMIT) {
        this.undoStack.shift();
      }
      this.redoStack = [];
    }
    this.committed = window.BitView.copyLayout(layout);
    this.committedKey = key;
  };

  /** 아직 안 보낸 변경(숫자 입력 중 등)이 있으면 지금 보낸다. */
  PartsEditor.prototype.flush = function () {
    if (this.commitTimer) {
      clearTimeout(this.commitTimer);
      this.commitTimer = null;
      this.sendLayout();
    }
  };

  PartsEditor.prototype.undo = function () {
    this.flush();
    if (!this.undoStack.length || this.drag) {
      return;
    }
    this.redoStack.push(this.committed);
    this.restore(this.undoStack.pop());
  };

  PartsEditor.prototype.redo = function () {
    this.flush();
    if (!this.redoStack.length || this.drag) {
      return;
    }
    this.undoStack.push(this.committed);
    this.restore(this.redoStack.pop());
  };

  PartsEditor.prototype.restore = function (layout) {
    this.layout = window.BitView.copyLayout(layout);
    this.committed = window.BitView.copyLayout(layout);
    this.committedKey = canonical(this.committed);
    this.refreshControls();
    this.draw();
    this.send("setParts", JSON.stringify(this.layout));
  };

  // ---------------------------------------------------------------- 움직임

  PartsEditor.prototype.buildMotion = function () {
    var self = this;
    Array.prototype.forEach.call(document.querySelectorAll("#parts-level .seg"), function (button) {
      button.addEventListener("click", function () {
        self.flush();
        self.level = button.getAttribute("data-level");
        self.refreshControls();
        self.draw();
        self.updateAnimation();
      });
    });
    Array.prototype.forEach.call(document.querySelectorAll("#parts-mode .seg"), function (button) {
      button.addEventListener("click", function () {
        self.mode = button.getAttribute("data-mode");
        self.refreshControls();
        self.draw();
        self.updateAnimation();
      });
    });
    function segments(containerId, keys, labels, onPick) {
      var container = document.getElementById(containerId);
      keys.forEach(function (key) {
        var button = document.createElement("button");
        button.className = "seg";
        button.textContent = labels[key];
        button.setAttribute("data-key", key);
        button.addEventListener("click", function () { onPick(key); });
        container.appendChild(button);
      });
    }
    segments("anim-states", window.BitView.STATES, STATE_LABELS, function (state) {
      self.animState = state;
      self.refreshControls();
      self.draw();
    });
    segments("motion-types", Object.keys(MOTION_LABELS), MOTION_LABELS, function (type) { self.changeMotion({ type: type }); });
    ["size", "speed"].forEach(function (field) {
      var input = document.getElementById("motion-" + field);
      input.addEventListener("input", function () {
        var change = {};
        change[field] = parseInt(input.value, 10);
        self.changeMotion(change);
      });
    });
    document.getElementById("motion-alternate").addEventListener("change", function (event) {
      self.changeMotion({ alternate: event.target.checked });
    });

    ["dx", "dy", "rotation"].forEach(function (field) {
      var input = document.getElementById("anim-" + field);
      input.addEventListener("input", function () {
        var value = parseInt(input.value, 10);
        if (isNaN(value) || self.animState === "always") {
          return;
        }
        self.anim()[field] = field === "rotation" ? normalizeRotation(value) : clamp(value, MAX_POSE_OFFSET);
        self.afterChange(self.selected);
        self.draw();
        self.commit(300);
      });
      input.addEventListener("change", function () { self.refreshControls(); });
    });
    var pivot = document.getElementById("anim-pivot");
    PIVOTS.forEach(function (item) {
      var option = document.createElement("option");
      option.value = item.key;
      option.textContent = item.label;
      pivot.appendChild(option);
    });
    pivot.addEventListener("change", function () {
      self.part().pivot = pivot.value;
      self.afterChange(self.selected);
      self.draw();
      self.commit(0);
    });
  };

  /** 지금 고른 부위의 지금 고른 상황 애니메이션. */
  PartsEditor.prototype.anim = function () {
    return this.part().animations[this.animState];
  };

  /** 고급(시간 막대) 탭인지. */
  PartsEditor.prototype.advanced = function () {
    return this.level === "advanced";
  };

  /** 일반 → 모양·사진 화면인지. */
  PartsEditor.prototype.shaping = function () {
    return !this.advanced() && this.mode === "shape";
  };

  /** 자세를 잡는 중인지 (일반 → 움직임 화면에서 "항상"이 아닌 상황을 골랐을 때). */
  PartsEditor.prototype.posing = function () {
    return !this.advanced() && this.mode === "motion" && this.animState !== "always";
  };

  /** 미리보기에서 부위를 끌거나 돌릴 수 있는지. */
  PartsEditor.prototype.editable = function () {
    return this.shaping() || this.posing() || this.advanced();
  };

  PartsEditor.prototype.changeMotion = function (change) {
    var motion = this.anim().motion;
    Object.keys(change).forEach(function (key) {
      motion[key] = change[key];
    });
    this.afterChange(this.selected);
    this.refreshControls();
    this.draw();
    this.commit(250);
  };

  /** 숨길 칸은 자리를 남긴 채 안 보이게 (어떤 탭·상황이어도 창 크기가 같다). */
  function setOff(id, off) {
    document.getElementById(id).classList.toggle("off", off);
  }

  /** 움직임 칸 상태를 고른 부위·상황에 맞춘다. */
  PartsEditor.prototype.refreshMotionControls = function () {
    var self = this;
    var shape = this.mode === "shape";
    var advanced = this.advanced();
    setOff("parts-basic", advanced);
    setOff("parts-advanced", !advanced);
    Array.prototype.forEach.call(document.querySelectorAll("#parts-level .seg"), function (button) {
      button.classList.toggle("selected", button.getAttribute("data-level") === self.level);
    });
    setOff("parts-shape", !shape);
    setOff("parts-motion", shape);
    Array.prototype.forEach.call(document.querySelectorAll("#parts-mode .seg"), function (button) {
      button.classList.toggle("selected", button.getAttribute("data-mode") === self.mode);
    });
    Array.prototype.forEach.call(document.querySelectorAll("#anim-states .seg"), function (button) {
      button.classList.toggle("selected", button.getAttribute("data-key") === self.animState);
    });

    var part = this.part();
    var a = this.anim();
    var m = a.motion;
    // 이 부위에 넣을 수 있는 움직임만 보여준다 (깜빡은 눈만, 숨쉬기는 몸통만)
    var allowed = window.BitView.motionTypesFor(this.selected);
    var labels = MOTION_LABELS_FOR[this.selected] || {};
    var types = document.getElementById("motion-types");
    Array.prototype.forEach.call(types.querySelectorAll(".seg"), function (button) {
      var key = button.getAttribute("data-key");
      button.hidden = allowed.indexOf(key) < 0;
      window.BitView.setText(button, labels[key] || MOTION_LABELS[key]);
      button.classList.toggle("selected", key === m.type);
    });
    types.style.gridTemplateColumns = "repeat(" + allowed.length + ", 1fr)";

    var always = this.animState === "always";
    ["dx", "dy", "rotation"].forEach(function (field) {
      var input = document.getElementById("anim-" + field);
      input.disabled = always;
      if (document.activeElement !== input) {
        input.value = always ? "" : (a[field] || 0);
      }
    });
    document.getElementById("anim-pivot").value = part.pivot;

    var active = m.type !== "none";
    setOff("motion-settings", !active);
    ["size", "speed"].forEach(function (field) {
      var input = document.getElementById("motion-" + field);
      if (document.activeElement !== input) {
        input.value = m[field];
      }
      window.BitView.setText(document.getElementById("motion-" + field + "-value"), String(m[field]));
    });
    var limb = !!MIRROR[this.selected];
    setOff("motion-alternate-row", !(limb && this.layout.symmetric));
    document.getElementById("motion-alternate").checked = !!m.alternate;

    var subtitle = document.getElementById("parts-subtitle");
    var hint;
    if (advanced) {
      hint = "시간을 고르고 부위를 끌거나 돌리면 ◆ 키가 생겨요";
    } else if (shape) {
      hint = "끌어서 옮기기 · 빈 곳 끌기: 여러 개 고르기 · Shift+끌기: 화면 옮기기";
    } else if (always) {
      hint = "언제나 하는 움직임 · 자세는 다른 상황에서 잡아요";
    } else {
      hint = "'" + STATE_LABELS[this.animState] + "' 자세: 끌거나 돌려서 잡아요";
    }
    window.BitView.setText(subtitle, hint);
  };

  /** 화면에 보일 때 움직임 화면이면 미리보기를 움직인다. */
  PartsEditor.prototype.setVisible = function (visible) {
    this.visible = visible;
    if (!visible) {
      this.closePartMenu();
    }
    this.updateAnimation();
  };

  PartsEditor.prototype.updateAnimation = function () {
    var self = this;
    // 일반 → 움직임: 늘 움직인다. 고급: 재생 중에만
    var should = this.visible && (this.advanced() ? !!(this.tl && this.tl.playing) : this.mode === "motion");
    if (!should || this.animating) {
      if (!should) {
        this.animating = false;
      }
      return;
    }
    this.animating = true;
    var last = null;
    var acc = 0;
    function frame(time) {
      if (!self.animating) {
        self.draw();
        return;
      }
      if (last === null) {
        last = time;
      }
      var dt = Math.min(time - last, TICK_MS * 4);
      acc = Math.min(acc + (time - last), TICK_MS * 4);
      last = time;
      if (self.advanced()) {
        self.timelineAdvance(dt);
        self.draw();
        window.requestAnimationFrame(frame);
        return;
      }
      while (acc >= TICK_MS) {
        // 고른 상황의 자세와 움직임을 보여준다 (상황을 바꾸면 비트처럼 서서히 바뀐다)
        self.clock.tick(self.animState, self.layout.timeline);
        acc -= TICK_MS;
      }
      self.draw();
      window.requestAnimationFrame(frame);
    }
    window.requestAnimationFrame(frame);
  };

  // ---------------------------------------------------------------- 상태

  /** Java 가 보낸 배치로 바꾼다 (끄는 중에는 무시). */
  PartsEditor.prototype.setLayout = function (layout) {
    if (this.drag) {
      return;
    }
    var before = this.layout && this.layout[this.selected];
    var image = before ? before.image : undefined;
    this.layout = window.BitView.copyLayout(layout);
    var after = this.layout[this.selected];
    // 사진 넣기/빼기는 Java 가 바꿔서 보내 준다: 대칭 그룹이면 짝 부위에도 맞춰서 다시 저장
    var group = after && image !== undefined && after.image !== image && this.groupOf ? this.groupOf(this.selected) : null;
    if (group && (group.mirrorX || group.mirrorY)) {
      this.mirrorGroupFrom(this.selected);
      this.commit(0);
    }
    this.remember(this.layout);
    this.refreshControls();
    this.draw();
  };

  /** 부위를 고른다. keepSelection 이 아니면 그 부위(그룹이면 그룹째)만 고른 상태로. */
  PartsEditor.prototype.select = function (key, keepSelection) {
    this.selected = key;
    if (!keepSelection) {
      this.selection = this.groupMembers ? this.groupMembers(key) : [key];
    }
    if (key !== "eyes") {
      this.expression = "neutral";
    }
    this.refreshControls();
    this.draw();
  };

  PartsEditor.prototype.part = function () {
    return this.layout[this.selected];
  };

  PartsEditor.prototype.maxCustom = function () {
    return MAX_CUSTOM;
  };

  PartsEditor.prototype.hasImage = function () {
    var part = this.part();
    return this.selected === "eyes" ? !!(part.images && part.images[this.expression]) : !!part.image;
  };

  PartsEditor.prototype.refreshControls = function () {
    // 고른 부위가 지워졌거나(되돌리기 등) 화면에서 빠졌으면 화면에 있는 첫 부위로
    if (!this.layout[this.selected] || this.layout[this.selected].visible === false) {
      this.selected = this.pickerKeys()[0] || "body";
    }
    this.renderPartTabs();
    var part = this.part();
    var eyeTabs = document.getElementById("parts-eye-tabs");
    // 표정 사진 줄은 눈을 골랐을 때만 (자리는 늘 있다)
    setOff("parts-eye-tabs", this.selected !== "eyes");
    this.refreshMotionControls();
    if (this.advanced() && this.refreshTimelineControls) {
      this.refreshTimelineControls();
    }
    var exprButtons = eyeTabs.querySelectorAll(".seg");
    for (var j = 0; j < exprButtons.length; j++) {
      exprButtons[j].classList.toggle("selected", exprButtons[j].getAttribute("data-expr") === this.expression);
    }
    FIELDS.forEach(function (field) {
      var input = document.getElementById("parts-" + field);
      if (document.activeElement !== input) {
        input.value = part[field] || 0;
      }
    });
    document.getElementById("parts-symmetric").checked = !!this.layout.symmetric;
    // 버튼은 짧게 (어느 부위인지는 탭에 보인다)
    var photo = document.getElementById("parts-photo");
    window.BitView.setText(photo.querySelector(".label"), this.hasImage() ? "사진 바꾸기" : "사진 넣기");
    document.getElementById("parts-photo-remove").disabled = !this.hasImage();
  };

  // ---------------------------------------------------------------- 좌우 맞추기

  /** 왼쪽(또는 고친 쪽)을 몸통 가운데 기준으로 반대쪽에 거울처럼 복사 (PartLayout.mirrorFrom). */
  PartsEditor.prototype.mirrorFrom = function (sourceKey) {
    var targetKey = MIRROR[sourceKey];
    if (!targetKey) {
      return;
    }
    var s = this.layout[sourceKey], t = this.layout[targetKey], b = this.layout.body;
    t.w = s.w;
    t.h = s.h;
    t.y = s.y;
    t.x = (b.x * 2 + b.w) - (s.x + s.w);
    t.image = s.image;
    t.flip = !!s.image && !s.flip;
    t.rotation = normalizeRotation(-(s.rotation || 0));
    t.resW = s.resW || 0;
    t.resH = s.resH || 0;
    t.pivot = window.BitView.mirroredPivot(s.pivot);
    t.animations = {};
    window.BitView.STATES.forEach(function (state) {
      t.animations[state] = window.BitView.mirroredAnimation(s.animations[state]);
    });
    // 고급 시간 막대 줄도 거울처럼 (PartTimeline.mirrorTrack)
    var timeline = this.layout.timeline;
    window.BitView.STATES.forEach(function (state) {
      var clip = timeline && timeline[state];
      if (!clip) {
        return;
      }
      var keys = clip.tracks[sourceKey];
      if (keys && keys.length) {
        clip.tracks[targetKey] = keys.map(function (k) {
          return {
            t: k.t, dx: -k.dx, dy: k.dy, rotation: -k.rotation, scaleX: k.scaleX, scaleY: k.scaleY, ease: k.ease,
            photo: k.photo || null, flip: !!k.photo && k.photo !== window.BitView.BASE_PHOTO && !k.flip
          };
        });
      } else {
        delete clip.tracks[targetKey];
      }
    });
  };

  /** key 를 바꾼 뒤 좌우 맞추기 반영. 팔/다리만 반대쪽이 따라오고, 몸통을 바꿔도 다른 부위는 그대로다. */
  PartsEditor.prototype.afterChange = function (key) {
    if (this.layout.symmetric && MIRROR[key]) {
      this.mirrorFrom(key);
    }
    if (this.mirrorGroupFrom) {
      this.mirrorGroupFrom(key); // 그룹 좌우·상하 대칭
    }
  };

  // ---------------------------------------------------------------- 숫자 입력

  PartsEditor.prototype.onNumber = function (field, text) {
    var value = parseInt(text, 10);
    if (isNaN(value)) {
      return;
    }
    var part = this.part();
    if (field === "rotation") {
      part.rotation = normalizeRotation(value);
    } else if (field === "w" || field === "h") {
      part[field] = Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    } else {
      part[field] = clamp(value, MAX_OFFSET);
    }
    this.afterChange(this.selected);
    this.draw();
    this.commit(300);
  };

  PartsEditor.prototype.commit = function (delay) {
    var self = this;
    if (this.commitTimer) {
      clearTimeout(this.commitTimer);
    }
    this.commitTimer = setTimeout(function () {
      self.commitTimer = null;
      self.sendLayout();
    }, delay);
  };

  PartsEditor.prototype.sendLayout = function () {
    this.remember(this.layout);
    this.send("setParts", JSON.stringify(this.layout));
  };

  // ---------------------------------------------------------------- 그리기

  /** 비트 전체(움직임 범위까지)가 캔버스에 들어오도록 배율과 위치를 정한다. */
  PartsEditor.prototype.fit = function (w, h) {
    var b = window.BitView.motionBounds(this.layout, false);
    var bw = Math.max(20, b.maxX - b.minX);
    var bh = Math.max(20, b.maxY - b.minY);
    // 위쪽은 회전 손잡이 자리만큼 더 비워 둔다
    var k = Math.max(1, Math.min(4, (w - 40) / bw, (h - 40 - ROTATE_GAP) / bh));
    return {
      k: k,
      ox: w / 2 - ((b.minX + b.maxX) / 2) * k,
      oy: (h + ROTATE_GAP) / 2 - ((b.minY + b.maxY) / 2) * k,
      w: w,
      h: h
    };
  };

  /** 지금 배율·위치로 비트 전체가 캔버스 안에 보이는지. */
  PartsEditor.prototype.fits = function () {
    var v = this.view;
    var b = window.BitView.motionBounds(this.layout, false);
    var margin = 6;
    return v.ox + b.minX * v.k >= margin && v.ox + b.maxX * v.k <= v.w - margin
      && v.oy + b.minY * v.k >= margin + ROTATE_GAP && v.oy + b.maxY * v.k <= v.h - margin;
  };

  /** 미리보기 자세: 움직임 화면이면 고른 상황의 표정(공중 = 떨어질 때 눈 + 팔 들기), 아니면 눈 표정 탭. */
  PartsEditor.prototype.previewPose = function () {
    if (this.advanced()) {
      return this.timelinePose();
    }
    var pose = window.BitView.newPose();
    if (this.mode === "motion") {
      pose.expression = this.animState === "air" ? "fall" : (this.animState === "grab" ? "grab" : "neutral");
      pose.motion = this.clock;
    } else {
      pose.expression = this.selected === "eyes" ? this.expression : "neutral";
    }
    if (pose.expression === "fall") {
      pose.armLift = -8;
    }
    return pose;
  };

  PartsEditor.prototype.draw = function () {
    if (!this.canvas || this.canvas.clientWidth === 0) {
      return;
    }
    var c = window.BitView.prepare(this.canvas);
    // 화면을 다시 맞추는 건 처음, 창 크기가 바뀌었을 때, 비트가 캔버스 밖으로 나갔을 때만
    // (부위 하나를 옮겼는데 나머지가 같이 움직여 보이지 않게). 끄는 중에는 그대로 둔다.
    if (!this.view || this.view.w !== c.w || this.view.h !== c.h || (!this.drag && !this.zoomed && !this.fits())) {
      this.view = this.fit(c.w, c.h);
      this.zoomed = false;
    }
    var v = this.view;
    var ctx = c.ctx;
    var d = c.dpr;

    // 바탕 격자 (4px 마다)
    ctx.fillStyle = "#f3eee4";
    var step = 4 * v.k;
    if (step >= 6) {
      for (var gx = (v.ox % step); gx < c.w; gx += step) {
        for (var gy = (v.oy % step); gy < c.h; gy += step) {
          ctx.fillRect(Math.round(gx * d), Math.round(gy * d), Math.max(1, Math.round(d)), Math.max(1, Math.round(d)));
        }
      }
    }

    var pose = this.previewPose();
    window.BitView.drawBitAt(ctx, v.ox * d, v.oy * d, v.k * d, this.getColor(), pose, this.layout);
    if (this.advanced()) {
      this.drawTimeline();
    }

    // 함께 고른 부위(파란 점선)와 고르기 네모
    if (this.drawSelection) {
      this.drawSelection(ctx, d, pose);
    }
    // 고른 부위 표시: 점선 테두리 + 크기 조절 손잡이(모양 화면만) + 회전 손잡이, 부위와 같이 돌린다.
    // 움직임 "항상"에서는 손잡이 없이 테두리만 (자세를 잡지 않으니까)
    var r = this.partRect(this.selected, pose);
    ctx.save();
    ctx.translate(r.cx * d, r.cy * d);
    ctx.rotate(r.angle);
    ctx.translate(-r.cx * d, -r.cy * d);
    ctx.strokeStyle = "#26221e";
    ctx.lineWidth = Math.max(1, Math.round(d));
    ctx.setLineDash([4 * d, 3 * d]);
    ctx.strokeRect(Math.round((r.x - 2) * d) + 0.5, Math.round((r.y - 2) * d) + 0.5, Math.round((r.w + 4) * d), Math.round((r.h + 4) * d));
    ctx.setLineDash([]);
    ctx.fillStyle = "#ffffff";
    if (this.shaping() || this.advanced()) {
      var hr = this.handleRect(r);
      ctx.fillRect(Math.round(hr.x * d), Math.round(hr.y * d), Math.round(hr.w * d), Math.round(hr.h * d));
      ctx.strokeRect(Math.round(hr.x * d) + 0.5, Math.round(hr.y * d) + 0.5, Math.round(hr.w * d), Math.round(hr.h * d));
    }
    if (this.editable()) {
      var knob = this.rotateKnob(r);
      ctx.beginPath();
      ctx.moveTo(knob.x * d, (r.y - 2) * d);
      ctx.lineTo(knob.x * d, (knob.y + ROTATE_RADIUS) * d);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(knob.x * d, knob.y * d, ROTATE_RADIUS * d, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    }
    ctx.restore();
    // 자세를 잡을 때는 중심점(돌리는 기준)을 표시한다
    if (this.posing() || this.advanced()) {
      ctx.save();
      ctx.fillStyle = "#c8402f";
      ctx.beginPath();
      ctx.arc(r.px * d, r.py * d, 3 * d, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }
  };

  /**
   * 부위의 캔버스 위 사각형 (화면 px, 돌리기 전). cx, cy = 가운데, angle = 회전(라디안), px, py = 중심점.
   * 자세를 잡는 중이면 그 상황의 자세, 고급이면 지금 시간의 모양(옮기기 + 중심점 기준 돌리기·늘이기)을 넣은 자리.
   */
  PartsEditor.prototype.partRect = function (key, pose) {
    var v = this.view;
    var part = this.layout[key];
    var lift = (key === "leftArm" || key === "rightArm") && pose && pose.armLift ? pose.armLift : 0;
    var r = { x: v.ox + part.x * v.k, y: v.oy + (part.y + lift) * v.k, w: part.w * v.k, h: part.h * v.k };
    r.cx = r.x + r.w / 2;
    r.cy = r.y + r.h / 2;
    r.angle = (part.rotation || 0) * Math.PI / 180;
    var pivot = window.BitView.pivotPoint(part);
    r.px = v.ox + pivot.x * v.k;
    r.py = v.oy + (pivot.y + lift) * v.k;
    var t = null;
    if (this.posing()) {
      var a = part.animations[this.animState];
      t = { dx: a.dx || 0, dy: a.dy || 0, rotation: a.rotation || 0, scaleX: 1, scaleY: 1 };
    } else if (this.advanced()) {
      t = this.timelineTransformAt(key);
    }
    if (t) {
      var turn = t.rotation * Math.PI / 180;
      // 중심점 기준으로 늘이고 돌린 다음 옮긴다 (bit.js applyPartTransform 과 같은 순서)
      var scaled = { x: r.px + (r.cx - r.px) * t.scaleX, y: r.py + (r.cy - r.py) * t.scaleY };
      var center = rotatePoint(scaled.x, scaled.y, r.px, r.py, turn);
      var dx = t.dx * v.k, dy = t.dy * v.k;
      r.w *= t.scaleX;
      r.h *= t.scaleY;
      r.cx = center.x + dx;
      r.cy = center.y + dy;
      r.x = r.cx - r.w / 2;
      r.y = r.cy - r.h / 2;
      r.px += dx;
      r.py += dy;
      r.angle += turn;
    }
    return r;
  };

  /** 회전 손잡이 가운데 (돌리기 전 좌표). */
  PartsEditor.prototype.rotateKnob = function (r) {
    return { x: r.cx, y: r.y - 2 - ROTATE_GAP };
  };

  /** 화면 점을 부위의 돌리기 전 좌표로 바꾼다 (돌린 부위도 네모 안인지 쉽게 보려고). */
  function toLocal(r, x, y) {
    return rotatePoint(x, y, r.cx, r.cy, -r.angle);
  }

  PartsEditor.prototype.handleRect = function (r) {
    return { x: r.x + r.w + 2 - HANDLE / 2, y: r.y + r.h + 2 - HANDLE / 2, w: HANDLE, h: HANDLE };
  };

  // ---------------------------------------------------------------- 마우스

  function pointIn(r, x, y) {
    return x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h;
  }

  PartsEditor.prototype.pointer = function (event) {
    var rect = this.canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  };

  PartsEditor.prototype.hit = function (x, y) {
    if (!this.view) {
      return null;
    }
    var pose = this.previewPose();
    var selected = this.partRect(this.selected, pose);
    var q = toLocal(selected, x, y);
    if (this.editable()) {
      var knob = this.rotateKnob(selected);
      var grab = ROTATE_RADIUS + 3;
      if (pointIn({ x: knob.x - grab, y: knob.y - grab, w: grab * 2, h: grab * 2 }, q.x, q.y)) {
        return { key: this.selected, rotate: true };
      }
    }
    if ((this.shaping() || this.advanced()) && pointIn(this.handleRect(selected), q.x, q.y)) {
      return { key: this.selected, resize: true };
    }
    // 겹쳐 있으면 이미 고른 부위를 먼저 (여러 개 고른 채로 끌 수 있게)
    var picked = this.selectedKeys ? this.selectedKeys() : [this.selected];
    var all = this.hitOrder();
    var order = all.filter(function (k) { return picked.indexOf(k) >= 0; })
      .concat(all.filter(function (k) { return picked.indexOf(k) < 0; }));
    for (var i = 0; i < order.length; i++) {
      var r = this.partRect(order[i], pose);
      var local = toLocal(r, x, y);
      // 아주 작은 부위도 잡을 수 있게 조금 넓게 본다
      if (pointIn({ x: r.x - 3, y: r.y - 3, w: r.w + 6, h: r.h + 6 }, local.x, local.y)) {
        return { key: order[i] };
      }
    }
    return null;
  };

  PartsEditor.prototype.onHover = function (event) {
    if (this.drag) {
      return;
    }
    var p = this.pointer(event);
    var target = this.hit(p.x, p.y);
    var movable = this.editable();
    this.canvas.style.cursor = !target ? "crosshair"
      : (target.rotate ? "grab" : (target.resize ? "nwse-resize" : (movable ? "move" : "pointer")));
  };

  PartsEditor.prototype.onMouseDown = function (event) {
    if (event.button === 2) {
      return; // 오른쪽 버튼은 우클릭 메뉴 (contextmenu)
    }
    var p = this.pointer(event);
    var target = this.hit(p.x, p.y);
    // 빈 곳 끌기(고르기 네모), Shift(더하기·화면 옮기기), 여러 개 옮기기는 parts-select.js
    if (this.selectMouseDown(event, p, target)) {
      return;
    }
    if (!target) {
      return;
    }
    event.preventDefault();
    if (target.key !== this.selected) {
      this.select(target.key);
    }
    if (this.advanced()) {
      this.timelineStartDrag(target, p);
      return;
    }
    if (!this.editable()) {
      return; // 움직임 "항상": 누르면 고르기만
    }
    var part = this.part();
    var r = this.partRect(target.key, this.previewPose());
    var posing = this.posing();
    var a = posing ? this.anim() : null;
    // 돌릴 때 기준: 모양 화면은 부위 가운데, 자세 잡기는 중심점
    var cx = posing ? r.px : r.cx, cy = posing ? r.py : r.cy;
    this.drag = {
      key: target.key, resize: !!target.resize, rotate: !!target.rotate, posing: posing, startX: p.x, startY: p.y,
      x: posing ? a.dx : part.x, y: posing ? a.dy : part.y, w: part.w, h: part.h,
      rotation: posing ? a.rotation : (part.rotation || 0),
      cx: cx, cy: cy, startAngle: Math.atan2(p.y - cy, p.x - cx), moved: false
    };
  };

  PartsEditor.prototype.onMouseMove = function (event) {
    if (!this.drag) {
      return;
    }
    var p = this.pointer(event);
    var drag = this.drag;
    if (this.selectMouseMove(p)) {
      return;
    }
    if (drag.pan) {
      this.view.ox = drag.ox + (p.x - drag.startX);
      this.view.oy = drag.oy + (p.y - drag.startY);
      this.zoomed = true;
      this.draw();
      return;
    }
    if (drag.timeline) {
      this.timelineDragMove(p);
      return;
    }
    var k = this.view.k;
    var part = this.layout[drag.key];
    var target = drag.posing ? part.animations[this.animState] : part;
    var xKey = drag.posing ? "dx" : "x", yKey = drag.posing ? "dy" : "y";
    var limit = drag.posing ? MAX_POSE_OFFSET : MAX_OFFSET;
    var before = JSON.stringify([target[xKey], target[yKey], part.w, part.h, target.rotation]);
    if (drag.rotate) {
      // 기준점에서 마우스까지의 각도가 바뀐 만큼 돌린다
      var turned = (Math.atan2(p.y - drag.cy, p.x - drag.cx) - drag.startAngle) * 180 / Math.PI;
      var angle = Math.round(drag.rotation + turned);
      var snapped = Math.round(angle / ROTATE_SNAP) * ROTATE_SNAP;
      target.rotation = normalizeRotation(Math.abs(angle - snapped) <= ROTATE_SNAP_RANGE ? snapped : angle);
    } else if (drag.resize) {
      // 마우스 움직임을 부위의 (돌린) 가로/세로 방향으로 나눠서 크기를 바꾸고, 돌린 왼쪽 위 모서리는 제자리에 둔다
      var a = drag.rotation * Math.PI / 180;
      var sx = p.x - drag.startX, sy = p.y - drag.startY;
      var w = Math.max(MIN_SIZE, Math.min(MAX_SIZE, drag.w + Math.round((sx * Math.cos(a) + sy * Math.sin(a)) / k)));
      var h = Math.max(MIN_SIZE, Math.min(MAX_SIZE, drag.h + Math.round((-sx * Math.sin(a) + sy * Math.cos(a)) / k)));
      var corner = rotatePoint(drag.x, drag.y, drag.x + drag.w / 2, drag.y + drag.h / 2, a);
      var center = rotatePoint(corner.x + w / 2, corner.y + h / 2, corner.x, corner.y, a);
      part.w = w;
      part.h = h;
      part.x = clamp(Math.round(center.x - w / 2), MAX_OFFSET);
      part.y = clamp(Math.round(center.y - h / 2), MAX_OFFSET);
    } else {
      target[xKey] = clamp(drag.x + Math.round((p.x - drag.startX) / k), limit);
      target[yKey] = clamp(drag.y + Math.round((p.y - drag.startY) / k), limit);
    }
    drag.moved = drag.moved || before !== JSON.stringify([target[xKey], target[yKey], part.w, part.h, target.rotation]);
    this.afterChange(drag.key);
    this.refreshControls();
    this.draw();
  };

  PartsEditor.prototype.onMouseUp = function () {
    if (!this.drag) {
      return;
    }
    if (this.selectMouseUp()) {
      return;
    }
    var moved = this.drag.moved;
    this.drag = null;
    this.draw();
    if (moved) {
      this.commit(0);
    }
  };

  window.PartsEditor = PartsEditor;
})();
