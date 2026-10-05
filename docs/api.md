# OrderDesk 接口说明

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。公开源码学习版，未经书面授权不得商用。

接口均同源`/api`，除CSRF引导、登录和`/actuator/health`外要求登录。写入先GET `/api/auth/csrf`获取`header/token`，携带会话Cookie及该请求头，不把口令或Cookie放在URL。错误返回`{code:...}`；401会话失效、403越权、409状态/版本/引用冲突、400输入不合法。

| 方法与路径 | 行为及权限 |
|---|---|
| GET auth/csrf；POST auth/login/logout/password；GET auth/me | 登录、当前实时身份及改密；密码重置使旧会话失效 |
| GET master/customers/products/prices | 员工master；客户、价格按部门范围 |
| POST/PUT master/customers、products、prices[/id] | 主数据维护；共享商品修改须ALL范围 |
| DELETE master/{type}/{id}?revision=N | 版本验证、外键引用保护 |
| GET prices.csv；POST prices/import | 员工master；最多500行整体事务 |
| GET portal | 客户portal、本客户资料/商品/协议价/订单/清单 |
| GET orders[/id] | 员工read范围或客户本人订单详情 |
| POST/PUT orders[/id] | 客户创建/编辑草稿；服务器核价 |
| DELETE orders/{id}?revision=N | 仅本人客户、无流程历史草稿 |
| POST orders/{id}/actions/submit/revise/cancel | 客户合法流程；cancel也允许员工在未交接前执行 |
| POST orders/{id}/actions/confirm/reject | 员工process；再次核价或说明退回 |
| POST orders/{id}/dispatches | 员工process；唯一外部凭据及剩余数量校验 |
| POST/PUT templates[/id]；DELETE templates/{id}?revision=N | 本客户清单；价格不存入清单 |
| GET catalog；GET reports | 员工dashboard，订单统计还需read |
| GET orders.csv?date=YYYY-MM-DD | 员工report/read；UTC提交日已确认/交付订单 |
| GET audit | 员工audit，部门过滤 |
| GET/POST/PUT/DELETE admin/{type}[/id] | admin及ALL范围，系统资源和最后管理员保护 |

创建草稿：

```json
{"deliveryDate":"2026-10-06","note":"TEST request","lines":[{"productId":1,"qty":3}]}
```

日期仅说明格式，实际须符合当前UTC日期。更新增加`revision`，服务端不接受客户端状态、单价、总额、客户ID覆盖本人客户绑定。清单输入`name/lines/revision`；价表输入`customerId/productId/unitPrice/minQty/stepQty/maxQty/enabled/revision`。状态命令输入`revision/note`，退回及取消须非空说明。

分批交接：

```json
{"revision":3,"reference":"TEST-HANDOFF-001","note":"TEST warehouse record","lines":[{"orderLineId":1,"qty":2}]}
```

使用订单详情返回的行ID。不得按商品ID冒充行ID。交接接口没有支付或库存扣减含义。CSV返回`text/csv`且private/no-store，公式前缀中和，不插入品牌广告。
