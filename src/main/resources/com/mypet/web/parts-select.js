// 모습 꾸미기: 여러 부위 고르기 · 그룹 · 그룹 대칭 · 부위 하나 더. PartsEditor 에 기능을 더한다 (parts-editor.js 다음에 읽는다).
// - 빈 곳을 끌면 네모 안에 걸린 부위들을 고른다, Shift+클릭 = 하나씩 더하기/빼기, Shift+끌기 = 화면 옮기기.
// - 고른 부위를 끌면 같이 움직인다 (모양 화면). 우클릭 → 그룹으로 묶기 / 그룹 풀기 / 좌우·상하 대칭.
// - 대칭은 팔·다리처럼 몸통 가운데 줄을 기준으로 짝 부위를 거울처럼 맞춘다 (둘 다 켜면 네 방향).
(function () {
  "use strict";

  var P = window.PartsEditor.prototype;
  var BV = window.BitView;
  var MAX_OFFSET = 2000;
  var CLICK_SLOP = 3; // 이만큼(화면 px) 안 움직이면 클릭

  function normalizeRotation(degrees) {
    var r = ((Math.round(degrees) % 360) + 360) % 360;
    return r > 180 ? r - 360 : r;
  }

  function clamp(v, limit) {
    return Math.max(-limit, Math.min(limit, v));
  }

  // ---------------------------------------------------------------- 그룹 찾기

  P.groups = function () {
    if (!Array.isArray(this.layout.groups)) {
      this.layout.groups = [];
    }
    return this.layout.groups;
  };

  /** key 가 든 그룹 (없으면 null). */
  P.groupOf = function (key) {
    var groups = this.groups();
    for (var i = 0; i < groups.length; i++) {
      if (groups[i].members.indexOf(key) >= 0) {
        return groups[i];
      }
    }
    return null;
  };

  /** 누르면 같이 골라지는 부위들: 그룹이면 그룹 전체(화면에 있는 것), 아니면 그 부위만. */
  P.groupMembers = function (key) {
    var group = this.groupOf(key);
    var layout = this.layout;
    if (!group) {
      return [key];
    }
    var members = group.members.filter(function (k) { return layout[k] && layout[k].visible !== false; });
    return members.indexOf(key) >= 0 ? members : [key];
  };

  // ---------------------------------------------------------------- 고르기

  /** 지금 고른 부위들 (늘 this.selected 를 포함, 화면에 있는 것만). */
  P.selectedKeys = function () {
    var layout = this.layout;
    var keys = (this.selection || []).filter(function (k) { return layout[k] && layout[k].visible !== false; });
    if (keys.indexOf(this.selected) < 0) {
      keys.unshift(this.selected);
    }
    return keys;
  };

  /** 여러 부위를 고른다 (primary = 숫자 칸·손잡이가 따라가는 부위). */
  P.selectKeys = function (keys, primary) {
    var unique = [];
    keys.forEach(function (k) {
      if (unique.indexOf(k) < 0) {
        unique.push(k);
      }
    });
    if (!unique.length) {
      return;
    }
    this.selection = unique;
    var key = primary && unique.indexOf(primary) >= 0 ? primary : unique[0];
    if (key !== this.selected) {
      this.select(key, true);
    } else {
      this.refreshControls();
      this.draw();
    }
  };

  /** Shift+클릭: 그 부위(그룹이면 그룹째)를 더하거나 뺀다. */
  P.toggleSelect = function (key) {
    var members = this.groupMembers(key);
    var keys = this.selectedKeys();
    var on = keys.indexOf(key) >= 0;
    if (on) {
      keys = keys.filter(function (k) { return members.indexOf(k) < 0; });
      if (!keys.length) {
        return; // 하나는 늘 골라져 있다
      }
      this.selectKeys(keys, keys.indexOf(this.selected) >= 0 ? this.selected : keys[0]);
    } else {
      this.selectKeys(keys.concat(members), key);
    }
  };

  /** 부위가 화면에서 차지하는 (돌린) 네모를 감싸는 똑바른 네모. */
  P.partBounds = function (key, pose) {
    var r = this.partRect(key, pose);
    var cos = Math.abs(Math.cos(r.angle)), sin = Math.abs(Math.sin(r.angle));
    var w = r.w * cos + r.h * sin, h = r.w * sin + r.h * cos;
    return { x: r.cx - w / 2, y: r.cy - h / 2, w: w, h: h };
  };

  /** 끌어서 그린 네모에 걸린 부위들을 고른다 (그룹은 그룹째). additive = Shift 로 더하기. */
  P.selectInRect = function (rect, additive) {
    var self = this;
    var pose = this.previewPose();
    var hits = [];
    this.hitOrder().forEach(function (key) {
      var b = self.partBounds(key, pose);
      if (b.x <= rect.x + rect.w && b.x + b.w >= rect.x && b.y <= rect.y + rect.h && b.y + b.h >= rect.y) {
        self.groupMembers(key).forEach(function (k) {
          if (hits.indexOf(k) < 0) {
            hits.push(k);
          }
        });
      }
    });
    if (!hits.length) {
      return;
    }
    this.selectKeys(additive ? this.selectedKeys().concat(hits) : hits, hits.indexOf(this.selected) >= 0 ? this.selected : hits[0]);
  };

  // ---------------------------------------------------------------- 마우스: 고르기 네모, Shift, 여러 개 옮기기

  /** 마우스를 눌렀을 때 고르기 쪽에서 처리하면 true (parts-editor.js 의 onMouseDown 이 먼저 부른다). */
  P.selectMouseDown = function (event, p, target) {
    if (event.shiftKey) {
      // Shift: 안 움직이고 떼면 고르기 더하기/빼기, 끌면 화면 옮기기
      event.preventDefault();
      this.drag = { shift: true, key: target && !target.rotate && !target.resize ? target.key : null,
        startX: p.x, startY: p.y, ox: this.view.ox, oy: this.view.oy, panning: false };
      return true;
    }
    if (!target) {
      // 빈 곳: 고르기 네모
      event.preventDefault();
      this.drag = { marquee: true, startX: p.x, startY: p.y, x: p.x, y: p.y };
      return true;
    }
    if (target.rotate || target.resize) {
      return false;
    }
    var keys = this.selectedKeys();
    if (keys.indexOf(target.key) < 0) {
      // 안 고른 부위를 누르면 그 부위(그룹이면 그룹째)만 고른다
      this.selectKeys(this.groupMembers(target.key), target.key);
      keys = this.selectedKeys();
    } else if (target.key !== this.selected) {
      this.select(target.key, true);
    }
    // 여러 개를 골랐으면 모양 화면에서는 같이 옮긴다
    if (keys.length > 1 && this.shaping() && !this.advanced()) {
      event.preventDefault();
      var starts = {};
      var layout = this.layout;
      keys.forEach(function (k) {
        starts[k] = { x: layout[k].x, y: layout[k].y };
      });
      this.drag = { multi: true, keys: keys, starts: starts, startX: p.x, startY: p.y, moved: false };
      return true;
    }
    return false;
  };

  /** 마우스를 움직였을 때 고르기 쪽에서 처리하면 true. */
  P.selectMouseMove = function (p) {
    var drag = this.drag;
    if (drag.shift) {
      if (!drag.panning && Math.abs(p.x - drag.startX) + Math.abs(p.y - drag.startY) > CLICK_SLOP) {
        drag.panning = true;
      }
      if (drag.panning) {
        this.view.ox = drag.ox + (p.x - drag.startX);
        this.view.oy = drag.oy + (p.y - drag.startY);
        this.zoomed = true;
        this.draw();
      }
      return true;
    }
    if (drag.marquee) {
      drag.x = p.x;
      drag.y = p.y;
      this.draw();
      return true;
    }
    if (drag.multi) {
      var k = this.view.k;
      var dx = Math.round((p.x - drag.startX) / k), dy = Math.round((p.y - drag.startY) / k);
      var layout = this.layout;
      var self = this;
      drag.keys.forEach(function (key) {
        layout[key].x = clamp(drag.starts[key].x + dx, MAX_OFFSET);
        layout[key].y = clamp(drag.starts[key].y + dy, MAX_OFFSET);
      });
      drag.moved = drag.moved || dx !== 0 || dy !== 0;
      // 대칭 그룹은 고른(또는 첫) 부위를 기준으로 다시 맞춘다
      var done = {};
      drag.keys.forEach(function (key) {
        var group = self.groupOf(key);
        if (group && !done[group.id] && (group.mirrorX || group.mirrorY)) {
          done[group.id] = true;
          self.mirrorGroupFrom(group.members.indexOf(self.selected) >= 0 ? self.selected : key);
        }
      });
      if (this.layout.symmetric) {
        drag.keys.forEach(function (key) {
          self.afterChange(key);
        });
      }
      this.refreshControls();
      this.draw();
      return true;
    }
    return false;
  };

  /** 마우스를 뗐을 때 고르기 쪽에서 처리하면 true. */
  P.selectMouseUp = function () {
    var drag = this.drag;
    if (drag.shift) {
      this.drag = null;
      if (!drag.panning && drag.key) {
        this.toggleSelect(drag.key);
      } else {
        this.draw();
      }
      return true;
    }
    if (drag.marquee) {
      this.drag = null;
      var rect = {
        x: Math.min(drag.startX, drag.x), y: Math.min(drag.startY, drag.y),
        w: Math.abs(drag.x - drag.startX), h: Math.abs(drag.y - drag.startY)
      };
      if (rect.w + rect.h > CLICK_SLOP) {
        this.selectInRect(rect, false);
      } else {
        // 빈 곳 클릭: 하나만 고른 상태로
        this.selectKeys([this.selected], this.selected);
      }
      this.draw();
      return true;
    }
    if (drag.multi) {
      this.drag = null;
      this.draw();
      if (drag.moved) {
        this.commit(0);
      }
      return true;
    }
    return false;
  };

  /** 함께 고른 부위(가는 점선)와 고르기 네모를 그린다. */
  P.drawSelection = function (ctx, d, pose) {
    var self = this;
    var keys = this.selectedKeys();
    ctx.save();
    ctx.strokeStyle = "#0288d1";
    ctx.lineWidth = Math.max(1, Math.round(d));
    ctx.setLineDash([3 * d, 3 * d]);
    keys.forEach(function (key) {
      if (key === self.selected) {
        return;
      }
      var r = self.partRect(key, pose);
      ctx.save();
      ctx.translate(r.cx * d, r.cy * d);
      ctx.rotate(r.angle);
      ctx.translate(-r.cx * d, -r.cy * d);
      ctx.strokeRect(Math.round((r.x - 2) * d) + 0.5, Math.round((r.y - 2) * d) + 0.5, Math.round((r.w + 4) * d), Math.round((r.h + 4) * d));
      ctx.restore();
    });
    var drag = this.drag;
    if (drag && drag.marquee) {
      var x = Math.min(drag.startX, drag.x), y = Math.min(drag.startY, drag.y);
      var w = Math.abs(drag.x - drag.startX), h = Math.abs(drag.y - drag.startY);
      ctx.fillStyle = "rgba(79, 195, 247, 0.15)";
      ctx.fillRect(x * d, y * d, w * d, h * d);
      ctx.strokeRect(Math.round(x * d) + 0.5, Math.round(y * d) + 0.5, Math.round(w * d), Math.round(h * d));
    }
    ctx.restore();
  };

  // ---------------------------------------------------------------- 그룹

  /** 고른 부위들을 그룹으로 묶는다 (이미 다른 그룹이던 부위는 그 그룹에서 빠진다). */
  P.groupSelected = function () {
    var keys = this.selectedKeys();
    if (keys.length < 2) {
      return;
    }
    var groups = this.groups().map(function (g) {
      g.members = g.members.filter(function (k) { return keys.indexOf(k) < 0; });
      return g;
    }).filter(function (g) { return g.members.length >= 2; });
    var id;
    do {
      id = "g_" + Math.random().toString(36).slice(2, 10).replace(/[^a-z0-9]/g, "a");
    } while (groups.some(function (g) { return g.id === id; }));
    groups.push({ id: id, members: keys.slice(), mirrorX: false, mirrorY: false, pairsX: [], pairsY: [] });
    this.layout.groups = BV.normalizeGroups(Object.assign({}, this.layout, { groups: groups }));
    this.afterPartsChanged();
    this.setStatus("부위 " + keys.length + "개를 그룹으로 묶었어요 · 우클릭으로 좌우·상하 대칭");
  };

  P.ungroup = function (group) {
    this.layout.groups = this.groups().filter(function (g) { return g !== group; });
    this.selectKeys([this.selected], this.selected);
    this.afterPartsChanged();
    this.setStatus("그룹을 풀었어요");
  };

  /** 부위를 지우면 그룹에서도 뺀다. */
  P.removeFromGroups = function (key) {
    this.groups().forEach(function (g) {
      g.members = g.members.filter(function (k) { return k !== key; });
      ["pairsX", "pairsY"].forEach(function (name) {
        g[name] = (g[name] || []).filter(function (p) { return p.indexOf(key) < 0; });
      });
    });
    this.layout.groups = BV.normalizeGroups(this.layout);
  };

  /** 몸통 가운데 (대칭 기준 줄). */
  P.bodyCenter = function () {
    var b = this.layout.body;
    return { x: b.x + b.w / 2, y: b.y + b.h / 2 };
  };

  /**
   * 대칭 짝 찾기: 바깥쪽 부위부터, 기준 줄 반대편에서 거울 자리에 가장 가까운 부위와 짝을 짓는다.
   * 기준 줄 위에 있는 부위는 짝 없이 둔다.
   */
  P.pairUp = function (members, axis) {
    var layout = this.layout;
    var center = this.bodyCenter();
    var line = axis === "x" ? center.x : center.y;
    function pos(k) {
      var p = layout[k];
      return { x: p.x + p.w / 2, y: p.y + p.h / 2 };
    }
    function side(k) {
      var c = pos(k);
      return (axis === "x" ? c.x : c.y) - line;
    }
    var order = members.slice().sort(function (a, b) { return Math.abs(side(b)) - Math.abs(side(a)); });
    var used = {};
    var pairs = [];
    order.forEach(function (a) {
      if (used[a] || Math.abs(side(a)) < 1) {
        return;
      }
      var ca = pos(a);
      var mirror = axis === "x" ? { x: 2 * line - ca.x, y: ca.y } : { x: ca.x, y: 2 * line - ca.y };
      var best = null, bestDist = Infinity;
      order.forEach(function (b) {
        if (b === a || used[b] || side(b) * side(a) > 0) {
          return; // 같은 쪽은 짝이 될 수 없다
        }
        var cb = pos(b);
        var dist = Math.hypot(cb.x - mirror.x, cb.y - mirror.y);
        if (dist < bestDist) {
          best = b;
          bestDist = dist;
        }
      });
      if (best) {
        used[a] = used[best] = true;
        pairs.push([a, best]);
      }
    });
    return pairs;
  };

  /** 그룹 대칭 켜기/끄기. 켜면 짝을 찾고 지금 고른 부위를 기준으로 맞춘다. */
  P.setGroupMirror = function (group, axis, on) {
    var name = axis === "x" ? "mirrorX" : "mirrorY";
    var pairsName = axis === "x" ? "pairsX" : "pairsY";
    group[name] = !!on;
    group[pairsName] = on ? this.pairUp(group.members, axis) : [];
    if (on && !group[pairsName].length) {
      this.setStatus(axis === "x" ? "좌우로 짝이 될 부위가 없어요 (몸통 가운데 양쪽에 놓아 주세요)"
        : "위아래로 짝이 될 부위가 없어요 (몸통 가운데 위아래에 놓아 주세요)");
    } else if (on) {
      this.setStatus((axis === "x" ? "좌우" : "상하") + " 대칭: 짝 " + group[pairsName].length + "쌍");
    }
    if (on) {
      this.mirrorGroupFrom(group.members.indexOf(this.selected) >= 0 ? this.selected : group.members[0]);
    }
    this.refreshControls();
    this.draw();
    this.commit(0);
  };

  /** key 를 바꾼 뒤: 그룹 대칭이면 짝 부위들을 차례로 거울처럼 맞춘다 (좌우 짝, 상하 짝, 그 짝의 짝...). */
  P.mirrorGroupFrom = function (key) {
    var group = this.groupOf(key);
    if (!group || (!group.mirrorX && !group.mirrorY)) {
      return;
    }
    var self = this;
    var done = {};
    done[key] = true;
    var queue = [key];
    function partner(pairs, k) {
      for (var i = 0; i < pairs.length; i++) {
        if (pairs[i][0] === k) {
          return pairs[i][1];
        }
        if (pairs[i][1] === k) {
          return pairs[i][0];
        }
      }
      return null;
    }
    while (queue.length) {
      var from = queue.shift();
      [["x", group.mirrorX, group.pairsX], ["y", group.mirrorY, group.pairsY]].forEach(function (rule) {
        if (!rule[1]) {
          return;
        }
        var to = partner(rule[2] || [], from);
        if (to && !done[to] && self.layout[to]) {
          done[to] = true;
          self.applyMirror(from, to, rule[0]);
          queue.push(to);
        }
      });
    }
  };

  /** source 를 몸통 가운데 줄 기준으로 거울처럼 target 에 복사 (axis "x" = 좌우, "y" = 상하). 팔·다리 좌우 맞추기와 같은 방식. */
  P.applyMirror = function (sourceKey, targetKey, axis) {
    var s = this.layout[sourceKey], t = this.layout[targetKey], b = this.layout.body;
    var x = axis === "x";
    t.w = s.w;
    t.h = s.h;
    if (x) {
      t.y = s.y;
      t.x = (b.x * 2 + b.w) - (s.x + s.w);
    } else {
      t.x = s.x;
      t.y = (b.y * 2 + b.h) - (s.y + s.h);
    }
    t.image = s.image;
    t.flip = !!s.image && (x ? !s.flip : !!s.flip);
    t.flipY = !!s.image && (x ? !!s.flipY : !s.flipY);
    t.rotation = normalizeRotation(-(s.rotation || 0));
    t.resW = s.resW || 0;
    t.resH = s.resH || 0;
    if (BV.isCustom(sourceKey) && BV.isCustom(targetKey)) {
      t.shape = s.shape;
    }
    t.pivot = x ? BV.mirroredPivot(s.pivot) : BV.mirroredPivotY(s.pivot);
    t.animations = {};
    var allowed = BV.motionTypesFor(targetKey);
    BV.STATES.forEach(function (state) {
      var a = x ? BV.mirroredAnimation(s.animations[state]) : BV.mirroredAnimationY(s.animations[state]);
      if (allowed.indexOf(a.motion.type) < 0) {
        a.motion.type = "none";
      }
      t.animations[state] = a;
    });
    // 고급 시간 막대 줄도 거울처럼
    var timeline = this.layout.timeline;
    BV.STATES.forEach(function (state) {
      var clip = timeline && timeline[state];
      if (!clip) {
        return;
      }
      var keys = clip.tracks[sourceKey];
      if (keys && keys.length) {
        clip.tracks[targetKey] = keys.map(function (k) {
          var photo = !!k.photo && k.photo !== BV.BASE_PHOTO;
          return {
            t: k.t, dx: x ? -k.dx : k.dx, dy: x ? k.dy : -k.dy, rotation: -k.rotation, scaleX: k.scaleX, scaleY: k.scaleY,
            ease: k.ease, photo: k.photo || null, flip: x ? photo && !k.flip : !!k.flip
          };
        });
      } else {
        delete clip.tracks[targetKey];
      }
    });
  };

  // ---------------------------------------------------------------- 하나 더 (같은 부위 여러 개)

  /** 이름 뒤에 숫자를 붙여 아직 없는 이름을 만든다 ("꼬리" → "꼬리 2"). */
  P.nextName = function (base) {
    var layout = this.layout;
    var names = BV.partKeys(layout).map(function (k) { return BV.partName(layout, k); });
    var stem = base.replace(/\s+\d+$/, "");
    for (var n = 2; n < 1000; n++) {
      var name = (stem + " " + n).slice(0, 20);
      if (names.indexOf(name) < 0) {
        return name;
      }
    }
    return stem;
  };

  /** 부위를 하나 더 화면에 넣는다: 그림·크기·움직임·시간 막대까지 복사해서 바로 옆에 (기본 부위도 만든 부위로 복사). */
  P.addAnother = function (key) {
    if (key === "eyes") {
      return null;
    }
    if (BV.customKeys(this.layout).length >= this.maxCustom()) {
      this.setStatus("만든 부위는 " + this.maxCustom() + "개까지 넣을 수 있어요");
      return null;
    }
    var copyKey;
    do {
      copyKey = "c_" + Math.random().toString(36).slice(2, 10).replace(/[^a-z0-9]/g, "a");
    } while (this.layout[copyKey]);
    var copy = JSON.parse(JSON.stringify(this.layout[key]));
    copy.name = this.nextName(BV.partName(this.layout, key));
    copy.visible = true;
    copy.x = clamp(copy.x + 4, MAX_OFFSET);
    copy.y = clamp(copy.y + 4, MAX_OFFSET);
    if (!BV.isCustom(key)) {
      copy.shape = "rect"; // 기본 부위(몸 색 네모)를 만든 부위로
    }
    this.layout[copyKey] = BV.normalizePart(copyKey, copy);
    var timeline = this.layout.timeline;
    BV.STATES.forEach(function (state) {
      var clip = timeline && timeline[state];
      if (clip && clip.tracks[key] && clip.tracks[key].length) {
        clip.tracks[copyKey] = JSON.parse(JSON.stringify(clip.tracks[key]));
      }
    });
    this.selection = [copyKey];
    this.selected = copyKey;
    this.afterPartsChanged();
    this.setStatus("'" + copy.name + "' 을(를) 하나 더 넣었어요");
    return copyKey;
  };

  // ---------------------------------------------------------------- 우클릭 메뉴: 그룹

  /** 우클릭 메뉴의 그룹 칸: 여러 개 골랐으면 [그룹으로 묶기], 그룹 부위면 대칭·풀기. */
  P.renderGroupMenu = function (key) {
    var self = this;
    var box = document.getElementById("part-menu-group");
    if (!box) {
      return;
    }
    box.innerHTML = "";
    var group = this.groupOf(key);
    var keys = this.selectedKeys();
    var sameGroup = group && keys.every(function (k) { return group.members.indexOf(k) >= 0; });
    if (keys.length >= 2 && !sameGroup) {
      var make = document.createElement("button");
      make.className = "btn btn-small btn-primary";
      make.id = "part-menu-group-make";
      make.textContent = "그룹으로 묶기 (" + keys.length + "개)";
      make.addEventListener("click", function () {
        self.closePartMenu();
        self.groupSelected();
      });
      box.appendChild(make);
    }
    if (group) {
      var title = document.createElement("span");
      title.className = "menu-group-title";
      title.textContent = "그룹 · 부위 " + group.members.length + "개";
      box.appendChild(title);
      [["x", "좌우 대칭", "mirrorX"], ["y", "상하 대칭", "mirrorY"]].forEach(function (item) {
        var label = document.createElement("label");
        label.className = "check menu-check";
        var input = document.createElement("input");
        input.type = "checkbox";
        input.id = "part-menu-mirror-" + item[0];
        input.checked = !!group[item[2]];
        input.addEventListener("change", function () {
          self.setGroupMirror(group, item[0], input.checked);
        });
        label.appendChild(input);
        label.appendChild(document.createTextNode(" " + item[1]));
        box.appendChild(label);
      });
      var split = document.createElement("button");
      split.className = "btn btn-small";
      split.id = "part-menu-ungroup";
      split.textContent = "그룹 풀기";
      split.addEventListener("click", function () {
        self.closePartMenu();
        self.ungroup(group);
      });
      box.appendChild(split);
    }
    box.hidden = !box.childNodes.length;
  };
})();
