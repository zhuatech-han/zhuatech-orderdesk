# OrderDesk 架构与状态规则

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

Vue3同源调用Nginx `/api`，Spring Security会话认证和CSRF，AccessService实时账号/角色，OrderService校验客户绑定、部门、版本、包装量和价格，JPA/Flyway/MySQL保存记录。AuthController仅返回安全资料；密码散列不能序列化。AdminService防止把客户账号转成员工身份、替换客户绑定或移除最后管理员。

状态：DRAFT→SUBMITTED→CONFIRMED→FULFILLED；SUBMITTED可REJECTED→DRAFT再核对；DRAFT/SUBMITTED客户可CANCELLED；员工可取消未交接的SUBMITTED/CONFIRMED。确认后只允许交接或合法取消。每次动作验证revision并增加版本；已提交不能直接编辑行或金额。

写事务锁定基础department(1)，串行校验价表、订单和交接。所有写入是一个事务，价表导入任何失败会回滚包括审计。读操作先按客户或部门授权，不将员工目录交给客户。列表最大10000条，前端在已授权列表搜索筛选排序分页。单实例适用于中小业务，不能当无限规模或跨租户服务。

客户填表保存只传productId/qty。服务端读取所属客户价表计算金额。提交和确认检查当前价格、包装、名称与编码，发生变化拒绝操作。已确认订单不因价表修改改金额。前端预计金额无记账权；货币单一、两位小数，库存/支付/税票没有集成。

外部CSV为仓库交接文件，不调用WMS、不自动出库；重复下载无状态变更，接收系统应按订单号+SKU去重处理。交接凭据唯一，数量不得跨单或超剩余；满额才关闭。日志只存元数据，流程说明留在授权订单内。
