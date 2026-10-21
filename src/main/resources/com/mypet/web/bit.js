// 비트 그리기 + 걷기/점프 애니메이션 (canvas).
// 모습 꾸미기 배치(layout)대로 그린다: 부위마다 사진이 있으면 사진, 없으면 몸 색 네모 + 외곽선.
// 치수와 움직임 값은 Java 쪽 PartLayout.java / PetApp.java 와 같다. (단위: 화면 px, 몸통 기본 위치 왼쪽 위가 0,0)
(function () {
  "use strict";

  // 기본 비트 배치 (PartLayout.defaults 와 같다)
  var DEFAULT_LAYOUT = {
    symmetric: true,
    body: { x: 0, y: 0, w: 44, h: 28, image: null, flip: false },
    leftArm: { x: -9, y: 14, w: 8, h: 8, image: null, flip: false },
    rightArm: { x: 45, y: 14, w: 8, h: 8, image: null, flip: false },
    leftLeg: { x: 10, y: 29, w: 8, h: 8, image: null, flip: false },
    rightLeg: { x: 25, y: 29, w: 8, h: 8, image: null, flip: false },
    eyes: { x: 14, y: 12, w: 18, h: 6, images: { neutral: null, grab: null, fall: null } }
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
    return JSON.parse(JSON.stringify(layout || DEFAULT_LAYOUT));
  }

  function newPose() {
    return { x: 0, y: 0, bob: 0, limbX: 0, limbY: 0, swayX: 0, armLift: 0, eyeShift: 0, falling: false, expression: null };
  }

  /** 전체 범위. withRaise 면 팔을 든 높이까지 포함. */
  function bounds(layout, withRaise) {
    var b = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
    PART_KEYS.forEach(function (key) {
      var part = layout[key];
      var arm = key === "leftArm" || key === "rightArm";
      b.minX = Math.min(b.minX, part.x);
      b.maxX = Math.max(b.maxX, part.x + part.w);
      b.minY = Math.min(b.minY, arm && withRaise ? part.y - ARM_RAISE : part.y);
      b.maxY = Math.max(b.maxY, part.y + part.h);
    });
    return b;
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

  // 부위 하나 (dx, dy: 움직임으로 생기는 어긋남)
  function drawPart(ctx, ox, oy, k, part, dx, dy, color) {
    var x = part.x + dx, y = part.y + dy;
    var image = loadedImage(part.image);
    if (image) {
      drawImage(ctx, image, ox + x * k, oy + y * k, part.w * k, part.h * k, part.flip);
    } else {
      outlinedRect(ctx, ox, oy, k, x, y, x + part.w, y + part.h, color);
    }
  }

  // ox, oy: 몸통 기본 위치(0,0)의 캔버스 좌표(장치 픽셀), k: 1px 이 몇 장치 픽셀인지
  function drawBitAt(ctx, ox, oy, k, color, pose, layout) {
    var p = pose || newPose();
    var L = layout || DEFAULT_LAYOUT;
    // 몸통, 다리 (앱과 같은 순서로 그린다)
    drawPart(ctx, ox, oy, k, L.body, 0, 0, color);
    drawPart(ctx, ox, oy, k, L.leftLeg, p.limbX - p.swayX, p.limbY, color);
    drawPart(ctx, ox, oy, k, L.rightLeg, p.limbX + p.swayX, p.limbY, color);
    drawEyes(ctx, ox, oy, k, p, L.eyes);
    // 팔
    drawPart(ctx, ox, oy, k, L.leftArm, p.limbX + p.swayX, p.armLift + p.limbY, color);
    drawPart(ctx, ox, oy, k, L.rightArm, p.limbX - p.swayX, p.armLift + p.limbY, color);
  }

  function expressionOf(p) {
    return p.expression || (p.falling ? "fall" : "neutral");
  }

  // 눈: 표정별 사진이 있으면 사진, 없으면 기본 눈을 눈 영역에 맞춰 늘려 그린다
  function drawEyes(ctx, ox, oy, k, p, eyes) {
    var expression = expressionOf(p);
    var image = loadedImage(eyes.images && eyes.images[expression]);
    if (image) {
      drawImage(ctx, image, ox + (eyes.x + p.eyeShift) * k, oy + eyes.y * k, eyes.w * k, eyes.h * k, false);
      return;
    }
    ctx.save();
    ctx.translate(ox + eyes.x * k, oy + eyes.y * k);
    ctx.scale(eyes.w / DEFAULT_EYES.w, eyes.h / DEFAULT_EYES.h);
    ctx.translate(-DEFAULT_EYES.x * k, -DEFAULT_EYES.y * k);
    drawDefaultEyes(ctx, k, p, expression);
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
  };

  Stage.prototype.draw = function () {
    var c = prepare(this.canvas);
    var L = this.layout;
    var b = bounds(L, true);
    // 기본 1.5배, 꾸민 비트가 커서 무대에 안 들어가면 줄인다
    var scale = Math.min(1.5, (c.w - 20) / Math.max(1, b.maxX - b.minX), (c.h - 20) / Math.max(1, b.maxY - b.minY + 70));
    var k = scale * c.dpr;
    var groundY = c.h * c.dpr - 8 * c.dpr; // 발이 닿는 바닥
    var centerX = (L.body.x + L.body.w / 2);
    var ox = (c.w * c.dpr) / 2 - centerX * k + this.pose.x * k;
    var oy = groundY - b.maxY * k + (this.pose.y + this.pose.bob) * k;
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
    prepare: prepare,
    newPose: newPose,
    drawBitAt: drawBitAt,
    drawIcon: drawIcon,
    Stage: Stage
  };
})();
