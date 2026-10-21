// 모습 꾸미기 편집기: 부위(몸통, 양팔, 양다리, 눈)를 고르고, 끌어서 옮기고, 모서리로 크기를 바꾸고, 위쪽 동그라미로 돌린다.
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
  var ROTATE_GAP = 16; // 회전 손잡이가 부위 위로 떨어진 거리 (화면 px)
  var ROTATE_RADIUS = 5;
  var ROTATE_SNAP = 15, ROTATE_SNAP_RANGE = 4; // 15도 단위 근처에서는 딱 맞춘다
  var MIN_SIZE = 2, MAX_SIZE = 400, MAX_OFFSET = 400;
  var FIELDS = ["x", "y", "w", "h", "rotation"];

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
    FIELDS.forEach(function (field) {
      var input = document.getElementById("parts-" + field);
      if (document.activeElement !== input) {
        input.value = part[field] || 0;
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
    t.rotation = normalizeRotation(-(s.rotation || 0));
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
    if (field === "rotation") {
      part.rotation = normalizeRotation(value);
    } else if (field === "w" || field === "h") {
      part[field] = Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    } else {
      part[field] = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
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
      self.send("setParts", JSON.stringify(self.layout));
    }, delay);
  };

  // ---------------------------------------------------------------- 그리기

  /** 비트 전체가 캔버스에 들어오도록 배율과 위치를 정한다. */
  PartsEditor.prototype.fit = function (w, h) {
    var b = window.BitView.bounds(this.layout, false);
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
    var b = window.BitView.bounds(this.layout, false);
    var margin = 6;
    return v.ox + b.minX * v.k >= margin && v.ox + b.maxX * v.k <= v.w - margin
      && v.oy + b.minY * v.k >= margin + ROTATE_GAP && v.oy + b.maxY * v.k <= v.h - margin;
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

    // 고른 부위 표시: 점선 테두리 + 크기 조절 손잡이(오른쪽 아래) + 회전 손잡이(위), 부위와 같이 돌린다
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
    var hr = this.handleRect(r);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(Math.round(hr.x * d), Math.round(hr.y * d), Math.round(hr.w * d), Math.round(hr.h * d));
    ctx.strokeRect(Math.round(hr.x * d) + 0.5, Math.round(hr.y * d) + 0.5, Math.round(hr.w * d), Math.round(hr.h * d));
    var knob = this.rotateKnob(r);
    ctx.beginPath();
    ctx.moveTo(knob.x * d, (r.y - 2) * d);
    ctx.lineTo(knob.x * d, (knob.y + ROTATE_RADIUS) * d);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(knob.x * d, knob.y * d, ROTATE_RADIUS * d, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  };

  /** 부위의 캔버스 위 사각형 (화면 px, 돌리기 전). cx, cy = 가운데, angle = 회전(라디안). */
  PartsEditor.prototype.partRect = function (key, pose) {
    var v = this.view;
    var part = this.layout[key];
    var lift = (key === "leftArm" || key === "rightArm") && pose && pose.armLift ? pose.armLift : 0;
    var r = { x: v.ox + part.x * v.k, y: v.oy + (part.y + lift) * v.k, w: part.w * v.k, h: part.h * v.k };
    r.cx = r.x + r.w / 2;
    r.cy = r.y + r.h / 2;
    r.angle = (part.rotation || 0) * Math.PI / 180;
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
    var selected = this.partRect(this.selected);
    var q = toLocal(selected, x, y);
    var knob = this.rotateKnob(selected);
    var grab = ROTATE_RADIUS + 3;
    if (pointIn({ x: knob.x - grab, y: knob.y - grab, w: grab * 2, h: grab * 2 }, q.x, q.y)) {
      return { key: this.selected, rotate: true };
    }
    if (pointIn(this.handleRect(selected), q.x, q.y)) {
      return { key: this.selected, resize: true };
    }
    for (var i = 0; i < HIT_ORDER.length; i++) {
      var r = this.partRect(HIT_ORDER[i]);
      var local = toLocal(r, x, y);
      // 아주 작은 부위도 잡을 수 있게 조금 넓게 본다
      if (pointIn({ x: r.x - 3, y: r.y - 3, w: r.w + 6, h: r.h + 6 }, local.x, local.y)) {
        return { key: HIT_ORDER[i] };
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
    this.canvas.style.cursor = !target ? "default" : (target.rotate ? "grab" : (target.resize ? "nwse-resize" : "move"));
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
    var r = this.partRect(target.key);
    this.drag = {
      key: target.key, resize: !!target.resize, rotate: !!target.rotate, startX: p.x, startY: p.y,
      x: part.x, y: part.y, w: part.w, h: part.h, rotation: part.rotation || 0,
      cx: r.cx, cy: r.cy, startAngle: Math.atan2(p.y - r.cy, p.x - r.cx), moved: false
    };
  };

  PartsEditor.prototype.onMouseMove = function (event) {
    if (!this.drag) {
      return;
    }
    var p = this.pointer(event);
    var drag = this.drag;
    var k = this.view.k;
    var part = this.layout[drag.key];
    var before = part.x + "," + part.y + "," + part.w + "," + part.h + "," + part.rotation;
    if (drag.rotate) {
      // 가운데에서 마우스까지의 각도가 바뀐 만큼 돌린다
      var turned = (Math.atan2(p.y - drag.cy, p.x - drag.cx) - drag.startAngle) * 180 / Math.PI;
      var angle = Math.round(drag.rotation + turned);
      var snapped = Math.round(angle / ROTATE_SNAP) * ROTATE_SNAP;
      part.rotation = normalizeRotation(Math.abs(angle - snapped) <= ROTATE_SNAP_RANGE ? snapped : angle);
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
      part.x = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, Math.round(center.x - w / 2)));
      part.y = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, Math.round(center.y - h / 2)));
    } else {
      part.x = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, drag.x + Math.round((p.x - drag.startX) / k)));
      part.y = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, drag.y + Math.round((p.y - drag.startY) / k)));
    }
    drag.moved = drag.moved || before !== part.x + "," + part.y + "," + part.w + "," + part.h + "," + part.rotation;
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
