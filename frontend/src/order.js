// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 客户订货及批量价表的输入规则，与服务端数量约束保持一致。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function validQty(qty, item) {
  return (
    Number.isInteger(qty) &&
    qty >= item.minQty &&
    qty <= item.maxQty &&
    (qty - item.minQty) % item.stepQty === 0
  );
}
/** 有限CSV解析：引号转义、CRLF、BOM及多行字段；结构异常整批拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parsePrices(text) {
  if (new TextEncoder().encode(text).length > 524288)
    throw new Error("IMPORT_TOO_LARGE");
  text = text.replace(/^\uFEFF/, "");
  const records = [];
  let row = [],
    value = "",
    quoted = false,
    closed = false;
  const field = () => {
    row.push(value);
    value = "";
    closed = false;
  };
  const end = () => {
    field();
    if (row.some((x) => x !== "")) records.push(row);
    row = [];
  };
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          value += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else value += c;
      continue;
    }
    if (c === '"') {
      if (value || closed) throw new Error("INVALID_IMPORT");
      quoted = true;
    } else if (c === ",") field();
    else if (c === "\r" || c === "\n") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      end();
    } else {
      if (closed) throw new Error("INVALID_IMPORT");
      value += c;
    }
  }
  if (quoted) throw new Error("INVALID_IMPORT");
  if (value || row.length || closed) end();
  const headers = [
    "customerCode",
    "sku",
    "unitPrice",
    "minQty",
    "stepQty",
    "maxQty",
    "enabled",
    "revision",
  ];
  if (
    !records.length ||
    records[0].join(",") !== headers.join(",") ||
    records.length < 2 ||
    records.length > 501
  )
    throw new Error("INVALID_IMPORT");
  return records.slice(1).map((r, index) => {
    const invalid = (zh, en) => {
      throw Object.assign(new Error("INVALID_IMPORT"), {
        recordNumber: index + 1,
        detail: [zh, en],
      });
    };
    if (r.length !== 8) invalid("须有8列", "Expected 8 columns");
    if (!r[0] || !r[1])
      invalid(
        "客户代码和商品编码不能为空",
        "Customer code and SKU are required",
      );
    if (
      !/^\d+(\.\d{1,2})?$/.test(r[2]) ||
      Number(r[2]) <= 0 ||
      Number(r[2]) > 999999.99
    )
      invalid(
        "单价须为正数、最多两位小数，最高999999.99",
        "Price must be positive, at most two decimals and no more than 999999.99",
      );
    if (!["true", "false"].includes(r[6]))
      invalid("enabled须为true或false", "enabled must be true or false");
    if (!r.slice(3, 6).every((x) => /^\d+$/.test(x)))
      invalid(
        "起订量、步长和上限须为整数",
        "Minimum, step and maximum must be integers",
      );
    if (r[7] && !/^\d+$/.test(r[7]))
      invalid(
        "revision须为空或非负整数",
        "revision must be blank or a non-negative integer",
      );
    return {
      customerCode: r[0],
      sku: r[1],
      unitPrice: r[2],
      minQty: Number(r[3]),
      stepQty: Number(r[4]),
      maxQty: Number(r[5]),
      enabled: r[6] === "true",
      revision: r[7] ? Number(r[7]) : null,
    };
  });
}
/** 可读的双语流程状态，数字与金额不由标签推导。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  SUBMITTED: ["待审核", "Submitted"],
  CONFIRMED: ["已确认", "Confirmed"],
  REJECTED: ["已退回", "Rejected"],
  CANCELLED: ["已取消", "Cancelled"],
  FULFILLED: ["已交付", "Fulfilled"],
};
/** 对应实际接口拒绝的原因，保留表单供修正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errors = {
  CATALOG_CHANGED: [
    "商品信息或包装单位已变化，请重新编辑草稿核对",
    "Product or pack unit changed; edit draft and review",
  ],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired; sign in again"],
  LOGIN_FAILED: ["账号或密码错误", "Incorrect username or password"],
  LOGIN_THROTTLED: [
    "登录尝试过多，请稍后重试",
    "Too many attempts; try again later",
  ],
  FORBIDDEN: ["当前账号没有此操作权限", "This account is not permitted"],
  OUT_OF_SCOPE: ["记录不属于当前数据范围", "Record is outside your scope"],
  STALE_VERSION: [
    "记录已变化，请刷新并核对后再操作",
    "Record changed; refresh and review before retrying",
  ],
  PRICE_CHANGED: [
    "协议价已变化，请修改草稿重新核价；供应商可退回订单",
    "Price changed; edit draft to reprice, or return the order",
  ],
  PRODUCT_UNAVAILABLE: [
    "清单中有未上架或未授权商品，请核对后重新选择",
    "A product is unavailable; review and select again",
  ],
  INVALID_QUANTITY: [
    "数量不符合最小量、增加步长或上限",
    "Quantity does not match minimum, step or maximum",
  ],
  INVALID_PRICE: [
    "价格必须为正值、最多两位小数且不超过999999.99",
    "Price must be positive, at most two decimal places and at most 999999.99",
  ],
  INVALID_INPUT: [
    "请检查必填字段及长度",
    "Review required fields and their length",
  ],
  INVALID_STATE: [
    "当前订单状态不允许此操作",
    "Action is not allowed in the current state",
  ],
  CUSTOMER_DISABLED: [
    "客户已停用，请联系供应商",
    "Customer is disabled; contact your supplier",
  ],
  CUSTOMER_ROLE_REQUIRED: [
    "客户账号必须使用客户角色并属于客户所在部门",
    "Customer accounts require a customer role and matching department",
  ],
  CUSTOMER_BINDING_REQUIRED: [
    "客户角色必须绑定客户；员工角色不能包含订货权限",
    "Customer role requires a customer binding; staff roles cannot include portal permission",
  ],
  CUSTOMER_BINDING_IMMUTABLE: [
    "已有账号的客户绑定不能调换，请新建账号",
    "Existing customer binding cannot be switched; create an account",
  ],
  GLOBAL_MASTER_REQUIRED: [
    "共享商品仅允许全部数据范围的管理者修改",
    "Shared products require an all-scope manager",
  ],
  INVALID_DELIVERY_DATE: [
    "要求交付日期须在今天起365天内",
    "Requested delivery date must be within the next 365 days",
  ],
  OVER_FULFILMENT: [
    "交接数量超过剩余数量或选错订单行",
    "Fulfilment exceeds remaining quantity or references another order",
  ],
  ALREADY_FULFILLED: [
    "已有交接记录，不能取消订单",
    "Orders with fulfilment records cannot be cancelled",
  ],
  HAS_HISTORY: [
    "已有流程历史，请保留记录",
    "Record has workflow history and must be retained",
  ],
  WEAK_PASSWORD: [
    "密码需12至72字节，含大小写字母和数字",
    "Password requires 12–72 bytes with upper/lowercase letters and digits",
  ],
  LAST_ADMIN: [
    "必须保留至少一个可用管理员",
    "At least one enabled administrator must remain",
  ],
  INVALID_IMPORT: [
    "CSV结构或字段无效，请使用价表导出的表头",
    "Invalid CSV; use the exported price headers",
  ],
  IMPORT_TOO_LARGE: ["CSV不可超过512KiB", "CSV must not exceed 512KiB"],
  DUPLICATE_IMPORT_ROW: [
    "同一客户与商品重复，请修正整批",
    "Duplicate customer/product in import",
  ],
  UNKNOWN_IMPORT_KEY: [
    "CSV存在未知客户代码或商品编码",
    "Unknown customer code or SKU",
  ],
  CURRENCY_LOCKED: [
    "已有价表或订单，不能改变币种",
    "Currency is locked after prices or orders exist",
  ],
  NETWORK_ERROR: [
    "连接未完成，表单已保留。请核对记录后再重试",
    "Connection failed; form retained. Review the record before retrying",
  ],
  EMPTY_LINES: [
    "请至少选择一项商品或交接数量",
    "Select at least one product or fulfilment quantity",
  ],
  CONFLICT: [
    "记录重复或仍被业务引用",
    "Record is duplicated or still referenced",
  ],
  INVALID_LINES: ["商品或订单行重复，请核对清单", "Duplicate or invalid lines"],
  DICTIONARY_KEY_IMMUTABLE: [
    "字典类型和代码不能调换",
    "Dictionary type and code are immutable",
  ],
  CUSTOMER_DEPARTMENT_LOCKED: [
    "客户已有账号或订单，不能改变部门",
    "Customer department is locked by accounts or orders",
  ],
  OLD_PASSWORD_INVALID: ["当前密码错误", "Current password is incorrect"],
  BUILTIN_RESOURCE: ["内建资源不能删除", "Built-in resource cannot be deleted"],
  NOT_FOUND: ["记录不存在", "Record was not found"],
};
