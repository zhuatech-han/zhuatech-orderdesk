# OrderDesk 验收方法

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

后端`mvn -B -f backend/pom.xml spotless:check test package`：3个规则测试+17个HTTP/JPA集成测试。覆盖从空库Flyway、登录、客户价格隔离、改价/单位复核、状态/版本、小数数量/主键/版本拒绝、事务导入回滚、部门、账号绑定、密码撤销、模板CRUD、历史引用及完整分批交接。H2是快速集成测试，实际MySQL必须另外执行。

前端`npm ci --no-audit --no-fund`，`npm run format:check`，`npm run lint`，`npm test`，`npm run build`：10项包含页面登录POST与CSRF引导、CSRF会话失效不重放写入、CSV下载授权及内容类型校验、金额显示、搜索排序分页、包装规则和CSV格式/大小/行数。Dockerfile同时执行这些检查及后端全量测试，CI复核源码、截图、品牌和许可。

在全新可丢弃数据库启动后运行`python3 scripts/smoke-test.py`，脚本会建立TEST客户、价表、账号和订单，验收真实MySQL事务、越权、价格快照、超量回滚、导入审计回滚、并发版本、CSV公式中和和密码撤销；输出实际断言数量。仅用于本项目独立测试实例，不能指向生产。输出私有测试口令文件在ignored output/，不提交Git。

页面人工验收：客户填写数量、保存/提交草稿、常购清单、历史重购、手机390×844、错误保留表单、搜索两页及语言切换；员工处理订单、实际交接、用户/角色/设置及报表下载。截图必须来自当前容器运行页面，不能沿用其他项目图片。

备份恢复：把测试库导出至权限0600的ignored SQL，独立Compose项目名/端口/新卷导入，验证原账号、Flyway、订单快照、模板及交接；重启后重新核对。运行`python3 scripts/release-check.py`和`git diff --check`，核对两张原二维码摘要、README所有相对图片、授权及品牌。最后只清理本次QA/恢复资源，不清理其他项目。
