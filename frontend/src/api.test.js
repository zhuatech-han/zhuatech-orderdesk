// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { api, resetCsrf, signIn } from "./api.js";
test("page sign-in sends credentials as POST JSON after CSRF bootstrap", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts) => {
    calls.push([url, opts]);
    if (url === "/api/auth/csrf")
      return new Response(
        JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
      );
    assert.equal(url, "/api/auth/login");
    assert.equal(opts.method, "POST");
    assert.equal(opts.headers["X-CSRF-TOKEN"], "TEST-token");
    assert.deepEqual(JSON.parse(opts.body), {
      username: "TEST-buyer",
      password: "TEST-password",
    });
    return new Response(
      JSON.stringify({ username: "TEST-buyer", customerId: 1 }),
    );
  };
  try {
    resetCsrf();
    assert.equal(
      (await signIn({ username: "TEST-buyer", password: "TEST-password" }))
        .customerId,
      1,
    );
    assert.equal(calls.length, 2);
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
test("expired session rejected by CSRF requests sign-in without replaying a write", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts) => {
    calls.push([url, opts?.method]);
    if (url === "/api/auth/csrf")
      return new Response(
        JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
      );
    if (url === "/api/auth/me")
      return new Response('{"code":"UNAUTHENTICATED"}', { status: 401 });
    return new Response('{"code":"FORBIDDEN"}', { status: 403 });
  };
  try {
    resetCsrf();
    await assert.rejects(() => api("/orders", "POST", { note: "TEST" }), {
      message: "UNAUTHENTICATED",
    });
    assert.equal(
      calls.filter(
        ([url, method]) => url === "/api/orders" && method === "POST",
      ).length,
      1,
    );
    assert.equal(calls.at(-1)[0], "/api/auth/me");
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("CSV download checks authorization and content type before returning a file", async () => {
  const original = globalThis.fetch;
  try {
    for (const kind of ["csv", "denied", "html"]) {
      resetCsrf();
      globalThis.fetch = async (url, opts) => {
        if (url === "/api/auth/csrf")
          return new Response(
            JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
          );
        assert.equal(url, "/api/orders.csv?date=2026-10-05");
        assert.equal(opts.method, "GET");
        assert.equal(opts.body, undefined);
        if (kind === "denied")
          return new Response('{"code":"UNAUTHENTICATED"}', {
            status: 401,
            headers: { "Content-Type": "application/json" },
          });
        if (kind === "html")
          return new Response("<html>Sign in</html>", {
            headers: { "Content-Type": "text/html" },
          });
        return new Response("reference,sku\r\nTEST-ORDER,TEST-SKU\r\n", {
          headers: { "Content-Type": "text/csv;charset=UTF-8" },
        });
      };
      if (kind === "csv")
        assert.equal(
          await api("/orders.csv?date=2026-10-05", "GET", undefined, true),
          "reference,sku\r\nTEST-ORDER,TEST-SKU\r\n",
        );
      else
        await assert.rejects(
          () => api("/orders.csv?date=2026-10-05", "GET", undefined, true),
          { message: kind === "denied" ? "UNAUTHENTICATED" : "NETWORK_ERROR" },
        );
    }
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
