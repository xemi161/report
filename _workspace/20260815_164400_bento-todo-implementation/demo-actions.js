const base = "http://localhost:9188";

async function post(path, params) {
  const opts = { method: "POST" };
  if (params) {
    opts.headers = { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" };
    opts.body = new URLSearchParams(params).toString();
  }
  const res = await fetch(base + path, opts);
  return { status: res.status, text: await res.text() };
}

(async () => {
  // clean up the mojibake test rows from curl (ids 1-3), keep id 4 (clean)
  for (const id of [1, 2, 3]) {
    const r = await post(`/todos/${id}/delete`);
    console.log("delete", id, r.status);
  }

  // add a few clean demo items across different due dates/priorities
  await post("/todos", { text: "결제 배치 재처리 결과 확인", dueDate: "2026-08-14", priority: "HIGH" }); // overdue (today is 08-16)
  await post("/todos", { text: "인증 흐름 문서 최신화", dueDate: "2026-08-18", priority: "LOW" });
  const r = await post("/todos", { text: "배포 체크리스트 검토", dueDate: "2026-08-16", priority: "MID" });
  console.log("added demo items", r.status);

  // toggle id 4 done, cycle priority on it first (before marking done, to show the cycle)
  console.log("priority cycle on id 4:", (await post("/todos/4/priority")).status);
  console.log("priority cycle again on id 4:", (await post("/todos/4/priority")).status);

  // find a currently-open id to mark done for the demo (id 4 should still be open)
  const dash = await (await fetch(base + "/")).text();
  const openIds = [...dash.matchAll(/\/todos\/(\d+)\/done/g)].map(m => m[1]);
  console.log("open ids now:", openIds);
})();
