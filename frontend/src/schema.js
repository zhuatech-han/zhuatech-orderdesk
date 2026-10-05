// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
const f = (key, zh, en, type = "text", options = null, optional = false) => ({
  key,
  zh,
  en,
  type,
  options,
  optional,
});
/** 客户、商品、价表与账号表单，字段对应真实接口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const forms = {
  users: [
    f("username", "登录账号", "Username"),
    f("displayName", "显示姓名", "Display name"),
    f(
      "password",
      "密码（新建必填，修改可留空）",
      "Password (required for new accounts)",
      "password",
      null,
      true,
    ),
    f("roleId", "角色", "Role", "select", "roles"),
    f("departmentId", "部门", "Department", "select", "departments"),
    f(
      "customerId",
      "绑定客户（员工留空）",
      "Customer (blank for staff)",
      "select",
      "customers",
      true,
    ),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  roles: [
    f("name", "角色名称", "Role name"),
    f("scope", "数据范围", "Data scope", "select", [
      ["ALL", "全部", "All"],
      ["DEPARTMENT", "本部门", "Department"],
      ["CUSTOMER", "绑定客户", "Bound customer"],
    ]),
    f("permissions", "权限", "Permissions", "permissions"),
  ],
  departments: [f("name", "部门名称", "Department name")],
  permissions: [f("name", "权限显示名称", "Permission display name")],
  menus: [
    f("name", "菜单名称", "Menu name"),
    f("nameEn", "英文名称", "English name"),
    f(
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ),
    f("position", "顺序", "Position", "number"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  dictionaries: [
    f("type", "字典类型", "Dictionary type"),
    f("code", "代码", "Code"),
    f("name", "名称", "Name"),
    f("nameEn", "英文名称", "English name"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  settings: [f("value", "设置值", "Value", "textarea")],

  customers: [
    f("code", "客户代码", "Customer code"),
    f("name", "客户名称", "Customer name"),
    f("departmentId", "所属部门", "Department", "select", "departments"),
    f("address", "配送地址", "Delivery address", "textarea"),
    f("paymentTerms", "约定结算说明", "Payment terms"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  products: [
    f("sku", "商品编码", "SKU"),
    f("name", "商品名称", "Product name"),
    f("nameEn", "英文名称", "English name"),
    f("unit", "订货单位", "Pack unit"),
    f("category", "分类", "Category", "select", "categories"),
    f("enabled", "上架", "Available", "checkbox"),
  ],
  prices: [
    f("customerId", "客户", "Customer", "select", "customers"),
    f("productId", "商品", "Product", "select", "products"),
    f("unitPrice", "协议单价", "Unit price"),
    f("minQty", "最小订购量", "Minimum quantity", "number"),
    f("stepQty", "增加步长", "Quantity step", "number"),
    f("maxQty", "单行上限", "Maximum quantity", "number"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  password: [
    f("oldPassword", "当前密码", "Current password", "password"),
    f("newPassword", "新密码", "New password", "password"),
  ],
};
