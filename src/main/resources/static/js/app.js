/*
 * 서버 왕복이 필요 없는 순수 화면 동작만 담당한다.
 * (데이터가 바뀌는 동작은 전부 htmx로 서버에 맡긴다.)
 *
 * - 일정·비고 상세 패널 접기/펴기
 * - 완료율 100% 입력 시 "완료" 배지 즉시 반영 — 인라인 저장은 화면을 다시 그리지 않으므로 여기서 처리
 * - 토스트 / 미리보기 모달 닫기
 * - 일일 기록: textarea 자동 높이, 날짜 헤더 합계 재계산, 추가 후 입력칸 재포커스, 좌측 패널 숨기기
 *   (기록의 인라인 수정도 hx-swap="none"이라 합계·칩을 서버가 못 갱신한다 — "완료" 배지와 같은 자리)
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

  /** 한 줄짜리 textarea가 내용 높이에 맞게 늘어나도록. 긴 기록이 잘리면 "보면서 쓴다"는 목적이 깨진다. */
  function autoGrow(el) {
    el.style.height = "auto";
    el.style.height = el.scrollHeight + "px";
  }

  function autoGrowAll(root) {
    var boxes = (root || document).querySelectorAll(".d-txt");
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
    var rows = card.querySelectorAll(":scope > .daily-item");
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
    if (field.matches(".d-txt")) {
      autoGrow(field);
      return;
    }
    // 기록 행의 시간 칸만 대상 — 주간보고 항목 행의 .hours-input과 클래스가 같아서 범위를 좁힌다
    if (field.matches(".hours-input")) {
      var dailyRow = field.closest(".daily-item");
      if (dailyRow) refreshDailyTotals(dailyRow);
    }
  });

  // 기록은 한 줄짜리 메모다 — Enter로 줄을 늘리는 대신 편집을 끝낸다(blur가 change를 일으켜 저장된다).
  document.addEventListener("keydown", function (event) {
    var el = event.target;
    if (el.matches && el.matches(".d-txt") && event.key === "Enter" && !event.shiftKey) {
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
    if (path.indexOf("/daily-notes") !== 0 || path.indexOf("/delete") !== -1) return;
    var input = swapped.querySelector("[data-daily-newtext]");
    if (input) input.focus();
  });

  flushPendingToast(document);
  autoGrowAll(document);
  applyPanelState(panelHiddenStored());
})();
