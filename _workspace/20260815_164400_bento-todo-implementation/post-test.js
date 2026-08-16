const base = "http://localhost:9188";

async function post(path, params) {
  const body = new URLSearchParams(params);
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
    body: body.toString(),
  });
  const text = await res.text();
  return { status: res.status, text };
}

(async () => {
  const r1 = await post("/todos", { text: "GTPP 로그인 2차인증 QA 시나리오 작성", dueDate: "2026-08-14", priority: "HIGH" });
  console.log("add1", r1.status);
  const m = r1.text.match(/GTPP[^<]*/);
  console.log("readback:", m && m[0]);

  const r2 = await post("/todos", { text: "SSO 운영 반영 요청서 제출", dueDate: "2026-08-16", priority: "MID" });
  console.log("add2", r2.status);
  const m2 = r2.text.match(/SSO[^<]*/);
  console.log("readback2:", m2 && m2[0]);
})();
