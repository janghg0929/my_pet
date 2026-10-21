// MY_PET 설정 창 동작.
// Java → JS : window.bitUpdate(state)   (state 는 WebSettings.java 가 만든다)
// JS → Java : send("메서드", 인자...)     (window.bit = Java 쪽 Bridge 객체)

(function () {
  "use strict";

  var MODES = [
    { key: "auto", label: "자동" },
    { key: "off", label: "끔" },
    { key: "always", label: "항상 켬" }
  ];
  var TITLES = { main: "비트 설정", appearance: "외형 설정", color: "색깔 설정", parts: "모습 꾸미기", looks: "저장한 모습" };
  // 뒤로가기로 돌아갈 화면 (없으면 메인)
  var PARENT = { looks: "parts" };
  var DEFAULT_COLOR = "#4fc3f7";

  // 외형 목록. 새 외형을 추가하면 여기에 한 줄 넣으면 된다 (Java 쪽 PetApp.setAppearanceMode 에도 key 추가).
  // keywords: 검색에 걸리는 말 (외형 이름, 앱 이름 - 한글/영어). 띄어쓰기와 대소문자는 무시하고 찾는다.
  var APPEARANCES = [
    {
      key: "browserMonocle", title: "브라우저 돋보기", when: "크롬·엣지를 쓸 때", whenMac: "브라우저를 쓸 때",
      keywords: ["브라우저", "돋보기", "크롬", "구글 크롬", "엣지", "마이크로소프트 엣지", "사파리",
        "browser", "chrome", "google chrome", "edge", "microsoft edge", "safari", "magnifier", "monocle", "magnifying glass"]
    },
    {
      key: "browserIeIcon", title: "브라우저 IE 아이콘", when: "크롬·엣지를 쓸 때", whenMac: "브라우저를 쓸 때",
      keywords: ["브라우저", "아이콘", "인터넷 익스플로러", "익스플로러", "크롬", "구글 크롬", "엣지", "마이크로소프트 엣지", "사파리",
        "browser", "ie", "internet explorer", "explorer", "icon", "chrome", "google chrome", "edge", "microsoft edge", "safari"]
    },
    {
      key: "discordColor", title: "디스코드 색상 변경", when: "디스코드를 쓸 때",
      keywords: ["디스코드", "디코", "색상", "색깔", "색", "discord", "color", "colour"]
    },
    {
      key: "pixelHeart", title: "스팀 픽셀 하트", when: "스팀을 쓸 때",
      keywords: ["스팀", "픽셀", "하트", "게임", "steam", "pixel", "heart", "game"]
    },
    {
      key: "headset", title: "음악 헤드셋", when: "유튜브·스포티파이",
      keywords: ["음악", "헤드셋", "헤드폰", "유튜브", "유튜브 뮤직", "스포티파이", "노래",
        "music", "headset", "headphone", "headphones", "youtube", "youtube music", "spotify", "media"]
    }
  ];
  var PAGE_SIZE = 5; // 한 페이지에 보여줄 외형 수
  var PAGE_GROUP = 5; // 페이지 번호를 몇 개씩 묶어서 보여줄지
  var appearancePage = 1;
  var appearanceQuery = "";
  var listFullHeight = 0; // 외형 5개가 찼을 때 목록 높이 (이 높이로 고정해서 창 크기가 흔들리지 않게)
  var nativeSearch = false; // 앱 안에서는 Java(Swing) 입력칸이 검색창 자리에 겹쳐진다 (한글 입력 때문)
  var looksPage = 1; // 저장한 모습 목록 페이지
  var lastLookCount = -1;

  // 일반 브라우저로 settings.html 을 열었을 때(=Java 연결 없음) 보여줄 예시 데이터
  var DEMO_STATE = {
    version: "미리보기",
    isMac: false,
    petColor: "#4fc3f7",
    baseColor: "#4fc3f7",
    status: "",
    modes: { browserMonocle: "auto", browserIeIcon: "off", discordColor: "auto", pixelHeart: "always", headset: "auto" },
    looks: [
      { id: "demo1", name: "기본 비트", color: null, parts: null },
      { id: "demo2", name: "분홍 비트", color: "#f48fb1", parts: null }
    ]
  };

  var state = null;
  var currentView = "main";

  var stage = null; // 색깔 설정의 걷기/점프 미리보기
  var editor = null; // 모습 꾸미기 편집기 (parts-editor.js)
  var requestedImages = {}; // Java 에 달라고 한 부위 사진

  /** 모습 꾸미기 배치 (Java 가 보낸 값, 없으면 기본 비트). */
  function currentLayout() {
    return state && state.parts ? state.parts : window.BitView.DEFAULT_LAYOUT;
  }

  function drawIcons() {
    if (!state || !window.BitView) {
      return;
    }
    var icons = document.querySelectorAll(".bit-icon");
    for (var i = 0; i < icons.length; i++) {
      window.BitView.drawIcon(icons[i], state.petColor, currentLayout());
    }
  }

  /** 배치에 쓰인 부위 사진 중 아직 없는 것을 Java 에 달라고 한다 (window.bitImage 로 온다). */
  function requestImages(layout) {
    if (!layout) {
      return;
    }
    var names = [];
    window.BitView.partKeys(layout).forEach(function (key) {
      var part = layout[key];
      if (part.image) {
        names.push(part.image);
      }
      if (part.images) {
        Object.keys(part.images).forEach(function (expr) {
          if (part.images[expr]) {
            names.push(part.images[expr]);
          }
        });
      }
    });
    // 고급 시간 막대 키 사진
    var timeline = layout.timeline || {};
    Object.keys(timeline).forEach(function (state) {
      var tracks = (timeline[state] && timeline[state].tracks) || {};
      Object.keys(tracks).forEach(function (part) {
        tracks[part].forEach(function (k) {
          if (k.photo && k.photo !== window.BitView.BASE_PHOTO) {
            names.push(k.photo);
          }
        });
      });
    });
    names.forEach(function (name) {
      if (!window.BitView.images[name] && !requestedImages[name]) {
        requestedImages[name] = true;
        send("requestImage", name);
      }
    });
  }

  // (Java 가 부름) 도트 그림판 그림을 저장했다 (실패면 null)
  window.bitDrawingSaved = function (name) {
    if (editor && editor.onDrawingSaved) {
      editor.onDrawingSaved(name);
    }
  };

  // (Java 가 부름) 이름 입력 창의 대답 (취소면 null)
  window.bitTextAnswer = function (text) {
    if (editor && editor.onTextAnswer) {
      editor.onTextAnswer(text);
    }
  };

  // (Java 가 부름) 고급 시간 막대에서 고른 키 사진 (취소면 null)
  window.bitPickedImage = function (name) {
    if (editor && editor.onPickedImage) {
      editor.onPickedImage(name);
    }
  };

  // (Java 가 부름) 부위 사진 도착
  window.bitImage = function (name, dataUrl) {
    if (!dataUrl) {
      return;
    }
    var image = new Image();
    image.pixelArt = /^px_/.test(name); // 도트 그림: 픽셀이 뭉개지지 않게, 몸 색 칸은 몸 색으로
    image.onload = function () {
      drawIcons();
      drawLookIcons();
      if (editor) {
        editor.draw();
      }
    };
    image.src = dataUrl;
    window.BitView.images[name] = image;
  };

  function send(method) {
    var args = Array.prototype.slice.call(arguments, 1);
    if (window.bit && typeof window.bit[method] === "function") {
      window.bit[method].apply(window.bit, args);
    }
  }

  // 비트 색을 흰색과 섞은 옅은 색 (마우스 올렸을 때 배경)
  function lighten(hex) {
    var n = parseInt(hex.slice(1), 16);
    var r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    function mix(c) { return Math.round((c + 255 * 2) / 3); }
    return "rgb(" + mix(r) + "," + mix(g) + "," + mix(b) + ")";
  }

  // 제목 막대 + 지금 보이는 화면의 실제 높이 (창 크기와 상관없이 잰다)
  function contentHeight() {
    var height = document.querySelector(".titlebar").offsetHeight;
    var view = document.querySelector('.view[data-view="' + currentView + '"]');
    return height + (view ? view.offsetHeight : 0);
  }
  window.bitContentHeight = contentHeight;

  // 화면별 창 내용 너비 (없으면 기본 360). 모습 꾸미기는 미리보기·시간 막대가 넓어지도록
  var VIEW_WIDTHS = { parts: 540 };

  function contentWidth() {
    return VIEW_WIDTHS[currentView] || 360;
  }
  window.bitContentWidth = contentWidth;

  function fitWindow() {
    // 내용 높이에 맞춰 창 크기를 조절해 달라고 Java 에 요청
    send("resize", contentHeight(), contentWidth());
  }

  function show(view) {
    currentView = view;
    var views = document.querySelectorAll(".view");
    for (var i = 0; i < views.length; i++) {
      views[i].hidden = views[i].getAttribute("data-view") !== view;
    }
    document.getElementById("title").textContent = TITLES[view];
    document.querySelector(".back").hidden = view === "main";
    document.getElementById("appearance-search").hidden = view !== "appearance";
    if (view === "appearance") {
      renderAppearanceList(); // 처음 볼 때 목록 높이를 재기 위해 다시 그린다
    }
    if (stage) {
      if (view === "color") {
        stage.start();
      } else {
        stage.stop();
      }
    }
    if (view === "parts" && editor) {
      editor.refreshControls();
      editor.draw();
    }
    if (editor) {
      editor.setVisible(view === "parts");
    }
    if (view === "looks") {
      drawLookIcons(); // 숨겨져 있을 때는 크기가 0 이라 보일 때 그린다
    }
    fitWindow();
    reportSearchBox();
  }

  // 앱 안에서는 검색창 자리에 Java 입력칸을 겹쳐 놓는다: 그 자리(좌표/크기)를 Java 에 알려준다
  function reportSearchBox() {
    if (!nativeSearch) {
      return;
    }
    var box = document.getElementById("appearance-search");
    var r = box.getBoundingClientRect();
    send("searchBox", r.left, r.top, r.width, r.height, !box.hidden, state ? state.petColor : "#4fc3f7");
  }

  function applySearch(text) {
    appearanceQuery = normalize(text);
    appearancePage = 1;
    renderAppearanceList();
  }

  // (Java 가 부름) 앱 안: 한글 조합 중인 글자까지 바로 보이도록 Java 입력칸을 쓴다
  window.bitUseNativeSearch = function () {
    nativeSearch = true;
    var box = document.getElementById("appearance-search");
    box.readOnly = true;
    box.tabIndex = -1;
    box.value = "";
    box.classList.add("native");
    reportSearchBox();
  };

  // (Java 가 부름) Java 입력칸에 글자를 칠 때마다 검색
  window.bitSearch = function (text) {
    applySearch(text);
  };

  // 검색용 정리: 소문자로 바꾸고 띄어쓰기·구분 기호를 뺀다 ("Google Chrome" → "googlechrome")
  function normalize(text) {
    return String(text).toLowerCase().replace(/[\s·\-_.]/g, "");
  }

  // 한글은 중간 글자로도 찾고(익스플로러 ← "플로러"), 영어는 단어 첫머리부터 맞을 때만 찾는다
  // (그래야 "ie" 가 magnifier 같은 단어 속 글자에 걸리지 않는다)
  function matchesQuery(item, query) {
    if (!query) {
      return true;
    }
    var english = /^[a-z0-9]+$/.test(query);
    var words = [item.title, item.when, item.whenMac || ""].concat(item.keywords || []);
    for (var i = 0; i < words.length; i++) {
      var word = String(words[i]).toLowerCase();
      if (!english) {
        if (normalize(word).indexOf(query) >= 0) {
          return true;
        }
        continue;
      }
      // 영어: 단어 첫머리부터 ("google chrome" 은 "google…", "chrome…", "googlechrome…" 으로 찾을 수 있다)
      var parts = word.split(/[\s·\-_.]+/);
      for (var j = 0; j < parts.length; j++) {
        if (normalize(parts.slice(j).join("")).indexOf(query) === 0) {
          return true;
        }
      }
    }
    return false;
  }

  function selectMode(option, modeKey) {
    var buttons = option.querySelectorAll(".seg");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].classList.toggle("selected", buttons[i].getAttribute("data-mode") === modeKey);
    }
  }

  function createOption(item) {
    var option = document.createElement("div");
    option.className = "option";
    option.setAttribute("data-key", item.key);
    var head = document.createElement("div");
    head.className = "option-head";
    var title = document.createElement("b");
    title.textContent = item.title;
    var when = document.createElement("small");
    when.textContent = state && state.isMac && item.whenMac ? item.whenMac : item.when;
    head.appendChild(title);
    head.appendChild(when);
    var group = document.createElement("div");
    group.className = "segmented";
    MODES.forEach(function (mode) {
      var button = document.createElement("button");
      button.className = "seg";
      button.textContent = mode.label;
      button.setAttribute("data-mode", mode.key);
      button.addEventListener("click", function () {
        selectMode(option, mode.key);
        send("setMode", item.key, mode.key);
      });
      group.appendChild(button);
    });
    option.appendChild(head);
    option.appendChild(group);
    if (state && state.modes) {
      selectMode(option, state.modes[item.key]);
    }
    return option;
  }

  // 페이지 번호 줄: 5쪽씩 묶어서 보여준다
  //  3쪽: < 1 2 [3] 4 5 … >   8쪽: < … 6 7 [8] 9 10 … >   13쪽(전체 15): < … 11 12 [13] 14 15 >
  function pagerItems(page, totalPages) {
    var items = [{ label: "<", page: page - 1, disabled: page <= 1, arrow: true }];
    var groupStart = Math.floor((page - 1) / PAGE_GROUP) * PAGE_GROUP + 1;
    var groupEnd = Math.min(groupStart + PAGE_GROUP - 1, totalPages);
    if (groupStart > 1) {
      items.push({ label: "…", page: groupStart - 1, ellipsis: true });
    }
    for (var p = groupStart; p <= groupEnd; p++) {
      items.push({ label: String(p), page: p, current: p === page });
    }
    if (groupEnd < totalPages) {
      items.push({ label: "…", page: groupEnd + 1, ellipsis: true });
    }
    items.push({ label: ">", page: page + 1, disabled: page >= totalPages, arrow: true });
    return items;
  }

  /** 페이지 번호 줄을 그린다. onGo(page) 는 다른 쪽을 눌렀을 때. */
  function renderPager(pager, page, totalPages, onGo) {
    pager.innerHTML = "";
    pagerItems(page, totalPages).forEach(function (item) {
      var button = document.createElement("button");
      button.textContent = item.label;
      button.className = "page" + (item.current ? " current" : "") + (item.arrow ? " arrow" : "") + (item.ellipsis ? " ellipsis" : "");
      button.disabled = !!item.disabled;
      if (item.current) {
        button.setAttribute("aria-current", "page");
      }
      button.addEventListener("click", function () {
        if (!item.disabled && !item.current) {
          onGo(item.page);
        }
      });
      pager.appendChild(button);
    });
  }

  function renderAppearanceList() {
    var found = APPEARANCES.filter(function (item) { return matchesQuery(item, appearanceQuery); });
    var totalPages = Math.max(1, Math.ceil(found.length / PAGE_SIZE));
    appearancePage = Math.min(Math.max(1, appearancePage), totalPages);
    var list = document.getElementById("appearance-list");
    var shown = found.slice((appearancePage - 1) * PAGE_SIZE, appearancePage * PAGE_SIZE);
    // 보이는 외형이 그대로면 선택만 고친다 (상태가 올 때마다 다시 만들면 누르는 중인 버튼이 사라져 클릭을 놓친다)
    var signature = JSON.stringify([shown.map(function (item) { return item.key; }), appearancePage, totalPages, !!(state && state.isMac)]);
    if (list.getAttribute("data-shown") === signature && listFullHeight) {
      Array.prototype.forEach.call(list.querySelectorAll(".option"), function (option) {
        if (state && state.modes) {
          selectMode(option, state.modes[option.getAttribute("data-key")]);
        }
      });
    } else {
      list.setAttribute("data-shown", signature);
      list.innerHTML = "";
      shown.forEach(function (item) {
        list.appendChild(createOption(item));
      });
      if (found.length === 0) {
        var empty = document.createElement("p");
        empty.className = "empty";
        empty.textContent = "검색 결과가 없어요";
        list.appendChild(empty);
      }
      fixListHeight(list, shown.length);
      renderPager(document.getElementById("appearance-pager"), appearancePage, totalPages, function (page) {
        appearancePage = page;
        renderAppearanceList();
      });
    }
    if (currentView === "appearance") {
      fitWindow();
    }
  }

  // 목록 높이를 "외형 5개가 찼을 때" 높이로 고정한다 (처음 5개를 그렸을 때 한 번 잰다)
  function fixListHeight(list, rowCount) {
    if (!listFullHeight && rowCount > 0 && list.offsetHeight > 0) {
      var first = list.querySelector(".option");
      var rowHeight = first ? first.offsetHeight : 0;
      var style = window.getComputedStyle(list);
      var frame = parseFloat(style.borderTopWidth) + parseFloat(style.borderBottomWidth);
      // 둘째 줄부터는 위쪽 점선(1px)이 붙는다
      listFullHeight = rowHeight * PAGE_SIZE + (PAGE_SIZE - 1) + frame;
    }
    list.style.minHeight = listFullHeight ? listFullHeight + "px" : "";
  }

  // ---------------------------------------------------------------- 저장한 모습

  function looks() {
    return state && state.looks ? state.looks : [];
  }

  function findLook(id) {
    var all = looks();
    for (var i = 0; i < all.length; i++) {
      if (all[i].id === id) {
        return all[i];
      }
    }
    return null;
  }

  function drawLookIcons() {
    var icons = document.querySelectorAll("#looks-list .look-icon");
    for (var i = 0; i < icons.length; i++) {
      var look = findLook(icons[i].getAttribute("data-id"));
      if (look) {
        window.BitView.drawIcon(icons[i], look.color || DEFAULT_COLOR, look.parts || window.BitView.DEFAULT_LAYOUT);
      }
    }
  }

  function smallButton(label, className, onClick) {
    var button = document.createElement("button");
    button.className = "btn btn-small" + (className ? " " + className : "");
    button.textContent = label;
    button.addEventListener("click", onClick);
    return button;
  }

  function createLookRow(look) {
    var row = document.createElement("div");
    row.className = "look";
    var icon = document.createElement("canvas");
    icon.className = "look-icon";
    icon.setAttribute("data-id", look.id);
    var name = document.createElement("span");
    name.className = "look-name";
    name.textContent = look.name;
    name.title = look.name;
    var buttons = document.createElement("span");
    buttons.className = "look-buttons";
    buttons.appendChild(smallButton("불러오기", "btn-primary", function () { send("applyLook", look.id); }));
    buttons.appendChild(smallButton("이름", "", function () { send("renameLook", look.id); }));
    // 지우기는 두 번 눌러야 한다
    var armed = false;
    var remove = smallButton("삭제", "btn-danger", function () {
      if (!armed) {
        armed = true;
        remove.textContent = "정말?";
        setTimeout(function () {
          armed = false;
          remove.textContent = "삭제";
        }, 3000);
        return;
      }
      send("deleteLook", look.id);
    });
    buttons.appendChild(remove);
    row.appendChild(icon);
    row.appendChild(name);
    row.appendChild(buttons);
    return row;
  }

  function renderLooks() {
    var all = looks();
    var totalPages = Math.max(1, Math.ceil(all.length / PAGE_SIZE));
    looksPage = Math.min(Math.max(1, looksPage), totalPages);
    var list = document.getElementById("looks-list");
    var shown = all.slice((looksPage - 1) * PAGE_SIZE, looksPage * PAGE_SIZE);
    // 보이는 모습이 그대로면 그림만 다시 (다시 만들면 누르는 중인 버튼이 사라지고, "정말?" 도 풀린다)
    var signature = JSON.stringify([shown.map(function (look) { return [look.id, look.name, look.color, look.parts]; }), looksPage, totalPages]);
    if (list.getAttribute("data-shown") !== signature) {
      list.setAttribute("data-shown", signature);
      list.innerHTML = "";
      shown.forEach(function (look) {
        list.appendChild(createLookRow(look));
      });
      if (all.length === 0) {
        var empty = document.createElement("p");
        empty.className = "empty";
        empty.textContent = "아직 저장한 모습이 없어요";
        list.appendChild(empty);
      }
      renderPager(document.getElementById("looks-pager"), looksPage, totalPages, function (page) {
        looksPage = page;
        renderLooks();
      });
    }
    window.BitView.setText(document.getElementById("looks-count"), all.length ? "(" + all.length + ")" : "");
    all.forEach(function (look) { requestImages(look.parts); });
    if (currentView === "looks") {
      drawLookIcons();
    }
  }

  function render() {
    if (!state) {
      return;
    }
    var root = document.documentElement.style;
    root.setProperty("--pet", state.petColor);
    root.setProperty("--pet-light", lighten(state.petColor));

    window.BitView.setText(document.getElementById("version"), "v" + state.version);
    window.BitView.setText(document.getElementById("hex"), state.baseColor.toUpperCase());
    var layout = currentLayout();
    requestImages(layout);
    drawIcons();
    // 색깔 미리보기는 (디스코드 색이 아닌) 원래 몸 색으로 보여준다
    if (stage) {
      stage.color = state.baseColor;
      stage.layout = layout;
    }
    if (editor) {
      editor.setLayout(layout);
    }

    renderAppearanceList();
    // 새로 저장했으면 마지막 쪽(새 모습)이 보이게
    var lookCount = looks().length;
    if (lastLookCount >= 0 && lookCount > lastLookCount) {
      looksPage = Math.ceil(lookCount / PAGE_SIZE);
    }
    lastLookCount = lookCount;
    renderLooks();

    if (state.status) {
      var STATUS = { color: "color-status", parts: "parts-status", looks: "looks-status" };
      var target = STATUS[currentView] || "appearance-status";
      document.getElementById(target).textContent = state.status;
    }
    if (currentView === "color" && !state.status) {
      document.getElementById("color-status").textContent = "현재 색상: " + state.baseColor;
    }
  }

  window.bitUpdate = function (newState) {
    state = newState;
    render();
    fitWindow();
    reportSearchBox();
  };

  document.addEventListener("click", function (event) {
    var target = event.target.closest ? event.target.closest("[data-go],[data-action]") : null;
    if (!target) {
      return;
    }
    var go = target.getAttribute("data-go");
    if (go) {
      if (go === "color") {
        document.getElementById("color-status").textContent = state ? "현재 색상: " + state.baseColor : "";
      }
      show(go);
      return;
    }
    var action = target.getAttribute("data-action");
    if (action === "back") {
      show(PARENT[currentView] || "main");
    } else {
      send(action);
    }
  });

  if (window.BitView) {
    stage = new window.BitView.Stage(document.querySelector(".bit-stage"));
  }
  if (window.PartsEditor) {
    editor = new window.PartsEditor({
      send: send,
      getColor: function () { return state ? state.baseColor : "#4fc3f7"; }
    });
  }
  // 창 너비가 바뀌면(화면마다 너비가 다르다) 줄바꿈이 달라질 수 있어 높이를 다시 잰다
  var refitTimer = null;
  window.addEventListener("resize", function () {
    clearTimeout(refitTimer);
    refitTimer = setTimeout(function () {
      fitWindow();
      reportSearchBox();
    }, 50);
  });
  var search = document.getElementById("appearance-search");
  // 일반 브라우저 미리보기에서는 이 HTML 입력칸을 그대로 쓴다
  search.addEventListener("input", function () {
    if (!nativeSearch) {
      applySearch(search.value);
    }
  });
  search.addEventListener("keydown", function (event) {
    if (!nativeSearch && (event.key === "Escape" || event.keyCode === 27)) {
      search.value = "";
      applySearch("");
    }
  });
  renderAppearanceList();
  show("main");
  send("ready");

  // 미리보기/테스트용: 모습 꾸미기 편집기
  window.bitPartsEditor = editor;

  // 미리보기/테스트용: 외형 목록과 페이지 번호 계산을 밖에서 볼 수 있게 한다
  window.bitAppearances = {
    items: APPEARANCES,
    pagerItems: pagerItems,
    refresh: renderAppearanceList,
    goToPage: function (page) { appearancePage = page; renderAppearanceList(); }
  };

  // Java 가 연결되지 않았으면(브라우저 미리보기) 예시 데이터로 그린다
  setTimeout(function () {
    if (!state && !window.bit) {
      window.bitUpdate(DEMO_STATE);
    }
  }, 300);
})();
