// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客户隔离、协议价核验、订货模板、冻结订单与分批交接；所有写入在同一事务序列化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class OrderService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public OrderService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 客户输入，不接受任意账号绑定或历史单据状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CustomerInput(
      String code,
      String name,
      Long departmentId,
      String address,
      String paymentTerms,
      Boolean enabled,
      Long revision) {}

  /** 商品输入；分类来自启用字典。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ProductInput(
      String sku,
      String name,
      String nameEn,
      String unit,
      String category,
      Boolean enabled,
      Long revision) {}

  /** 单客户商品价表，不接收客户端计算的订单金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PriceInput(
      Long customerId,
      Long productId,
      BigDecimal unitPrice,
      Integer minQty,
      Integer stepQty,
      Integer maxQty,
      Boolean enabled,
      Long revision) {}

  /** CSV 批量价表的一行，更新已有记录必须携带当前 revision。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PriceRow(
      String customerCode,
      String sku,
      BigDecimal unitPrice,
      Integer minQty,
      Integer stepQty,
      Integer maxQty,
      Boolean enabled,
      Long revision) {}

  /** 订单和常购清单商品只传商品ID及整数包装数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record LineInput(Long productId, Integer qty) {}

  /** 客户填写期望交付日期和备注，配送地址与价格由服务端确定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record OrderInput(
      LocalDate deliveryDate, String note, List<LineInput> lines, Long revision) {}

  /** 常购清单不冻结价格，下次新建草稿按当前协议价计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record TemplateInput(String name, List<LineInput> lines, Long revision) {}

  /** 状态命令需提供版本；拒绝和取消还需原因。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(Long revision, String note) {}

  /** 分批交接数量按订单行，不允许跨订单行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record DispatchItem(Long orderLineId, Integer qty) {}

  /** 外部交接凭据需唯一，重复提交不重复交付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record DispatchInput(
      Long revision, String reference, String note, List<DispatchItem> lines) {}

  private void lock() {
    db.lock(Department.class, 1L);
  }

  private void staff(String permission) {
    access.require(permission);
    if (access.current().customerId != null) throw new Problem(403, "FORBIDDEN");
  }

  private Customer own() {
    access.require("portal");
    var a = access.current();
    if (a.customerId == null) throw new Problem(403, "CUSTOMER_BINDING_REQUIRED");
    var c = db.get(Customer.class, a.customerId);
    if (!c.enabled) throw new Problem(403, "CUSTOMER_DISABLED");
    return c;
  }

  private Customer customer(Long id) {
    var c = db.get(Customer.class, id);
    access.department(c.departmentId);
    return c;
  }

  private String optional(String s, int max) {
    if (s == null) return "";
    if (s.length() > max) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }

  private String currency() {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", "currency")
        .getFirst()
        .value;
  }

  private boolean productActive(Product p) {
    return p.enabled
        && db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where type=?1 and code=?2 and enabled=true",
                    "category",
                    p.category)
                .size()
            == 1;
  }

  private CustomerPrice activePrice(Long customerId, Long productId) {
    var rows =
        db.query(
            CustomerPrice.class,
            "from CustomerPrice where customerId=?1 and productId=?2",
            customerId,
            productId);
    if (rows.isEmpty()
        || !rows.getFirst().enabled
        || !productActive(db.get(Product.class, productId)))
      throw new Problem(409, "PRODUCT_UNAVAILABLE");
    return rows.getFirst();
  }

  private SalesOrder visibleOrder(Long id) {
    var o = db.get(SalesOrder.class, id);
    if (access.current().customerId != null) {
      if (!Objects.equals(own().id, o.customerId)) throw new Problem(403, "OUT_OF_SCOPE");
    } else {
      staff("read");
      customer(o.customerId);
    }
    return o;
  }

  private List<OrderLine> lines(Long id) {
    return db.query(OrderLine.class, "from OrderLine where orderId=?1 order by id", id);
  }

  private void event(SalesOrder o, String action, String note) {
    var e = new OrderEvent();
    e.orderId = o.id;
    e.action = action;
    e.actor = access.current().username;
    e.note = optional(note, 1000);
    e.createdAt = clock.instant();
    db.save(e);
    access.audit("ORDER_" + action, o.id, db.get(Customer.class, o.customerId).departmentId);
  }

  /** 员工只读取本部门客户；客户账号不能读取人员或价表目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Customer> customers() {
    staff("master");
    return db.all(Customer.class).stream().filter(c -> access.visible(c.departmentId)).toList();
  }

  /** 创建、更新客户；部门不能在已有账号或订单后被迁移。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Customer saveCustomer(Long id, CustomerInput v) {
    staff("master");
    lock();
    var c = id == null ? new Customer() : customer(id);
    if (id != null) {
      OrderPolicy.revision(c.revision, v.revision);
      if (!Objects.equals(c.departmentId, v.departmentId)
          && (!db.query(Account.class, "from Account where customerId=?1", id).isEmpty()
              || !db.query(SalesOrder.class, "from SalesOrder where customerId=?1", id).isEmpty()))
        throw new Problem(409, "CUSTOMER_DEPARTMENT_LOCKED");
    }
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    c.code = text(v.code, 60);
    if (!c.code.matches("[A-Za-z0-9_.-]+")) throw new Problem(400, "INVALID_CODE");
    c.name = text(v.name, 160);
    c.departmentId = v.departmentId;
    c.address = text(v.address, 1000);
    c.paymentTerms = text(v.paymentTerms, 300);
    c.enabled = Boolean.TRUE.equals(v.enabled);
    c.revision++;
    if (id == null) db.save(c);
    access.audit("CUSTOMER_SAVE", c.id, c.departmentId);
    return c;
  }

  /** 只删除尚未被账号、价格、清单、订单引用的客户，外键保护历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteCustomer(Long id, Long revision) {
    staff("master");
    lock();
    var c = customer(id);
    OrderPolicy.revision(c.revision, revision);
    access.audit("CUSTOMER_DELETE", c.id, c.departmentId);
    db.delete(c);
  }

  /** 员工商品主数据不是客户目录，客户目录由独立接口按协议价过滤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Product> products() {
    staff("master");
    return db.all(Product.class);
  }

  /** 商品主数据为全公司共享；只有全部范围的管理者可以修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Product saveProduct(Long id, ProductInput v) {
    staff("master");
    if (!access.role().scope.equals("ALL")) throw new Problem(403, "GLOBAL_MASTER_REQUIRED");
    lock();
    var p = id == null ? new Product() : db.get(Product.class, id);
    if (id != null) OrderPolicy.revision(p.revision, v.revision);
    p.sku = text(v.sku, 60);
    if (!p.sku.matches("[A-Za-z0-9_.-]+")) throw new Problem(400, "INVALID_CODE");
    p.name = text(v.name, 160);
    p.nameEn = text(v.nameEn, 160);
    p.unit = text(v.unit, 60);
    p.category = text(v.category, 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type=?1 and code=?2 and enabled=true",
            "category",
            p.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    p.enabled = Boolean.TRUE.equals(v.enabled);
    p.revision++;
    if (id == null) db.save(p);
    access.audit("PRODUCT_SAVE", p.id, access.current().departmentId);
    return p;
  }

  /** 删除没有业务引用的商品；已有订货历史改为停用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteProduct(Long id, Long revision) {
    staff("master");
    if (!access.role().scope.equals("ALL")) throw new Problem(403, "GLOBAL_MASTER_REQUIRED");
    lock();
    var p = db.get(Product.class, id);
    OrderPolicy.revision(p.revision, revision);
    access.audit("PRODUCT_DELETE", id, access.current().departmentId);
    db.delete(p);
  }

  /** 员工范围内的客户价表，客户永远不拿到其他客户ID与协议价格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<CustomerPrice> prices() {
    staff("master");
    return db.all(CustomerPrice.class).stream()
        .filter(p -> access.visible(db.get(Customer.class, p.customerId).departmentId))
        .toList();
  }

  /** 价表更新受客户数据范围及版本约束；不能将已有价表移至其他客户商品。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CustomerPrice savePrice(Long id, PriceInput v) {
    staff("master");
    lock();
    var c = customer(v.customerId);
    db.get(Product.class, v.productId);
    var p = id == null ? new CustomerPrice() : db.get(CustomerPrice.class, id);
    if (id != null) {
      customer(p.customerId);
      OrderPolicy.revision(p.revision, v.revision);
      if (!Objects.equals(p.customerId, v.customerId) || !Objects.equals(p.productId, v.productId))
        throw new Problem(409, "PRICE_KEY_IMMUTABLE");
    }
    if (v.minQty == null || v.stepQty == null || v.maxQty == null)
      throw new Problem(400, "INVALID_QUANTITY");
    OrderPolicy.quantity(v.minQty, v.minQty, v.stepQty, v.maxQty);
    p.customerId = c.id;
    p.productId = v.productId;
    p.unitPrice = OrderPolicy.price(v.unitPrice);
    p.minQty = v.minQty;
    p.stepQty = v.stepQty;
    p.maxQty = v.maxQty;
    p.enabled = Boolean.TRUE.equals(v.enabled);
    p.revision++;
    if (id == null) db.save(p);
    access.audit("PRICE_SAVE", p.id, c.departmentId);
    return p;
  }

  /** 价表可删除，历史订单仍保留自己的协议价快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deletePrice(Long id, Long revision) {
    staff("master");
    lock();
    var p = db.get(CustomerPrice.class, id);
    var c = customer(p.customerId);
    OrderPolicy.revision(p.revision, revision);
    access.audit("PRICE_DELETE", id, c.departmentId);
    db.delete(p);
  }

  /** 一次最多500行批量维护，重复键、未知SKU、越权或版本冲突会回滚整批。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object importPrices(List<PriceRow> rows) {
    staff("master");
    lock();
    if (rows == null || rows.isEmpty() || rows.size() > 500)
      throw new Problem(400, "INVALID_IMPORT");
    var keys = new HashSet<String>();
    for (var r : rows) {
      if (r == null || !keys.add(r.customerCode + "\n" + r.sku))
        throw new Problem(400, "DUPLICATE_IMPORT_ROW");
    }
    for (var r : rows) {
      var cs = db.query(Customer.class, "from Customer where code=?1", r.customerCode);
      var ps = db.query(Product.class, "from Product where sku=?1", r.sku);
      if (cs.isEmpty() || ps.isEmpty()) throw new Problem(400, "UNKNOWN_IMPORT_KEY");
      var found =
          db.query(
              CustomerPrice.class,
              "from CustomerPrice where customerId=?1 and productId=?2",
              cs.getFirst().id,
              ps.getFirst().id);
      if (found.isEmpty() && r.revision != null) throw new Problem(409, "STALE_VERSION");
      savePrice(
          found.isEmpty() ? null : found.getFirst().id,
          new PriceInput(
              cs.getFirst().id,
              ps.getFirst().id,
              r.unitPrice,
              r.minQty,
              r.stepQty,
              r.maxQty,
              r.enabled,
              r.revision));
    }
    return Map.of("imported", rows.size());
  }

  /** 字典与部门供员工表单使用，不返回任何密码或客户账号目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object catalog() {
    staff("dashboard");
    return Map.of(
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "companyName")
            .getFirst()
            .value,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "categories",
        db.query(DictionaryEntry.class, "from DictionaryEntry where type=?1", "category"),
        "currency",
        currency());
  }

  /** 客户订货页只包含本客户协议商品、记录和常购清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object portal() {
    var c = own();
    var items = new ArrayList<Map<String, Object>>();
    for (var p :
        db.query(
            CustomerPrice.class, "from CustomerPrice where customerId=?1 and enabled=true", c.id)) {
      var product = db.get(Product.class, p.productId);
      if (!productActive(product)) continue;
      items.add(
          Map.of(
              "product",
              product,
              "unitPrice",
              p.unitPrice,
              "minQty",
              p.minQty,
              "stepQty",
              p.stepQty,
              "maxQty",
              p.maxQty));
    }
    var templates =
        db.query(OrderTemplate.class, "from OrderTemplate where customerId=?1", c.id).stream()
            .map(
                x ->
                    Map.of(
                        "template",
                        x,
                        "lines",
                        db.query(
                            TemplateLine.class, "from TemplateLine where templateId=?1", x.id)))
            .toList();
    return Map.of(
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "companyName")
            .getFirst()
            .value,
        "categories",
        db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type=?1 and enabled=true",
            "category"),
        "customer",
        c,
        "items",
        items,
        "orders",
        db.query(SalesOrder.class, "from SalesOrder where customerId=?1 order by id desc", c.id),
        "templates",
        templates,
        "currency",
        currency(),
        "notice",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "orderingNotice")
            .getFirst()
            .value);
  }

  /** 客户新建或编辑草稿；服务端按当前专属价表计算，完整替换草稿行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public SalesOrder saveOrder(Long id, OrderInput v) {
    lock();
    var c = own();
    var o = id == null ? new SalesOrder() : visibleOrder(id);
    if (id != null) {
      OrderPolicy.revision(o.revision, v.revision);
      if (!o.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
    }
    validateDate(v.deliveryDate);
    var evaluated = evaluate(c.id, v.lines);
    if (id == null) {
      o.reference = "OD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
      o.customerId = c.id;
      o.creatorId = access.current().id;
      o.createdAt = clock.instant();
      o.status = "DRAFT";
      o.currency = currency();
      o.customerName = c.name;
      o.address = c.address;
      o.deliveryDate = v.deliveryDate;
      o.note = optional(v.note, 1000);
      o.total = BigDecimal.ZERO.setScale(2);
      db.save(o);
    } else {
      for (var l : lines(id)) db.delete(l);
    }
    o.customerName = c.name;
    o.address = c.address;
    o.deliveryDate = v.deliveryDate;
    o.note = optional(v.note, 1000);
    o.total = BigDecimal.ZERO.setScale(2);
    for (var l : evaluated) {
      l.orderId = o.id;
      db.save(l);
      o.total = o.total.add(l.unitPrice.multiply(BigDecimal.valueOf(l.qty)));
    }
    o.revision++;
    access.audit("ORDER_DRAFT_SAVE", o.id, c.departmentId);
    return o;
  }

  private void validateDate(LocalDate d) {
    var today = LocalDate.now(clock);
    if (d == null || d.isBefore(today) || d.isAfter(today.plusDays(365)))
      throw new Problem(400, "INVALID_DELIVERY_DATE");
  }

  private List<OrderLine> evaluate(Long customerId, List<LineInput> input) {
    if (input == null || input.isEmpty() || input.size() > 200)
      throw new Problem(400, "EMPTY_LINES");
    var used = new HashSet<Long>();
    var result = new ArrayList<OrderLine>();
    for (var v : input) {
      if (v == null || v.productId == null || v.qty == null || !used.add(v.productId))
        throw new Problem(400, "INVALID_LINES");
      var p = activePrice(customerId, v.productId);
      OrderPolicy.quantity(v.qty, p.minQty, p.stepQty, p.maxQty);
      var product = db.get(Product.class, p.productId);
      var l = new OrderLine();
      l.productId = product.id;
      l.sku = product.sku;
      l.name = product.name;
      l.nameEn = product.nameEn;
      l.unit = product.unit;
      l.qty = v.qty;
      l.unitPrice = p.unitPrice;
      result.add(l);
    }
    return result;
  }

  /** 客户订单详情包括本人流程、价格快照及分批交接历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var o = visibleOrder(id);
    var dispatches =
        db.query(Dispatch.class, "from Dispatch where orderId=?1 order by id", id).stream()
            .map(
                d ->
                    Map.of(
                        "dispatch",
                        d,
                        "lines",
                        db.query(
                            DispatchLine.class, "from DispatchLine where dispatchId=?1", d.id)))
            .toList();
    return Map.of(
        "order",
        o,
        "lines",
        lines(id),
        "events",
        db.query(OrderEvent.class, "from OrderEvent where orderId=?1 order by id", id),
        "dispatches",
        dispatches);
  }

  /** 员工订单列表按客户部门隔离，不按菜单隐藏代替访问检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<SalesOrder> orders() {
    staff("read");
    return db.all(SalesOrder.class).stream()
        .filter(o -> access.visible(db.get(Customer.class, o.customerId).departmentId))
        .toList();
  }

  /** 合法状态流转，客户提交重新核价，供应商确认前重新核验上下架与价变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public SalesOrder command(Long id, String action, Command v) {
    lock();
    var o = visibleOrder(id);
    OrderPolicy.revision(o.revision, v.revision);
    boolean portal = access.current().customerId != null;
    switch (action) {
      case "submit" -> {
        own();
        state(o, "DRAFT");
        validateDate(o.deliveryDate);
        checkPrices(o);
        o.status = "SUBMITTED";
        o.submittedAt = clock.instant();
      }
      case "revise" -> {
        own();
        state(o, "REJECTED");
        o.status = "DRAFT";
      }
      case "cancel" -> {
        if (portal) {
          own();
          if (!Set.of("DRAFT", "SUBMITTED").contains(o.status))
            throw new Problem(409, "INVALID_STATE");
        } else {
          staff("process");
          if (!Set.of("SUBMITTED", "CONFIRMED").contains(o.status))
            throw new Problem(409, "INVALID_STATE");
        }
        if (lines(id).stream().anyMatch(l -> l.fulfilled > 0))
          throw new Problem(409, "ALREADY_FULFILLED");
        text(v.note, 1000);
        o.status = "CANCELLED";
      }
      case "confirm" -> {
        staff("process");
        state(o, "SUBMITTED");
        if (!customer(o.customerId).enabled) throw new Problem(409, "CUSTOMER_DISABLED");
        checkPrices(o);
        o.status = "CONFIRMED";
        o.confirmedAt = clock.instant();
      }
      case "reject" -> {
        staff("process");
        state(o, "SUBMITTED");
        text(v.note, 1000);
        o.status = "REJECTED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    o.revision++;
    event(o, action.toUpperCase(Locale.ROOT), v.note);
    return o;
  }

  private void state(SalesOrder o, String expected) {
    if (!o.status.equals(expected)) throw new Problem(409, "INVALID_STATE");
  }

  private void checkPrices(SalesOrder o) {
    for (var l : lines(o.id)) {
      var p = activePrice(o.customerId, l.productId);
      OrderPolicy.quantity(l.qty, p.minQty, p.stepQty, p.maxQty);
      var product = db.get(Product.class, l.productId);
      if (!Objects.equals(l.sku, product.sku)
          || !Objects.equals(l.unit, product.unit)
          || !Objects.equals(l.name, product.name)
          || !Objects.equals(l.nameEn, product.nameEn)) throw new Problem(409, "CATALOG_CHANGED");
      if (l.unitPrice.compareTo(p.unitPrice) != 0) throw new Problem(409, "PRICE_CHANGED");
    }
  }

  /** 删除从未进入流程的客户草稿；取消后的历史不删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteOrder(Long id, Long revision) {
    lock();
    own();
    var o = visibleOrder(id);
    OrderPolicy.revision(o.revision, revision);
    state(o, "DRAFT");
    if (!db.query(OrderEvent.class, "from OrderEvent where orderId=?1", id).isEmpty())
      throw new Problem(409, "HAS_HISTORY");
    for (var l : lines(id)) db.delete(l);
    access.audit("ORDER_DRAFT_DELETE", id, db.get(Customer.class, o.customerId).departmentId);
    db.delete(o);
  }

  /** 常购清单真实持久化，可增删改，下单时要重新校验当前商品及包装规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveTemplate(Long id, TemplateInput v) {
    lock();
    var c = own();
    var template = id == null ? new OrderTemplate() : template(id);
    if (id != null) OrderPolicy.revision(template.revision, v.revision);
    var evaluated = evaluate(c.id, v.lines);
    template.customerId = c.id;
    template.name = text(v.name, 160);
    if (id == null) {
      template.creatorId = access.current().id;
      db.save(template);
    } else {
      for (var l : db.query(TemplateLine.class, "from TemplateLine where templateId=?1", id))
        db.delete(l);
    }
    for (var row : evaluated) {
      var l = new TemplateLine();
      l.templateId = template.id;
      l.productId = row.productId;
      l.qty = row.qty;
      db.save(l);
    }
    template.revision++;
    access.audit("TEMPLATE_SAVE", template.id, c.departmentId);
    return template;
  }

  private OrderTemplate template(Long id) {
    var t = db.get(OrderTemplate.class, id);
    if (!Objects.equals(t.customerId, own().id)) throw new Problem(403, "OUT_OF_SCOPE");
    return t;
  }

  /** 只删除本人客户的常购清单及所属行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteTemplate(Long id, Long revision) {
    lock();
    var t = template(id);
    OrderPolicy.revision(t.revision, revision);
    for (var l : db.query(TemplateLine.class, "from TemplateLine where templateId=?1", id))
      db.delete(l);
    access.audit("TEMPLATE_DELETE", id, own().departmentId);
    db.delete(t);
  }

  /** 已确认订单的分批交接，拒绝跨行、超量、重复凭据，全部交完才结束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object dispatch(Long id, DispatchInput v) {
    staff("process");
    lock();
    var o = visibleOrder(id);
    state(o, "CONFIRMED");
    OrderPolicy.revision(o.revision, v.revision);
    if (v.lines == null || v.lines.isEmpty() || v.lines.size() > 200)
      throw new Problem(400, "EMPTY_LINES");
    var d = new Dispatch();
    d.orderId = id;
    d.reference = text(v.reference, 160);
    d.note = optional(v.note, 1000);
    d.actor = access.current().username;
    d.createdAt = clock.instant();
    db.save(d);
    var used = new HashSet<Long>();
    for (var row : v.lines) {
      if (row == null
          || row.orderLineId == null
          || row.qty == null
          || row.qty <= 0
          || !used.add(row.orderLineId)) throw new Problem(400, "INVALID_LINES");
      var l = db.get(OrderLine.class, row.orderLineId);
      if (!Objects.equals(l.orderId, id) || row.qty > l.qty - l.fulfilled)
        throw new Problem(409, "OVER_FULFILMENT");
      l.fulfilled += row.qty;
      var dl = new DispatchLine();
      dl.dispatchId = d.id;
      dl.orderLineId = l.id;
      dl.qty = row.qty;
      db.save(dl);
    }
    if (lines(id).stream().allMatch(l -> l.fulfilled == l.qty)) o.status = "FULFILLED";
    o.revision++;
    event(o, "DISPATCH", d.reference);
    return detail(id);
  }

  /** 当前范围统计使用真实订单，不将草稿、拒绝或取消金额算作已确认交易。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report() {
    staff("dashboard");
    var rows = orders();
    var stats = new LinkedHashMap<String, Long>();
    for (var s : List.of("DRAFT", "SUBMITTED", "CONFIRMED", "REJECTED", "CANCELLED", "FULFILLED"))
      stats.put(s, rows.stream().filter(o -> o.status.equals(s)).count());
    var total =
        rows.stream()
            .filter(o -> Set.of("CONFIRMED", "FULFILLED").contains(o.status))
            .map(o -> o.total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "statuses",
        stats,
        "confirmedValue",
        total,
        "currency",
        currency(),
        "recent",
        rows.stream()
            .sorted(Comparator.comparing((SalesOrder o) -> o.id).reversed())
            .limit(8)
            .toList());
  }

  /** 按UTC提交日导出已确认或已交付订单行；含剩余数量，不假装已经自动入WMS。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(LocalDate date) {
    staff("report");
    if (date == null) throw new Problem(400, "INVALID_DATE");
    var b =
        new StringBuilder(
            "\ufeffOrder,Customer,CustomerCode,DeliveryDate,Address,SKU,Product,Unit,Quantity,Fulfilled,Remaining,UnitPrice,Currency,LineTotal,Status\r\n");
    for (var o : orders()) {
      if (!Set.of("CONFIRMED", "FULFILLED").contains(o.status)
          || o.submittedAt == null
          || !o.submittedAt.atZone(ZoneOffset.UTC).toLocalDate().equals(date)) continue;
      var c = db.get(Customer.class, o.customerId);
      for (var l : lines(o.id)) {
        for (var value :
            List.of(
                o.reference,
                o.customerName,
                c.code,
                o.deliveryDate.toString(),
                o.address,
                l.sku,
                l.name,
                l.unit,
                Integer.toString(l.qty),
                Integer.toString(l.fulfilled),
                Integer.toString(l.qty - l.fulfilled),
                l.unitPrice.toPlainString(),
                o.currency,
                l.unitPrice.multiply(BigDecimal.valueOf(l.qty)).toPlainString(),
                o.status)) b.append(cell(value)).append(',');
        b.setLength(b.length() - 1);
        b.append("\r\n");
      }
    }
    return b.toString();
  }

  /** 价表导出携带版本，支持安全编辑后原子导入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String exportPrices() {
    var b =
        new StringBuilder(
            "\ufeffcustomerCode,sku,unitPrice,minQty,stepQty,maxQty,enabled,revision\r\n");
    for (var p : prices()) {
      for (var v :
          List.of(
              db.get(Customer.class, p.customerId).code,
              db.get(Product.class, p.productId).sku,
              p.unitPrice.toPlainString(),
              "" + p.minQty,
              "" + p.stepQty,
              "" + p.maxQty,
              "" + p.enabled,
              "" + p.revision)) b.append(cell(v)).append(',');
      b.setLength(b.length() - 1);
      b.append("\r\n");
    }
    return b.toString();
  }

  private String cell(String value) {
    if (value.matches("^[\\s]*[=+\\-@].*")) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  /** 元数据审计按员工范围，客户账号不能访问员工日志目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audits() {
    staff("audit");
    return db.all(AuditEvent.class).stream().filter(a -> access.visible(a.departmentId)).toList();
  }
}
