// 비트 그리기 + 걷기/점프 애니메이션 (canvas).
// 모습 꾸미기 배치(layout)대로 그린다: 부위마다 사진이 있으면 사진, 없으면 몸 색 네모 + 외곽선.
// 치수와 움직임 값은 Java 쪽 PartLayout.java / PetApp.java 와 같다. (단위: 화면 px, 몸통 기본 위치 왼쪽 위가 0,0)
(function () {
  "use strict";

  // 기본 비트 배치 (PartLayout.defaults 와 같다)
  var DEFAULT_LAYOUT = {
    symmetric: true,
    body: { x: 0, y: 0, w: 44, h: 28, rotation: 0, image: null, flip: false },
    leftArm: { x: -9, y: 14, w: 8, h: 8, rotation: 0, image: null, flip: false },
    rightArm: { x: 45, y: 14, w: 8, h: 8, rotation: 0, image: null, flip: false },
    leftLeg: { x: 10, y: 29, w: 8, h: 8, rotation: 0, image: null, flip: false },
    rightLeg: { x: 25, y: 29, w: 8, h: 8, rotation: 0, image: null, flip: false },
    eyes: { x: 14, y: 12, w: 18, h: 6, rotation: 0, images: { neutral: null, grab: null, fall: null } }
  };
  var PART_KEYS = ["body", "leftArm", "rightArm", "leftLeg", "rightLeg", "eyes"];
  var DEFAULT_EYES = { x: 14, y: 12, w: 18, h: 6 };
  var ARM_RAISE = 8; // 점프할 때 팔을 드는 높이
  var EYE = { scale: 28 / 96, offsetX: 8 - 2.5, stroke: 2 };

  // 부위 사진 (파일 이름 → 불러온 Image). settings.js 가 채운다.
  var images = {};

  // 64Hz 틱 기준 값 (파이썬 버전이 실제로 돌던 속도)
  var TICK_MS = 1000 / 64;
  var FRAME_MS = 10; // 로직 내부 시간 단위
  var GRAVITY = 0.65, MAX_FALL_SPEED = 30, GRAVITY_RAMP_MS = 480;
  var JUMP_VELOCITY = 9.5, JUMP_COUNT = 2;
  var BOUNCE_MIN_FALL_PX = 65, BOUNCE_HEIGHT_GAIN = 0.5, BOUNCE_MAX_SPEED = 20, BOUNCE_MIN_SPEED = 1.2;
  var WALK_BOB_MAX = 8 * 0.38, WALK_BOB_SMOOTHING = 0.28;
  var LIMB_REACT_GAIN = 0.6, LIMB_REACT_MAX_X = 8 * 0.6, LIMB_REACT_MAX_Y = 8 * 0.45, LIMB_REACT_SMOOTHING = 0.35;
  var LIMB_SWAY_MAX_X = 8 * 0.225, LIMB_SWAY_SPEED_SCALE = 0.11, LIMB_SWAY_SPEED_MIN = 0.018, LIMB_SWAY_SPEED_MAX = 0.09;
  var LIMB_SWAY_SMOOTHING = 0.18, ARM_POSE_SMOOTHING = 0.35;
  var EYE_LOOK_MAX = 4, EYE_LOOK_GAIN = 4.2, EYE_LOOK_SMOOTHING = 0.35;

  function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }

  function copyLayout(layout) {
    var copy = JSON.parse(JSON.stringify(layout || DEFAULT_LAYOUT));
    PART_KEYS.forEach(function (key) {
      normalizePart(key, copy[key]);
    });
    return copy;
  }

  // ---------------------------------------------------------------- 부위 애니메이션 (PartAnimation.java, PartMotion.java 와 같은 계산)

  var STATES = ["always", "idle", "walk", "air", "grab"];
  var MOTION_TICKS_PER_SECOND = 64, ANIMATION_FADE = 0.15, BLINK_SECONDS = 0.18;
  // 부위마다 넣을 수 있는 움직임 (PartMotion.typesFor)
  var MOTION_TYPES_FOR = {
    eyes: ["none", "blink", "sway", "bob"],
    body: ["none", "breathe", "bob", "sway", "swing"],
    limb: ["none", "swing", "bob", "sway"]
  };

  function motionTypesFor(key) {
    return MOTION_TYPES_FOR[key] || MOTION_TYPES_FOR.limb;
  }

  /** 부위의 기본 중심점: 팔은 몸 쪽 가장자리, 다리는 위쪽, 나머지는 가운데 (PartLayout.defaultPivot). */
  function defaultPivot(key) {
    return { leftArm: "right", rightArm: "left", leftLeg: "top", rightLeg: "top" }[key] || "center";
  }

  function defaultMotion() {
    return { type: "none", size: 5, speed: 5, alternate: false, flip: false, half: false };
  }

  function normalizeMotion(motion) {
    var m = defaultMotion();
    if (motion) {
      Object.keys(m).forEach(function (key) {
        if (motion[key] !== undefined && motion[key] !== null) {
          m[key] = motion[key];
        }
      });
    }
    return m;
  }

  function emptyAnimation() {
    return { dx: 0, dy: 0, rotation: 0, motion: defaultMotion() };
  }

  /** 빠진 값(중심점, 상황별 애니메이션)을 채운다. 예전 미리보기의 part.motion 은 그 "언제" 상황으로 옮긴다. */
  function normalizePart(key, part) {
    part.rotation = part.rotation || 0;
    part.pivot = part.pivot || defaultPivot(key);
    var animations = part.animations || {};
    if (!part.animations && part.motion && part.motion.type) {
      var state = STATES.indexOf(part.motion.when) >= 0 ? part.motion.when : "always";
      animations[state] = { dx: 0, dy: 0, rotation: 0, motion: part.motion };
    }
    delete part.motion;
    var allowed = motionTypesFor(key);
    part.animations = {};
    STATES.forEach(function (state) {
      var a = animations[state] || emptyAnimation();
      var m = normalizeMotion(a.motion);
      if (allowed.indexOf(m.type) < 0) {
        m.type = "none";
      }
      part.animations[state] = { dx: a.dx || 0, dy: a.dy || 0, rotation: a.rotation || 0, motion: m };
    });
    return part;
  }

  function motionActive(m) {
    return !!m && m.type !== "none";
  }

  function animationEmpty(a) {
    return !a.dx && !a.dy && !a.rotation && !motionActive(a.motion);
  }

  function partAnimated(part) {
    return STATES.some(function (state) {
      return part.animations && !animationEmpty(part.animations[state]);
    });
  }

  /** 좌우 맞추기: 반대쪽 부위에 넣을 움직임 (PartMotion.mirrored). */
  function mirroredMotion(m) {
    var copy = normalizeMotion(m);
    copy.flip = !copy.flip;
    copy.half = copy.alternate !== !!(m && m.half);
    return copy;
  }

  function normalizeRotation(degrees) {
    var r = ((Math.round(degrees) % 360) + 360) % 360;
    return r > 180 ? r - 360 : r;
  }

  /** 좌우 맞추기: 반대쪽 부위에 넣을 애니메이션 (PartAnimation.mirrored). */
  function mirroredAnimation(a) {
    return { dx: -(a.dx || 0), dy: a.dy || 0, rotation: normalizeRotation(-(a.rotation || 0)), motion: mirroredMotion(a.motion) };
  }

  function mirroredPivot(pivot) {
    return pivot === "left" ? "right" : pivot === "right" ? "left" : pivot;
  }

  var NO_MOTION = { dx: 0, dy: 0, rotation: 0, scaleX: 1, scaleY: 1 };

  /** ticks 시점의 반복 움직임 모양 (PartMotion.at). */
  function motionTransform(m, ticks, weight) {
    if (!motionActive(m) || !(weight > 0)) {
      return NO_MOTION;
    }
    var seconds = ticks / MOTION_TICKS_PER_SECOND;
    var hz = 0.15 * m.speed;
    var angle = 2 * Math.PI * (hz * seconds + (m.half ? 0.5 : 0));
    var direction = m.flip ? -1 : 1;
    var wave = Math.sin(angle);
    var rise = (1 - Math.cos(angle)) / 2;
    switch (m.type) {
      case "swing": return { dx: 0, dy: 0, rotation: direction * m.size * 3 * wave * weight, scaleX: 1, scaleY: 1 };
      case "bob": return { dx: 0, dy: -m.size * rise * weight, rotation: 0, scaleX: 1, scaleY: 1 };
      case "sway": return { dx: direction * m.size * wave * weight, dy: 0, rotation: 0, scaleX: 1, scaleY: 1 };
      case "breathe": {
        var scale = 1 + m.size * 0.02 * rise * weight;
        return { dx: 0, dy: 0, rotation: 0, scaleX: scale, scaleY: scale };
      }
      case "blink": {
        var interval = 7 - m.speed * 0.6;
        var into = (seconds + (m.half ? interval / 2 : 0)) % interval;
        if (into >= BLINK_SECONDS) {
          return NO_MOTION;
        }
        var closed = Math.sin(Math.PI * into / BLINK_SECONDS) * (0.5 + m.size * 0.05);
        return { dx: 0, dy: 0, rotation: 0, scaleX: 1, scaleY: 1 - closed * weight };
      }
    }
    return NO_MOTION;
  }

  /** 상황별 켜짐 정도로 섞은 부위의 지금 모양 (PartAnimation.transform). "항상"은 자세 없이 움직임만. */
  function partTransform(part, ticks, weights) {
    if (!part.animations) {
      return NO_MOTION;
    }
    var t = { dx: 0, dy: 0, rotation: 0, scaleX: 1, scaleY: 1 };
    STATES.forEach(function (state) {
      var a = part.animations[state];
      var weight = state === "always" ? 1 : (weights[state] || 0);
      if (!a || weight <= 0) {
        return;
      }
      if (state !== "always") {
        t.dx += a.dx * weight;
        t.dy += a.dy * weight;
        t.rotation += a.rotation * weight;
      }
      var m = motionTransform(a.motion, ticks, weight);
      t.dx += m.dx;
      t.dy += m.dy;
      t.rotation += m.rotation;
      t.scaleX *= m.scaleX;
      t.scaleY *= m.scaleY;
    });
    return t;
  }

  /** 시간과 상황별 켜짐 정도를 들고 다니는 시계. tick(state) 를 64Hz 로 부른다 (PartAnimation.fadeWeights). */
  function MotionClock() {
    this.ticks = 0;
    this.weights = {};
  }

  MotionClock.prototype.tick = function (state) {
    var self = this;
    this.ticks += 1;
    STATES.forEach(function (s) {
      if (s === "always") {
        return;
      }
      var target = s === state ? 1 : 0;
      var w = self.weights[s] || 0;
      w += (target - w) * ANIMATION_FADE;
      if (Math.abs(target - w) < 0.01) {
        w = target;
      }
      self.weights[s] = w;
    });
  };

  MotionClock.prototype.transform = function (layout, key) {
    return partTransform(layout[key], this.ticks, this.weights);
  };

  function newPose() {
    return { x: 0, y: 0, bob: 0, limbX: 0, limbY: 0, swayX: 0, armLift: 0, eyeShift: 0, falling: false, expression: null };
  }

  /** 돌린 부위를 감싸는 사각형 (PartLayout.bounds 와 같은 계산). */
  function partBox(part) {
    var half = halfExtents(part);
    var cx = part.x + part.w / 2, cy = part.y + part.h / 2;
    return {
      minX: Math.floor(cx - half[0] + 1e-6), maxX: Math.ceil(cx + half[0] - 1e-6),
      minY: Math.floor(cy - half[1] + 1e-6), maxY: Math.ceil(cy + half[1] - 1e-6)
    };
  }

  function halfExtents(part) {
    var rad = (part.rotation || 0) * Math.PI / 180;
    var cos = Math.abs(Math.cos(rad)), sin = Math.abs(Math.sin(rad));
    return [part.w / 2 * cos + part.h / 2 * sin, part.w / 2 * sin + part.h / 2 * cos];
  }

  /** 중심점 (몸통 기준 좌표, 부위 회전까지 반영: PartLayout.Part.pivotPoint). */
  function pivotPoint(part) {
    var ox = part.pivot === "left" ? -part.w / 2 : part.pivot === "right" ? part.w / 2 : 0;
    var oy = part.pivot === "top" ? -part.h / 2 : part.pivot === "bottom" ? part.h / 2 : 0;
    var rad = (part.rotation || 0) * Math.PI / 180;
    var cos = Math.cos(rad), sin = Math.sin(rad);
    return { x: part.x + part.w / 2 + ox * cos - oy * sin, y: part.y + part.h / 2 + ox * sin + oy * cos };
  }

  /** 애니메이션 하나가 가장 크게 움직이는 값: [돌기, 옆, 위, 아래, 커지는 배율] (PartLayout.reach). */
  function reach(a, withPose) {
    var m = a.motion;
    var e = !motionActive(m) ? [0, 0, 0, 1] : m.type === "swing" ? [m.size * 3, 0, 0, 1] : m.type === "bob" ? [0, m.size, 0, 1]
      : m.type === "sway" ? [0, 0, m.size, 1] : m.type === "breathe" ? [0, 0, 0, 1 + m.size * 0.02] : [0, 0, 0, 1];
    return [
      e[0] + (withPose ? Math.abs(a.rotation) : 0),
      e[2] + (withPose ? Math.abs(a.dx) : 0),
      e[1] + (withPose ? Math.max(0, -a.dy) : 0),
      withPose ? Math.max(0, a.dy) : 0,
      e[3]
    ];
  }

  /** 애니메이션까지 넣은 범위, 넉넉하게 (PartLayout.motionBounds). */
  function motionBounds(layout, withRaise) {
    var b = bounds(layout, withRaise);
    PART_KEYS.forEach(function (key) {
      var part = layout[key];
      if (!partAnimated(part)) {
        return;
      }
      var always = reach(part.animations.always, false);
      var most = [0, 0, 0, 0, 1];
      STATES.forEach(function (state) {
        if (state === "always") {
          return;
        }
        var r = reach(part.animations[state], true);
        for (var i = 0; i < 5; i++) {
          most[i] = Math.max(most[i], r[i]);
        }
      });
      var turn = always[0] + most[0], side = always[1] + most[1], up = always[2] + most[2], down = always[3] + most[3];
      var scale = always[4] * most[4];
      var pivot = pivotPoint(part);
      var half = halfExtents(part);
      var cx = part.x + part.w / 2, cy = part.y + part.h / 2;
      var left, right, top, bottom;
      if (turn > 0) {
        var radius = Math.hypot(Math.abs(cx - pivot.x) + half[0], Math.abs(cy - pivot.y) + half[1]) * scale;
        left = pivot.x - radius;
        right = pivot.x + radius;
        top = pivot.y - radius;
        bottom = pivot.y + radius;
      } else {
        left = pivot.x + (cx - half[0] - pivot.x) * scale;
        right = pivot.x + (cx + half[0] - pivot.x) * scale;
        top = pivot.y + (cy - half[1] - pivot.y) * scale;
        bottom = pivot.y + (cy + half[1] - pivot.y) * scale;
      }
      var arm = key === "leftArm" || key === "rightArm";
      b.minX = Math.min(b.minX, Math.floor(left - side));
      b.maxX = Math.max(b.maxX, Math.ceil(right + side));
      b.minY = Math.min(b.minY, Math.floor(top - up - (arm && withRaise ? ARM_RAISE : 0)));
      b.maxY = Math.max(b.maxY, Math.ceil(bottom + down));
    });
    return b;
  }

  /** 전체 범위. withRaise 면 팔을 든 높이까지 포함. */
  function bounds(layout, withRaise) {
    var b = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
    PART_KEYS.forEach(function (key) {
      var box = partBox(layout[key]);
      var arm = key === "leftArm" || key === "rightArm";
      b.minX = Math.min(b.minX, box.minX);
      b.maxX = Math.max(b.maxX, box.maxX);
      b.minY = Math.min(b.minY, arm && withRaise ? box.minY - ARM_RAISE : box.minY);
      b.maxY = Math.max(b.maxY, box.maxY);
    });
    return b;
  }

  /**
   * 부위를 그릴 좌표계로 바꾼다 (PetApp.applyPartTransform 과 같은 순서):
   * 애니메이션(옮기기 + 중심점 기준 돌리기·늘이기) 다음에 부위 자체 회전(가운데 기준).
   * left, top: 부위 왼쪽 위의 캔버스 좌표, k: 1px 크기.
   */
  function applyPartTransform(ctx, part, left, top, mt, k) {
    var m = mt || NO_MOTION;
    var pivot = pivotPoint(part);
    var px = left + (pivot.x - part.x) * k, py = top + (pivot.y - part.y) * k;
    ctx.translate(m.dx * k, m.dy * k);
    if (m.rotation || m.scaleX !== 1 || m.scaleY !== 1) {
      ctx.translate(px, py);
      ctx.rotate(m.rotation * Math.PI / 180);
      ctx.scale(m.scaleX, m.scaleY);
      ctx.translate(-px, -py);
    }
    if (part.rotation) {
      var cx = left + part.w / 2 * k, cy = top + part.h / 2 * k;
      ctx.translate(cx, cy);
      ctx.rotate(part.rotation * Math.PI / 180);
      ctx.translate(-cx, -cy);
    }
  }

  // ---------------------------------------------------------------- 그리기

  // 캔버스를 화면 배율에 맞춰 선명하게 준비하고 (가로, 세로, 배율) 을 돌려준다
  function prepare(canvas) {
    var dpr = window.devicePixelRatio || 1;
    var w = canvas.clientWidth, h = canvas.clientHeight;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
    }
    var ctx = canvas.getContext("2d");
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    return { ctx: ctx, w: w, h: h, dpr: dpr };
  }

  // 외곽선이 캐릭터 맨 바깥에만 오도록: 검은 사각형을 채우고 안쪽에 몸 색을 칠한다
  function outlinedRect(ctx, ox, oy, k, x1, y1, x2, y2, color) {
    var X1 = Math.round(ox + x1 * k), Y1 = Math.round(oy + y1 * k);
    var X2 = Math.round(ox + x2 * k), Y2 = Math.round(oy + y2 * k);
    var b = Math.max(1, Math.round(k));
    ctx.fillStyle = "#000";
    ctx.fillRect(X1, Y1, X2 - X1, Y2 - Y1);
    if (X2 - X1 > b * 2 && Y2 - Y1 > b * 2) {
      ctx.fillStyle = color;
      ctx.fillRect(X1 + b, Y1 + b, X2 - X1 - b * 2, Y2 - Y1 - b * 2);
    }
  }

  function loadedImage(name) {
    var image = name ? images[name] : null;
    return image && image.complete && image.naturalWidth > 0 ? image : null;
  }

  function drawImage(ctx, image, x, y, w, h, flip) {
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    if (flip) {
      ctx.translate(x + w, y);
      ctx.scale(-1, 1);
      ctx.drawImage(image, 0, 0, w, h);
    } else {
      ctx.drawImage(image, x, y, w, h);
    }
    ctx.restore();
  }

  // 부위 하나 (dx, dy: 걷기 등으로 생기는 어긋남, mt: 반복 움직임 모양)
  function drawPart(ctx, ox, oy, k, part, dx, dy, color, mt) {
    var x = part.x + dx, y = part.y + dy;
    var image = loadedImage(part.image);
    ctx.save();
    applyPartTransform(ctx, part, ox + x * k, oy + y * k, mt, k);
    if (image) {
      drawImage(ctx, image, ox + x * k, oy + y * k, part.w * k, part.h * k, part.flip);
    } else {
      outlinedRect(ctx, ox, oy, k, x, y, x + part.w, y + part.h, color);
    }
    ctx.restore();
  }

  // ox, oy: 몸통 기본 위치(0,0)의 캔버스 좌표(장치 픽셀), k: 1px 이 몇 장치 픽셀인지
  // pose.motion (MotionClock) 이 있으면 부위 반복 움직임도 그린다
  function drawBitAt(ctx, ox, oy, k, color, pose, layout) {
    var p = pose || newPose();
    var L = layout || DEFAULT_LAYOUT;
    function mt(key) {
      return p.motion && L[key].animations ? p.motion.transform(L, key) : NO_MOTION;
    }
    // 몸통, 다리 (앱과 같은 순서로 그린다)
    drawPart(ctx, ox, oy, k, L.body, 0, 0, color, mt("body"));
    drawPart(ctx, ox, oy, k, L.leftLeg, p.limbX - p.swayX, p.limbY, color, mt("leftLeg"));
    drawPart(ctx, ox, oy, k, L.rightLeg, p.limbX + p.swayX, p.limbY, color, mt("rightLeg"));
    drawEyes(ctx, ox, oy, k, p, L.eyes, mt("eyes"));
    // 팔
    drawPart(ctx, ox, oy, k, L.leftArm, p.limbX + p.swayX, p.armLift + p.limbY, color, mt("leftArm"));
    drawPart(ctx, ox, oy, k, L.rightArm, p.limbX - p.swayX, p.armLift + p.limbY, color, mt("rightArm"));
  }

  function expressionOf(p) {
    return p.expression || (p.falling ? "fall" : "neutral");
  }

  // 눈: 표정별 사진이 있으면 사진, 없으면 기본 눈을 눈 영역에 맞춰 늘려 그린다
  function drawEyes(ctx, ox, oy, k, p, eyes, mt) {
    var expression = expressionOf(p);
    var image = loadedImage(eyes.images && eyes.images[expression]);
    ctx.save();
    applyPartTransform(ctx, eyes, ox + eyes.x * k, oy + eyes.y * k, mt, k);
    if (image) {
      drawImage(ctx, image, ox + (eyes.x + p.eyeShift) * k, oy + eyes.y * k, eyes.w * k, eyes.h * k, false);
      ctx.restore();
      return;
    }
    ctx.save();
    ctx.translate(ox + eyes.x * k, oy + eyes.y * k);
    ctx.scale(eyes.w / DEFAULT_EYES.w, eyes.h / DEFAULT_EYES.h);
    ctx.translate(-DEFAULT_EYES.x * k, -DEFAULT_EYES.y * k);
    drawDefaultEyes(ctx, k, p, expression);
    ctx.restore();
    ctx.restore();
  }

  // 기본 눈 (PetApp.paintDefaultEyes 와 같은 좌표 계산, 몸통 기준)
  function drawDefaultEyes(ctx, k, p, expression) {
    function sx(v) { return Math.round(EYE.offsetX + p.eyeShift + v * EYE.scale) * k; }
    function sy(v) { return Math.round(v * EYE.scale) * k; }
    function lx(v) { return v - 2; }
    function rx(v) { return v + 2; }
    ctx.fillStyle = "#000";
    ctx.strokeStyle = "#000";
    ctx.lineWidth = EYE.stroke * k;
    ctx.lineCap = "round";
    if (expression === "fall") {
      // 점프/낙하 중: > < 모양
      [[lx(29), 43, lx(50), 52], [lx(29), 61, lx(50), 52], [rx(91), 43, rx(70), 52], [rx(91), 61, rx(70), 52]].forEach(function (l) {
        ctx.beginPath();
        ctx.moveTo(sx(l[0]), sy(l[1]));
        ctx.lineTo(sx(l[2]), sy(l[3]));
        ctx.stroke();
      });
      return;
    }
    // 앱 안의 웹 엔진(JavaFX WebView)은 ctx.ellipse 를 지원하지 않아서, 원을 늘려서 그린다
    function ellipse(x1, y1, x2, y2, upperHalf) {
      ctx.save();
      ctx.translate((x1 + x2) / 2, (y1 + y2) / 2);
      ctx.scale((x2 - x1) / 2, (y2 - y1) / 2);
      ctx.beginPath();
      if (upperHalf) {
        ctx.arc(0, 0, 1, Math.PI, Math.PI * 2);
      } else {
        ctx.arc(0, 0, 1, 0, Math.PI * 2);
      }
      ctx.restore();
    }
    if (expression === "grab") {
      // 잡혔을 때: ∩ ∩ 모양
      [[lx(27), lx(53)], [rx(67), rx(93)]].forEach(function (eye) {
        ellipse(sx(eye[0]), sy(40), sx(eye[1]), sy(66), true);
        ctx.stroke();
      });
      return;
    }
    [[lx(30), lx(50)], [rx(70), rx(90)]].forEach(function (eye) {
      ellipse(sx(eye[0]), sy(42), sx(eye[1]), sy(62), false);
      ctx.fill();
    });
  }

  // 가만히 있는 비트를 캔버스 가운데에 맞춰 그린다 (제목 막대 아이콘)
  function drawIcon(canvas, color, layout) {
    var L = layout || DEFAULT_LAYOUT;
    var c = prepare(canvas);
    var b = bounds(L, false);
    var scale = Math.min(c.w / (b.maxX - b.minX + 2), c.h / (b.maxY - b.minY + 2));
    var k = scale * c.dpr;
    var ox = (c.w * c.dpr - (b.maxX - b.minX) * k) / 2 - b.minX * k;
    var oy = (c.h * c.dpr - (b.maxY - b.minY) * k) / 2 - b.minY * k;
    drawBitAt(c.ctx, ox, oy, k, color, null, L);
  }

  // ---------------------------------------------------------------- 애니메이션 (색깔 설정 미리보기)

  // 걸어갔다 돌아오기 → 쉬기 → 두 번 점프 → 쉬기 를 반복
  // wait 는 틱 수 (64틱 = 1초). 한 바퀴 약 15초: 3초 쉬고 → 걷기 → 4초 쉬고 → 점프 → 5초 쉬기
  var SCRIPT = [
    { wait: 192 },
    { walkTo: 34, speed: 0.9 },
    { wait: 20 },
    { walkTo: 0, speed: 0.9 },
    { wait: 256 },
    { jump: true },
    { wait: 320 }
  ];

  function Stage(canvas) {
    this.canvas = canvas;
    this.color = "#4fc3f7";
    this.layout = copyLayout(DEFAULT_LAYOUT);
    this.running = false;
    this.motion = new MotionClock();
    this.reset();
  }

  Stage.prototype.reset = function () {
    this.pose = newPose();
    this.prevX = 0;
    this.prevY = 0;
    this.step = 0;
    this.stepTicks = 0;
    this.vy = 0;
    this.airborne = false;
    this.fallElapsed = 0;
    this.peakY = 0;
    this.jumpsLeft = 0;
    this.walkPhase = 0;
    this.bobAmp = 0;
    this.swayPhase = 0;
    this.swayAmp = 0;
    this.eyeTarget = 0;
  };

  Stage.prototype.start = function () {
    if (this.running) {
      return;
    }
    this.running = true;
    this.reset();
    this.last = null;
    this.acc = 0;
    var self = this;
    function frame(time) {
      if (!self.running) {
        return;
      }
      if (self.last === null) {
        self.last = time;
      }
      self.acc = Math.min(self.acc + (time - self.last), TICK_MS * 4);
      self.last = time;
      while (self.acc >= TICK_MS) {
        self.tick();
        self.acc -= TICK_MS;
      }
      self.draw();
      window.requestAnimationFrame(frame);
    }
    window.requestAnimationFrame(frame);
  };

  Stage.prototype.stop = function () {
    this.running = false;
  };

  Stage.prototype.startJump = function () {
    this.jumpsLeft -= 1;
    this.vy = -JUMP_VELOCITY;
    this.airborne = true;
    this.fallElapsed = 0;
    this.peakY = this.pose.y;
  };

  Stage.prototype.advance = function () {
    this.step = (this.step + 1) % SCRIPT.length;
    this.stepTicks = 0;
  };

  Stage.prototype.tick = function () {
    var p = this.pose;
    var action = SCRIPT[this.step];
    this.stepTicks += 1;

    if (action.wait !== undefined) {
      if (this.stepTicks >= action.wait) {
        this.advance();
      }
    } else if (action.walkTo !== undefined) {
      var delta = action.walkTo - p.x;
      if (Math.abs(delta) <= action.speed) {
        p.x = action.walkTo;
        this.advance();
      } else {
        p.x += delta > 0 ? action.speed : -action.speed;
      }
    } else if (action.jump) {
      if (this.stepTicks === 1) {
        this.jumpsLeft = JUMP_COUNT;
        this.startJump();
      }
      if (!this.airborne) {
        this.advance();
      }
    }

    // 중력/착지 (PetApp.applyGravity 와 같은 계산, 바닥은 y = 0)
    if (this.airborne) {
      this.fallElapsed = Math.min(GRAVITY_RAMP_MS, this.fallElapsed + FRAME_MS);
      this.peakY = Math.min(this.peakY, p.y);
      var ramp = this.fallElapsed / GRAVITY_RAMP_MS;
      this.vy = Math.min(MAX_FALL_SPEED, this.vy + GRAVITY * (1 + ramp * ramp));
      var nextY = p.y + this.vy;
      if (nextY < 0) {
        p.y = nextY;
      } else {
        p.y = 0;
        var fallDistance = Math.max(0, -this.peakY);
        var bounce = fallDistance >= BOUNCE_MIN_FALL_PX ? Math.min(BOUNCE_MAX_SPEED, Math.sqrt(fallDistance) * BOUNCE_HEIGHT_GAIN) : 0;
        if (bounce >= BOUNCE_MIN_SPEED) {
          this.vy = -bounce;
          this.fallElapsed = 0;
          this.peakY = 0;
        } else if (this.jumpsLeft > 0) {
          this.startJump();
        } else {
          this.vy = 0;
          this.airborne = false;
        }
      }
    }
    p.falling = this.airborne;

    // 팔 포즈, 걷기 흔들림, 팔다리 반응, 시선 (PetApp.tick 뒷부분과 같은 계산)
    var dx = p.x - this.prevX;
    var dy = p.y - this.prevY;
    this.prevX = p.x;
    this.prevY = p.y;
    var speed = Math.hypot(dx, dy);
    var phaseStep = speed > 0.01 ? clamp(speed * LIMB_SWAY_SPEED_SCALE, LIMB_SWAY_SPEED_MIN, LIMB_SWAY_SPEED_MAX) : 0;

    var armTarget = p.falling ? -ARM_RAISE : 0;
    p.armLift += (armTarget - p.armLift) * ARM_POSE_SMOOTHING;

    this.eyeTarget = Math.abs(dx) < 0.01 ? 0 : clamp(dx * EYE_LOOK_GAIN, -EYE_LOOK_MAX, EYE_LOOK_MAX);
    p.eyeShift += (this.eyeTarget - p.eyeShift) * EYE_LOOK_SMOOTHING;

    var walking = Math.abs(dx) > 0.01;
    this.bobAmp += ((walking ? WALK_BOB_MAX : 0) - this.bobAmp) * WALK_BOB_SMOOTHING;
    if (walking) {
      this.walkPhase += phaseStep * 2;
    }
    p.bob = -((1 - Math.cos(this.walkPhase)) * 0.5) * this.bobAmp;

    var limbTargetX = speed < 0.01 ? 0 : clamp(-dx * LIMB_REACT_GAIN, -LIMB_REACT_MAX_X, LIMB_REACT_MAX_X);
    var limbTargetY = speed < 0.01 ? 0 : clamp(-dy * LIMB_REACT_GAIN, -LIMB_REACT_MAX_Y, LIMB_REACT_MAX_Y);
    p.limbX += (limbTargetX - p.limbX) * LIMB_REACT_SMOOTHING;
    p.limbY += (limbTargetY - p.limbY) * LIMB_REACT_SMOOTHING;

    this.swayAmp += ((speed > 0.01 ? 1 : 0) - this.swayAmp) * LIMB_SWAY_SMOOTHING;
    if (speed > 0.01) {
      this.swayPhase += phaseStep;
    }
    p.swayX = Math.sin(this.swayPhase) * LIMB_SWAY_MAX_X * this.swayAmp;

    this.motion.tick(this.airborne ? "air" : (walking ? "walk" : "idle"));
    p.motion = this.motion;
  };

  Stage.prototype.draw = function () {
    var c = prepare(this.canvas);
    var L = this.layout;
    var b = motionBounds(L, true);
    var feet = bounds(L, true).maxY; // 발 높이는 움직임 없는 모양으로
    // 기본 1.5배, 꾸민 비트가 커서 무대에 안 들어가면 줄인다
    var scale = Math.min(1.5, (c.w - 20) / Math.max(1, b.maxX - b.minX), (c.h - 20) / Math.max(1, b.maxY - b.minY + 70));
    var k = scale * c.dpr;
    var groundY = c.h * c.dpr - 8 * c.dpr; // 발이 닿는 바닥
    var centerX = (L.body.x + L.body.w / 2);
    var ox = (c.w * c.dpr) / 2 - centerX * k + this.pose.x * k;
    var oy = groundY - feet * k + (this.pose.y + this.pose.bob) * k;
    // 바닥 선
    c.ctx.fillStyle = "#e9e1d3";
    c.ctx.fillRect(0, Math.round(groundY), c.w * c.dpr, Math.max(1, Math.round(c.dpr)));
    drawBitAt(c.ctx, ox, oy, k, this.color, this.pose, L);
  };

  window.BitView = {
    DEFAULT_LAYOUT: DEFAULT_LAYOUT,
    PART_KEYS: PART_KEYS,
    images: images,
    copyLayout: copyLayout,
    bounds: bounds,
    motionBounds: motionBounds,
    MotionClock: MotionClock,
    STATES: STATES,
    motionTypesFor: motionTypesFor,
    defaultMotion: defaultMotion,
    normalizePart: normalizePart,
    mirroredAnimation: mirroredAnimation,
    mirroredPivot: mirroredPivot,
    pivotPoint: pivotPoint,
    partTransform: partTransform,
    prepare: prepare,
    newPose: newPose,
    drawBitAt: drawBitAt,
    drawIcon: drawIcon,
    Stage: Stage
  };
})();
