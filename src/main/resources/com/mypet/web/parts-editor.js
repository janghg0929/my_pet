// 모습 꾸미기 편집기: 부위(몸통, 양팔, 양다리, 눈)를 고르고, 끌어서 옮기고, 모서리로 크기를 바꾼다.
// 숫자 입력, 부위 사진 넣기/빼기(눈은 표정별), 좌우 맞추기, 기본 비트로 되돌리기.
// 바뀐 배치는 send("setParts", JSON) 으로 Java 에 보내 화면 비트에 바로 적용하고 저장한다.
(function () {
  "use strict";

  var PART_LABELS = { body: "몸통", leftArm: "왼팔", rightArm: "오른팔", leftLeg: "왼다리", rightLeg: "오른다리", eyes: "눈" };
  var PART_ORDER = ["body", "eyes", "leftArm", "rightArm", "leftLeg", "rightLeg"];
  var EXPRESSIONS = [{ key: "neutral", label: "평소" }, { key: "grab", label: "잡혔을 때" }, { key: "fall", label: "떨어질 때" }];
  // 클릭했을 때 위에 그려진 부위부터 고른다
  var HIT_ORDER = ["rightArm", "leftArm", "eyes", "rightLeg", "leftLeg", "body"];
  var MIRROR = { leftArm: "rightArm", rightArm: "leftArm", leftLeg: "rightLeg", rightLeg: "leftLeg" };
  var HANDLE = 9; // 크기 조절 손잡이 (화면 px)
  var MIN_SIZE = 2, MAX_SIZE = 400, MAX_OFFSET = 400;

  function PartsEditor(options) {
    this.send = options.send;
    this.getColor = options.getColor;
    this.onHeightChange = options.onHeightChange;
    this.canvas = document.getElementById("parts-canvas");
    this.layout = window.BitView.copyLayout();
    this.selected = "body";
    this.expression = "neutral";
    this.drag = null;
    this.view = null; // { k, ox, oy, w, h } (화면 px)
    this.commitTimer = null;
    this.resetArmed = false;
    this.build();
  }

  // ---------------------------------------------------------------- 화면 구성

  PartsEditor.prototype.build = function () {
    var self = this;
    var tabs = document.getElementById("parts-tabs");
    PART_ORDER.forEach(function (key) {
      var button = document.createElement("button");
      button.className = "seg";
      button.textContent = PART_LABELS[key];
      button.setAttribute("data-part", key);
      button.addEventListener("click", function () { self.select(key); });
      tabs.appendChild(button);
    });
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

    ["x", "y", "w", "h"].forEach(function (field) {
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
      self.draw();
      self.commit(0);
    });
    document.getElementById("parts-photo").addEventListener("click", function () {
      self.send("pickPartImage", self.selected, self.expression);
    });
    document.getElementById("parts-photo-remove").addEventListener("click", function () {
      self.send("removePartImage", self.selected, self.expression);
    });
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

    this.canvas.addEventListener("mousedown", function (event) { self.onMouseDown(event); });
    this.canvas.addEventListener("mousemove", function (event) { self.onHover(event); });
    document.addEventListener("mousemove", function (event) { self.onMouseMove(event); });
    document.addEventListener("mouseup", function () { self.onMouseUp(); });
    // 창 너비가 바뀌면 다시 맞춰 그린다
    window.addEventListener("resize", function () {
      if (!self.drag) {
        self.view = null;
        self.draw();
      }
    });
  };

  // ---------------------------------------------------------------- 상태

  /** Java 가 보낸 배치로 바꾼다 (끄는 중에는 무시). */
  PartsEditor.prototype.setLayout = function (layout) {
    if (this.drag) {
      return;
    }
    this.layout = window.BitView.copyLayout(layout);
    this.refreshControls();
    this.draw();
  };

  PartsEditor.prototype.select = function (key) {
    this.selected = key;
    if (key !== "eyes") {
      this.expression = "neutral";
    }
    this.refreshControls();
    this.draw();
  };

  PartsEditor.prototype.part = function () {
    return this.layout[this.selected];
  };

  PartsEditor.prototype.hasImage = function () {
    var part = this.part();
    return this.selected === "eyes" ? !!(part.images && part.images[this.expression]) : !!part.image;
  };

  PartsEditor.prototype.refreshControls = function () {
    var part = this.part();
    var tabs = document.querySelectorAll("#parts-tabs .seg");
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle("selected", tabs[i].getAttribute("data-part") === this.selected);
    }
    var eyeTabs = document.getElementById("parts-eye-tabs");
    var hideEyeTabs = this.selected !== "eyes";
    if (eyeTabs.hidden !== hideEyeTabs) {
      eyeTabs.hidden = hideEyeTabs;
      // 표정 줄이 생기거나 없어지면 창 높이를 다시 맞춘다
      if (this.onHeightChange) {
        this.onHeightChange();
      }
    }
    var exprButtons = eyeTabs.querySelectorAll(".seg");
    for (var j = 0; j < exprButtons.length; j++) {
      exprButtons[j].classList.toggle("selected", exprButtons[j].getAttribute("data-expr") === this.expression);
    }
    ["x", "y", "w", "h"].forEach(function (field) {
      var input = document.getElementById("parts-" + field);
      if (document.activeElement !== input) {
        input.value = part[field];
      }
    });
    document.getElementById("parts-symmetric").checked = !!this.layout.symmetric;
    var photo = document.getElementById("parts-photo");
    var label = this.selected === "eyes" ? "'" + labelOf(this.expression) + "' 눈 사진" : PART_LABELS[this.selected] + " 사진";
    photo.textContent = (this.hasImage() ? "바꾸기: " : "넣기: ") + label;
    document.getElementById("parts-photo-remove").disabled = !this.hasImage();

    function labelOf(key) {
      for (var n = 0; n < EXPRESSIONS.length; n++) {
        if (EXPRESSIONS[n].key === key) {
          return EXPRESSIONS[n].label;
        }
      }
      return key;
    }
  };

  // ---------------------------------------------------------------- 좌우 맞추기

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
  };

  /** key 를 바꾼 뒤 좌우 맞추기 반영. 팔/다리만 반대쪽이 따라오고, 몸통을 바꿔도 다른 부위는 그대로다. */
  PartsEditor.prototype.afterChange = function (key) {
    if (this.layout.symmetric && MIRROR[key]) {
      this.mirrorFrom(key);
    }
  };

  // ---------------------------------------------------------------- 숫자 입력

  PartsEditor.prototype.onNumber = function (field, text) {
    var value = parseInt(text, 10);
    if (isNaN(value)) {
      return;
    }
    var part = this.part();
    part[field] = field === "w" || field === "h"
      ? Math.max(MIN_SIZE, Math.min(MAX_SIZE, value))
      : Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
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
      self.send("setParts", JSON.stringify(self.layout));
    }, delay);
  };

  // ---------------------------------------------------------------- 그리기

  /** 비트 전체가 캔버스에 들어오도록 배율과 위치를 정한다. */
  PartsEditor.prototype.fit = function (w, h) {
    var b = window.BitView.bounds(this.layout, false);
    var bw = Math.max(20, b.maxX - b.minX);
    var bh = Math.max(20, b.maxY - b.minY);
    var k = Math.max(1, Math.min(4, (w - 40) / bw, (h - 40) / bh));
    return {
      k: k,
      ox: w / 2 - ((b.minX + b.maxX) / 2) * k,
      oy: h / 2 - ((b.minY + b.maxY) / 2) * k,
      w: w,
      h: h
    };
  };

  /** 지금 배율·위치로 비트 전체가 캔버스 안에 보이는지. */
  PartsEditor.prototype.fits = function () {
    var v = this.view;
    var b = window.BitView.bounds(this.layout, false);
    var margin = 6;
    return v.ox + b.minX * v.k >= margin && v.ox + b.maxX * v.k <= v.w - margin
      && v.oy + b.minY * v.k >= margin && v.oy + b.maxY * v.k <= v.h - margin;
  };

  PartsEditor.prototype.draw = function () {
    if (!this.canvas || this.canvas.clientWidth === 0) {
      return;
    }
    var c = window.BitView.prepare(this.canvas);
    // 화면을 다시 맞추는 건 처음, 창 크기가 바뀌었을 때, 비트가 캔버스 밖으로 나갔을 때만
    // (부위 하나를 옮겼는데 나머지가 같이 움직여 보이지 않게). 끄는 중에는 그대로 둔다.
    if (!this.view || this.view.w !== c.w || this.view.h !== c.h || (!this.drag && !this.fits())) {
      this.view = this.fit(c.w, c.h);
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

    var pose = window.BitView.newPose();
    pose.expression = this.selected === "eyes" ? this.expression : "neutral";
    if (pose.expression === "fall") {
      pose.armLift = -8;
    }
    window.BitView.drawBitAt(ctx, v.ox * d, v.oy * d, v.k * d, this.getColor(), pose, this.layout);

    // 고른 부위 표시: 점선 테두리 + 크기 조절 손잡이
    var r = this.partRect(this.selected, pose);
    ctx.save();
    ctx.strokeStyle = "#26221e";
    ctx.lineWidth = Math.max(1, Math.round(d));
    ctx.setLineDash([4 * d, 3 * d]);
    ctx.strokeRect(Math.round((r.x - 2) * d) + 0.5, Math.round((r.y - 2) * d) + 0.5, Math.round((r.w + 4) * d), Math.round((r.h + 4) * d));
    ctx.setLineDash([]);
    var hr = this.handleRect(r);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(Math.round(hr.x * d), Math.round(hr.y * d), Math.round(hr.w * d), Math.round(hr.h * d));
    ctx.strokeRect(Math.round(hr.x * d) + 0.5, Math.round(hr.y * d) + 0.5, Math.round(hr.w * d), Math.round(hr.h * d));
    ctx.restore();
  };

  /** 부위의 캔버스 위 사각형 (화면 px). */
  PartsEditor.prototype.partRect = function (key, pose) {
    var v = this.view;
    var part = this.layout[key];
    var lift = (key === "leftArm" || key === "rightArm") && pose && pose.armLift ? pose.armLift : 0;
    return { x: v.ox + part.x * v.k, y: v.oy + (part.y + lift) * v.k, w: part.w * v.k, h: part.h * v.k };
  };

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
    if (pointIn(this.handleRect(this.partRect(this.selected)), x, y)) {
      return { key: this.selected, resize: true };
    }
    for (var i = 0; i < HIT_ORDER.length; i++) {
      var r = this.partRect(HIT_ORDER[i]);
      // 아주 작은 부위도 잡을 수 있게 조금 넓게 본다
      if (pointIn({ x: r.x - 3, y: r.y - 3, w: r.w + 6, h: r.h + 6 }, x, y)) {
        return { key: HIT_ORDER[i], resize: false };
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
    this.canvas.style.cursor = !target ? "default" : (target.resize ? "nwse-resize" : "move");
  };

  PartsEditor.prototype.onMouseDown = function (event) {
    var p = this.pointer(event);
    var target = this.hit(p.x, p.y);
    if (!target) {
      return;
    }
    event.preventDefault();
    if (target.key !== this.selected) {
      this.select(target.key);
    }
    var part = this.part();
    this.drag = { key: target.key, resize: target.resize, startX: p.x, startY: p.y, x: part.x, y: part.y, w: part.w, h: part.h, moved: false };
  };

  PartsEditor.prototype.onMouseMove = function (event) {
    if (!this.drag) {
      return;
    }
    var p = this.pointer(event);
    var dx = Math.round((p.x - this.drag.startX) / this.view.k);
    var dy = Math.round((p.y - this.drag.startY) / this.view.k);
    var part = this.layout[this.drag.key];
    if (this.drag.resize) {
      part.w = Math.max(MIN_SIZE, Math.min(MAX_SIZE, this.drag.w + dx));
      part.h = Math.max(MIN_SIZE, Math.min(MAX_SIZE, this.drag.h + dy));
    } else {
      part.x = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, this.drag.x + dx));
      part.y = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, this.drag.y + dy));
    }
    this.drag.moved = this.drag.moved || dx !== 0 || dy !== 0;
    this.afterChange(this.drag.key);
    this.refreshControls();
    this.draw();
  };

  PartsEditor.prototype.onMouseUp = function () {
    if (!this.drag) {
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
