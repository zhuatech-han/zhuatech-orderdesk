[中文](README.md) | [English](README.en.md)

# OrderDesk · 知华批发客户订货公开源码学习版

**把客户自己的协议价、常购清单和订货记录放在同一个工作台。** 知华科技（上海如静知华信息科技有限公司）提供，官网 [zhuatech.cn](https://www.zhuatech.cn/)。中文 / English，版本1.0.0。

适用于有固定批发客户、商品范围和协商价格的供应商，以及负责私有部署和对接的 IT 团队。客户不再反复问价、在群里发送零散订单；销售与仓库从同一份已确认订单交接。系统面向客户订货，不替代内部进销存或财务软件。

## 客户与供应商怎样协作

1. 管理员建立部门、商品分类与商品，录入客户及配送地址。
2. 为每个客户分配商品、单价、最小订货量、增加步长和上限；可使用CSV批量维护。
3. 为客户创建独立登录账号，绑定客户和所属部门。客户账号不能读取员工目录或其他客户的价表、清单、订单。
4. 客户在电脑或手机填写包装数量，保存草稿、核对金额和交付日期后提交；常购清单和历史订单可再次载入。
5. 供应商确认接单或填写原因退回。提交和确认均检查当前价格、商品名称和包装单位；发生变化时要求重新核对，不悄悄改变金额。
6. 已确认订单冻结协议价格与配送资料。仓库人员登记外部凭据和分批数量，全部交完才变为“已交付”。
7. 按提交日（UTC）导出已确认或已交付订单CSV，交给自己的仓库系统。人工交接记录不等于物流接口同步。

## 功能清单与实际边界

| 业务 | 已实现 |
|---|---|
| 客户 | 部门范围、配送地址、结算说明、启停、增删改查与版本；引用保护 |
| 商品 | 编码、中英文名称、订货单位、分类、上架、版本；共享商品仅允许全部范围管理者修改 |
| 客户价表 | 每客户每商品独立单价及包装数量规则；CSV导出、预览和最多500行原子导入 |
| 客户业务端 | 专属商品表、草稿金额核对、提交/撤回、退回修订、历史再购、常购清单增删改 |
| 供应商业务端 | 按部门审核、退回、确认、无交接前取消；冻结订单，部分交接与完整结束 |
| 操作保护 | 写入版本、精确十进制金额、重复商品校验、跨客户拒绝、超量交接拒绝、重复外部凭据拒绝 |
| 统计与导出 | 真实状态数量、确认及交付订单金额、近期订单、按UTC提交日仓库交接CSV、剩余数量 |
| 管理后台 | 用户、角色、权限名称、菜单、部门、分类字典、参数、实时权限、最后管理员保护 |
| 通用能力 | 搜索、状态筛选、排序、每页10条、操作/审计记录、会话、BCrypt、CSRF、改密、健康检查 |

**未实现：** 库存预占和扣减、仓库/ERP自动同步、自动邮件或短信、在线支付、退款、发票及税额计算、信用额度与对账、物流轨迹、客户公开注册、跨公司多租户SaaS、照片附件和多币种同时交易。金额是供双方核对的协议价记录，不代表已经收款或开票。

**无需第三方密钥即可运行。** 正式HTTPS证书、外部数据库由部署方配置；邮件、支付、ERP/WMS接口属于额外开发，不能仅填一个密钥就启用。客户账号由管理员建立并通过自己的安全渠道交付，系统不发送明文密码邮件。

## 实际运行页面

登录页按账号进入客户或员工工作台；客户订货和常购清单用于本人下单，订单详情页查看价格快照与已交付数量。账号与角色页管理身份和权限，参数页维护实例配置，统计页核对已确认及已交付订单，手机页展示窄屏订货。

截图来自独立测试库。`TEST`客户、商品和订单均为验收资料；空库安装不会创建这些业务记录。

| 登录 | 客户商品与订货 |
|---|---|
| ![登录](docs/screenshots/login.jpg) | ![客户订货](docs/screenshots/customer.jpg) |
| 供应商工作台 | 订单详情与已交付数量 |
| ![工作台](docs/screenshots/workspace.jpg) | ![订单](docs/screenshots/order.jpg) |
| 账号管理 | 订单统计与CSV交接 |
| ![账号](docs/screenshots/accounts.jpg) | ![统计](docs/screenshots/reports.jpg) |
| 角色权限 | 系统参数 |
| ![角色](docs/screenshots/roles.jpg) | ![设置](docs/screenshots/settings.jpg) |
| 常购清单 | 手机订货 |
| ![常购](docs/screenshots/templates.jpg) | ![手机](docs/screenshots/mobile.jpg) |

客户业务端只有本人客户订货、订单和清单；供应商业务端用于主数据、接单和交接；后台管理端负责账号与设置。所有页面来自真实接口，没有浏览器中的演示业务数组。

## 安装一套独立实例

要求：Java **21**、Maven **3.9**、Node **24.19.0+**、MySQL **8.4**、Docker与Compose v2。工程使用 Spring Boot **4.0.7**、Vue **3.5.40**、Vite **8.1.5**，MariaDB Java Client **3.5.10** 连接MySQL8.4，JDBC地址为 `jdbc:mariadb://`。

```sh
python3 scripts/init-env.py
# 在私有 .env 中查看首次管理员口令；不要上传该文件
docker compose config --quiet
docker compose up -d --build --wait
```

访问 [http://localhost:8116/](http://localhost:8116/)，健康 [http://localhost:8116/actuator/health](http://localhost:8116/actuator/health)。员工和客户共用登录页，按真实账号权限进入不同工作台；首次账号名默认`admin`，密码取私有`.env`的`ADMIN_PASSWORD`，没有公开通用口令。

首次安装创建管理员、4种角色、8个权限、9个菜单、基础部门、3个商品分类和3个参数，**不创建客户、商品、协议价、业务订单或其他账号**。重启不重置密码。分类用于真实商品表单，不是虚拟业务数据。

| 配置名 | 说明 |
|---|---|
| MYSQL_ROOT_PASSWORD | MySQL管理口令，独立强密码 |
| DATABASE_PASSWORD | 应用数据库口令，独立强密码 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 首次账号名与12–72字节含大小写字母、数字的口令 |
| WEB_PORT / BIND_ADDRESS | 默认8116 / 127.0.0.1；可覆盖，例如18116，避免其他项目冲突 |
| COOKIE_SECURE | 本机HTTP学习false；正式HTTPS设置true |
| DATABASE_URL / DATABASE_USER | 可选外部MySQL；正式外部数据库须验证TLS和CA |

示例仅声明配置名，不含真实密钥。`scripts/init-env.py`生成随机口令、文件权限0600，不覆盖已有`.env`。数据库不发布主机端口；不需要停止其他项目。

分别运行前后端：

```sh
docker compose up -d mysql --wait
# 使用自己的可达MySQL8.4地址，安全注入 DATABASE_URL、DATABASE_USER、DATABASE_PASSWORD
# 同时注入 ADMIN_USERNAME、ADMIN_PASSWORD；默认Compose不向主机暴露数据库
mvn -f backend/pom.xml spring-boot:run
cd frontend
npm ci --no-audit --no-fund
npm run dev
```

Vite本机5173代理8080后端；正式前端由Nginx同源代理`/api`、处理SPA回退。正式部署、备份与恢复详见[部署](docs/deployment.md)。

## 架构与数据库

```text
backend/src/main/java/cn/zhuatech/orderdesk/    权限、管理、协议价与订货事务
backend/src/main/resources/db/migration/      Flyway版本化SQL
backend/src/test/                              业务规则及HTTP/JPA测试
frontend/src/                                 客户业务端、供应商端、管理端和双语界面
frontend/public/brand/                         正式LOGO及第三方许可
scripts/                                      初始化、发布扫描、独立测试库验收
docs/                                         手册、接口、部署、数据库、安全及截图
compose.yaml                                  MySQL / Spring Boot / Vue Nginx
```

Java/Spring Security负责会话和业务权限，JPA持久化，Vue3负责表格与表单，MySQL8.4保存全部记录。19张应用表（包括角色权限关联），另有Flyway历史表。初始化及迁移是 `backend/src/main/resources/db/migration/V1__order_schema.sql`；外键保护引用，价表有客户商品唯一键，订单号、交接凭据唯一，数量约束禁止超量。

金额在服务端使用`BigDecimal`，单价最多两位小数，拒绝静默舍入；数量、主键及版本不接受小数截断；数量为包装单位整数，合法量为 `minQty + n × stepQty`，不是任意散件。币种为实例级设置，建立价表或订单后锁定。草稿客户端估算仅用于显示，保存/提交/确认由服务端校验。

写操作在事务内锁定基础部门记录，串行核验版本和数量，适合单实例中小供应商。列表上限10000条，服务器先按权限过滤，再在前端搜索排序分页；不是海量分布式电商。提交日按UTC导出；交付日期是人工要求日期。订单、清单、账号和配置全部持久化，不依赖本地已有数据库。

升级先备份并在独立卷恢复验收，新增`V2__...sql`迁移，不修改已执行的V1。JPA仅验证结构，不自动重建。详见[数据库](docs/database.md)、[架构](docs/architecture.md)。

## 验证与维护

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 scripts/release-check.py
git diff --check
docker compose config --quiet
docker compose build
```

后端20项（3项业务单元、17项实际HTTP/JPA集成），前端10项，Docker构建执行完整测试，不跳过测试。H2测试覆盖迁移和HTTP事务；MySQL验收须单独执行，不能用H2替代。`scripts/smoke-test.py`仅对**全新、独立、可丢弃测试库**执行，禁止在生产或已有业务实例运行。详见[测试](docs/testing.md)。

常见问题：

- 页面打不开：核对`WEB_PORT`及三容器健康；查看本项目日志，不删除其他项目卷。
- 客户登录看不到商品：检查客户、分类、商品与价表是否启用，账号绑定是否正确。
- 包装数量报错：核对最小量、步长和上限，如最小5、步长3，合法量为5/8/11。
- 协议价或单位变化：客户编辑草稿重新保存；供应商退回已提交订单，让客户核对后再提交。
- 版本冲突：刷新并核对最新内容再操作；不要重复提交原表单。
- 导入失败：CSV表头必须与导出一致，新行revision留空、更新行保留当前版本；未知键或重复行会整批回滚。
- 不能改商品：共享商品需要全部范围，部门员工维护本人范围的客户价表。
- 账号绑定失败：客户角色必须使用CUSTOMER范围、仅portal权限、客户和账号同部门；员工账号绑定留空。

更多流程见[操作手册](docs/manual.md)、[接口](docs/api.md)。

## 安全、授权与反馈

BCrypt12轮、HttpOnly/SameSite=Strict会话Cookie、30分钟会话、CSRF、错误统一代码、每次请求重新检查账号与角色、密码重置撤销旧会话、最后管理员保护。登录连续失败限流为单实例内存窗口；正式部署配置HTTPS、强密码、备份权限及外围访问限制。CSV导出中和公式前缀；导入只读UTF-8有限CSV，不执行公式或写入客户端传来的业务状态。没有通用附件上传接口。

公开源码仅供个人学习、技术研究和非商业交流，**未经书面授权不得商用**；商业交付、收费部署、私有化商用、SaaS或源码转售均须授权，见[LICENSE](LICENSE)。不是OSI标准开源许可证。第三方依赖按原许可保留，见[第三方声明](docs/third-party.md)和页面随附许可。

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问[知华科技官网](https://www.zhuatech.cn/)，或添加微信 zhuatech、zhuatech2 咨询。

Issues用于脱敏问题反馈，请附版本、环境、实际/预期结果及复现步骤；贡献先说明业务场景，提交小范围变化并运行检查，保留版权许可。安全漏洞通过官方微信私下反馈，不公开真实客户资料、协议价格、密码或会话。源码按LICENSE现状提供；实际贸易、税务、收款、货运和数据保管由使用者负责，不承诺业务成交或合规认证。详见[安全](docs/security.md)。

## 联系知华科技

商业授权或深度定制开发请联系知华科技。

公司：**上海如静知华信息科技有限公司**。官网：[https://www.zhuatech.cn/](https://www.zhuatech.cn/)。商业授权、私有化部署、软件定制和系统集成咨询微信：**zhuatech**、**zhuatech2**。

<table><tr><td align="center" valign="top"><img src="docs/images/wechat-zhuatech.png" height="200" alt="知华科技咨询微信 zhuatech"><br>微信：zhuatech</td><td align="center" valign="top"><img src="docs/images/wechat-zhuatech2.png" height="200" alt="知华科技咨询微信 zhuatech2"><br>微信：zhuatech2</td></tr></table>
