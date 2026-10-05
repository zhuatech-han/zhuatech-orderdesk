// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 员工主数据、客户订货、订单交接和系统管理接口，所有业务权限由事务服务检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final OrderService service;
  final AdminService admin;

  public ApiController(OrderService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 员工主数据列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/master/{type}")
  public Object master(@PathVariable String type) {
    return switch (type) {
      case "customers" -> service.customers();
      case "products" -> service.products();
      case "prices" -> service.prices();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 新建客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/customers")
  public Object customer(@RequestBody OrderService.CustomerInput v) {
    return service.saveCustomer(null, v);
  }

  /** 编辑客户资料并验证版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/customers/{id}")
  public Object editCustomer(@PathVariable Long id, @RequestBody OrderService.CustomerInput v) {
    return service.saveCustomer(id, v);
  }

  /** 新建商品。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/products")
  public Object product(@RequestBody OrderService.ProductInput v) {
    return service.saveProduct(null, v);
  }

  /** 更新共享商品，需要全部范围权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/products/{id}")
  public Object editProduct(@PathVariable Long id, @RequestBody OrderService.ProductInput v) {
    return service.saveProduct(id, v);
  }

  /** 新建协议价与包装数量规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/prices")
  public Object price(@RequestBody OrderService.PriceInput v) {
    return service.savePrice(null, v);
  }

  /** 更新现有协议价，不允许修改客户或商品主键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/prices/{id}")
  public Object editPrice(@PathVariable Long id, @RequestBody OrderService.PriceInput v) {
    return service.savePrice(id, v);
  }

  /** 删除未被引用的主数据或价表；历史外键保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(
      @PathVariable String type, @PathVariable Long id, @RequestParam Long revision) {
    switch (type) {
      case "customers" -> service.deleteCustomer(id, revision);
      case "products" -> service.deleteProduct(id, revision);
      case "prices" -> service.deletePrice(id, revision);
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return Map.of("ok", true);
  }

  /** 客户价表原子导入，不自动发送账号密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/prices/import")
  public Object importPrices(@RequestBody List<OrderService.PriceRow> rows) {
    return service.importPrices(rows);
  }

  /** 当前价表CSV，更新行保留版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/prices.csv")
  public ResponseEntity<String> pricesCsv() {
    return csv(service.exportPrices(), "orderdesk-prices.csv");
  }

  /** 员工表单部门和分类字典。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return service.catalog();
  }

  /** 当前客户的目录、清单和订单，不返回其他客户信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/portal")
  public Object portal() {
    return service.portal();
  }

  /** 员工订单列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders")
  public Object orders() {
    return service.orders();
  }

  /** 订单详情按照客户绑定或员工部门范围授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 客户建立订货草稿，金额服务端计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders")
  public Object order(@RequestBody OrderService.OrderInput v) {
    return service.saveOrder(null, v);
  }

  /** 客户编辑本人草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/orders/{id}")
  public Object editOrder(@PathVariable Long id, @RequestBody OrderService.OrderInput v) {
    return service.saveOrder(id, v);
  }

  /** 客户删除没有历史的草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/orders/{id}")
  public Object deleteOrder(@PathVariable Long id, @RequestParam Long revision) {
    service.deleteOrder(id, revision);
    return Map.of("ok", true);
  }

  /** 提交、修订、审核与取消流程命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/actions/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody OrderService.Command v) {
    return service.command(id, action, v);
  }

  /** 不可覆写的分批交接，金额和数量来自确认订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/dispatches")
  public Object dispatch(@PathVariable Long id, @RequestBody OrderService.DispatchInput v) {
    return service.dispatch(id, v);
  }

  /** 新建客户常购清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/templates")
  public Object template(@RequestBody OrderService.TemplateInput v) {
    return service.saveTemplate(null, v);
  }

  /** 修改本人客户常购清单并验证版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/templates/{id}")
  public Object editTemplate(@PathVariable Long id, @RequestBody OrderService.TemplateInput v) {
    return service.saveTemplate(id, v);
  }

  /** 删除本人客户常购清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/templates/{id}")
  public Object deleteTemplate(@PathVariable Long id, @RequestParam Long revision) {
    service.deleteTemplate(id, revision);
    return Map.of("ok", true);
  }

  /** 员工工作台真实统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return service.report();
  }

  /** UTC提交日的订单交接CSV，包括已交数量及剩余数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders.csv")
  public ResponseEntity<String> export(@RequestParam LocalDate date) {
    return csv(service.export(date), "orderdesk-orders.csv");
  }

  private ResponseEntity<String> csv(String value, String name) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=" + name)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(value);
  }

  /** 员工元数据审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audits();
  }

  /** 管理目录，不向客户返回内部账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object list(@PathVariable String type) {
    return admin.list(type);
  }

  /** 增加管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object add(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改管理资源，客户账号绑定不可调换。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object edit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除未被业务外键引用的管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object delete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
