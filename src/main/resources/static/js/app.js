/*
 * 서버 왕복이 필요 없는 순수 화면 동작만 담당한다.
 * (데이터가 바뀌는 동작은 전부 htmx로 서버에 맡긴다.)
 *
 * - 일정·비고 상세 패널 접기/펴기
 * - 완료율 100% 입력 시 "완료" 배지 즉시 반영 — 인라인 저장은 화면을 다시 그리지 않으므로 여기서 처리
 * - 토스트 / 미리보기 모달 닫기
 * - 일일 기록: textarea 자동 높이, 날짜 헤더 합계 재계산, 추가 후 입력칸 재포커스, 좌측 패널 숨기기
 *   (기록의 인라인 수정도 hx-swap="none"이라 합계·칩을 서버가 못 갱신한다 — "완료" 배지와 같은 자리)
 * - TODO 리스트: 접기/펼치기, 완료 목록 여닫기, 추가줄 우선순위 pill 순환, 지표 타일 동기화
 *   (서버 왕복이 필요 없거나, 서버가 갱신해줄 수 없는 카드 바깥 영역이라 여기서 처리한다)
 */
(function () {
  "use strict";

  var toastTimer = null;
  var PANEL_HIDDEN_KEY = "weeklyReport.dailyPanelHidden";

  function showToast(message) {
    var toast = document.getElementById("toast");
    if (!toast || !message) return;
    toast.textContent = message;
    toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove("show");
    }, 2200);
  }

  /** 서버가 writeView 안에 심어둔 토스트 문구를 화면 갱신 후 집어서 띄운다. */
  function flushPendingToast(root) {
    var holder = (root || document).querySelector("[data-toast]");
    if (!holder) return;
    showToast(holder.getAttribute("data-toast"));
    holder.removeAttribute("data-toast");
  }

  function closeModal() {
    var host = document.getElementById("modalHost");
    if (host) host.innerHTML = "";
  }

  function toggleDoneBadge(row, completionValue) {
    var main = row.querySelector(".row-main");
    if (!main) return;
    var badge = main.querySelector(".badge.done");
    var isDone = Number(completionValue) === 100;
    if (isDone && !badge) {
      var span = document.createElement("span");
      span.className = "badge done";
      span.textContent = "완료";
      main.insertBefore(span, main.querySelector("[data-detail-toggle]"));
    } else if (!isDone && badge) {
      badge.remove();
    }
  }

  // ---------- 일일 기록(한 일) ----------

  /** "8.50" → "8.5", "4.00" → "4". 서버의 hoursDisplay()와 같은 표기 규칙. */
  function formatHours(value) {
    return Number(value.toFixed(2)).toString();
  }

  function sumHoursOf(rows) {
    var sum = 0;
    for (var i = 0; i < rows.length; i++) {
      var input = rows[i].querySelector(".hours-input");
      var value = input ? parseFloat(input.value) : NaN;
      if (!isNaN(value)) sum += value;
    }
    return sum;
  }

  /**
   * 한 줄짜리 textarea가 내용 높이에 맞게 늘어나도록. 긴 기록/할 일이 잘리면 "보면서 쓴다"는 목적이 깨진다.
   * ⚠️ 감춰진 요소(접힌 할 일·완료 목록)는 scrollHeight가 0이라 그대로 재면 height:0px으로 굳어,
   *    나중에 펼쳤을 때 글자가 안 보인다 — 그럴 땐 인라인 높이를 지우고 펼쳐질 때 다시 잰다(applyTodoState).
   */
  function autoGrow(el) {
    el.style.height = "auto";
    var height = el.scrollHeight;
    el.style.height = height > 0 ? height + "px" : "";
  }

  function autoGrowAll(root) {
    var boxes = (root || document).querySelectorAll(".d-txt, .t-txt");
    for (var i = 0; i < boxes.length; i++) autoGrow(boxes[i]);
  }

  /**
   * 날짜 헤더의 합계만 다시 계산해 갈아끼운다 — 입력 포커스를 잃지 않으려고 행 전체를 다시 그리지 않는다.
   * 합이 0이면 아예 지운다("0h"를 들이밀면 채워야 할 빈칸으로 읽혀 선택 입력 성격과 충돌).
   */
  function refreshDaySum(group) {
    var head = group.querySelector(".day-head");
    if (!head) return;
    var sum = sumHoursOf(group.querySelectorAll(".daily-item"));
    var el = head.querySelector(".day-sum");
    if (sum <= 0) {
      if (el) el.remove();
      return;
    }
    if (!el) {
      el = document.createElement("span");
      el.className = "day-sum tabular";
      head.appendChild(el);
    }
    el.textContent = formatHours(sum) + "h";
  }

  /**
   * 대시보드 카드의 오늘 기록은 날짜 헤더 없이 바로 깔리므로 합계를 카드 제목 칩이 대신 받는다.
   * (하단 "이번 주 기록" 요약은 화면에 없는 날의 기록까지 포함하는 값이라 여기서 계산하지 않는다 —
   *  추가/삭제 때 서버가 카드를 통째로 다시 그리면서 정확한 값으로 돌아온다.)
   */
  function refreshTodayChip(card) {
    var chip = card.querySelector(".card-title .count");
    if (!chip) return;
    // 오늘 기록은 날짜 그룹 없이 타일 본문(.tile-body) 바로 아래에 깔린다 — 그 직계 행들만 센다
    var rows = card.querySelectorAll(":scope > .tile-body > .daily-item");
    var sum = sumHoursOf(rows);
    chip.textContent = rows.length + "건" + (sum > 0 ? " · " + formatHours(sum) + "h" : "");
  }

  function refreshDailyTotals(row) {
    var group = row.closest(".day-group");
    if (group) {
      refreshDaySum(group);
      return;
    }
    var card = row.closest(".dash-daily");
    if (card) refreshTodayChip(card);
  }

  // ---------- TODO 리스트 ----------
  // 서버는 할 일을 전부 내려주고(접힌 것 포함) 화면이 클래스로 감춘다 — "+N건 더 보기"에 서버 왕복을 만들지 않기 위해서다.
  // 세 상태는 메모리에만 둔다: 카드가 htmx로 통째로 교체되는 사이에는 유지되고, 페이지를 새로 열면 초기값으로 돌아간다
  // (승인된 목업의 todoExpanded/todoDoneOpen/todoNewPrio와 같은 수명 — 취향 설정이 아니라 그때그때의 열람 상태라서).

  var todoExpanded = false;
  var todoDoneOpen = false;
  var todoNewPriority = null; // null이면 서버가 렌더한 기본값(보통)을 그대로 둔다

  function todoCard() {
    return document.getElementById("todoCard");
  }

  /** 버튼 라벨은 서버가 data-*로 실어 보낸 것만 쓴다(건수·한국어 표기를 여기서 만들지 않는다). */
  function swapLabel(button, attr) {
    if (!button) return;
    var label = button.getAttribute(attr);
    var holder = button.querySelector("span");
    if (label && holder) holder.textContent = label;
  }

  function applyTodoState(card) {
    if (!card) return;
    card.classList.toggle("expanded", todoExpanded);
    card.classList.toggle("done-open", todoDoneOpen);
    swapLabel(card.querySelector("[data-todo-more]"), todoExpanded ? "data-less-label" : "data-more-label");
    swapLabel(card.querySelector("[data-todo-donetoggle]"), todoDoneOpen ? "data-hide-label" : "data-show-label");
    // 방금 드러난 행들은 감춰져 있던 동안 높이를 잴 수 없었다 — 보이게 된 지금 다시 잰다
    autoGrowAll(card);
  }

  /** 추가줄 pill의 순환 순서/라벨은 서버가 숨은 목록으로 내려준다(HIGH·MID·LOW 표기를 JS에 하드코딩하지 않으려고). */
  function priorityOptions(card) {
    var items = card.querySelectorAll("[data-prio-map] i");
    var out = [];
    for (var i = 0; i < items.length; i++) {
      out.push({ value: items[i].getAttribute("data-value"), label: items[i].textContent.trim() });
    }
    return out;
  }

  function paintNewPriority(card, option) {
    var button = card.querySelector("[data-todo-newprio]");
    var field = card.querySelector("[data-todo-newprio-value]");
    if (!button || !field || !option) return;
    button.className = "prio " + option.value.toLowerCase();
    button.textContent = option.label;
    button.title = "우선순위 " + option.label + " · 클릭해 변경";
    field.value = option.value;
  }

  /** 카드가 다시 그려지면 서버 기본값으로 돌아오므로, 사용자가 고른 값이 있으면 다시 칠한다. */
  function applyNewPriority(card) {
    if (!card || !todoNewPriority) return;
    var options = priorityOptions(card);
    for (var i = 0; i < options.length; i++) {
      if (options[i].value === todoNewPriority) {
        paintNewPriority(card, options[i]);
        return;
      }
    }
  }

  function cycleNewPriority(card) {
    var options = priorityOptions(card);
    var field = card.querySelector("[data-todo-newprio-value]");
    if (!options.length || !field) return;
    var current = 0;
    for (var i = 0; i < options.length; i++) {
      if (options[i].value === field.value) current = i;
    }
    var next = options[(current + 1) % options.length];
    todoNewPriority = next.value;
    paintNewPriority(card, next);
  }

  /**
   * "기한 지난 할 일" 지표 타일은 TODO 카드 밖(벤토 상단)에 있어 htmx 스왑 대상이 아니다.
   * 서버가 카드에 실어 보낸 수치를 스왑 직후 여기로 옮겨 적는다 — DOM에서 행을 세지 않는 이유는
   * 접혀서 감춰진 행도 DOM에는 남아 있어 "무엇을 세는가"가 카드 마크업 구조에 묶여버리기 때문이다.
   */
  function syncTodoMetric(card) {
    var tile = document.querySelector("[data-metric-overdue]");
    if (!tile || !card) return;
    var overdue = Number(card.getAttribute("data-overdue-count") || 0);
    var dueToday = Number(card.getAttribute("data-duetoday-count") || 0);
    var number = tile.querySelector(".v .num");
    var sub = tile.querySelector(".s");
    if (number) number.textContent = overdue;
    if (sub) sub.textContent = dueToday > 0 ? "오늘 마감 " + dueToday + "건" : "오늘 마감 없음";
    tile.classList.toggle("low", overdue > 0);
  }

  /** 작성 화면 좌측 패널 표시/숨김. 숨기면 body.wide가 풀려 기존 단일 컬럼(1140px)으로 돌아간다. */
  function applyPanelState(hidden) {
    var split = document.getElementById("splitLayout");
    if (!split) return;
    var panel = document.getElementById("weekPanel");
    var showBtn = split.querySelector("[data-panel-show]");
    if (panel) panel.hidden = hidden;
    if (showBtn) showBtn.hidden = !hidden;
    split.classList.toggle("panel-hidden", hidden);
    document.body.classList.toggle("wide", !hidden);
  }

  function storePanelState(hidden) {
    try {
      if (hidden) window.localStorage.setItem(PANEL_HIDDEN_KEY, "1");
      else window.localStorage.removeItem(PANEL_HIDDEN_KEY);
    } catch (e) {
      /* localStorage를 못 쓰는 환경이면 이번 화면에서만 적용된다 */
    }
  }

  function panelHiddenStored() {
    try {
      return window.localStorage.getItem(PANEL_HIDDEN_KEY) === "1";
    } catch (e) {
      return false;
    }
  }

  document.addEventListener("click", function (event) {
    if (event.target.closest("[data-panel-hide]")) {
      applyPanelState(true);
      storePanelState(true);
      return;
    }
    if (event.target.closest("[data-panel-show]")) {
      applyPanelState(false);
      storePanelState(false);
      return;
    }

    // TODO 카드의 접기/펼치기·완료 여닫기·추가줄 우선순위는 서버에 물어볼 것이 없다(할 일은 이미 전부 받아뒀다)
    if (event.target.closest("[data-todo-more]")) {
      todoExpanded = !todoExpanded;
      applyTodoState(todoCard());
      return;
    }
    if (event.target.closest("[data-todo-donetoggle]")) {
      todoDoneOpen = !todoDoneOpen;
      applyTodoState(todoCard());
      return;
    }
    if (event.target.closest("[data-todo-newprio]")) {
      var addCard = todoCard();
      if (addCard) cycleNewPriority(addCard);
      return;
    }

    var toggle = event.target.closest("[data-detail-toggle]");
    if (toggle) {
      var panel = document.getElementById(toggle.getAttribute("data-detail-toggle"));
      if (panel) {
        panel.classList.toggle("open");
        toggle.classList.toggle("open");
      }
      return;
    }

    if (event.target.closest("[data-close-modal]")) {
      closeModal();
      return;
    }
    // 모달 바깥(배경) 클릭으로 닫기
    if (event.target.classList && event.target.classList.contains("modal-backdrop")) {
      closeModal();
    }
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") closeModal();
  });

  document.addEventListener("input", function (event) {
    var field = event.target;
    if (!field.matches) return;

    if (field.matches(".completion-input")) {
      var row = field.closest(".row");
      if (row) toggleDoneBadge(row, field.value);
      return;
    }
    if (field.matches(".d-txt, .t-txt")) {
      autoGrow(field);
      return;
    }
    // 기록 행의 시간 칸만 대상 — 주간보고 항목 행의 .hours-input과 클래스가 같아서 범위를 좁힌다
    if (field.matches(".hours-input")) {
      var dailyRow = field.closest(".daily-item");
      if (dailyRow) refreshDailyTotals(dailyRow);
    }
  });

  // 기록·할 일은 한 줄짜리 메모다 — Enter로 줄을 늘리는 대신 편집을 끝낸다(blur가 change를 일으켜 저장된다).
  document.addEventListener("keydown", function (event) {
    var el = event.target;
    if (el.matches && el.matches(".d-txt, .t-txt") && event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      el.blur();
    }
  });

  // htmx가 화면 일부를 갈아끼운 뒤: 대기 중인 토스트를 띄우고, 새로 들어온 textarea 높이를 맞추고,
  // 기록을 추가한 직후라면 입력칸으로 커서를 돌려놓는다(연달아 여러 건 적는 흐름을 끊지 않는다).
  document.body.addEventListener("htmx:afterSwap", function (event) {
    var swapped = event.target;
    flushPendingToast(swapped);
    autoGrowAll(swapped);

    var config = event.detail && event.detail.requestConfig;
    if (!config || config.verb !== "post" || !swapped.querySelector) return;
    var path = String(config.path || "");

    // TODO 카드는 어떤 조작이든 통째로 교체되므로, 화면에만 있던 상태(펼침·고른 우선순위)를 다시 입힌다.
    if (swapped.id === "todoCard") {
      applyTodoState(swapped);
      applyNewPriority(swapped);
      syncTodoMetric(swapped);
      // 방금 추가한 경우에만 커서를 입력칸으로 돌려놓는다(완료 토글·삭제까지 포커스를 뺏으면 안 된다).
      if (/^\/todos(\?|$)/.test(path)) {
        var todoInput = swapped.querySelector("[data-todo-newtext]");
        if (todoInput) todoInput.focus();
      }
      return;
    }

    if (path.indexOf("/daily-notes") !== 0 || path.indexOf("/delete") !== -1) return;
    var input = swapped.querySelector("[data-daily-newtext]");
    if (input) input.focus();
  });

  /**
   * ⚠️ 카드 클래스(.expanded/.done-open)는 afterSwap만으로는 유지되지 않는다.
   * htmx는 class를 attributesToSettle 대상으로 삼아, 스왑 직후에는 옛 요소의 class를 잠시 그대로 두었다가
   * settle 시점(기본 20ms 뒤)에 서버가 준 값으로 되돌린다 — afterSwap에서 붙인 클래스는 그때 조용히 지워진다.
   * (실제로 겪은 버그: 펼쳐둔 목록이 할 일 하나를 추가하자마자 다시 접혔다. 라벨 텍스트는 속성이 아니라
   *  textContent라 살아남아서 "접기"라고 적힌 채 접혀 있는 상태가 됐다.)
   * 그래서 afterSwap에서 한 번(라벨을 즉시 맞추려고), afterSettle에서 한 번 더(클래스를 최종적으로 입히려고) 부른다.
   */
  document.body.addEventListener("htmx:afterSettle", function (event) {
    if (event.target && event.target.id === "todoCard") applyTodoState(event.target);
  });

  flushPendingToast(document);
  autoGrowAll(document);
  applyPanelState(panelHiddenStored());
})();
