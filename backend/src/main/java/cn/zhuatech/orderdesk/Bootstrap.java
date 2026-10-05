// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化员工及客户权限，客户、商品、协议价和订单由实际操作建立。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;

  @Value("${orderdesk.admin-username}")
  String username;

  @Value("${orderdesk.admin-password}")
  String password;

  public Bootstrap(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  /** 仅第一次建立管理员，不在重启时重设密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("INVALID_ADMIN_USERNAME");
    var d = new Department();
    d.name = "批发运营 / Wholesale operations";
    db.save(d);
    var all = new HashSet<String>();
    for (var v :
        new String[][] {
          {"dashboard", "工作台 / Workspace"},
          {"read", "员工订单 / Staff orders"},
          {"master", "客户商品与价格 / Customers and pricing"},
          {"process", "审核与交付 / Review and fulfilment"},
          {"report", "订单导出与统计 / Export and reports"},
          {"admin", "账号与配置 / Administration"},
          {"audit", "操作记录 / Audit"},
          {"portal", "本人客户订货 / Customer ordering"}
        }) {
      var p = new Permission();
      p.code = v[0];
      p.name = v[1];
      db.save(p);
      if (!v[0].equals("portal")) all.add(v[0]);
    }
    var admin = role("管理员 / Administrator", "ALL", all);
    role(
        "销售运营 / Sales operations",
        "DEPARTMENT",
        Set.of("dashboard", "read", "master", "process", "report"));
    role("仓库交接 / Fulfilment", "DEPARTMENT", Set.of("dashboard", "read", "process", "report"));
    role("客户 / Customer", "CUSTOMER", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    int pos = 0;
    for (var v :
        new String[][] {
          {"dashboard", "工作台", "Workspace", "dashboard"},
          {"customers", "客户", "Customers", "master"},
          {"products", "商品", "Products", "master"},
          {"prices", "客户价格", "Customer prices", "master"},
          {"orders", "订单处理", "Orders", "read"},
          {"portal", "客户订货", "Ordering", "portal"},
          {"reports", "交接与统计", "Export & reports", "report"},
          {"admin", "账号与设置", "Administration", "admin"},
          {"audit", "操作记录", "Audit", "audit"}
        }) {
      var m = new NavMenu();
      m.code = v[0];
      m.name = v[1];
      m.nameEn = v[2];
      m.permissionCode = v[3];
      m.position = pos++;
      m.enabled = true;
      db.save(m);
    }
    for (var v :
        new String[][] {
          {"companyName", "批发运营 / Wholesale operations"},
          {"currency", "CNY"},
          {
            "orderingNotice",
            "请核对商品单位、数量与要求交付日期。 / Check pack units, quantities and requested delivery date."
          }
        }) {
      var s = new SystemSetting();
      s.code = v[0];
      s.value = v[1];
      db.save(s);
    }
    for (var v :
        new String[][] {
          {"GENERAL", "常规商品", "General"}, {"SUPPLIES", "用品耗材", "Supplies"}, {"FOOD", "食品", "Food"}
        }) {
      var e = new DictionaryEntry();
      e.type = "category";
      e.code = v[0];
      e.name = v[1];
      e.nameEn = v[2];
      e.enabled = true;
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
