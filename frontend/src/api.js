// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let csrf;
/** 请求超时解除等待，允许保留表单后重试；版本号仍由业务表单保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20000);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch {
    throw new Error("NETWORK_ERROR");
  } finally {
    clearTimeout(timeout);
  }
}
/** 同源请求；CSRF 令牌保存在内存，会话由 HttpOnly Cookie 管理。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, method = "GET", body, csv = false) {
  if (!csrf || path === "/auth/csrf") {
    const response = await request("/api/auth/csrf");
    if (!response.ok) throw new Error("NETWORK_ERROR");
    csrf = await response.json();
    if (path === "/auth/csrf") return csrf;
  }
  const response = await request("/api" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(method === "GET" ? {} : { [csrf.header]: csrf.token }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (response.ok && csv) {
    if (!response.headers.get("Content-Type")?.startsWith("text/csv"))
      throw new Error("NETWORK_ERROR");
    return response.text().catch(() => {
      throw new Error("NETWORK_ERROR");
    });
  }
  const value = await response.json().catch(() => ({ code: "NETWORK_ERROR" }));
  if (!response.ok) {
    if (response.status === 403 && !path.startsWith("/public/")) {
      const session = await request("/api/auth/me");
      if (session.status === 401) {
        csrf = null;
        throw new Error("UNAUTHENTICATED");
      }
    }
    if (response.status === 401) csrf = null;
    throw new Error(
      value.code ||
        (response.status === 413 ? "UPLOAD_TOO_LARGE" : "NETWORK_ERROR"),
    );
  }
  return value;
}
/** 登录使用 POST JSON 和当前 CSRF 令牌；不缓存密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function signIn(credentials) {
  return api("/auth/login", "POST", credentials);
}
/** 会话结束后重新生成 CSRF 引导。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetCsrf() {
  csrf = null;
}
