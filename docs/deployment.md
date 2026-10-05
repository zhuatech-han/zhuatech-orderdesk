# OrderDesk 部署、备份和恢复

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。公开源码学习版，商用部署须书面授权。

运行`python3 scripts/init-env.py`生成本机私有`.env`，核对WEB_PORT和随机口令，然后`docker compose config --quiet`及`docker compose up -d --build --wait`。MySQL健康后启动后端，后端健康后启动非root Nginx前端。镜像Maven BuildKit缓存sharing=locked、预取依赖和网络重试，打包不跳过测试。

默认8116，仅127.0.0.1，MySQL不暴露主机端口。用自己的WEB_PORT覆盖冲突。正式对外部署由自己的HTTPS反向代理接入，配置COOKIE_SECURE=true、受信任代理、正确访问边界、数据库外部TLS、最小用户权限及业务备份。无需把数据库或管理员秘密写进源码。前后端容器非root。没有公开托管试用站点。

只备份当前项目，输出含协议价、地址和口令散列，文件不要上传：

```sh
mkdir -p output
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > output/orderdesk-private-backup.sql
```

恢复须用独立Compose项目名、不同WEB_PORT和全新卷。创建自己的私有`.env.restore`，配置独立数据库/初始管理员强口令。仅先启动数据库并导入，再启动应用：

```sh
docker compose --env-file .env.restore -p orderdesk-restore up -d mysql --wait
docker compose --env-file .env.restore -p orderdesk-restore exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < output/orderdesk-private-backup.sql
docker compose --env-file .env.restore -p orderdesk-restore up -d --build --wait
```

导入的是已有账号，Bootstrap不会替换原口令；使用备份中的原测试/业务账号登录。核对Flyway、账号、订单价格和分批数量后再决定切换，不能直接把备份导入已有生产卷。仅对本次命名测试实例执行`down -v`；已有生产库或其他项目禁止清理。备份定期做恢复演练，文件加密与保留期限由部署方配置。
