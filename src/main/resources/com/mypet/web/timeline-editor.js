// 모습 꾸미기 → 고급: 영상 편집처럼 시간 막대에 장면(◆)을 찍는다. PartsEditor 에 기능을 더한다.
// 상황(항상/쉴 때/걸을 때/공중/잡힐 때)마다 클립 하나, 부위마다 키 줄 하나.
// 재생 위치(빨간 선)에서 비트를 끌거나(옮기기) 돌리거나(회전) 모서리를 끌거나(크기) 사진을 바꾸고 [◆ 추가]를 누르면
// 그 시간의 비트 모습 전체(모든 부위의 자리·크기·사진)가 장면 하나로 저장된다. 추가하기 전에는 미리보기에서만 바뀐다.
// 장면 사이는 부드럽게 이어지고(사진은 그 장면에서 딱 바뀜), 실제 비트는 그 상황이 시작될 때 0초부터 재생한다
// (이어서 = 멈췄던 데서 계속). 계산은 PartTimeline.java, bit.js clipAt / clipPhotoAt 과 같다.
(function () {
  "use strict";

  var P = window.PartsEditor.prototype;
  var BV = window.BitView;

  var STATE_LABELS = { always: "항상", idle: "쉴 때", walk: "걸을 때", air: "공중", grab: "잡힐 때" };
  var ROWS = ["body", "eyes", "leftArm", "rightArm", "leftLeg", "rightLeg"];
  var ROW_LABELS = { body: "몸통", eyes: "눈", leftArm: "왼팔", rightArm: "오른팔", leftLeg: "왼다리", rightLeg: "오른다리" };
  var MIRROR = { leftArm: "rightArm", rightArm: "leftArm", leftLeg: "rightLeg", rightLeg: "leftLeg" };
  var LABEL_W = 46, RULER_H = 16, ROW_H = 13, PAD_R = 8;
  var KEY_HIT = 6, MAX_KEYS = 60;
  var MIN_VIEW_MS = 100, TIME_ZOOM_STEP = 1.25; // 시간 막대를 가장 크게 확대하면 0.1초가 한 화면
  // 눈금 간격 후보 (ms): 확대할수록 촘촘한 눈금
  var TICK_STEPS = [5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000];
  var LABEL_STEPS = [10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000];
  var MAX_OFFSET = 200, MAX_TURN = 1080, MIN_SCALE = 10, MAX_SCALE = 400;
  var ROTATE_SNAP = 15, ROTATE_SNAP_RANGE = 4;
  var FIELDS = ["dx", "dy", "rotation", "scaleX", "scaleY"];

  function clamp(v, min, max) {
    return Math.max(min, Math.min(max, v));
  }

  // ---------------------------------------------------------------- 준비

  P.initTimeline = function () {
    var self = this;
    // view: 시간 막대에 보이는 구간 {start, span} (ms), null = 전체
    // drafts: 아직 [◆ 추가] 하지 않은 변경 ("상황@시간" → {state, t, parts: {부위: 값}})
    this.tl = { state: "idle", playhead: 0, playing: false, drag: null, view: null, drafts: {} };
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
    document.getElementById("tl-add").addEventListener("click", function () { self.addFrame(self.tl.playhead); });
    document.getElementById("tl-delete").addEventListener("click", function () { self.deleteFrame(); });
    // 사진 넣기/빼기: 지금 시간의 비트 모습에서 고른 부위 사진을 바꾼다 ([◆ 추가]로 저장)
    document.getElementById("tl-photo").addEventListener("click", function () {
      self.tl.pendingPhoto = { state: self.tl.state, t: self.tl.playhead, part: self.selected };
      self.send("pickKeyImage");
    });
    document.getElementById("tl-photo-remove").addEventListener("click", function () {
      self.setDraft(self.selected, { photo: BV.BASE_PHOTO, flip: false });
    });

    FIELDS.forEach(function (field) {
      var input = document.getElementById("tl-" + field);
      input.addEventListener("input", function () {
        var value = parseInt(input.value, 10);
        if (isNaN(value)) {
          return;
        }
        var change = {};
        change[field] = field === "rotation" ? clamp(value, -MAX_TURN, MAX_TURN)
          : (field === "scaleX" || field === "scaleY") ? clamp(value, MIN_SCALE, MAX_SCALE) : clamp(value, -MAX_OFFSET, MAX_OFFSET);
        self.setDraft(self.selected, change, true);
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
    // 사진 칸: 원래 사진 / 이미 쓴 사진 / 새 사진 고르기
    var keyPhoto = document.getElementById("tl-key-photo");
    keyPhoto.addEventListener("change", function () {
      var value = keyPhoto.value;
      if (value === "@pick") {
        self.tl.pendingPhoto = { state: self.tl.state, t: self.tl.playhead, part: self.selected };
        self.send("pickKeyImage");
        self.refreshControls();
        return;
      }
      self.setDraft(self.selected, { photo: value || BV.BASE_PHOTO, flip: false });
    });
    // 부드럽게: 지금 장면의 키 전체
    document.getElementById("tl-ease").addEventListener("change", function (event) {
      var keys = self.frameKeys(self.tl.playhead);
      if (keys.length) {
        keys.forEach(function (k) { k.ease = event.target.checked; });
        self.draw();
        self.commit(0);
      }
    });

    this.tlCanvas.addEventListener("mousedown", function (event) { self.timelineMouseDown(event); });
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
      if (event.shiftKey || Math.abs(dx) > Math.abs(dy)) {
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

  /** 지금 고른 상황의 클립. */
  P.clip = function () {
    return this.layout.timeline[this.tl.state];
  };

  P.track = function (part) {
    return this.clip().tracks[part] || [];
  };

  P.keyAt = function (part, t) {
    var keys = this.track(part);
    for (var i = 0; i < keys.length; i++) {
      if (Math.abs(keys[i].t - t) < 1) {
        return keys[i];
      }
    }
    return null;
  };

  P.keyAtPlayhead = function (part) {
    return this.keyAt(part, this.tl.playhead);
  };

  /** t 시간 장면의 키들 (모든 부위). */
  P.frameKeys = function (t) {
    var self = this;
    var keys = [];
    ROWS.forEach(function (part) {
      var key = self.keyAt(part, t);
      if (key) {
        keys.push(key);
      }
    });
    return keys;
  };

  // ---------------------------------------------------------------- 지금 시간의 비트 모습 (추가 전 변경 포함)

  function draftId(state, t) {
    return state + "@" + Math.round(t);
  }

  /** 지금 상황·시간의 아직 추가 안 한 변경 (없으면 null). */
  P.draftHere = function () {
    return this.tl.drafts[draftId(this.tl.state, this.tl.playhead)] || null;
  };

  /** 클립에 저장된 t 시간의 부위 모양과 사진 (키 사이는 이어진 값). */
  P.savedValuesAt = function (part, t) {
    var values = BV.newKey(t);
    var c = BV.clipAt(this.clip(), part, t);
    if (c) {
      values.dx = Math.round(c.dx);
      values.dy = Math.round(c.dy);
      values.rotation = Math.round(c.rotation);
      values.scaleX = Math.round(c.scaleX * 100);
      values.scaleY = Math.round(c.scaleY * 100);
    }
    var photo = BV.clipPhotoAt(this.clip(), part, t);
    values.photo = photo ? photo.name : BV.BASE_PHOTO;
    values.flip = photo ? photo.flip : false;
    return values;
  };

  /** 지금 시간의 부위 모양과 사진: 추가 안 한 변경이 있으면 그것, 아니면 저장된 값. */
  P.valuesAt = function (part) {
    var draft = this.draftHere();
    if (draft && draft.parts[part]) {
      return draft.parts[part];
    }
    return this.savedValuesAt(part, this.tl.playhead);
  };

  /** 지금 시간의 비트 모습을 바꾼다 ([◆ 추가] 전까지는 미리보기에서만). 좌우 맞추기면 반대쪽도 거울처럼. */
  P.setDraftAt = function (state, t, part, change) {
    var id = draftId(state, t);
    var draft = this.tl.drafts[id] || (this.tl.drafts[id] = { state: state, t: Math.round(t), parts: {} });
    var stateBefore = this.tl.state, timeBefore = this.tl.playhead;
    this.tl.state = state;
    this.tl.playhead = t;
    var values = draft.parts[part] || JSON.parse(JSON.stringify(this.valuesAt(part)));
    Object.keys(change).forEach(function (f) { values[f] = change[f]; });
    draft.parts[part] = values;
    var mirror = MIRROR[part];
    if (mirror && this.layout.symmetric) {
      var m = JSON.parse(JSON.stringify(values));
      m.dx = -values.dx;
      m.rotation = -values.rotation;
      m.flip = values.photo !== BV.BASE_PHOTO && !values.flip;
      draft.parts[mirror] = m;
    }
    this.tl.state = stateBefore;
    this.tl.playhead = timeBefore;
  };

  P.setDraft = function (part, change, typing) {
    this.setDraftAt(this.tl.state, this.tl.playhead, part, change);
    if (!typing) {
      this.refreshControls();
    } else {
      this.refreshTimelineButtons();
    }
    this.draw();
  };

  /** (Java 가 부름) 사진을 골랐다: 고를 때의 상황·시간·부위 모습에 넣는다. 취소면 name = null. */
  P.onPickedImage = function (name) {
    var pending = this.tl.pendingPhoto;
    this.tl.pendingPhoto = null;
    if (pending && name) {
      this.setDraftAt(pending.state, pending.t, pending.part, { photo: name, flip: false });
      this.send("requestImage", name);
    }
    this.refreshControls();
    this.draw();
  };

  /** 고른 부위의 키에 쓰인 사진들 (모든 상황, 순서대로) — 사진 칸에서 다시 고를 수 있게. */
  P.keyPhotos = function (part) {
    var names = [];
    var add = function (name) {
      if (name && name !== BV.BASE_PHOTO && names.indexOf(name) < 0) {
        names.push(name);
      }
    };
    var timeline = this.layout.timeline;
    BV.STATES.forEach(function (state) {
      (timeline[state].tracks[part] || []).forEach(function (k) { add(k.photo); });
    });
    var drafts = this.tl.drafts;
    Object.keys(drafts).forEach(function (id) {
      var values = drafts[id].parts[part];
      add(values && values.photo);
    });
    return names;
  };

  // ---------------------------------------------------------------- 장면 추가/빼기

  /** t 시간에 지금 비트 모습 전체(모든 부위의 자리·크기·사진)를 장면으로 저장한다. */
  P.addFrame = function (t) {
    var self = this;
    var timeBefore = this.tl.playhead;
    this.tl.playhead = t;
    var values = {};
    ROWS.forEach(function (part) {
      values[part] = JSON.parse(JSON.stringify(self.valuesAt(part)));
    });
    var clip = this.clip();
    ROWS.forEach(function (part) {
      var key = self.keyAt(part, t);
      if (!key) {
        key = BV.newKey(Math.round(t));
        var keys = clip.tracks[part] = (clip.tracks[part] || []).concat([key]);
        keys.sort(function (a, b) { return a.t - b.t; });
        if (keys.length > MAX_KEYS) {
          keys.splice(keys.indexOf(key) === 0 ? 1 : 0, 1);
        }
      }
      var v = values[part];
      FIELDS.forEach(function (f) { key[f] = v[f]; });
      key.photo = v.photo || BV.BASE_PHOTO;
      key.flip = !!v.flip && key.photo !== BV.BASE_PHOTO;
    });
    delete this.tl.drafts[draftId(this.tl.state, t)];
    this.tl.playhead = timeBefore;
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  /** 지금 시간의 장면을 뺀다 (추가 안 한 변경만 있으면 그 변경을 버린다). */
  P.deleteFrame = function () {
    var self = this;
    var t = this.tl.playhead;
    var id = draftId(this.tl.state, t);
    var hadDraft = !!this.tl.drafts[id];
    delete this.tl.drafts[id];
    var clip = this.clip();
    var removed = false;
    ROWS.forEach(function (part) {
      var key = self.keyAt(part, t);
      if (!key) {
        return;
      }
      removed = true;
      var keys = clip.tracks[part].filter(function (k) { return k !== key; });
      if (keys.length) {
        clip.tracks[part] = keys;
      } else {
        delete clip.tracks[part];
      }
    });
    if (removed || hadDraft) {
      this.refreshControls();
      this.draw();
    }
    if (removed) {
      this.commit(0);
    }
  };

  // ---------------------------------------------------------------- 미리보기

  function asTransform(v) {
    return { dx: v.dx, dy: v.dy, rotation: v.rotation, scaleX: v.scaleX / 100, scaleY: v.scaleY / 100 };
  }

  /** 고른 상황이 playhead 만큼 지났을 때 부위 모양 (일반 애니메이션 + "항상" 클립 + 이 상황의 모습). */
  P.timelineTransformAt = function (part) {
    var state = this.tl.state;
    var weights = {};
    if (state !== "always") {
      weights[state] = 1;
    }
    var ticks = Math.round(this.tl.playhead * 64 / 1000);
    var t = BV.partTransform(this.layout[part], ticks, weights);
    if (state !== "always") {
      var always = BV.clipAt(this.layout.timeline.always, part, this.tl.playhead);
      if (always) {
        t = BV.combineTransforms(t, always);
      }
    }
    var draft = this.draftHere();
    if (draft && draft.parts[part]) {
      return BV.combineTransforms(t, asTransform(draft.parts[part]));
    }
    var saved = BV.clipAt(this.clip(), part, this.tl.playhead);
    return saved ? BV.combineTransforms(t, saved) : t;
  };

  /** 고른 상황이 playhead 만큼 지났을 때 부위 사진 (추가 안 한 변경 포함, 실제 비트와 같은 계산). */
  P.timelinePhotoAt = function (part) {
    var draft = this.draftHere();
    if (draft && draft.parts[part]) {
      return { name: draft.parts[part].photo, flip: !!draft.parts[part].flip };
    }
    var state = this.tl.state;
    var weights = {};
    if (state !== "always") {
      weights[state] = 1;
    }
    var elapsed = { always: this.tl.playhead };
    elapsed[state] = this.tl.playhead;
    return BV.timelinePhoto(this.layout.timeline, part, weights, elapsed);
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

  /** 버튼만 (숫자 칸을 고치는 중에도 부를 수 있게). */
  P.refreshTimelineButtons = function () {
    var draft = this.draftHere();
    var frame = this.frameKeys(this.tl.playhead).length > 0;
    var add = document.getElementById("tl-add");
    add.classList.toggle("btn-primary", !!draft);
    add.textContent = draft ? "◆ 추가 *" : "◆ 추가";
    add.title = draft ? "바꾼 모습을 이 시간 장면으로 저장 (아직 저장 안 됨)" : "지금 비트 모습을 이 시간 장면으로 저장";
    document.getElementById("tl-delete").disabled = !frame && !draft;
    var hasPhoto = this.valuesAt(this.selected).photo !== BV.BASE_PHOTO;
    document.getElementById("tl-photo").textContent = hasPhoto ? "사진 바꾸기" : "사진 넣기";
    document.getElementById("tl-photo-remove").disabled = !hasPhoto;
  };

  P.refreshTimelineControls = function () {
    var self = this;
    var tl = this.tl;
    var clip = this.clip();
    Array.prototype.forEach.call(document.querySelectorAll("#tl-states .seg"), function (button) {
      var state = button.getAttribute("data-key");
      button.classList.toggle("selected", state === tl.state);
      // 장면이 있는 상황은 ◆ 표시
      var hasKeys = Object.keys(self.layout.timeline[state].tracks).some(function (part) {
        return self.layout.timeline[state].tracks[part].length > 0;
      });
      button.textContent = STATE_LABELS[state] + (hasKeys ? " ◆" : "");
    });
    document.getElementById("tl-play").textContent = tl.playing ? "❚❚" : "▶";
    var values = this.valuesAt(this.selected);
    FIELDS.forEach(function (field) {
      var input = document.getElementById("tl-" + field);
      if (document.activeElement !== input) {
        input.value = values[field];
      }
    });
    var duration = document.getElementById("tl-duration");
    if (document.activeElement !== duration) {
      duration.value = (clip.duration / 1000).toFixed(1);
    }
    document.getElementById("tl-loop").checked = clip.loop;
    document.getElementById("tl-continue").checked = !clip.restart;
    // 사진 칸
    var keyPhoto = document.getElementById("tl-key-photo");
    if (document.activeElement !== keyPhoto) {
      var options = [[BV.BASE_PHOTO, "원래 사진"]];
      this.keyPhotos(this.selected).forEach(function (name, i) {
        options.push([name, "사진 " + (i + 1)]);
      });
      options.push(["@pick", "새 사진…"]);
      keyPhoto.innerHTML = "";
      options.forEach(function (o) {
        var option = document.createElement("option");
        option.value = o[0];
        option.textContent = o[1];
        keyPhoto.appendChild(option);
      });
      keyPhoto.value = values.photo || BV.BASE_PHOTO;
    }
    var frame = this.frameKeys(tl.playhead);
    var ease = document.getElementById("tl-ease");
    ease.checked = frame.length ? frame[0].ease : true;
    ease.disabled = !frame.length;
    this.refreshTimelineButtons();
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
    return LABEL_W + ((t - v.start) / v.span) * (width - LABEL_W - PAD_R);
  };

  /** x 자리의 시간 (확대할수록 잘게 맞춘다). */
  P.xToTime = function (x, width) {
    var v = this.timeView();
    var t = v.start + (x - LABEL_W) / (width - LABEL_W - PAD_R) * v.span;
    var snap = v.span < 400 ? 1 : (v.span < 2000 ? 5 : 10);
    return clamp(Math.round(t / snap) * snap, 0, this.clip().duration);
  };

  /** 마우스 자리(p)의 시간을 그대로 두고 확대/축소. */
  P.timelineZoom = function (p, factor) {
    var duration = this.clip().duration;
    var v = this.timeView();
    var area = p.w - LABEL_W - PAD_R;
    var anchor = v.start + clamp((p.x - LABEL_W) / area, 0, 1) * v.span;
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

  function diamond(ctx, kx, cy, r) {
    ctx.beginPath();
    ctx.moveTo(kx, cy - r);
    ctx.lineTo(kx + r, cy);
    ctx.lineTo(kx, cy + r);
    ctx.lineTo(kx - r, cy);
    ctx.closePath();
  }

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
    ctx.save();
    ctx.scale(d, d);
    // 이름 칸
    ctx.fillStyle = "#f6f1e7";
    ctx.fillRect(0, 0, LABEL_W, h);
    // 시간 쪽은 보이는 구간만 그린다
    var view = this.timeView();
    var pxPerMs = (w - LABEL_W - PAD_R) / view.span;
    ctx.save();
    ctx.beginPath();
    ctx.rect(LABEL_W, 0, w - LABEL_W, h);
    ctx.clip();
    // 눈금: 확대한 만큼 촘촘하게 (짧은 눈금은 6px, 숫자는 36px 이상 떨어지게)
    var minor = pickStep(TICK_STEPS, pxPerMs, 6);
    var major = pickStep(LABEL_STEPS, pxPerMs, 36);
    var decimals = major < 100 ? 2 : (major < 1000 ? 1 : 0);
    ctx.fillStyle = "#7d7468";
    ctx.font = "9px Consolas, monospace";
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
      var area = w - LABEL_W - PAD_R;
      ctx.fillStyle = "rgba(38, 34, 30, 0.35)";
      ctx.fillRect(LABEL_W + view.start / clip.duration * area, RULER_H - 4, Math.max(4, view.span / clip.duration * area), 3);
    }
    ctx.restore();
    ctx.fillStyle = "#26221e";
    ctx.fillRect(0, RULER_H - 1, w, 1);
    // 이 상황의 추가 안 한 변경
    var drafts = Object.keys(this.tl.drafts).map(function (id) { return self.tl.drafts[id]; })
      .filter(function (draft) { return draft.state === self.tl.state; });
    // 부위 줄
    ROWS.forEach(function (part, i) {
      var y = RULER_H + i * ROW_H;
      if (part === self.selected) {
        ctx.fillStyle = "rgba(79, 195, 247, 0.18)";
        ctx.fillRect(0, y, w, ROW_H);
      }
      ctx.fillStyle = "#e9e1d3";
      ctx.fillRect(LABEL_W, y + ROW_H - 1, w - LABEL_W, 1);
      ctx.fillStyle = part === self.selected ? "#26221e" : "#7d7468";
      ctx.font = (part === self.selected ? "bold " : "") + "10px 'Malgun Gothic', sans-serif";
      ctx.fillText(ROW_LABELS[part], 5, y + ROW_H - 3);
      // 키 사이를 선으로 이어서 움직이는 구간이 보이게 (시간 쪽 밖은 잘라낸다)
      ctx.save();
      ctx.beginPath();
      ctx.rect(LABEL_W, y, w - LABEL_W, ROW_H);
      ctx.clip();
      var keys = clip.tracks[part] || [];
      var cy = y + ROW_H / 2;
      if (keys.length > 1) {
        ctx.fillStyle = "#cfc7b8";
        ctx.fillRect(self.timeToX(keys[0].t, w), cy - 0.5, self.timeToX(keys[keys.length - 1].t, w) - self.timeToX(keys[0].t, w), 1);
      }
      keys.forEach(function (k) {
        var kx = self.timeToX(k.t, w);
        var current = Math.abs(k.t - self.tl.playhead) < 1;
        ctx.fillStyle = current ? "#c8402f" : "#26221e";
        if (k.ease) {
          // 부드럽게 = ◆
          diamond(ctx, kx, cy, 4.5);
        } else {
          // 일정하게 = ■
          ctx.beginPath();
          ctx.rect(kx - 3.5, cy - 3.5, 7, 7);
        }
        ctx.fill();
        if (k.photo && k.photo !== BV.BASE_PHOTO) {
          // 사진이 바뀐 장면: 오른쪽 위 작은 파란 네모
          ctx.fillStyle = "#4fc3f7";
          ctx.fillRect(kx + 2, cy - 6, 5, 5);
          ctx.strokeStyle = "#26221e";
          ctx.lineWidth = 1;
          ctx.strokeRect(kx + 2.5, cy - 5.5, 4, 4);
        }
      });
      // 추가 안 한 변경: 주황 빈 ◇
      drafts.forEach(function (draft) {
        if (!draft.parts[part]) {
          return;
        }
        ctx.strokeStyle = "#e08a1e";
        ctx.lineWidth = 1.5;
        diamond(ctx, self.timeToX(draft.t, w), cy, 4.5);
        ctx.stroke();
      });
      ctx.restore();
    });
    ctx.fillStyle = "#26221e";
    ctx.fillRect(LABEL_W - 1, 0, 1, h);
    ctx.save();
    ctx.beginPath();
    ctx.rect(LABEL_W, 0, w - LABEL_W, h);
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
    var i = Math.floor((y - RULER_H) / ROW_H);
    return i >= 0 && i < ROWS.length ? ROWS[i] : null;
  };

  P.timelineMouseDown = function (event) {
    event.preventDefault();
    this.flush();
    var p = this.timelinePointer(event);
    var part = this.rowAt(p.y);
    if (part && part !== this.selected) {
      this.select(part);
    }
    if (p.x < LABEL_W) {
      return;
    }
    // ◆를 눌렀으면 그 장면으로 가고, 끌면 장면(모든 부위 키)의 시간을 옮긴다
    if (part) {
      var keys = this.track(part);
      for (var i = 0; i < keys.length; i++) {
        if (Math.abs(this.timeToX(keys[i].t, p.w) - p.x) <= KEY_HIT) {
          this.tl.playhead = keys[i].t;
          this.tl.drag = { frame: this.frameKeys(keys[i].t), from: keys[i].t, moved: false };
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
    var self = this;
    var drag = this.tl && this.tl.drag;
    if (!drag) {
      return;
    }
    var p = this.timelinePointer(event);
    var t = this.xToTime(p.x, p.w);
    if (drag.scrub) {
      this.tl.playhead = t;
    } else if (drag.frame && drag.frame[0].t !== t) {
      // 옮길 자리에 다른 장면이 있으면 못 간다
      var blocked = ROWS.some(function (part) {
        return self.track(part).some(function (k) { return drag.frame.indexOf(k) < 0 && Math.abs(k.t - t) < 1; });
      });
      if (!blocked) {
        drag.frame.forEach(function (k) { k.t = t; });
        ROWS.forEach(function (part) {
          self.track(part).sort(function (a, b) { return a.t - b.t; });
        });
        this.tl.playhead = t;
        drag.moved = true;
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
      // 옮긴 장면에 추가 안 한 변경이 있었으면 같이 옮긴다
      var from = draftId(this.tl.state, drag.from);
      var to = draftId(this.tl.state, drag.frame[0].t);
      if (this.tl.drafts[from] && !this.tl.drafts[to]) {
        this.tl.drafts[to] = this.tl.drafts[from];
        this.tl.drafts[to].t = drag.frame[0].t;
        delete this.tl.drafts[from];
      }
      this.commit(0);
    }
  };

  /** 줄을 두 번 누르면 그 시간에 지금 비트 모습을 장면으로 추가한다. */
  P.timelineDoubleClick = function (event) {
    var p = this.timelinePointer(event);
    if (p.y < RULER_H) {
      // 눈금을 두 번 누르면 전체 보기
      this.tl.view = null;
      this.drawTimeline();
      return;
    }
    var part = this.rowAt(p.y);
    if (!part || p.x < LABEL_W) {
      return;
    }
    this.tl.playhead = this.xToTime(p.x, p.w);
    this.addFrame(this.tl.playhead);
  };

  /** 고급에서 Ctrl 없는 키: Delete/Backspace = 지금 장면 빼기, 스페이스 = 재생/멈춤, Enter = 장면 추가. */
  P.timelineKey = function (event) {
    var active = document.activeElement;
    if (active && (active.tagName === "INPUT" || active.tagName === "SELECT")) {
      return;
    }
    if (event.key === "Delete" || event.key === "Backspace" || event.keyCode === 46 || event.keyCode === 8) {
      event.preventDefault();
      this.deleteFrame();
    } else if (event.key === " " || event.keyCode === 32) {
      event.preventDefault();
      this.timelineTogglePlay();
    } else if (event.key === "Enter" || event.keyCode === 13) {
      event.preventDefault();
      this.addFrame(this.tl.playhead);
    }
  };

  // ---------------------------------------------------------------- 미리보기에서 끌어서 모습 바꾸기

  P.timelineStartDrag = function (target, p) {
    this.tl.playing = false;
    this.updateAnimation();
    var r = this.partRect(target.key, this.previewPose());
    var base = this.valuesAt(target.key);
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
      return; // 누르기만 하면 바꾸지 않는다
    }
    // 저장은 [◆ 추가] 때: 여기서는 미리보기 모습만 바꾼다 (끝나도 commit 하지 않게 moved 는 두지 않는다)
    drag.edited = true;
    this.setDraft(drag.key, values);
  };
})();
