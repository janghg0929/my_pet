// 도트 그림판: 부위 크기만큼의 칸(1칸 = 화면 1px, 2배·4배도)에 펜·지우개·채우기·스포이트로 그린다.
// "몸 색" 물감으로 칠한 칸은 알파 254 로 저장해서, 비트가 그릴 때 지금 몸 색으로 바꿔 칠한다 (PartImages.BODY_ALPHA).
// 저장하면 PNG data URL 을 onSave 로 넘긴다 (Java 가 parts 폴더에 "px_…png" 로 저장).
(function () {
  "use strict";

  var BODY_ALPHA = 254;
  var MAX_SIDE = 96; // 칸 수 한쪽 최대
  var HISTORY = 50;
  var PALETTE = [
    "#000000", "#ffffff", "#7d7468", "#c8c2b8", "#e53935", "#fb8c00", "#fdd835", "#43a047",
    "#1e88e5", "#4fc3f7", "#8e24aa", "#f48fb1", "#6d4c41", "#ffcc80"
  ];

  function clamp(v, min, max) {
    return Math.max(min, Math.min(max, v));
  }

  function hexToRgb(hex) {
    var n = parseInt(String(hex).replace("#", ""), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  function PixelPainter() {
    this.panel = document.getElementById("paint-panel");
    this.canvas = document.getElementById("paint-canvas");
    this.tool = "pen";
    this.color = { hex: "#000000", body: false };
    this.data = null; // RGBA, 칸 하나 = 4칸
    this.undoStack = [];
    this.redoStack = [];
    this.stroke = null;
    this.options = null;
    this.build();
  }

  PixelPainter.prototype.isOpen = function () {
    return !this.panel.hidden;
  };

  // ---------------------------------------------------------------- 화면 구성

  PixelPainter.prototype.build = function () {
    var self = this;
    Array.prototype.forEach.call(this.panel.querySelectorAll("[data-tool]"), function (button) {
      button.addEventListener("click", function () {
        self.tool = button.getAttribute("data-tool");
        self.refreshTools();
      });
    });
    document.getElementById("paint-scale").addEventListener("change", function (event) {
      self.setScale(parseInt(event.target.value, 10) || 1);
    });
    document.getElementById("paint-undo").addEventListener("click", function () { self.undo(); });
    document.getElementById("paint-redo").addEventListener("click", function () { self.redo(); });
    document.getElementById("paint-clear").addEventListener("click", function () {
      self.pushUndo();
      self.data = new Uint8ClampedArray(self.w * self.h * 4);
      self.draw();
    });
    document.getElementById("paint-cancel").addEventListener("click", function () { self.close(); });
    document.getElementById("paint-save").addEventListener("click", function () { self.save(); });

    var palette = document.getElementById("paint-palette");
    PALETTE.forEach(function (hex) {
      var swatch = document.createElement("button");
      swatch.className = "paint-swatch";
      swatch.style.background = hex;
      swatch.title = hex;
      swatch.setAttribute("data-color", hex);
      swatch.addEventListener("click", function () { self.setColor({ hex: hex, body: false }); });
      palette.appendChild(swatch);
    });
    // "몸 색" 물감: 색깔 설정을 바꾸면 같이 바뀐다
    var body = document.createElement("button");
    body.className = "paint-swatch paint-body";
    body.textContent = "몸";
    body.title = "몸 색 (색깔 설정을 따라 바뀌어요)";
    body.setAttribute("data-color", "body");
    body.addEventListener("click", function () { self.setColor({ hex: self.bodyColor(), body: true }); });
    palette.appendChild(body);
    var hex = document.getElementById("paint-hex");
    hex.addEventListener("change", function () {
      var value = hex.value.trim();
      if (!/^#?[0-9a-fA-F]{6}$/.test(value)) {
        hex.value = self.color.body ? "" : self.color.hex;
        return;
      }
      self.setColor({ hex: (value.charAt(0) === "#" ? value : "#" + value).toLowerCase(), body: false });
    });

    this.canvas.addEventListener("mousedown", function (event) { self.onDown(event); });
    this.canvas.addEventListener("contextmenu", function (event) { event.preventDefault(); });
    document.addEventListener("mousemove", function (event) { self.onMove(event); });
    document.addEventListener("mouseup", function () { self.onUp(); });
  };

  PixelPainter.prototype.bodyColor = function () {
    return this.options && this.options.bodyColor ? this.options.bodyColor() : "#4fc3f7";
  };

  PixelPainter.prototype.setColor = function (color) {
    this.color = color;
    if (this.tool === "eraser" || this.tool === "picker") {
      this.tool = "pen";
    }
    this.refreshTools();
  };

  PixelPainter.prototype.refreshTools = function () {
    var self = this;
    Array.prototype.forEach.call(this.panel.querySelectorAll("[data-tool]"), function (button) {
      button.classList.toggle("selected", button.getAttribute("data-tool") === self.tool);
    });
    Array.prototype.forEach.call(this.panel.querySelectorAll(".paint-swatch"), function (swatch) {
      var key = swatch.getAttribute("data-color");
      swatch.classList.toggle("selected", self.color.body ? key === "body" : key === self.color.hex);
    });
    this.panel.querySelector(".paint-body").style.background = this.bodyColor();
    document.getElementById("paint-current").style.background = this.color.body ? this.bodyColor() : this.color.hex;
    var hex = document.getElementById("paint-hex");
    if (document.activeElement !== hex) {
      hex.value = this.color.body ? "" : this.color.hex;
    }
    document.getElementById("paint-undo").disabled = !this.undoStack.length;
    document.getElementById("paint-redo").disabled = !this.redoStack.length;
  };

  // ---------------------------------------------------------------- 열기/닫기

  /**
   * options: title, width, height (부위 크기 = 1배 칸 수), image (지금 그림, 없으면 null),
   * base ("rect" 몸 색 네모+외곽선 / "dot" 검은 점 / "none" 빈칸: 그림이 없을 때 시작 모습),
   * bodyColor() (지금 몸 색), onSave(dataUrl).
   */
  PixelPainter.prototype.open = function (options) {
    this.options = options;
    this.baseW = clamp(Math.round(options.width), 1, MAX_SIDE);
    this.baseH = clamp(Math.round(options.height), 1, MAX_SIDE);
    // 그림이 이미 있으면 그 크기(도트 그림)로, 아니면 1배
    var scale = 1;
    if (options.image && options.image.pixelArt) {
      var natural = options.image.naturalWidth || options.image.width;
      scale = [1, 2, 4].filter(function (s) { return s * this.baseW === natural; }, this)[0] || 1;
    }
    this.scale = scale;
    this.w = this.baseW * scale;
    this.h = this.baseH * scale;
    while ((this.w > MAX_SIDE || this.h > MAX_SIDE) && this.scale > 1) {
      this.scale /= 2;
      this.w = this.baseW * this.scale;
      this.h = this.baseH * this.scale;
    }
    this.data = options.image ? this.fromImage(options.image, this.w, this.h) : this.startShape(options.base);
    this.undoStack = [];
    this.redoStack = [];
    this.tool = "pen";
    document.getElementById("paint-title").textContent = options.title;
    document.getElementById("paint-scale").value = String(this.scale);
    Array.prototype.forEach.call(document.querySelectorAll("#paint-scale option"), function (option) {
      var s = parseInt(option.value, 10);
      option.disabled = this.baseW * s > MAX_SIDE || this.baseH * s > MAX_SIDE;
    }, this);
    this.panel.hidden = false;
    this.refreshTools();
    this.draw();
  };

  PixelPainter.prototype.close = function () {
    this.panel.hidden = true;
    this.stroke = null;
  };

  PixelPainter.prototype.save = function () {
    var out = document.createElement("canvas");
    out.width = this.w;
    out.height = this.h;
    var ctx = out.getContext("2d");
    var image = ctx.createImageData(this.w, this.h);
    image.data.set(this.data);
    ctx.putImageData(image, 0, 0);
    var url = out.toDataURL("image/png");
    this.close();
    if (this.options && this.options.onSave) {
      this.options.onSave(url);
    }
  };

  /** 지금 그림(사진)을 칸에 옮긴다. 사진은 반투명을 없애서 또렷하게. */
  PixelPainter.prototype.fromImage = function (image, w, h) {
    var c = document.createElement("canvas");
    c.width = w;
    c.height = h;
    var ctx = c.getContext("2d");
    ctx.imageSmoothingEnabled = !image.pixelArt;
    ctx.drawImage(image, 0, 0, w, h);
    var data = ctx.getImageData(0, 0, w, h).data;
    var result = new Uint8ClampedArray(data.length);
    for (var i = 0; i < data.length; i += 4) {
      var a = data[i + 3];
      if (image.pixelArt && a === BODY_ALPHA) {
        result[i + 3] = BODY_ALPHA; // 몸 색 칸은 그대로
      } else if (a >= 128) {
        result[i] = data[i];
        result[i + 1] = data[i + 1];
        result[i + 2] = data[i + 2];
        result[i + 3] = 255;
      }
    }
    return result;
  };

  /** 그림이 없을 때 시작 모습: 몸 색 네모 + 검은 외곽선 / 검은 점 / 빈칸. */
  PixelPainter.prototype.startShape = function (base) {
    var w = this.w, h = this.h, s = this.scale;
    var data = new Uint8ClampedArray(w * h * 4);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var i = (y * w + x) * 4;
        if (base === "rect") {
          var edge = x < s || y < s || x >= w - s || y >= h - s;
          data[i + 3] = edge ? 255 : BODY_ALPHA;
        } else if (base === "dot") {
          var dx = (x + 0.5) / w * 2 - 1, dy = (y + 0.5) / h * 2 - 1;
          if (dx * dx + dy * dy <= 1) {
            data[i + 3] = 255;
          }
        }
      }
    }
    return data;
  };

  /** 칸 크기(1배·2배·4배)를 바꾼다: 지금 그림을 칸에 맞춰 늘리거나 줄인다. */
  PixelPainter.prototype.setScale = function (scale) {
    var w = this.baseW * scale, h = this.baseH * scale;
    if (w > MAX_SIDE || h > MAX_SIDE || scale === this.scale) {
      document.getElementById("paint-scale").value = String(this.scale);
      return;
    }
    this.pushUndo();
    var old = this.data, ow = this.w, oh = this.h;
    var data = new Uint8ClampedArray(w * h * 4);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var sx = Math.floor(x * ow / w), sy = Math.floor(y * oh / h);
        var from = (sy * ow + sx) * 4, to = (y * w + x) * 4;
        data[to] = old[from];
        data[to + 1] = old[from + 1];
        data[to + 2] = old[from + 2];
        data[to + 3] = old[from + 3];
      }
    }
    this.scale = scale;
    this.w = w;
    this.h = h;
    this.data = data;
    this.draw();
  };

  // ---------------------------------------------------------------- 되돌리기

  PixelPainter.prototype.snapshot = function () {
    return { w: this.w, h: this.h, scale: this.scale, data: new Uint8ClampedArray(this.data) };
  };

  PixelPainter.prototype.restoreSnapshot = function (snap) {
    this.w = snap.w;
    this.h = snap.h;
    this.scale = snap.scale;
    this.data = snap.data;
    document.getElementById("paint-scale").value = String(this.scale);
  };

  PixelPainter.prototype.pushUndo = function () {
    this.undoStack.push(this.snapshot());
    if (this.undoStack.length > HISTORY) {
      this.undoStack.shift();
    }
    this.redoStack = [];
    this.refreshTools();
  };

  PixelPainter.prototype.undo = function () {
    if (!this.undoStack.length) {
      return;
    }
    this.redoStack.push(this.snapshot());
    this.restoreSnapshot(this.undoStack.pop());
    this.refreshTools();
    this.draw();
  };

  PixelPainter.prototype.redo = function () {
    if (!this.redoStack.length) {
      return;
    }
    this.undoStack.push(this.snapshot());
    this.restoreSnapshot(this.redoStack.pop());
    this.refreshTools();
    this.draw();
  };

  // ---------------------------------------------------------------- 그리기

  /** 칸 크기(화면 px)와 그림 자리. */
  PixelPainter.prototype.layout = function () {
    var cw = this.canvas.clientWidth, ch = this.canvas.clientHeight;
    var cell = Math.max(1, Math.floor(Math.min((cw - 8) / this.w, (ch - 8) / this.h)));
    return { cell: cell, ox: Math.floor((cw - cell * this.w) / 2), oy: Math.floor((ch - cell * this.h) / 2), cw: cw, ch: ch };
  };

  PixelPainter.prototype.draw = function () {
    if (!this.data || this.canvas.clientWidth === 0) {
      return;
    }
    var dpr = window.devicePixelRatio || 1;
    var l = this.layout();
    this.canvas.width = Math.round(l.cw * dpr);
    this.canvas.height = Math.round(l.ch * dpr);
    var ctx = this.canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = "#f3eee4";
    ctx.fillRect(0, 0, l.cw, l.ch);
    var body = hexToRgb(this.bodyColor());
    for (var y = 0; y < this.h; y++) {
      for (var x = 0; x < this.w; x++) {
        var i = (y * this.w + x) * 4;
        var a = this.data[i + 3];
        var px = l.ox + x * l.cell, py = l.oy + y * l.cell;
        if (a === 0) {
          // 투명: 바둑판
          ctx.fillStyle = (x + y) % 2 ? "#ffffff" : "#ece6da";
        } else if (a === BODY_ALPHA) {
          ctx.fillStyle = "rgb(" + body[0] + "," + body[1] + "," + body[2] + ")";
        } else {
          ctx.fillStyle = "rgb(" + this.data[i] + "," + this.data[i + 1] + "," + this.data[i + 2] + ")";
        }
        ctx.fillRect(px, py, l.cell, l.cell);
      }
    }
    // 칸 선 (칸이 크면)
    if (l.cell >= 6) {
      ctx.fillStyle = "rgba(38, 34, 30, 0.12)";
      for (var gx = 0; gx <= this.w; gx++) {
        ctx.fillRect(l.ox + gx * l.cell, l.oy, 1, this.h * l.cell);
      }
      for (var gy = 0; gy <= this.h; gy++) {
        ctx.fillRect(l.ox, l.oy + gy * l.cell, this.w * l.cell, 1);
      }
    }
    ctx.strokeStyle = "#26221e";
    ctx.lineWidth = 1;
    ctx.strokeRect(l.ox - 0.5, l.oy - 0.5, this.w * l.cell + 1, this.h * l.cell + 1);
    document.getElementById("paint-size").textContent = this.w + "×" + this.h + "칸";
  };

  PixelPainter.prototype.cellAt = function (event) {
    var rect = this.canvas.getBoundingClientRect();
    var l = this.layout();
    var x = Math.floor((event.clientX - rect.left - l.ox) / l.cell);
    var y = Math.floor((event.clientY - rect.top - l.oy) / l.cell);
    return { x: x, y: y, inside: x >= 0 && y >= 0 && x < this.w && y < this.h };
  };

  /** 칸 하나 칠하기 (erase = 투명). */
  PixelPainter.prototype.paint = function (x, y, erase) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) {
      return;
    }
    var i = (y * this.w + x) * 4;
    if (erase) {
      this.data[i] = this.data[i + 1] = this.data[i + 2] = this.data[i + 3] = 0;
    } else if (this.color.body) {
      this.data[i] = this.data[i + 1] = this.data[i + 2] = 0;
      this.data[i + 3] = BODY_ALPHA;
    } else {
      var rgb = hexToRgb(this.color.hex);
      this.data[i] = rgb[0];
      this.data[i + 1] = rgb[1];
      this.data[i + 2] = rgb[2];
      this.data[i + 3] = 255;
    }
  };

  /** 두 칸 사이를 빈틈없이 (빨리 끌어도 선이 끊기지 않게). */
  PixelPainter.prototype.line = function (x0, y0, x1, y1, erase) {
    var dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    var sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    var err = dx + dy;
    for (;;) {
      this.paint(x0, y0, erase);
      if (x0 === x1 && y0 === y1) {
        return;
      }
      var e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y0 += sy;
      }
    }
  };

  /** 같은 색으로 이어진 칸을 한 번에 칠하기. */
  PixelPainter.prototype.fill = function (x, y) {
    var w = this.w, h = this.h, data = this.data;
    var start = (y * w + x) * 4;
    var target = [data[start], data[start + 1], data[start + 2], data[start + 3]];
    var same = function (i) {
      return data[i + 3] === target[3] && (target[3] === 0 || target[3] === BODY_ALPHA
        || (data[i] === target[0] && data[i + 1] === target[1] && data[i + 2] === target[2]));
    };
    var probe = this.snapshot().data;
    this.paint(x, y, false);
    var painted = [data[start], data[start + 1], data[start + 2], data[start + 3]];
    data.set(probe);
    if (painted.join() === target.join()) {
      return; // 이미 그 색
    }
    var stack = [[x, y]];
    var seen = new Uint8Array(w * h);
    while (stack.length) {
      var p = stack.pop();
      var cx = p[0], cy = p[1];
      if (cx < 0 || cy < 0 || cx >= w || cy >= h || seen[cy * w + cx]) {
        continue;
      }
      seen[cy * w + cx] = 1;
      var i = (cy * w + cx) * 4;
      if (!same(i)) {
        continue;
      }
      this.paint(cx, cy, false);
      stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
    }
  };

  PixelPainter.prototype.onDown = function (event) {
    if (!this.isOpen()) {
      return;
    }
    event.preventDefault();
    var c = this.cellAt(event);
    if (!c.inside) {
      return;
    }
    var erase = event.button === 2 || this.tool === "eraser";
    if (this.tool === "picker" && event.button !== 2) {
      var i = (c.y * this.w + c.x) * 4;
      var a = this.data[i + 3];
      if (a === BODY_ALPHA) {
        this.setColor({ hex: this.bodyColor(), body: true });
      } else if (a) {
        var hex = "#" + [this.data[i], this.data[i + 1], this.data[i + 2]].map(function (v) {
          return (v < 16 ? "0" : "") + v.toString(16);
        }).join("");
        this.setColor({ hex: hex, body: false });
      }
      return;
    }
    this.pushUndo();
    if (this.tool === "fill" && !erase) {
      this.fill(c.x, c.y);
      this.draw();
      return;
    }
    this.paint(c.x, c.y, erase);
    this.stroke = { x: c.x, y: c.y, erase: erase };
    this.draw();
  };

  PixelPainter.prototype.onMove = function (event) {
    if (!this.stroke) {
      return;
    }
    var c = this.cellAt(event);
    if (c.x === this.stroke.x && c.y === this.stroke.y) {
      return;
    }
    this.line(this.stroke.x, this.stroke.y, c.x, c.y, this.stroke.erase);
    this.stroke.x = c.x;
    this.stroke.y = c.y;
    this.draw();
  };

  PixelPainter.prototype.onUp = function () {
    this.stroke = null;
  };

  /** 그림판 안에서의 키: Ctrl+Z/Y 되돌리기·다시 하기, Esc 닫기. 처리했으면 true. */
  PixelPainter.prototype.handleKey = function (event) {
    if (!this.isOpen()) {
      return false;
    }
    var key = (event.key || "").toLowerCase();
    if (event.ctrlKey || event.metaKey) {
      if (key === "z" || event.keyCode === 90) {
        if (event.shiftKey) {
          this.redo();
        } else {
          this.undo();
        }
        return true;
      }
      if (key === "y" || event.keyCode === 89) {
        this.redo();
        return true;
      }
    } else if (key === "escape" || event.keyCode === 27) {
      this.close();
      return true;
    }
    return false;
  };

  window.PixelPainter = PixelPainter;
})();
