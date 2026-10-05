<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, reactive, computed, onMounted, nextTick } from "vue";
import {
  LayoutDashboard,
  Package,
  Users,
  ShoppingCart,
  ClipboardList,
  Settings,
  History,
  ChartNoAxesColumn,
  Plus,
  Search,
  LogOut,
  RefreshCw,
  ArrowLeft,
  X,
  Menu,
  Download,
  Globe,
  Trash2,
} from "@lucide/vue";
import { api, resetCsrf, signIn } from "./api.js";
import { forms } from "./schema.js";
import { money, pageRows, searchText } from "./format.js";
import { states, errors, validQty, parsePrices } from "./order.js";
const lang = ref(localStorage.getItem("orderdesk-language") || "zh");
const t = (zh, en) => (lang.value === "en" ? en : zh);
const label = (s) => states[s]?.[lang.value === "en" ? 1 : 0] || s;
const moneyValue = (v) => money(v, data.currency, lang.value);
const me = ref(null),
  view = ref("dashboard"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  navOpen = ref(false),
  search = ref(""),
  status = ref(""),
  page = ref(1),
  descending = ref(true),
  detail = ref(null),
  adminType = ref("users"),
  modal = ref(null),
  modalElement = ref(null),
  returnFocus = ref(null),
  importFile = ref(null),
  importRows = ref(null),
  previewError = ref("");
const credentials = reactive({ username: "", password: "" });
const data = reactive({
  customers: [],
  products: [],
  prices: [],
  departments: [],
  categories: [],
  currency: "CNY",
  orders: [],
  audit: [],
  report: null,
  portal: null,
  admin: {
    users: [],
    roles: [],
    departments: [],
    permissions: [],
    menus: [],
    dictionaries: [],
    settings: [],
  },
});
const draftId = ref(null),
  draftRevision = ref(null),
  templateId = ref(null),
  templateRevision = ref(null),
  templateName = ref("");
const cart = reactive({}),
  deliveryDate = ref(new Date().toISOString().slice(0, 10)),
  orderNote = ref("");
const icons = {
  dashboard: LayoutDashboard,
  customers: Users,
  products: Package,
  prices: ClipboardList,
  orders: ClipboardList,
  portal: ShoppingCart,
  reports: ChartNoAxesColumn,
  admin: Settings,
  audit: History,
};
const can = (p) => me.value?.permissions.includes(p);
const isCustomer = computed(() => me.value?.customerId != null);
const title = computed(
  () =>
    me.value?.menus.find((x) => x.code === view.value)?.[
      lang.value === "en" ? "nameEn" : "name"
    ] || t("工作台", "Workspace"),
);
const adminNames = {
  users: ["账号", "Accounts"],
  roles: ["角色", "Roles"],
  departments: ["部门", "Departments"],
  permissions: ["权限", "Permissions"],
  menus: ["菜单", "Menus"],
  dictionaries: ["字典", "Dictionaries"],
  settings: ["参数", "Settings"],
};
const categoryName = (code) => {
  const v = data.categories.find((x) => x.code === code);
  return v?.[lang.value === "en" ? "nameEn" : "name"] || code;
};
const productName = (p) => (lang.value === "en" ? p.nameEn : p.name);
const sourceRows = computed(() =>
  view.value === "admin"
    ? data.admin[adminType.value]
    : view.value === "portal"
      ? (data.portal?.items || []).map((x) => ({ ...x, id: x.product.id }))
      : data[view.value] || [],
);
const filteredRows = computed(() =>
  sourceRows.value.filter(
    (x) =>
      (!status.value || x.status === status.value) &&
      searchText(x).includes(search.value.toLowerCase()),
  ),
);
const rows = computed(() =>
  pageRows(filteredRows.value, "", page.value, 10, descending.value),
);
const orderRows = computed(() =>
  pageRows(
    (isCustomer.value ? data.portal?.orders || [] : data.orders).filter(
      (x) => !status.value || x.status === status.value,
    ),
    search.value,
    page.value,
    10,
    descending.value,
  ),
);
const orderCount = computed(
  () =>
    (isCustomer.value ? data.portal?.orders || [] : data.orders).filter(
      (x) =>
        (!status.value || x.status === status.value) &&
        searchText(x).includes(search.value.toLowerCase()),
    ).length,
);
const portalTab = ref("catalog");
const totalCount = computed(() =>
  view.value === "orders" ||
  (view.value === "portal" && portalTab.value === "orders")
    ? orderCount.value
    : filteredRows.value.length,
);
const cartItems = computed(() =>
  Object.entries(cart)
    .filter(([, q]) => Number(q) > 0)
    .map(([id, q]) => ({
      item: data.portal?.items.find((x) => x.product.id === Number(id)),
      qty: Number(q),
      productId: Number(id),
    })),
);
const invalidCart = computed(() =>
  cartItems.value.some((x) => !x.item || !validQty(x.qty, x.item)),
);
const cartTotal = computed(() =>
  cartItems.value.reduce(
    (sum, x) => sum + (x.item ? Number(x.item.unitPrice) * x.qty : 0),
    0,
  ),
);
const columns = computed(() =>
  view.value === "admin"
    ? forms[adminType.value].filter((x) => x.key !== "password")
    : forms[view.value] || [],
);
const formFields = computed(() =>
  modal.value?.kind === "resource"
    ? forms[modal.value.type]
    : modal.value?.kind === "password"
      ? forms.password
      : [],
);
const form = reactive({});
function toggleLanguage() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("orderdesk-language", lang.value);
  document.documentElement.lang = lang.value === "en" ? "en" : "zh-CN";
}
function resetFilters() {
  search.value = "";
  status.value = "";
  page.value = 1;
  descending.value = true;
}
async function run(action) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await action();
  } catch (e) {
    error.value =
      errors[e.message]?.[lang.value === "en" ? 1 : 0] ||
      t("操作未完成：", "Operation failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") {
      me.value = null;
      resetCsrf();
    }
  } finally {
    busy.value = false;
  }
}
async function reload() {
  me.value = await api("/auth/me");
  if (isCustomer.value) {
    data.portal = await api("/portal");
    data.currency = data.portal.currency;
    data.categories = data.portal.categories;
    data.companyName = data.portal.companyName;
  } else {
    if (can("read")) data.orders = await api("/orders");
    if (can("dashboard")) {
      const c = await api("/catalog");
      Object.assign(data, c);
      data.report = await api("/reports");
    }
    if (can("master")) {
      for (const type of ["customers", "products", "prices"])
        data[type] = await api("/master/" + type);
    }
    if (can("admin")) {
      for (const type of Object.keys(data.admin))
        data.admin[type] = await api("/admin/" + type);
    }
    if (can("audit")) data.audit = await api("/audit");
  }
  if (!me.value.menus.some((x) => x.code === view.value))
    view.value = me.value.menus[0]?.code || "dashboard";
  page.value = Math.min(
    page.value,
    Math.max(1, Math.ceil(totalCount.value / 10)),
  );
  if (detail.value)
    detail.value = await api("/orders/" + detail.value.order.id);
}
function go(code) {
  view.value = code;
  detail.value = null;
  resetFilters();
  navOpen.value = false;
}
async function login() {
  await run(async () => {
    me.value = await signIn(credentials);
    credentials.password = "";
    view.value = isCustomer.value ? "portal" : "dashboard";
    await reload();
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    me.value = null;
    detail.value = null;
    clearCart();
    resetCsrf();
  });
}
function clearCart() {
  for (const k of Object.keys(cart)) delete cart[k];
  draftId.value = null;
  draftRevision.value = null;
  templateId.value = null;
  templateRevision.value = null;
  templateName.value = "";
  orderNote.value = "";
  deliveryDate.value = new Date().toISOString().slice(0, 10);
}
function options(field) {
  if (Array.isArray(field.options))
    return field.options.map((v) => ({ value: v[0], label: t(v[1], v[2]) }));
  let items =
    field.options === "customers"
      ? data.customers
      : field.options === "products"
        ? data.products
        : field.options === "categories"
          ? data.categories
          : data.admin[field.options] || data[field.options] || [];
  return items.map((x) => ({
    value:
      field.options === "categories"
        ? x.code
        : field.options === "permissions"
          ? x.code
          : x.id,
    label:
      field.options === "products"
        ? x.sku + " · " + productName(x)
        : x.name || x.displayName || x.code,
  }));
}
function display(row, field) {
  const v = row[field.key];
  if (field.key === "permissions")
    return v
      .map(
        (code) =>
          data.admin.permissions.find((x) => x.code === code)?.name || code,
      )
      .join(" · ");
  if (field.type === "checkbox")
    return v ? t("启用", "Enabled") : t("停用", "Disabled");
  if (field.key === "category") return categoryName(v);
  if (field.type === "select")
    return options(field).find((x) => x.value === v)?.label || "—";
  return v ?? "—";
}
async function openModal(value) {
  error.value = "";
  returnFocus.value = document.activeElement;
  modal.value = value;
  await nextTick();
  modalElement.value?.querySelector("input,select,textarea,button")?.focus();
}
function closeModal() {
  if (busy.value) return;
  modal.value = null;
  error.value = "";
  nextTick(() => returnFocus.value?.focus());
}
function trap(event) {
  if (event.key === "Escape") {
    closeModal();
    return;
  }
  if (event.key !== "Tab") return;
  const nodes = [
    ...modalElement.value.querySelectorAll(
      "button:not([disabled]),input:not([disabled]),textarea,select",
    ),
  ];
  const first = nodes[0],
    last = nodes.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
async function edit(type, row = null) {
  for (const k of Object.keys(form)) delete form[k];
  for (const f of forms[type])
    form[f.key] =
      f.type === "checkbox"
        ? true
        : f.type === "permissions"
          ? []
          : f.type === "number"
            ? 1
            : "";
  if (type === "prices") {
    form.minQty = 1;
    form.stepQty = 1;
    form.maxQty = 10000;
  }
  if (row) Object.assign(form, row);
  if (type === "users") form.password = "";
  await openModal({
    kind: "resource",
    type,
    id: row?.id || null,
    title:
      t(row ? "修改" : "新增", row ? "Edit" : "New") +
      " · " +
      (adminNames[type] ? t(...adminNames[type]) : title.value),
  });
}
async function saveResource() {
  await run(async () => {
    const m = modal.value;
    const body = { ...form };
    for (const f of formFields.value) {
      if (
        f.type === "select" &&
        !Array.isArray(f.options) &&
        !["categories", "permissions"].includes(f.options)
      )
        body[f.key] =
          body[f.key] === "" || body[f.key] == null
            ? null
            : Number(body[f.key]);
      if (f.type === "number") body[f.key] = Number(body[f.key]);
    }
    if (m.type === "users" && m.id && !body.password) delete body.password;
    const prefix = view.value === "admin" ? "/admin/" : "/master/";
    await api(
      prefix + m.type + (m.id ? "/" + m.id : ""),
      m.id ? "PUT" : "POST",
      body,
    );
    modal.value = null;
    await reload();
    notice.value = t("已保存", "Saved");
  });
}
async function removeResource(type, row) {
  await openModal({
    kind: "delete",
    title: t("删除记录", "Delete record"),
    message: t(
      "已有业务引用的记录不能删除。",
      "Referenced business records cannot be deleted.",
    ),
    path:
      (view.value === "admin" ? "/admin/" : "/master/") +
      type +
      "/" +
      row.id +
      (view.value === "admin" ? "" : "?revision=" + row.revision),
  });
}
async function confirmDelete() {
  await run(async () => {
    await api(modal.value.path, "DELETE");
    modal.value = null;
    detail.value = null;
    await reload();
    notice.value = t("已删除", "Deleted");
  });
}
async function openOrder(id) {
  await run(async () => {
    detail.value = await api("/orders/" + id);
  });
}
async function saveDraft() {
  await run(async () => {
    if (invalidCart.value) throw new Error("INVALID_QUANTITY");
    const o = await api(
      "/orders" + (draftId.value ? "/" + draftId.value : ""),
      draftId.value ? "PUT" : "POST",
      {
        deliveryDate: deliveryDate.value,
        note: orderNote.value,
        lines: cartItems.value.map((x) => ({
          productId: x.productId,
          qty: x.qty,
        })),
        revision: draftRevision.value,
      },
    );
    clearCart();
    await reload();
    detail.value = await api("/orders/" + o.id);
    portalTab.value = "orders";
    notice.value = t(
      "草稿已保存，请核对后提交",
      "Draft saved; review before submitting",
    );
  });
}
function fillCart(input) {
  if (
    input.some(
      (x) => !data.portal.items.some((i) => i.product.id === x.productId),
    )
  )
    throw new Error("PRODUCT_UNAVAILABLE");
  clearCart();
  for (const x of input) cart[x.productId] = x.qty;
  portalTab.value = "catalog";
  detail.value = null;
  resetFilters();
}
async function editDraft() {
  await run(async () => {
    const d = detail.value;
    fillCart(d.lines);
    draftId.value = d.order.id;
    draftRevision.value = d.order.revision;
    deliveryDate.value = d.order.deliveryDate;
    orderNote.value = d.order.note;
  });
}
async function repeatOrder() {
  await run(async () => {
    fillCart(detail.value.lines);
    notice.value = t(
      "已载入商品，请核对当前协议价与数量",
      "Products loaded; check current prices and quantities",
    );
  });
}
async function useTemplate(row, editing = false) {
  await run(async () => {
    fillCart(row.lines);
    if (editing) {
      templateId.value = row.template.id;
      templateRevision.value = row.template.revision;
      templateName.value = row.template.name;
    }
    notice.value = t("已载入常购清单", "Saved list loaded");
  });
}
async function saveTemplate() {
  await run(async () => {
    await api(
      "/templates" + (templateId.value ? "/" + templateId.value : ""),
      templateId.value ? "PUT" : "POST",
      {
        name: templateName.value,
        lines: cartItems.value.map((x) => ({
          productId: x.productId,
          qty: x.qty,
        })),
        revision: templateRevision.value,
      },
    );
    await reload();
    templateId.value = null;
    templateRevision.value = null;
    notice.value = t("常购清单已保存", "Saved list updated");
  });
}
async function command(action) {
  form.note = "";
  await openModal({
    kind: "action",
    action,
    title: t(
      {
        submit: "提交订单",
        confirm: "确认接单",
        reject: "退回修订",
        cancel: "取消订单",
        revise: "重新修订",
      }[action],
      {
        submit: "Submit order",
        confirm: "Confirm order",
        reject: "Return order",
        cancel: "Cancel order",
        revise: "Revise order",
      }[action],
    ),
  });
}
async function saveCommand() {
  await run(async () => {
    const d = detail.value;
    await api(
      "/orders/" + d.order.id + "/actions/" + modal.value.action,
      "POST",
      { revision: d.order.revision, note: form.note },
    );
    modal.value = null;
    await reload();
    notice.value = t("订单状态已更新", "Order status updated");
  });
}
async function openDispatch() {
  form.reference = "";
  form.note = "";
  form.lines = detail.value.lines
    .filter((x) => x.fulfilled < x.qty)
    .map((x) => ({ ...x, deliveryQty: 0 }));
  await openModal({
    kind: "dispatch",
    title: t("登记分批交接", "Record fulfilment"),
  });
}
async function saveDispatch() {
  await run(async () => {
    const o = detail.value.order;
    await api("/orders/" + o.id + "/dispatches", "POST", {
      revision: o.revision,
      reference: form.reference,
      note: form.note,
      lines: form.lines
        .filter((x) => x.deliveryQty > 0)
        .map((x) => ({ orderLineId: x.id, qty: Number(x.deliveryQty) })),
    });
    modal.value = null;
    await reload();
    notice.value = t("交接已记录", "Fulfilment recorded");
  });
}
async function passwordModal() {
  form.oldPassword = "";
  form.newPassword = "";
  await openModal({
    kind: "password",
    title: t("修改密码", "Change password"),
  });
}
async function changePassword() {
  await run(async () => {
    await api("/auth/password", "POST", form);
    modal.value = null;
    me.value = null;
    resetCsrf();
    notice.value = t(
      "密码已修改，请重新登录",
      "Password changed; sign in again",
    );
  });
}
async function download(path) {
  await run(async () => {
    const csv = await api(path, "GET", undefined, true);
    const url = URL.createObjectURL(
      new Blob([csv], { type: "text/csv;charset=utf-8" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = path.startsWith("/orders.csv")
      ? "orderdesk-orders.csv"
      : "orderdesk-prices.csv";
    document.body.append(a);
    a.click();
    a.remove();
    // Keep the file available while the browser starts its download.
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  });
}
const exportDate = ref(new Date().toISOString().slice(0, 10));
async function previewImport(e) {
  previewError.value = "";
  importRows.value = null;
  importFile.value = e.target.files[0];
  try {
    if (!importFile.value) return;
    if (importFile.value.size > 524288) throw new Error("IMPORT_TOO_LARGE");
    const text = new TextDecoder("utf-8", { fatal: true }).decode(
      await importFile.value.arrayBuffer(),
    );
    importRows.value = parsePrices(text);
  } catch (err) {
    previewError.value =
      errors[err.message]?.[lang.value === "en" ? 1 : 0] ||
      t("文件不是有效UTF-8 CSV", "File is not valid UTF-8 CSV");
    if (err.recordNumber)
      previewError.value +=
        t(
          " · 第" + err.recordNumber + "条数据：",
          " · Data record " + err.recordNumber + ": ",
        ) + err.detail[lang.value === "en" ? 1 : 0];
  }
}
async function importPrices() {
  await run(async () => {
    await api("/prices/import", "POST", importRows.value);
    modal.value = null;
    importRows.value = null;
    await reload();
    notice.value = t("整批价表已保存", "Price batch saved");
  });
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    view.value = isCustomer.value ? "portal" : "dashboard";
    await run(reload);
  } catch {
    me.value = null;
  }
});
</script>

<template>
  <div v-if="!me" class="login-page">
    <form class="login" @submit.prevent="login">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>OrderDesk</strong
          ><span>{{
            t("知华批发客户订货", "ZhuaTech customer ordering")
          }}</span>
        </div>
      </div>
      <div class="login-heading">
        <h1>{{ t("登录订货工作台", "Sign in") }}</h1>
        <button type="button" class="plain" @click="toggleLanguage">
          <Globe :size="16" />{{ lang === "zh" ? "English" : "中文" }}
        </button>
      </div>
      <label
        >{{ t("登录账号", "Username")
        }}<input
          v-model="credentials.username"
          autocomplete="username"
          required
          maxlength="60"
      /></label>
      <label
        >{{ t("密码", "Password")
        }}<input
          v-model="credentials.password"
          type="password"
          autocomplete="current-password"
          required
          maxlength="128"
      /></label>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <p v-if="notice" role="status" class="success">{{ notice }}</p>
      <button class="primary full" :disabled="busy">
        {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in") }}
      </button>
      <div class="license">
        {{
          t(
            "公开源码学习版 · 商用须书面授权",
            "Non-commercial source edition · Written permission required for business use",
          )
        }}<br />上海如静知华信息科技有限公司<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >zhuatech.cn</a
        >
        · {{ t("咨询微信", "WeChat") }} zhuatech / zhuatech2
      </div>
    </form>
  </div>
  <div v-else class="shell" :class="{ 'customer-shell': isCustomer }">
    <aside :class="{ opened: navOpen }">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>OrderDesk</strong
          ><span>{{
            data.companyName || t("知华批发订货", "ZhuaTech ordering")
          }}</span>
        </div>
      </div>
      <nav>
        <button
          v-for="m in me.menus"
          :key="m.id"
          :class="{ active: view === m.code }"
          @click="go(m.code)"
        >
          <component :is="icons[m.code] || ClipboardList" :size="18" />{{
            lang === "en" ? m.nameEn : m.name
          }}
        </button>
      </nav>
      <div class="aside-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技 · 商业咨询", "ZhuaTech · Business enquiries")
        }}</a
        ><span>zhuatech / zhuatech2</span
        ><span>{{ t("公开源码学习版", "Non-commercial source edition") }}</span>
      </div>
    </aside>
    <main>
      <header>
        <div class="header-title">
          <button
            class="icon mobile-menu"
            :aria-label="t('菜单', 'Menu')"
            @click="navOpen = !navOpen"
          >
            <Menu :size="20" />
          </button>
          <h1>{{ title }}</h1>
        </div>
        <div class="account">
          <button class="plain" @click="toggleLanguage">
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><span>{{ me.displayName }}</span
          ><button class="plain" @click="passwordModal">
            {{ t("改密", "Password") }}</button
          ><button
            class="icon"
            :disabled="busy"
            :aria-label="t('退出', 'Sign out')"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <div class="page-body">
        <div v-if="error" class="error" role="alert">
          {{ error
          }}<button
            class="plain"
            :aria-label="t('关闭提示', 'Dismiss message')"
            @click="error = ''"
          >
            ×
          </button>
        </div>
        <div v-if="notice" class="success" role="status">{{ notice }}</div>
        <div v-if="detail" class="detail">
          <div class="section-heading">
            <button class="plain" @click="detail = null">
              <ArrowLeft :size="16" />{{
                t("返回列表", "Back to list")
              }}</button
            ><button
              class="plain"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="run(reload)"
            >
              <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
            </button>
          </div>
          <div class="order-heading">
            <div>
              <span class="eyebrow">{{ detail.order.customerName }}</span>
              <h2>{{ detail.order.reference }}</h2>
            </div>
            <span class="badge" :class="detail.order.status">{{
              label(detail.order.status)
            }}</span>
          </div>
          <div class="order-info">
            <div>
              <span>{{ t("要求交付日期", "Requested delivery") }}</span
              ><strong>{{ detail.order.deliveryDate }}</strong>
            </div>
            <div>
              <span>{{ t("配送地址", "Delivery address") }}</span
              ><strong>{{ detail.order.address }}</strong>
            </div>
            <div>
              <span>{{ t("下单时间 · UTC", "Created · UTC") }}</span
              ><strong>{{
                detail.order.createdAt.replace("T", " ").slice(0, 19)
              }}</strong>
            </div>
            <div>
              <span>{{ t("订单备注", "Order note") }}</span
              ><strong>{{ detail.order.note || "—" }}</strong>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("商品", "Product") }}</th>
                  <th>{{ t("单位", "Unit") }}</th>
                  <th>{{ t("单价", "Unit price") }}</th>
                  <th>{{ t("数量", "Quantity") }}</th>
                  <th>{{ t("已交付", "Fulfilled") }}</th>
                  <th>{{ t("金额", "Amount") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="l in detail.lines" :key="l.id">
                  <td>
                    <strong>{{ productName(l) }}</strong
                    ><small>{{ l.sku }}</small>
                  </td>
                  <td>{{ l.unit }}</td>
                  <td>{{ moneyValue(l.unitPrice) }}</td>
                  <td>{{ l.qty }}</td>
                  <td>{{ l.fulfilled }}</td>
                  <td>{{ moneyValue(Number(l.unitPrice) * l.qty) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="order-total">
            <span>{{ t("订单总额", "Order total") }}</span
            ><strong>{{ moneyValue(detail.order.total) }}</strong>
          </div>
          <div class="actions">
            <template v-if="isCustomer"
              ><button
                v-if="detail.order.status === 'DRAFT'"
                :disabled="busy"
                @click="editDraft"
              >
                {{ t("修改草稿 / 重新核价", "Edit draft / reprice") }}</button
              ><button
                v-if="detail.order.status === 'DRAFT'"
                class="primary"
                :disabled="busy"
                @click="command('submit')"
              >
                {{ t("提交订单", "Submit order") }}</button
              ><button
                v-if="['DRAFT', 'SUBMITTED'].includes(detail.order.status)"
                :disabled="busy"
                @click="command('cancel')"
              >
                {{ t("取消订单", "Cancel order") }}</button
              ><button
                v-if="detail.order.status === 'REJECTED'"
                :disabled="busy"
                @click="command('revise')"
              >
                {{ t("重新修订", "Revise order") }}</button
              ><button :disabled="busy" @click="repeatOrder">
                {{ t("再次订购", "Order again") }}</button
              ><button
                v-if="detail.order.status === 'DRAFT' && !detail.events.length"
                class="danger plain"
                @click="
                  openModal({
                    kind: 'delete',
                    title: t('删除草稿', 'Delete draft'),
                    path:
                      '/orders/' +
                      detail.order.id +
                      '?revision=' +
                      detail.order.revision,
                  })
                "
              >
                {{ t("删除草稿", "Delete draft") }}
              </button></template
            ><template v-else-if="can('process')"
              ><button
                v-if="detail.order.status === 'SUBMITTED'"
                class="primary"
                :disabled="busy"
                @click="command('confirm')"
              >
                {{ t("确认接单", "Confirm order") }}</button
              ><button
                v-if="detail.order.status === 'SUBMITTED'"
                :disabled="busy"
                @click="command('reject')"
              >
                {{ t("退回修订", "Return for revision") }}</button
              ><button
                v-if="detail.order.status === 'CONFIRMED'"
                class="primary"
                :disabled="busy"
                @click="openDispatch"
              >
                {{ t("登记交接", "Record fulfilment") }}</button
              ><button
                v-if="
                  ['SUBMITTED', 'CONFIRMED'].includes(detail.order.status) &&
                  detail.lines.every((l) => l.fulfilled === 0)
                "
                :disabled="busy"
                @click="command('cancel')"
              >
                {{ t("取消订单", "Cancel order") }}
              </button></template
            >
          </div>
          <section v-if="detail.dispatches.length" class="history">
            <h3>{{ t("分批交接", "Fulfilment records") }}</h3>
            <article v-for="d in detail.dispatches" :key="d.dispatch.id">
              <strong>{{ d.dispatch.reference }}</strong
              ><span
                >{{ d.dispatch.createdAt.replace("T", " ").slice(0, 19) }} UTC ·
                {{ d.dispatch.actor }}</span
              >
              <p>{{ d.dispatch.note }}</p>
              <p v-for="l in d.lines" :key="l.id">
                {{ detail.lines.find((x) => x.id === l.orderLineId)?.sku }} ×
                {{ l.qty }}
              </p>
            </article>
          </section>
          <section class="history">
            <h3>{{ t("流程历史", "Workflow history") }}</h3>
            <p v-if="!detail.events.length" class="muted">
              {{ t("草稿尚未提交", "Draft has not been submitted") }}
            </p>
            <article v-for="e in detail.events" :key="e.id">
              <strong>{{
                t(
                  {
                    SUBMIT: "客户提交",
                    CONFIRM: "供应商确认",
                    REJECT: "退回修订",
                    CANCEL: "取消订单",
                    REVISE: "客户开始修订",
                    DISPATCH: "分批交接",
                  }[e.action] || e.action,
                  {
                    SUBMIT: "Customer submitted",
                    CONFIRM: "Supplier confirmed",
                    REJECT: "Returned",
                    CANCEL: "Cancelled",
                    REVISE: "Revision started",
                    DISPATCH: "Fulfilment recorded",
                  }[e.action] || e.action,
                )
              }}</strong
              ><span
                >{{ e.createdAt.replace("T", " ").slice(0, 19) }} UTC ·
                {{ e.actor }}</span
              >
              <p>{{ e.note }}</p>
            </article>
          </section>
        </div>
        <template v-else>
          <template v-if="view === 'dashboard' || view === 'reports'"
            ><div class="section-heading">
              <h2>{{ t("订单概况", "Order overview") }}</h2>
              <button :disabled="busy" class="plain" @click="run(reload)">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
              </button>
            </div>
            <div v-if="data.report" class="metrics">
              <button
                v-for="s in ['SUBMITTED', 'CONFIRMED', 'FULFILLED']"
                :key="s"
                @click="
                  go('orders');
                  status = s;
                "
              >
                <span>{{ label(s) }}</span
                ><strong>{{ data.report.statuses[s] }}</strong>
              </button>
              <div>
                <span>{{
                  t("确认与交付订单金额", "Confirmed & fulfilled value")
                }}</span
                ><strong>{{ moneyValue(data.report.confirmedValue) }}</strong>
              </div>
            </div>
            <div v-if="view === 'reports'" class="export-box">
              <div>
                <h3>{{ t("仓库订单交接", "Warehouse order handoff") }}</h3>
                <p class="muted">
                  {{
                    t(
                      "按提交日（UTC）导出已确认订单及剩余数量。",
                      "Export confirmed orders and remaining quantities by submission date (UTC).",
                    )
                  }}
                </p>
              </div>
              <label
                >{{ t("提交日期", "Submission date")
                }}<input v-model="exportDate" type="date" /></label
              ><button
                v-if="can('report')"
                :disabled="!exportDate"
                @click="download('/orders.csv?date=' + exportDate)"
              >
                <Download :size="16" />{{ t("导出CSV", "Export CSV") }}
              </button>
            </div>
            <section v-if="data.report" class="panel">
              <div class="section-heading">
                <h3>{{ t("最近订单", "Recent orders") }}</h3>
                <button class="plain" @click="go('orders')">
                  {{ t("全部订单", "All orders") }} →
                </button>
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("订单", "Order") }}</th>
                      <th>{{ t("客户", "Customer") }}</th>
                      <th>{{ t("日期", "Requested date") }}</th>
                      <th>{{ t("金额", "Value") }}</th>
                      <th>{{ t("状态", "Status") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="o in data.report.recent" :key="o.id">
                      <td>
                        <button class="link" @click="openOrder(o.id)">
                          {{ o.reference }}
                        </button>
                      </td>
                      <td>{{ o.customerName }}</td>
                      <td>{{ o.deliveryDate }}</td>
                      <td>{{ moneyValue(o.total) }}</td>
                      <td>
                        <span class="badge" :class="o.status">{{
                          label(o.status)
                        }}</span>
                      </td>
                    </tr>
                    <tr v-if="!data.report.recent.length">
                      <td colspan="5" class="empty">
                        {{ t("尚无订单", "No orders yet") }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section></template
          >
          <template v-else-if="view === 'portal'"
            ><div class="customer-heading">
              <div>
                <span class="eyebrow">{{ data.portal?.customer.code }}</span>
                <h2>{{ data.portal?.customer.name }}</h2>
                <p class="muted">{{ data.portal?.customer.paymentTerms }}</p>
              </div>
              <button
                class="plain"
                :aria-label="t('刷新', 'Refresh')"
                :disabled="busy"
                @click="run(reload)"
              >
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
              </button>
            </div>
            <div class="tabs">
              <button
                v-for="tab in [
                  ['catalog', '商品订货', 'Order sheet'],
                  ['orders', '订单记录', 'Order history'],
                  ['templates', '常购清单', 'Saved lists'],
                ]"
                :key="tab[0]"
                :class="{ active: portalTab === tab[0] }"
                @click="
                  portalTab = tab[0];
                  resetFilters();
                "
              >
                {{ t(tab[1], tab[2]) }}
              </button>
            </div>
            <template v-if="portalTab === 'catalog'"
              ><div class="ordering-grid">
                <section>
                  <div class="toolbar">
                    <label class="search"
                      ><Search :size="17" /><input
                        v-model="search"
                        :placeholder="
                          t('搜索商品或编码', 'Search product or SKU')
                        "
                        @input="page = 1" /></label
                    ><span class="muted"
                      >{{ filteredRows.length }}
                      {{ t("种商品", "products") }}</span
                    >
                  </div>
                  <div class="table-wrap">
                    <table class="order-sheet">
                      <thead>
                        <tr>
                          <th>{{ t("商品", "Product") }}</th>
                          <th>{{ t("协议价 / 单位", "Price / unit") }}</th>
                          <th>{{ t("订货量", "Quantity") }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="row in rows" :key="row.product.id">
                          <td>
                            <strong>{{ productName(row.product) }}</strong
                            ><small
                              >{{ row.product.sku }} ·
                              {{ categoryName(row.product.category) }}</small
                            >
                          </td>
                          <td>
                            {{ moneyValue(row.unitPrice)
                            }}<small>{{ row.product.unit }}</small>
                          </td>
                          <td>
                            <input
                              v-model.number="cart[row.product.id]"
                              type="number"
                              min="0"
                              :max="row.maxQty"
                              step="1"
                              :aria-label="
                                t('数量 ', 'Quantity ') + row.product.sku
                              "
                            /><small
                              >{{ t("最少", "Min") }} {{ row.minQty }} ·
                              {{ t("每增", "Step") }} {{ row.stepQty }} ·
                              {{ t("上限", "Max") }} {{ row.maxQty }}</small
                            ><small
                              v-if="
                                cart[row.product.id] > 0 &&
                                !validQty(Number(cart[row.product.id]), row)
                              "
                              class="danger"
                              >{{
                                t(
                                  "数量不符合规则",
                                  "Quantity does not match rules",
                                )
                              }}</small
                            >
                          </td>
                        </tr>
                        <tr v-if="!rows.length">
                          <td colspan="3" class="empty">
                            {{
                              t(
                                "暂无可订商品，请联系供应商设置目录",
                                "No available products; ask your supplier to configure the catalogue",
                              )
                            }}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <button
                      :aria-label="t('上一页', 'Previous page')"
                      :disabled="page === 1"
                      @click="page--"
                    >
                      ←</button
                    ><span
                      >{{ page }} /
                      {{
                        Math.max(1, Math.ceil(filteredRows.length / 10))
                      }}</span
                    ><button
                      :aria-label="t('下一页', 'Next page')"
                      :disabled="page * 10 >= filteredRows.length"
                      @click="page++"
                    >
                      →
                    </button>
                  </div>
                </section>
                <form class="basket" @submit.prevent="saveDraft">
                  <div class="section-heading">
                    <h3>
                      {{
                        draftId
                          ? t("修改草稿", "Edit draft")
                          : t("本次订货", "Current order")
                      }}
                    </h3>
                    <button type="button" class="plain" @click="clearCart">
                      {{ t("清空", "Clear") }}
                    </button>
                  </div>
                  <p v-if="!cartItems.length" class="muted">
                    {{
                      t(
                        "在商品表填写订货数量",
                        "Enter quantities in the order sheet",
                      )
                    }}
                  </p>
                  <div
                    v-for="x in cartItems"
                    :key="x.productId"
                    class="basket-line"
                  >
                    <span
                      >{{
                        x.item
                          ? productName(x.item.product)
                          : "#" + x.productId
                      }}<small
                        >{{ x.qty }} {{ x.item?.product.unit }}</small
                      ></span
                    ><strong>{{
                      moneyValue(Number(x.item?.unitPrice || 0) * x.qty)
                    }}</strong
                    ><button
                      type="button"
                      class="icon"
                      :aria-label="t('移除', 'Remove')"
                      @click="delete cart[x.productId]"
                    >
                      <X :size="15" />
                    </button>
                  </div>
                  <div class="basket-total">
                    <span>{{ t("预计金额", "Estimated total") }}</span
                    ><strong>{{ moneyValue(cartTotal) }}</strong>
                  </div>
                  <label
                    >{{ t("要求交付日期", "Requested delivery date")
                    }}<input
                      v-model="deliveryDate"
                      type="date"
                      required /></label
                  ><label
                    >{{ t("订单备注", "Order note")
                    }}<textarea
                      v-model="orderNote"
                      maxlength="1000"
                      rows="2"
                    ></textarea>
                  </label>
                  <p class="muted small">{{ data.portal?.notice }}</p>
                  <button
                    class="primary full"
                    :disabled="busy || !cartItems.length || invalidCart"
                  >
                    {{ t("保存草稿并核对", "Save draft & review") }}
                  </button>
                  <div class="template-save">
                    <label
                      >{{
                        templateId
                          ? t("修改常购清单名称", "Edit saved list name")
                          : t("另存常购清单", "Save a frequent list")
                      }}<input v-model="templateName" maxlength="160" /></label
                    ><button
                      type="button"
                      :disabled="
                        busy ||
                        !templateName.trim() ||
                        !cartItems.length ||
                        invalidCart
                      "
                      @click="saveTemplate"
                    >
                      {{ t("保存清单", "Save list") }}
                    </button>
                  </div>
                </form>
              </div></template
            >
            <template v-else-if="portalTab === 'templates'"
              ><section class="panel">
                <div
                  v-for="row in data.portal?.templates"
                  :key="row.template.id"
                  class="template-row"
                >
                  <div>
                    <h3>{{ row.template.name }}</h3>
                    <span class="muted"
                      >{{ row.lines.length }}
                      {{
                        t(
                          "项商品 · 使用当前协议价",
                          "products · Current negotiated prices apply",
                        )
                      }}</span
                    >
                  </div>
                  <div class="actions">
                    <button
                      class="primary"
                      :disabled="busy"
                      @click="useTemplate(row)"
                    >
                      {{ t("使用清单", "Use list") }}</button
                    ><button :disabled="busy" @click="useTemplate(row, true)">
                      {{ t("修改", "Edit") }}</button
                    ><button
                      class="plain danger"
                      :aria-label="t('删除清单', 'Delete list')"
                      @click="
                        openModal({
                          kind: 'delete',
                          title: t('删除清单', 'Delete list'),
                          path:
                            '/templates/' +
                            row.template.id +
                            '?revision=' +
                            row.template.revision,
                        })
                      "
                    >
                      <Trash2 :size="16" />
                    </button>
                  </div>
                </div>
                <p v-if="!data.portal?.templates.length" class="empty">
                  {{
                    t(
                      "填写商品数量后可以保存常购清单",
                      "Enter product quantities to save a frequent list",
                    )
                  }}
                </p>
              </section></template
            >
          </template>
          <template v-else-if="view === 'admin'"
            ><div class="tabs">
              <button
                v-for="(names, k) in adminNames"
                :key="k"
                :class="{ active: adminType === k }"
                @click="
                  adminType = k;
                  resetFilters();
                "
              >
                {{ t(...names) }}
              </button>
            </div></template
          >
          <template
            v-if="
              view === 'orders' || (view === 'portal' && portalTab === 'orders')
            "
            ><div class="toolbar">
              <label class="search"
                ><Search :size="17" /><input
                  v-model="search"
                  :placeholder="
                    t('搜索订单、客户或备注', 'Search order, customer or note')
                  "
                  @input="page = 1" /></label
              ><select
                v-model="status"
                :aria-label="t('状态筛选', 'Filter status')"
                @change="page = 1"
              >
                <option value="">{{ t("全部状态", "All statuses") }}</option>
                <option v-for="(_, s) in states" :key="s" :value="s">
                  {{ label(s) }}
                </option></select
              ><button
                class="plain"
                :aria-label="t('刷新', 'Refresh')"
                :disabled="busy"
                @click="run(reload)"
              >
                <RefreshCw :size="16" />
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("订单 / 客户", "Order / customer") }}</th>
                    <th>{{ t("要求交付", "Requested date") }}</th>
                    <th>{{ t("金额", "Value") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="o in orderRows" :key="o.id">
                    <td>
                      <strong>{{ o.reference }}</strong
                      ><small>{{ o.customerName }}</small>
                    </td>
                    <td>{{ o.deliveryDate }}</td>
                    <td>{{ moneyValue(o.total) }}</td>
                    <td>
                      <span class="badge" :class="o.status">{{
                        label(o.status)
                      }}</span>
                    </td>
                    <td>
                      <button class="link" @click="openOrder(o.id)">
                        {{ t("查看", "Open") }} →
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!orderRows.length">
                    <td colspan="5" class="empty">
                      {{ t("没有匹配的订单", "No matching orders") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div></template
          >
          <template
            v-if="['customers', 'products', 'prices', 'admin'].includes(view)"
            ><div class="toolbar">
              <label class="search"
                ><Search :size="17" /><input
                  v-model="search"
                  :placeholder="t('搜索当前列表', 'Search this list')"
                  @input="page = 1" /></label
              ><template v-if="view === 'prices'"
                ><button @click="download('/prices.csv')">
                  <Download :size="16" />CSV</button
                ><button
                  @click="
                    importRows = null;
                    previewError = '';
                    openModal({
                      kind: 'import',
                      title: t('批量维护价表', 'Import customer prices'),
                    });
                  "
                >
                  {{ t("导入", "Import") }}
                </button></template
              ><button
                v-if="
                  view === 'admin'
                    ? !['permissions', 'menus', 'settings'].includes(adminType)
                    : view !== 'products' || me.scope === 'ALL'
                "
                class="primary"
                :disabled="busy"
                @click="edit(view === 'admin' ? adminType : view)"
              >
                <Plus :size="16" />{{ t("新增", "New") }}</button
              ><button
                class="plain"
                :aria-label="t('刷新', 'Refresh')"
                :disabled="busy"
                @click="run(reload)"
              >
                <RefreshCw :size="16" />
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-if="view === 'admin' && adminType === 'settings'">
                      {{ t("参数", "Setting") }}
                    </th>
                    <th v-for="field in columns" :key="field.key">
                      {{ t(field.zh, field.en) }}
                    </th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in rows" :key="row.id">
                    <td v-if="view === 'admin' && adminType === 'settings'">
                      <strong>{{ row.code }}</strong>
                    </td>
                    <td
                      v-for="field in columns"
                      :key="field.key"
                      class="bounded"
                    >
                      {{ display(row, field) }}
                    </td>
                    <td class="row-actions">
                      <button
                        class="link"
                        :disabled="
                          busy || (view === 'products' && me.scope !== 'ALL')
                        "
                        @click="edit(view === 'admin' ? adminType : view, row)"
                      >
                        {{ t("修改", "Edit") }}</button
                      ><button
                        v-if="
                          view === 'admin'
                            ? !['permissions', 'menus', 'settings'].includes(
                                adminType,
                              )
                            : view !== 'products' || me.scope === 'ALL'
                        "
                        class="plain danger"
                        :disabled="busy"
                        @click="
                          removeResource(
                            view === 'admin' ? adminType : view,
                            row,
                          )
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!rows.length">
                    <td :colspan="columns.length + 2" class="empty">
                      {{ t("没有匹配的记录", "No matching records") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div></template
          >
          <template v-if="view === 'audit'"
            ><div class="toolbar">
              <label class="search"
                ><Search :size="17" /><input
                  v-model="search"
                  :placeholder="t('搜索账号或动作', 'Search actor or action')"
                  @input="page = 1" /></label
              ><button
                class="plain"
                :aria-label="t('刷新', 'Refresh')"
                :disabled="busy"
                @click="run(reload)"
              >
                <RefreshCw :size="16" />
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间 · UTC", "Time · UTC") }}</th>
                    <th>{{ t("账号", "Actor") }}</th>
                    <th>{{ t("动作", "Action") }}</th>
                    <th>{{ t("记录", "Record") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in rows" :key="row.id">
                    <td>{{ row.createdAt.replace("T", " ").slice(0, 19) }}</td>
                    <td>{{ row.actor }}</td>
                    <td>{{ row.action }}</td>
                    <td>{{ row.objectId }}</td>
                  </tr>
                </tbody>
              </table>
            </div></template
          >
          <div
            v-if="
              [
                'customers',
                'products',
                'prices',
                'admin',
                'orders',
                'audit',
              ].includes(view) ||
              (view === 'portal' && portalTab === 'orders')
            "
            class="pagination"
          >
            <span>{{ totalCount }} {{ t("条", "records") }}</span
            ><select
              v-model="descending"
              :aria-label="t('排序', 'Sort')"
              @change="page = 1"
            >
              <option :value="true">{{ t("最新在前", "Newest first") }}</option>
              <option :value="false">
                {{ t("最早在前", "Oldest first") }}
              </option></select
            ><button
              :aria-label="t('上一页', 'Previous page')"
              :disabled="page === 1"
              @click="page--"
            >
              ←</button
            ><span
              >{{ page }} / {{ Math.max(1, Math.ceil(totalCount / 10)) }}</span
            ><button
              :aria-label="t('下一页', 'Next page')"
              :disabled="page * 10 >= totalCount"
              @click="page++"
            >
              →
            </button>
          </div>
        </template>
      </div>
    </main>
    <div v-if="modal" class="modal-backdrop" @click.self="closeModal">
      <section
        ref="modalElement"
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        tabindex="-1"
        @keydown="trap"
      >
        <div class="section-heading">
          <h2 id="modal-title">{{ modal.title }}</h2>
          <button
            class="icon"
            :disabled="busy"
            :aria-label="t('关闭', 'Close')"
            @click="closeModal"
          >
            <X :size="20" />
          </button>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <form
          v-if="['resource', 'password'].includes(modal.kind)"
          @submit.prevent="
            modal.kind === 'password' ? changePassword() : saveResource()
          "
        >
          <template v-for="field in formFields" :key="field.key"
            ><fieldset v-if="field.type === 'permissions'">
              <legend>{{ t(field.zh, field.en) }}</legend>
              <label
                v-for="p in data.admin.permissions"
                :key="p.id"
                class="check"
                ><input
                  v-model="form.permissions"
                  type="checkbox"
                  :value="p.code"
                />{{ p.name }}</label
              >
            </fieldset>
            <label v-else :class="{ check: field.type === 'checkbox' }"
              >{{ t(field.zh, field.en)
              }}<input
                v-if="field.type === 'checkbox'"
                v-model="form[field.key]"
                type="checkbox" /><textarea
                v-else-if="field.type === 'textarea'"
                v-model="form[field.key]"
                rows="3"
                :required="!field.optional"
                maxlength="6000"
              ></textarea
              ><select
                v-else-if="field.type === 'select'"
                v-model="form[field.key]"
                :required="!field.optional"
                :disabled="
                  !!modal.id &&
                  ((modal.type === 'prices' &&
                    ['customerId', 'productId'].includes(field.key)) ||
                    (modal.type === 'users' && field.key === 'customerId'))
                "
              >
                <option value="">
                  {{ t("请选择 / 留空", "Select / none") }}
                </option>
                <option
                  v-for="o in options(field)"
                  :key="o.value"
                  :value="o.value"
                >
                  {{ o.label }}
                </option></select
              ><input
                v-else
                v-model="form[field.key]"
                :type="
                  field.type === 'password'
                    ? 'password'
                    : field.type === 'number'
                      ? 'number'
                      : 'text'
                "
                :required="!field.optional"
                :maxlength="
                  field.type === 'password'
                    ? 72
                    : modal.type === 'settings'
                      ? 6000
                      : 1000
                "
                :autocomplete="
                  field.type === 'password' ? 'new-password' : 'off'
                " /></label
          ></template>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="busy">
              {{ t("保存", "Save") }}
            </button>
          </div>
        </form>
        <form v-else-if="modal.kind === 'action'" @submit.prevent="saveCommand">
          <p>
            {{
              t(
                "请核对订单、金额和当前状态。",
                "Review the order, value and current status.",
              )
            }}
          </p>
          <label
            >{{
              t(
                "说明（取消或退回必填）",
                "Note (required for cancellation or return)",
              )
            }}<textarea
              v-model="form.note"
              rows="3"
              maxlength="1000"
              :required="['cancel', 'reject'].includes(modal.action)"
            ></textarea>
          </label>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("返回", "Back") }}</button
            ><button class="primary" :disabled="busy">
              {{ t("确认操作", "Confirm action") }}
            </button>
          </div>
        </form>
        <form
          v-else-if="modal.kind === 'dispatch'"
          @submit.prevent="saveDispatch"
        >
          <label
            >{{
              t(
                "外部交接凭据（不可重复）",
                "Unique external fulfilment reference",
              )
            }}<input v-model="form.reference" required maxlength="160"
          /></label>
          <div v-for="l in form.lines" :key="l.id" class="dispatch-row">
            <span
              >{{ l.sku }} · {{ productName(l)
              }}<small
                >{{ t("剩余", "Remaining") }} {{ l.qty - l.fulfilled }}
                {{ l.unit }}</small
              ></span
            ><input
              v-model.number="l.deliveryQty"
              type="number"
              min="0"
              :max="l.qty - l.fulfilled"
              step="1"
              :aria-label="t('交接量 ', 'Fulfilment quantity ') + l.sku"
            />
          </div>
          <label
            >{{ t("交接备注", "Fulfilment note")
            }}<textarea
              v-model="form.note"
              rows="2"
              maxlength="1000"
            ></textarea>
          </label>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("取消", "Cancel") }}</button
            ><button
              class="primary"
              :disabled="busy || !form.lines.some((x) => x.deliveryQty > 0)"
            >
              {{ t("登记交接", "Record fulfilment") }}
            </button>
          </div>
        </form>
        <form
          v-else-if="modal.kind === 'delete'"
          @submit.prevent="confirmDelete"
        >
          <p>
            {{
              modal.message ||
              t(
                "删除后无法恢复，请核对当前记录。",
                "Deletion is permanent; review this record.",
              )
            }}
          </p>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("返回", "Back") }}</button
            ><button class="danger" :disabled="busy">
              {{ t("确认删除", "Delete record") }}
            </button>
          </div>
        </form>
        <form
          v-else-if="modal.kind === 'import'"
          @submit.prevent="importPrices"
        >
          <p>
            {{
              t(
                "先导出价表，保留更新行的revision；新增行revision留空。任一行失败整批不保存。",
                "Export prices first. Keep revision for updates; leave blank for new rows. Any invalid row rolls back the whole batch.",
              )
            }}
          </p>
          <label
            >{{
              t(
                "UTF-8 CSV · 最多500行 / 512KiB",
                "UTF-8 CSV · Up to 500 rows / 512KiB",
              )
            }}<input
              type="file"
              accept=".csv,text/csv"
              required
              @change="previewImport"
          /></label>
          <p v-if="previewError" class="error">{{ previewError }}</p>
          <div v-if="importRows" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("客户", "Customer") }}</th>
                  <th>SKU</th>
                  <th>{{ t("单价", "Price") }}</th>
                  <th>{{ t("规则", "Rules") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in importRows.slice(0, 8)" :key="i">
                  <td>{{ r.customerCode }}</td>
                  <td>{{ r.sku }}</td>
                  <td>{{ r.unitPrice }}</td>
                  <td>{{ r.minQty }} / {{ r.stepQty }} / {{ r.maxQty }}</td>
                </tr>
              </tbody>
            </table>
            <p>
              {{ importRows.length }}
              {{
                t(
                  "行已解析，待服务器校验",
                  "rows parsed; awaiting server validation",
                )
              }}
            </p>
          </div>
          <div class="modal-footer">
            <button type="button" :disabled="busy" @click="closeModal">
              {{ t("返回", "Back") }}</button
            ><button class="primary" :disabled="busy || !importRows">
              {{ t("校验并保存整批", "Validate & save batch") }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
