# OrderDesk 数据与迁移

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

Flyway按`backend/src/main/resources/db/migration/V1__order_schema.sql`创建结构。JPA validate不改结构。19张应用表及Flyway历史表：department、access_role、permission、role_permission、nav_menu、account、audit_event、system_setting、dictionary_entry、customer、product、customer_price、sales_order、order_line、order_event、order_template、template_line、dispatch、dispatch_line。

角色权限关联有权限外键；账号指向角色、部门及可空客户。客户商品协议价唯一，订单号唯一，清单名称按客户唯一，交接外部凭据唯一。订单行和交接行外键禁止删历史。订单价格/名称/单位、客户名称/地址都是快照。模板只存当前商品引用和数量。

价格decimal(18,2)，数量整数，日期无时区，事件timestamp(6)按UTC。会话在单实例内存，重启须重新登录；业务资料在MySQL卷，不保存在会话。Bootstrap仅在无账号时初始化，不能用重新启动重设已有管理员。

升级新增版本迁移，先备份，再在独立实例恢复、运行Flyway、核对账号权限和业务金额/数量。不要修改已执行V1或手动清除Flyway记录。部署示例备份包括协议价等业务敏感数据，只保存在本机受控路径，严禁提交Git。
