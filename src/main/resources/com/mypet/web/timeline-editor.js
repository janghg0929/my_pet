// 모습 꾸미기 → 고급: 영상 편집처럼 시간 막대에 키프레임(◆)을 찍는다. PartsEditor 에 기능을 더한다.
// 상황(항상/쉴 때/걸을 때/공중/잡힐 때)마다 클립 하나, 부위마다 키 줄 하나.
// 재생 위치(빨간 선)를 고르고 미리보기에서 부위를 끌거나(옮기기) 돌리거나(회전) 모서리를 끌면(크기) 그 시간에 키가 생긴다.
// 키 사이는 부드럽게 이어지고, 실제 비트는 그 상황이 시작될 때 0초부터 재생한다(이어서 = 멈췄던 데서 계속).
// 키에 사진을 넣으면 그 시간부터 다음 사진 키까지 부위 사진이 바뀐다 (PartTimeline.java, bit.js clipAt / clipPhotoAt).
(function () {
  "use strict";

  var P = window.PartsEditor.prototype;
  var BV = window.BitView;

  var STATE_LABELS = { always: "항상", idle: "쉴 때", walk: "걸을 때", air: "공중", grab: "잡힐 때" };

  var RULER_H = 16, ROW_H = 12, PAD_R = 8; // 줄 높이 12: 기본 부위 6줄이 한 번에 보이게
  // 부위 이름 칸 너비: 이름 칸과 시간 칸 사이 선을 끌어서 바꾼다
  var labelW = 54, LABEL_MIN = 36, LABEL_MAX = 180, LABEL_GRAB = 4;
  var KEY_HIT = 6, MAX_KEYS = 60;
  var MIN_VIEW_MS = 100, TIME_ZOOM_STEP = 1.25; // 시간 막대를 가장 크게 확대하면 0.1초가 한 화면
  // 눈금 간격 후보 (ms): 확대할수록 촘촘한 눈금
  var TICK_STEPS = [5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000];
  var LABEL_STEPS = [10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000];
  var MAX_OFFSET = 2000, MAX_TURN = 1080, MIN_SCALE = 10, MAX_SCALE = 1000;
  var ROTATE_SNAP = 15, ROTATE_SNAP_RANGE = 4;
  var FIELDS = ["dx", "dy", "rotation", "scaleX", "scaleY"];

  function clamp(v, min, max) {
    return Math.max(min, Math.min(max, v));
  }

  // ---------------------------------------------------------------- 준비

  P.initTimeline = function () {
    var self = this;
    // view: 시간 막대에 보이는 구간 {start, span} (ms), null = 전체
    // rowOffset: 부위가 많아 줄이 넘칠 때 위에서 몇 줄을 넘겼는지
    this.tl = { state: "idle", playhead: 0, playing: false, drag: null, view: null, rowOffset: 0 };
    this.tlCanvas = document.getElementById("tl-canvas");

    var states = document.getElementById("tl-states");
    BV.STATES.forEach(function (state) {
      var button = document.createElement("button");
      button.className = "seg";
      button.textContent = STATE_LABELS[state];
      button.setAttribute("data-key", state);
      button.addEventListener("click", function () {
        self.tl.state = state;
        self.tl.view = null;
        self.tl.playhead = Math.min(self.tl.playhead, self.clip().duration);
        self.refreshControls();
        self.draw();
      });
      states.appendChild(button);
    });

    document.getElementById("tl-play").addEventListener("click", function () { self.timelineTogglePlay(); });
    document.getElementById("tl-add").addEventListener("click", function () {
      self.ensureKey(self.selected, self.tl.playhead);
      self.afterChange(self.selected);
      self.refreshControls();
      self.draw();
      self.commit(0);
    });
    document.getElementById("tl-delete").addEventListener("click", function () { self.deleteKeyAtPlayhead(); });
    // 사진 넣기/빼기: 지금 시간의 키에 사진을 넣고 뺀다 (시간 막대에 저장, 부위의 원래 사진은 일반 탭에서)
    document.getElementById("tl-photo").addEventListener("click", function () {
      self.tl.pendingPhoto = { state: self.tl.state, part: self.selected, t: self.tl.playhead };
      self.send("pickKeyImage");
    });
    document.getElementById("tl-draw").addEventListener("click", function () { self.openPainter("key"); });
    document.getElementById("tl-photo-remove").addEventListener("click", function () {
      var key = self.keyAtPlayhead(self.selected);
      if (key && key.photo) {
        key.photo = null;
        key.flip = false;
        self.afterChange(self.selected);
        self.refreshControls();
        self.draw();
        self.commit(0);
      }
    });

    FIELDS.forEach(function (field) {
      var input = document.getElementById("tl-" + field);
      input.addEventListener("input", function () {
        var value = parseInt(input.value, 10);
        if (isNaN(value)) {
          return;
        }
        var key = self.ensureKey(self.selected, self.tl.playhead);
        key[field] = field === "rotation" ? clamp(value, -MAX_TURN, MAX_TURN)
          : (field === "scaleX" || field === "scaleY") ? clamp(value, MIN_SCALE, MAX_SCALE) : clamp(value, -MAX_OFFSET, MAX_OFFSET);
        self.afterChange(self.selected);
        self.draw();
        self.commit(300);
      });
      input.addEventListener("change", function () { self.refreshControls(); });
    });
    var duration = document.getElementById("tl-duration");
    duration.addEventListener("change", function () {
      var seconds = parseFloat(duration.value);
      if (isNaN(seconds)) {
        self.refreshControls();
        return;
      }
      var clip = self.clip();
      clip.duration = clamp(Math.round(seconds * 1000), 100, 60000);
      // 길이보다 뒤에 있던 키는 끝으로
      Object.keys(clip.tracks).forEach(function (part) {
        clip.tracks[part].forEach(function (k) {
          k.t = Math.min(k.t, clip.duration);
        });
      });
      self.tl.playhead = Math.min(self.tl.playhead, clip.duration);
      self.tl.view = null;
      self.refreshControls();
      self.draw();
      self.commit(0);
    });
    document.getElementById("tl-loop").addEventListener("change", function (event) {
      self.clip().loop = event.target.checked;
      self.draw();
      self.commit(0);
    });
    document.getElementById("tl-continue").addEventListener("change", function (event) {
      self.clip().restart = !event.target.checked;
      self.commit(0);
    });
    document.getElementById("tl-ease").addEventListener("change", function (event) {
      var key = self.keyAtPlayhead(self.selected);
      if (key) {
        key.ease = event.target.checked;
        self.afterChange(self.selected);
        self.draw();
        self.commit(0);
      }
    });

    this.tlCanvas.addEventListener("mousedown", function (event) { self.timelineMouseDown(event); });
    this.tlCanvas.addEventListener("mousemove", function (event) {
      if (!self.tl.drag) {
        var p = self.timelinePointer(event);
        self.tlCanvas.style.cursor = Math.abs(p.x - labelW) <= LABEL_GRAB ? "col-resize" : "pointer";
      }
    });
    this.tlCanvas.addEventListener("dblclick", function (event) { self.timelineDoubleClick(event); });
    // 휠: 마우스 자리 기준 확대/축소, Shift+휠(또는 옆으로 굴리기): 좌우로 옮기기, 눈금 두 번 누르기: 전체 보기
    // (앱 안의 웹 엔진은 wheel 과 예전 mousewheel 을 둘 다 보낼 수 있어서, wheel 을 받았으면 mousewheel 은 무시)
    var sawWheel = false;
    function onWheel(event) {
      if (event.type === "wheel") {
        sawWheel = true;
      } else if (sawWheel) {
        return;
      }
      event.preventDefault();
      var dy = event.type === "wheel" ? event.deltaY : -event.wheelDelta;
      var dx = event.type === "wheel" ? (event.deltaX || 0) : 0;
      if (self.timelinePointer(event).x < labelW) {
        // 이름 칸: 줄 위아래로
        if (dy) {
          self.timelineScrollRows(dy > 0 ? 1 : -1);
        }
      } else if (event.shiftKey || Math.abs(dx) > Math.abs(dy)) {
        var amount = event.shiftKey ? dy : dx;
        if (amount) {
          self.timelineScroll(amount > 0 ? 1 : -1);
        }
      } else if (dy) {
        self.timelineZoom(self.timelinePointer(event), dy < 0 ? TIME_ZOOM_STEP : 1 / TIME_ZOOM_STEP);
      }
    }
    this.tlCanvas.addEventListener("wheel", onWheel);
    this.tlCanvas.addEventListener("mousewheel", onWheel);
    document.addEventListener("mousemove", function (event) { self.timelineMouseMove(event); });
    document.addEventListener("mouseup", function () { self.timelineMouseUp(); });
  };

  /** (Java 가 부름) 키 사진을 골랐다: 고를 때의 상황·부위·시간 키에 넣는다. 취소면 name = null. */
  P.onPickedImage = function (name) {
    var pending = this.tl.pendingPhoto;
    this.tl.pendingPhoto = null;
    if (!pending || !name) {
      this.refreshControls();
      return;
    }
    var state = this.tl.state;
    this.tl.state = pending.state;
    var key = this.ensureKey(pending.part, pending.t);
    key.photo = name;
    key.flip = false;
    this.tl.state = state;
    this.afterChange(pending.part);
    this.send("requestImage", name);
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  /** 지금 고른 상황의 클립. */
  P.clip = function () {
    return this.layout.timeline[this.tl.state];
  };

  P.track = function (part) {
    return this.clip().tracks[part] || [];
  };

  P.keyAtPlayhead = function (part) {
    var t = this.tl.playhead;
    var keys = this.track(part);
    for (var i = 0; i < keys.length; i++) {
      if (Math.abs(keys[i].t - t) < 1) {
        return keys[i];
      }
    }
    return null;
  };

  /** 지금 시간의 클립 값 (키가 없으면 기본 모양) — 키를 새로 만들 때 이 값으로 시작해서 모양이 튀지 않게. */
  P.clipValuesAt = function (part, t) {
    var c = BV.clipAt(this.clip(), part, t);
    if (!c) {
      return BV.newKey(t);
    }
    var key = BV.newKey(t);
    key.dx = Math.round(c.dx);
    key.dy = Math.round(c.dy);
    key.rotation = Math.round(c.rotation);
    key.scaleX = Math.round(c.scaleX * 100);
    key.scaleY = Math.round(c.scaleY * 100);
    return key;
  };

  /** part 줄의 t 시간 키 (없으면 지금 모양으로 만든다). */
  P.ensureKey = function (part, t) {
    var existing = null;
    var keys = this.track(part);
    for (var i = 0; i < keys.length; i++) {
      if (Math.abs(keys[i].t - t) < 1) {
        existing = keys[i];
      }
    }
    if (existing) {
      return existing;
    }
    var key = this.clipValuesAt(part, t);
    key.t = Math.round(t);
    var clip = this.clip();
    keys = clip.tracks[part] = (clip.tracks[part] || []).concat([key]);
    keys.sort(function (a, b) { return a.t - b.t; });
    if (keys.length > MAX_KEYS) {
      keys.splice(keys.indexOf(key) === 0 ? 1 : 0, 1);
    }
    return key;
  };

  P.deleteKeyAtPlayhead = function () {
    var key = this.keyAtPlayhead(this.selected);
    if (!key) {
      return;
    }
    var clip = this.clip();
    var keys = clip.tracks[this.selected].filter(function (k) { return k !== key; });
    if (keys.length) {
      clip.tracks[this.selected] = keys;
    } else {
      delete clip.tracks[this.selected];
    }
    this.afterChange(this.selected);
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  // ---------------------------------------------------------------- 미리보기

  /** 고른 상황이 playhead 만큼 지났을 때 부위 모양 (일반 애니메이션 + 시간 막대, 실제 비트와 같은 계산). */
  P.timelineTransformAt = function (key) {
    var state = this.tl.state;
    var weights = {};
    if (state !== "always") {
      weights[state] = 1;
    }
    var elapsed = { always: this.tl.playhead };
    elapsed[state] = this.tl.playhead;
    var ticks = Math.round(this.tl.playhead * 64 / 1000);
    return BV.combineTransforms(BV.partTransform(this.layout[key], ticks, weights),
      BV.timelineTransform(this.layout.timeline, key, weights, elapsed));
  };

  /** 고른 상황이 playhead 만큼 지났을 때 부위 사진 (실제 비트와 같은 계산). */
  P.timelinePhotoAt = function (key) {
    var state = this.tl.state;
    var weights = {};
    if (state !== "always") {
      weights[state] = 1;
    }
    var elapsed = { always: this.tl.playhead };
    elapsed[state] = this.tl.playhead;
    return BV.timelinePhoto(this.layout.timeline, key, weights, elapsed);
  };

  P.timelinePose = function () {
    var self = this;
    var pose = BV.newPose();
    var state = this.tl.state;
    pose.expression = state === "air" ? "fall" : (state === "grab" ? "grab" : "neutral");
    if (pose.expression === "fall") {
      pose.armLift = -8;
    }
    pose.motion = {
      transform: function (layout, key) { return self.timelineTransformAt(key); },
      photo: function (layout, key) { return self.timelinePhotoAt(key); }
    };
    return pose;
  };

  P.timelineTogglePlay = function () {
    var tl = this.tl;
    tl.playing = !tl.playing;
    if (tl.playing && !this.clip().loop && tl.playhead >= this.clip().duration) {
      tl.playhead = 0;
    }
    this.refreshTimelineControls();
    this.updateAnimation();
    this.draw();
  };

  /** 재생 중: 시간을 dt(ms) 만큼 보낸다. */
  P.timelineAdvance = function (dt) {
    var tl = this.tl;
    if (!tl.playing) {
      return;
    }
    var clip = this.clip();
    tl.playhead += dt;
    if (tl.playhead >= clip.duration) {
      if (clip.loop) {
        tl.playhead %= clip.duration;
      } else {
        tl.playhead = clip.duration;
        tl.playing = false;
        this.updateAnimation();
      }
    }
    if (tl.view) {
      var v = this.timeView();
      if (tl.playhead < v.start || tl.playhead > v.start + v.span) {
        tl.view = { start: tl.playhead, span: v.span };
      }
    }
    this.refreshTimelineControls();
  };

  // ---------------------------------------------------------------- 칸

  P.refreshTimelineControls = function () {
    var self = this;
    var tl = this.tl;
    var clip = this.clip();
    Array.prototype.forEach.call(document.querySelectorAll("#tl-states .seg"), function (button) {
      var state = button.getAttribute("data-key");
      button.classList.toggle("selected", state === tl.state);
      // 키가 있는 상황은 ◆ 표시 (화면에 있는 부위의 키만: 뺀 부위의 키는 남겨 두지만 줄이 안 보이니까)
      var visible = self.pickerKeys();
      var hasKeys = Object.keys(self.layout.timeline[state].tracks).some(function (part) {
        return visible.indexOf(part) >= 0 && self.layout.timeline[state].tracks[part].length > 0;
      });
      BV.setText(button, STATE_LABELS[state] + (hasKeys ? " ◆" : ""));
    });
    BV.setText(document.getElementById("tl-play"), tl.playing ? "❚❚" : "▶");
    var key = this.keyAtPlayhead(this.selected);
    var values = key || this.clipValuesAt(this.selected, tl.playhead);
    FIELDS.forEach(function (field) {
      var input = document.getElementById("tl-" + field);
      if (document.activeElement !== input && input.value !== String(values[field])) {
        input.value = values[field];
      }
    });
    document.getElementById("tl-delete").disabled = !key;
    document.getElementById("tl-add").disabled = !!key;
    var duration = document.getElementById("tl-duration");
    var seconds = (clip.duration / 1000).toFixed(1);
    if (document.activeElement !== duration && duration.value !== seconds) {
      duration.value = seconds;
    }
    document.getElementById("tl-loop").checked = clip.loop;
    document.getElementById("tl-continue").checked = !clip.restart;
    var keyHasPhoto = !!(key && key.photo);
    var photo = document.getElementById("tl-photo");
    photo.removeAttribute("title");
    photo.setAttribute("data-tip", keyHasPhoto ? "사진 바꾸기" : "사진 넣기");
    document.getElementById("tl-photo-remove").disabled = !keyHasPhoto;
    var ease = document.getElementById("tl-ease");
    ease.checked = key ? key.ease : true;
    ease.disabled = !key;
  };

  // ---------------------------------------------------------------- 시간 막대 그리기

  /** 시간 막대에 보이는 구간 (ms): 확대하지 않았으면 클립 전체. */
  P.timeView = function () {
    var duration = this.clip().duration;
    var v = this.tl.view;
    if (!v) {
      return { start: 0, span: duration };
    }
    var span = clamp(v.span, Math.min(MIN_VIEW_MS, duration), duration);
    return { start: clamp(v.start, 0, duration - span), span: span };
  };

  P.timeToX = function (t, width) {
    var v = this.timeView();
    return labelW + ((t - v.start) / v.span) * (width - labelW - PAD_R);
  };

  /** x 자리의 시간 (확대할수록 잘게 맞춘다). */
  P.xToTime = function (x, width) {
    var v = this.timeView();
    var t = v.start + (x - labelW) / (width - labelW - PAD_R) * v.span;
    var snap = v.span < 400 ? 1 : (v.span < 2000 ? 5 : 10);
    return clamp(Math.round(t / snap) * snap, 0, this.clip().duration);
  };

  /** 마우스 자리(p)의 시간을 그대로 두고 확대/축소. */
  P.timelineZoom = function (p, factor) {
    var duration = this.clip().duration;
    var v = this.timeView();
    var area = p.w - labelW - PAD_R;
    var anchor = v.start + clamp((p.x - labelW) / area, 0, 1) * v.span;
    var span = clamp(v.span / factor, Math.min(MIN_VIEW_MS, duration), duration);
    this.tl.view = span >= duration ? null : { start: anchor - (anchor - v.start) * span / v.span, span: span };
    this.drawTimeline();
  };

  /** 확대한 시간 막대를 좌우로 옮긴다 (한 번에 보이는 구간의 15%). */
  P.timelineScroll = function (direction) {
    if (!this.tl.view) {
      return;
    }
    var v = this.timeView();
    this.tl.view = { start: v.start + direction * v.span * 0.15, span: v.span };
    this.drawTimeline();
  };

  P.drawTimeline = function () {
    var canvas = this.tlCanvas;
    if (!canvas || canvas.clientWidth === 0) {
      return;
    }
    var c = BV.prepare(canvas);
    var ctx = c.ctx;
    var d = c.dpr;
    var w = c.w, h = c.h;
    var clip = this.clip();
    var self = this;
    // 고른 부위 줄이 보이게 넘긴다
    var allRows = this.timelineRows();
    var rowCount = this.timelineRowCount(h);
    var at = allRows.indexOf(this.selected);
    var maxOffset = Math.max(0, allRows.length - rowCount);
    if (at >= 0 && (at < this.tl.rowOffset || at >= this.tl.rowOffset + rowCount)) {
      this.tl.rowOffset = at < this.tl.rowOffset ? at : at - rowCount + 1;
    }
    this.tl.rowOffset = clamp(this.tl.rowOffset, 0, maxOffset);
    ctx.save();
    ctx.scale(d, d);
    // 이름 칸
    ctx.fillStyle = "#f6f1e7";
    ctx.fillRect(0, 0, labelW, h);
    // 시간 쪽은 보이는 구간만 그린다
    var view = this.timeView();
    var pxPerMs = (w - labelW - PAD_R) / view.span;
    ctx.save();
    ctx.beginPath();
    ctx.rect(labelW, 0, w - labelW, h);
    ctx.clip();
    // 눈금: 확대한 만큼 촘촘하게 (짧은 눈금은 6px, 숫자는 36px 이상 떨어지게)
    var minor = pickStep(TICK_STEPS, pxPerMs, 6);
    var major = pickStep(LABEL_STEPS, pxPerMs, 36);
    var decimals = major < 100 ? 2 : (major < 1000 ? 1 : 0);
    ctx.fillStyle = "#7d7468";
    ctx.font = "11px DalseoHealing, Consolas, monospace";
    for (var t = Math.ceil(view.start / minor) * minor; t <= Math.min(clip.duration, view.start + view.span) + 0.5; t += minor) {
      var x = Math.round(this.timeToX(t, w)) + 0.5;
      var isMajor = Math.round(t) % major === 0;
      ctx.fillRect(x - 0.5, isMajor ? 8 : 12, 1, isMajor ? 8 : 4);
      if (isMajor) {
        // 오른쪽 끝 숫자는 눈금 왼쪽에 써서 잘리지 않게
        var label = (t / 1000).toFixed(decimals);
        var tw = ctx.measureText(label).width;
        ctx.fillText(label, x + 2 + tw > w ? x - 2 - tw : x + 2, 9);
      }
    }
    // 확대했으면 전체 중 어디를 보고 있는지 눈금 아래에 막대로
    if (this.tl.view) {
      var area = w - labelW - PAD_R;
      ctx.fillStyle = "rgba(38, 34, 30, 0.35)";
      ctx.fillRect(labelW + view.start / clip.duration * area, RULER_H - 4, Math.max(4, view.span / clip.duration * area), 3);
    }
    ctx.restore();
    ctx.fillStyle = "#26221e";
    ctx.fillRect(0, RULER_H - 1, w, 1);
    // 부위 줄 (화면에 있는 부위, 많으면 이름 칸에서 휠로 위아래로 넘겨 본다)
    var rows = this.timelineRows();
    var offset = this.tl.rowOffset;
    rows.slice(offset, offset + this.timelineRowCount(h)).forEach(function (part, i) {
      var y = RULER_H + i * ROW_H;
      if (part === self.selected) {
        ctx.fillStyle = "rgba(79, 195, 247, 0.18)";
        ctx.fillRect(0, y, w, ROW_H);
      }
      ctx.fillStyle = "#e9e1d3";
      ctx.fillRect(labelW, y + ROW_H - 1, w - labelW, 1);
      ctx.fillStyle = part === self.selected ? "#26221e" : "#7d7468";
      ctx.font = (part === self.selected ? "bold " : "") + "11px DalseoHealing, 'Malgun Gothic', sans-serif";
      var label = BV.partName(self.layout, part);
      while (label.length > 1 && ctx.measureText(label).width > labelW - 8) {
        label = label.slice(0, -1);
      }
      ctx.fillText(label, 5, y + ROW_H - 3);
      // 키 사이를 선으로 이어서 움직이는 구간이 보이게 (시간 쪽 밖은 잘라낸다)
      ctx.save();
      ctx.beginPath();
      ctx.rect(labelW, y, w - labelW, ROW_H);
      ctx.clip();
      var keys = clip.tracks[part] || [];
      var cy = y + ROW_H / 2;
      if (keys.length > 1) {
        ctx.fillStyle = "#cfc7b8";
        ctx.fillRect(self.timeToX(keys[0].t, w), cy - 0.5, self.timeToX(keys[keys.length - 1].t, w) - self.timeToX(keys[0].t, w), 1);
      }
      keys.forEach(function (k) {
        var kx = self.timeToX(k.t, w);
        var current = part === self.selected && Math.abs(k.t - self.tl.playhead) < 1;
        ctx.fillStyle = current ? "#c8402f" : "#26221e";
        ctx.beginPath();
        if (k.ease) {
          // 부드럽게 = ◆
          ctx.moveTo(kx, cy - 4.5);
          ctx.lineTo(kx + 4.5, cy);
          ctx.lineTo(kx, cy + 4.5);
          ctx.lineTo(kx - 4.5, cy);
        } else {
          // 일정하게 = ■
          ctx.rect(kx - 3.5, cy - 3.5, 7, 7);
        }
        ctx.closePath();
        ctx.fill();
        if (k.photo) {
          // 사진 키: 오른쪽 위 작은 네모 (원래 사진으로 돌아가는 키는 빈 네모)
          ctx.fillStyle = k.photo === BV.BASE_PHOTO ? "#ffffff" : "#4fc3f7";
          ctx.fillRect(kx + 2, cy - 6, 5, 5);
          ctx.strokeStyle = "#26221e";
          ctx.lineWidth = 1;
          ctx.strokeRect(kx + 2.5, cy - 5.5, 4, 4);
        }
      });
      ctx.restore();
    });
    ctx.fillStyle = "#26221e";
    ctx.fillRect(labelW - 1, 0, 2, h);
    if (maxOffset > 0) {
      ctx.fillStyle = "#7d7468";
      ctx.font = "8px sans-serif";
      ctx.fillText(this.tl.rowOffset > 0 ? "▲" : "", labelW - 9, RULER_H + 7);
      ctx.fillText(this.tl.rowOffset < maxOffset ? "▼" : "", labelW - 9, h - 2);
    }
    ctx.save();
    ctx.beginPath();
    ctx.rect(labelW, 0, w - labelW, h);
    ctx.clip();
    // 재생 위치
    var px = Math.round(this.timeToX(this.tl.playhead, w)) + 0.5;
    ctx.fillStyle = "#c8402f";
    ctx.fillRect(px - 0.5, 0, 1.5, h);
    ctx.beginPath();
    ctx.moveTo(px - 5, 0);
    ctx.lineTo(px + 5, 0);
    ctx.lineTo(px, 6);
    ctx.closePath();
    ctx.fill();
    // 반복 안 함: 끝에 멈춤 표시
    if (!clip.loop) {
      ctx.fillStyle = "#7d7468";
      ctx.fillRect(Math.round(this.timeToX(clip.duration, w)) - 1, RULER_H, 2, h - RULER_H);
    }
    ctx.restore();
    ctx.restore();
  };

  /** 후보 중 화면에서 minPx 이상 떨어지는 가장 촘촘한 간격. */
  function pickStep(steps, pxPerMs, minPx) {
    for (var i = 0; i < steps.length; i++) {
      if (steps[i] * pxPerMs >= minPx) {
        return steps[i];
      }
    }
    return steps[steps.length - 1];
  }

  // ---------------------------------------------------------------- 시간 막대 마우스

  P.timelinePointer = function (event) {
    var rect = this.tlCanvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top, w: rect.width };
  };

  P.rowAt = function (y) {
    var rows = this.timelineRows();
    var i = Math.floor((y - RULER_H) / ROW_H) + this.tl.rowOffset;
    return y >= RULER_H && i >= 0 && i < rows.length ? rows[i] : null;
  };

  /** 시간 막대 줄 = 화면에 있는 부위 (부위 고르기 순서). */
  P.timelineRows = function () {
    return this.pickerKeys();
  };

  /** 부위 이름 칸 너비 (px). */
  P.timelineLabelWidth = function () {
    return labelW;
  };

  P.timelineRowCount = function (height) {
    return Math.max(1, Math.floor((height - RULER_H) / ROW_H));
  };

  /** 줄을 한 줄씩 위아래로 (이름 칸에서 휠). */
  P.timelineScrollRows = function (direction) {
    var max = Math.max(0, this.timelineRows().length - this.timelineRowCount(this.tlCanvas.clientHeight));
    this.tl.rowOffset = clamp(this.tl.rowOffset + direction, 0, max);
    this.drawTimeline();
  };

  P.timelineMouseDown = function (event) {
    event.preventDefault();
    this.flush();
    var p = this.timelinePointer(event);
    // 이름 칸과 시간 칸 사이 선: 끌어서 이름 칸 너비 바꾸기
    if (Math.abs(p.x - labelW) <= LABEL_GRAB) {
      this.tl.drag = { resizeLabel: true };
      return;
    }
    var part = this.rowAt(p.y);
    if (part && part !== this.selected) {
      this.select(part);
    }
    if (p.x < labelW) {
      return;
    }
    // 키를 눌렀으면 그 키로 가고, 끌어서 시간을 옮긴다
    if (part) {
      var keys = this.track(part);
      for (var i = 0; i < keys.length; i++) {
        if (Math.abs(this.timeToX(keys[i].t, p.w) - p.x) <= KEY_HIT) {
          this.tl.playhead = keys[i].t;
          this.tl.drag = { retime: keys[i], part: part, moved: false };
          this.refreshControls();
          this.draw();
          return;
        }
      }
    }
    // 빈 곳: 재생 위치 옮기기 (끌면 계속)
    this.tl.playing = false;
    this.tl.playhead = this.xToTime(p.x, p.w);
    this.tl.drag = { scrub: true };
    this.updateAnimation();
    this.refreshControls();
    this.draw();
  };

  P.timelineMouseMove = function (event) {
    var drag = this.tl && this.tl.drag;
    if (!drag) {
      return;
    }
    var p = this.timelinePointer(event);
    if (drag.resizeLabel) {
      labelW = clamp(Math.round(p.x), LABEL_MIN, Math.min(LABEL_MAX, p.w - 80));
      this.drawTimeline();
      return;
    }
    var t = this.xToTime(p.x, p.w);
    if (drag.scrub) {
      this.tl.playhead = t;
    } else if (drag.retime && drag.retime.t !== t) {
      // 같은 시간에 다른 키가 있으면 그 자리로는 못 간다
      var others = this.track(drag.part).filter(function (k) { return k !== drag.retime && Math.abs(k.t - t) < 1; });
      if (!others.length) {
        drag.retime.t = t;
        this.track(drag.part).sort(function (a, b) { return a.t - b.t; });
        this.tl.playhead = t;
        drag.moved = true;
        this.afterChange(drag.part);
      }
    }
    this.refreshControls();
    this.draw();
  };

  P.timelineMouseUp = function () {
    var drag = this.tl && this.tl.drag;
    if (!drag) {
      return;
    }
    this.tl.drag = null;
    if (drag.moved) {
      this.commit(0);
    }
  };

  /** 줄을 두 번 누르면 그 시간에 키를 넣는다. */
  P.timelineDoubleClick = function (event) {
    var p = this.timelinePointer(event);
    if (p.y < RULER_H) {
      // 눈금을 두 번 누르면 전체 보기
      this.tl.view = null;
      this.drawTimeline();
      return;
    }
    var part = this.rowAt(p.y);
    if (!part || p.x < labelW) {
      return;
    }
    this.tl.playhead = this.xToTime(p.x, p.w);
    this.ensureKey(part, this.tl.playhead);
    this.afterChange(part);
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  /** 고급에서 Ctrl 없는 키: Delete/Backspace = 지금 키 지우기, 스페이스 = 재생/멈춤. */
  P.timelineKey = function (event) {
    var active = document.activeElement;
    if (active && (active.tagName === "INPUT" || active.tagName === "SELECT")) {
      return;
    }
    if (event.key === "Delete" || event.key === "Backspace" || event.keyCode === 46 || event.keyCode === 8) {
      event.preventDefault();
      this.deleteKeyAtPlayhead();
    } else if (event.key === " " || event.keyCode === 32) {
      event.preventDefault();
      this.timelineTogglePlay();
    }
  };

  // ---------------------------------------------------------------- 미리보기에서 끌어서 키 만들기

  P.timelineStartDrag = function (target, p) {
    this.tl.playing = false;
    this.updateAnimation();
    var r = this.partRect(target.key, this.previewPose());
    var base = this.keyAtPlayhead(target.key) || this.clipValuesAt(target.key, this.tl.playhead);
    this.drag = {
      timeline: true, key: target.key, rotate: !!target.rotate, resize: !!target.resize,
      startX: p.x, startY: p.y, base: { dx: base.dx, dy: base.dy, rotation: base.rotation, scaleX: base.scaleX, scaleY: base.scaleY },
      cx: r.px, cy: r.py, angle: r.angle, w: Math.max(1, r.w), h: Math.max(1, r.h),
      startAngle: Math.atan2(p.y - r.py, p.x - r.px), moved: false
    };
  };

  P.timelineDragMove = function (p) {
    var drag = this.drag;
    var k = this.view.k;
    var b = drag.base;
    var values = { dx: b.dx, dy: b.dy, rotation: b.rotation, scaleX: b.scaleX, scaleY: b.scaleY };
    if (drag.rotate) {
      var turned = (Math.atan2(p.y - drag.cy, p.x - drag.cx) - drag.startAngle) * 180 / Math.PI;
      // 한 바퀴 넘게도 돌 수 있게 (앞의 값에서 가까운 쪽으로)
      turned = ((turned + 540) % 360) - 180;
      var angle = Math.round(b.rotation + turned);
      var snapped = Math.round(angle / ROTATE_SNAP) * ROTATE_SNAP;
      values.rotation = clamp(Math.abs(angle - snapped) <= ROTATE_SNAP_RANGE ? snapped : angle, -MAX_TURN, MAX_TURN);
    } else if (drag.resize) {
      // 마우스 움직임을 부위의 (돌린) 가로/세로 방향으로 나눠서 크기(%)를 바꾼다
      var sx = p.x - drag.startX, sy = p.y - drag.startY;
      var lx = sx * Math.cos(drag.angle) + sy * Math.sin(drag.angle);
      var ly = -sx * Math.sin(drag.angle) + sy * Math.cos(drag.angle);
      values.scaleX = clamp(Math.round(b.scaleX * (drag.w + lx) / drag.w), MIN_SCALE, MAX_SCALE);
      values.scaleY = clamp(Math.round(b.scaleY * (drag.h + ly) / drag.h), MIN_SCALE, MAX_SCALE);
    } else {
      values.dx = clamp(b.dx + Math.round((p.x - drag.startX) / k), -MAX_OFFSET, MAX_OFFSET);
      values.dy = clamp(b.dy + Math.round((p.y - drag.startY) / k), -MAX_OFFSET, MAX_OFFSET);
    }
    var changed = FIELDS.some(function (f) { return values[f] !== b[f]; });
    if (!changed && !drag.moved) {
      return; // 누르기만 하면 키를 만들지 않는다
    }
    var key = this.ensureKey(drag.key, this.tl.playhead);
    FIELDS.forEach(function (f) { key[f] = values[f]; });
    drag.moved = true;
    this.afterChange(drag.key);
    this.refreshControls();
    this.draw();
  };
})();
