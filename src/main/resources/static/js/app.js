/*
 * 서버 왕복이 필요 없는 순수 화면 동작만 담당한다.
 * (데이터가 바뀌는 동작은 전부 htmx로 서버에 맡긴다.)
 *
 * - 일정·비고 상세 패널 접기/펴기
 * - 완료율 100% 입력 시 "완료" 배지 즉시 반영 — 인라인 저장은 화면을 다시 그리지 않으므로 여기서 처리
 * - 토스트 / 미리보기 모달 닫기
 */
(function () {
  "use strict";

  var toastTimer = null;

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

  document.addEventListener("click", function (event) {
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
    if (field.matches && field.matches(".completion-input")) {
      var row = field.closest(".row");
      if (row) toggleDoneBadge(row, field.value);
    }
  });

  // htmx가 writeView를 갈아끼운 뒤에도 대기 중인 토스트를 놓치지 않게 한다.
  document.body.addEventListener("htmx:afterSwap", function (event) {
    flushPendingToast(event.target);
  });

  flushPendingToast(document);
})();
