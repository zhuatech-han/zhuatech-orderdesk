// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP/JPA价表隔离、原子导入、订单核价冻结、版本、分批交接及权限回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:orders;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("orderdesk.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, buyer, other;
  String suffix;
  long customerId, otherCustomerId, productId, priceId, customerRole;

  @BeforeEach
  void setup() throws Exception {
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    customerRole = role("Customer");
    customerId = customer("C" + suffix).get("id").asLong();
    otherCustomerId = customer("B" + suffix).get("id").asLong();
    productId = product("P" + suffix).get("id").asLong();
    priceId = price(customerId, productId, "12.35", 1, 1, 100).get("id").asLong();
    price(otherCustomerId, productId, "8.20", 1, 1, 100);
    user("buyer" + suffix, customerRole, 1, customerId);
    user("other" + suffix, customerRole, 1, otherCustomerId);
    buyer = login("buyer" + suffix);
    other = login("other" + suffix);
  }

  Map<String, Object> m(Object... pairs) {
    var r = new LinkedHashMap<String, Object>();
    for (int n = 0; n < pairs.length; n += 2) r.put(pairs[n].toString(), pairs[n + 1]);
    return r;
  }

  JsonNode call(MockHttpSession s, String path, String method, Object body, int code)
      throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (s != null) b.session(s);
    if (!method.equals("GET")) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    var res = mvc.perform(b).andReturn().getResponse();
    assertEquals(code, res.getStatus(), method + path + res.getContentAsString());
    return res.getContentAsString().isEmpty()
        ? json.readTree("{}")
        : json.readTree(res.getContentAsString());
  }

  MockHttpSession login(String name) throws Exception {
    var res =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", name, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
    return (MockHttpSession) res.getRequest().getSession(false);
  }

  long role(String name) throws Exception {
    for (var r : call(admin, "/admin/roles", "GET", null, 200))
      if (r.get("name").asString().contains(name)) return r.get("id").asLong();
    throw new AssertionError(name);
  }

  JsonNode customer(String code) throws Exception {
    return call(
        admin,
        "/master/customers",
        "POST",
        m(
            "code",
            code,
            "name",
            "TEST " + code,
            "departmentId",
            1,
            "address",
            "TEST warehouse",
            "paymentTerms",
            "TEST external payment",
            "enabled",
            true),
        200);
  }

  JsonNode product(String sku) throws Exception {
    return call(
        admin,
        "/master/products",
        "POST",
        m(
            "sku",
            sku,
            "name",
            "TEST product",
            "nameEn",
            "TEST product",
            "category",
            "GENERAL",
            "unit",
            "箱 / case",
            "enabled",
            true),
        200);
  }

  JsonNode price(long cid, long pid, String value, int min, int step, int max) throws Exception {
    return call(
        admin,
        "/master/prices",
        "POST",
        m(
            "customerId",
            cid,
            "productId",
            pid,
            "unitPrice",
            value,
            "minQty",
            min,
            "stepQty",
            step,
            "maxQty",
            max,
            "enabled",
            true),
        200);
  }

  long user(String name, long role, long department, Long cid) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "password",
                PASSWORD,
                "roleId",
                role,
                "departmentId",
                department,
                "customerId",
                cid,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  JsonNode draft() throws Exception {
    return call(
        buyer,
        "/orders",
        "POST",
        m(
            "deliveryDate",
            LocalDate.now().plusDays(1).toString(),
            "note",
            "TEST request",
            "lines",
            List.of(m("productId", productId, "qty", 3))),
        200);
  }

  JsonNode action(MockHttpSession s, long id, String action, int code) throws Exception {
    var d = call(s, "/orders/" + id, "GET", null, 200);
    return call(
        s,
        "/orders/" + id + "/actions/" + action,
        "POST",
        m("revision", d.get("order").get("revision").asLong(), "note", "TEST reason"),
        code);
  }

  JsonNode updatePrice(String value, long rev, int code) throws Exception {
    return call(
        admin,
        "/master/prices/" + priceId,
        "PUT",
        m(
            "customerId",
            customerId,
            "productId",
            productId,
            "unitPrice",
            value,
            "minQty",
            1,
            "stepQty",
            1,
            "maxQty",
            100,
            "enabled",
            true,
            "revision",
            rev),
        code);
  }

  @Test
  void completeOrderFreezesPriceAndPartialFulfilment() throws Exception {
    var o = draft();
    long id = o.get("id").asLong();
    assertEquals("37.05", o.get("total").asString());
    action(buyer, id, "submit", 200);
    action(admin, id, "confirm", 200);
    updatePrice("20.00", 1, 200);
    var d = call(admin, "/orders/" + id, "GET", null, 200);
    long lid = d.get("lines").get(0).get("id").asLong();
    var partial =
        call(
            admin,
            "/orders/" + id + "/dispatches",
            "POST",
            m(
                "revision",
                d.get("order").get("revision").asLong(),
                "reference",
                "PART" + suffix,
                "lines",
                List.of(m("orderLineId", lid, "qty", 1))),
            200);
    assertEquals("CONFIRMED", partial.get("order").get("status").asString());
    assertEquals(1, partial.get("lines").get(0).get("fulfilled").asInt());
    var full =
        call(
            admin,
            "/orders/" + id + "/dispatches",
            "POST",
            m(
                "revision",
                partial.get("order").get("revision").asLong(),
                "reference",
                "FULL" + suffix,
                "lines",
                List.of(m("orderLineId", lid, "qty", 2))),
            200);
    assertEquals("FULFILLED", full.get("order").get("status").asString());
    assertEquals("37.05", full.get("order").get("total").asString());
    assertEquals(2, full.get("dispatches").size());
  }

  @Test
  void customerIsolationAndStaffApis() throws Exception {
    var a = call(buyer, "/portal", "GET", null, 200);
    var b = call(other, "/portal", "GET", null, 200);
    assertEquals("12.35", a.get("items").get(0).get("unitPrice").asString());
    assertEquals(
        0,
        new java.math.BigDecimal("8.20")
            .compareTo(
                new java.math.BigDecimal(b.get("items").get(0).get("unitPrice").asString())));
    assertFalse(call(admin, "/catalog", "GET", null, 200).get("companyName").asString().isBlank());
    var o = draft();
    call(other, "/orders/" + o.get("id").asLong(), "GET", null, 403);
    for (var path :
        List.of("/orders", "/master/prices", "/catalog", "/admin/users", "/audit", "/reports"))
      call(buyer, path, "GET", null, 403);
    assertFalse(call(buyer, "/auth/me", "GET", null, 200).toString().contains("password"));
  }

  @Test
  void changedPriceRequiresReviewInsteadOfSilentReprice() throws Exception {
    var o = draft();
    long id = o.get("id").asLong();
    updatePrice("15.50", 1, 200);
    assertEquals("PRICE_CHANGED", action(buyer, id, "submit", 409).get("code").asString());
    var saved =
        call(
            buyer,
            "/orders/" + id,
            "PUT",
            m(
                "deliveryDate",
                LocalDate.now().plusDays(1).toString(),
                "lines",
                List.of(m("productId", productId, "qty", 3)),
                "revision",
                o.get("revision").asLong()),
            200);
    assertEquals(
        0,
        new java.math.BigDecimal("46.50")
            .compareTo(new java.math.BigDecimal(saved.get("total").asString())));
    action(buyer, id, "submit", 200);
    updatePrice("17.00", 2, 200);
    assertEquals("PRICE_CHANGED", action(admin, id, "confirm", 409).get("code").asString());
  }

  @Test
  void importsAreAtomicAndRequireCurrentRevision() throws Exception {
    var first =
        m(
            "customerCode",
            "C" + suffix,
            "sku",
            "P" + suffix,
            "unitPrice",
            "9.90",
            "minQty",
            1,
            "stepQty",
            1,
            "maxQty",
            100,
            "enabled",
            true,
            "revision",
            1);
    var bad =
        m(
            "customerCode",
            "NO-CUSTOMER",
            "sku",
            "P" + suffix,
            "unitPrice",
            "10",
            "minQty",
            1,
            "stepQty",
            1,
            "maxQty",
            100,
            "enabled",
            true);
    call(admin, "/prices/import", "POST", List.of(first, bad), 400);
    assertEquals(
        "12.35",
        call(buyer, "/portal", "GET", null, 200).get("items").get(0).get("unitPrice").asString());
    call(admin, "/prices/import", "POST", List.of(first), 200);
    call(admin, "/prices/import", "POST", List.of(first), 409);
    call(admin, "/prices/import", "POST", List.of(first, first), 400);
  }

  @Test
  void packRulesDuplicateLinesAndInvalidDateRejected() throws Exception {
    call(
        buyer,
        "/orders",
        "POST",
        m(
            "deliveryDate",
            LocalDate.now().plusDays(1).toString(),
            "lines",
            List.of(m("productId", productId, "qty", 2), m("productId", productId, "qty", 3))),
        400);
    call(
        buyer,
        "/orders",
        "POST",
        m(
            "deliveryDate",
            LocalDate.now().minusDays(1).toString(),
            "lines",
            List.of(m("productId", productId, "qty", 1))),
        400);
    call(
        buyer,
        "/orders",
        "POST",
        m(
            "deliveryDate",
            LocalDate.now().toString(),
            "lines",
            List.of(m("productId", productId, "qty", 101))),
        400);
    assertEquals(0, call(buyer, "/portal", "GET", null, 200).get("orders").size());
  }

  @Test
  void staleOrderCannotSubmitTwice() throws Exception {
    var o = draft();
    long id = o.get("id").asLong();
    action(buyer, id, "submit", 200);
    call(
        buyer,
        "/orders/" + id + "/actions/submit",
        "POST",
        m("revision", o.get("revision").asLong()),
        409);
    call(
        buyer,
        "/orders/" + id,
        "PUT",
        m(
            "revision",
            2,
            "deliveryDate",
            LocalDate.now().toString(),
            "lines",
            List.of(m("productId", productId, "qty", 3))),
        409);
  }

  @Test
  void fractionalPackageQuantitiesAndIdentifiersNeverSilentlyTruncate() throws Exception {
    int orderCount = call(buyer, "/portal", "GET", null, 200).get("orders").size();
    for (Object qty : List.of(1.5, "1.5")) {
      var invalid =
          call(
              buyer,
              "/orders",
              "POST",
              m(
                  "deliveryDate",
                  LocalDate.now().plusDays(1).toString(),
                  "lines",
                  List.of(m("productId", productId, "qty", qty))),
              400);
      assertEquals("INVALID_INPUT", invalid.get("code").asString());
      call(
          buyer,
          "/templates",
          "POST",
          m(
              "name",
              "TEST fractional basket",
              "lines",
              List.of(m("productId", productId, "qty", qty))),
          400);
    }
    call(
        buyer,
        "/orders",
        "POST",
        m(
            "deliveryDate",
            LocalDate.now().plusDays(1).toString(),
            "lines",
            List.of(m("productId", productId + 0.5, "qty", 1))),
        400);
    call(
        admin,
        "/master/prices/" + priceId,
        "PUT",
        m(
            "customerId",
            customerId,
            "productId",
            productId,
            "unitPrice",
            "12.35",
            "minQty",
            1.5,
            "stepQty",
            1,
            "maxQty",
            100,
            "enabled",
            true,
            "revision",
            1),
        400);
    assertEquals(orderCount, call(buyer, "/portal", "GET", null, 200).get("orders").size());
    long id = draft().get("id").asLong();
    call(
        buyer,
        "/orders/" + id + "/actions/submit",
        "POST",
        m("revision", 1.5, "note", "TEST invalid version"),
        400);
    action(buyer, id, "submit", 200);
    action(admin, id, "confirm", 200);
    var before = call(admin, "/orders/" + id, "GET", null, 200);
    call(
        admin,
        "/orders/" + id + "/dispatches",
        "POST",
        m(
            "revision",
            before.get("order").get("revision").asLong(),
            "reference",
            "FRACTIONAL" + suffix,
            "lines",
            List.of(m("orderLineId", before.get("lines").get(0).get("id").asLong(), "qty", 1.5))),
        400);
    assertEquals(before, call(admin, "/orders/" + id, "GET", null, 200));
  }

  @Test
  void dispatchOverrunRollsBackAndCancellationBlockedAfterPartial() throws Exception {
    long id = draft().get("id").asLong();
    action(buyer, id, "submit", 200);
    action(admin, id, "confirm", 200);
    var d = call(admin, "/orders/" + id, "GET", null, 200);
    long lid = d.get("lines").get(0).get("id").asLong(),
        rev = d.get("order").get("revision").asLong();
    call(
        admin,
        "/orders/" + id + "/dispatches",
        "POST",
        m(
            "revision",
            rev,
            "reference",
            "BAD" + suffix,
            "lines",
            List.of(m("orderLineId", lid, "qty", 4))),
        409);
    assertEquals(0, call(admin, "/orders/" + id, "GET", null, 200).get("dispatches").size());
    call(
        admin,
        "/orders/" + id + "/dispatches",
        "POST",
        m(
            "revision",
            rev,
            "reference",
            "OK" + suffix,
            "lines",
            List.of(m("orderLineId", lid, "qty", 1))),
        200);
    action(admin, id, "cancel", 409);
    action(buyer, id, "cancel", 409);
  }

  @Test
  void templateCrudAndCrossCustomerDenied() throws Exception {
    var body = m("name", "TEST saved list", "lines", List.of(m("productId", productId, "qty", 2)));
    long id = call(buyer, "/templates", "POST", body, 200).get("id").asLong();
    body.put("revision", 1);
    body.put("name", "TEST updated");
    call(other, "/templates/" + id, "PUT", body, 403);
    call(buyer, "/templates/" + id, "PUT", body, 200);
    call(buyer, "/templates/" + id + "?revision=1", "DELETE", null, 409);
    call(buyer, "/templates/" + id + "?revision=2", "DELETE", null, 200);
    assertEquals(0, call(buyer, "/portal", "GET", null, 200).get("templates").size());
  }

  @Test
  void rejectionAndRevisionRetainHistory() throws Exception {
    long id = draft().get("id").asLong();
    action(buyer, id, "submit", 200);
    action(admin, id, "reject", 200);
    action(buyer, id, "revise", 200);
    call(buyer, "/orders/" + id + "?revision=4", "DELETE", null, 409);
    assertEquals(3, call(buyer, "/orders/" + id, "GET", null, 200).get("events").size());
    action(buyer, id, "cancel", 200);
  }

  @Test
  void customerBindingAndPrivilegeEscalationProtected() throws Exception {
    long uid = user("third" + suffix, customerRole, 1, customerId);
    call(
        admin,
        "/admin/users/" + uid,
        "PUT",
        m(
            "username",
            "third" + suffix,
            "displayName",
            "TEST third",
            "roleId",
            customerRole,
            "departmentId",
            1,
            "customerId",
            otherCustomerId,
            "enabled",
            true),
        409);
    call(
        admin,
        "/admin/users",
        "POST",
        m(
            "username",
            "bad" + suffix,
            "displayName",
            "TEST bad",
            "password",
            PASSWORD,
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "customerId",
            customerId,
            "enabled",
            true),
        409);
  }

  @Test
  void departmentScopeDoesNotExposeOtherCustomer() throws Exception {
    long did =
        call(admin, "/admin/departments", "POST", m("name", "TEST Department " + suffix), 200)
            .get("id")
            .asLong();
    user("staff" + suffix, role("Sales operations"), did, null);
    var staff = login("staff" + suffix);
    assertEquals(0, call(staff, "/master/customers", "GET", null, 200).size());
    long id = draft().get("id").asLong();
    call(staff, "/orders/" + id, "GET", null, 403);
    call(
        staff,
        "/master/products/" + productId,
        "PUT",
        m(
            "sku",
            "P" + suffix,
            "name",
            "BAD",
            "nameEn",
            "BAD",
            "unit",
            "box",
            "category",
            "GENERAL",
            "enabled",
            true,
            "revision",
            1),
        403);
  }

  @Test
  void disabledCustomerBlocksLiveSession() throws Exception {
    call(
        admin,
        "/master/customers/" + customerId,
        "PUT",
        m(
            "code",
            "C" + suffix,
            "name",
            "TEST disabled",
            "departmentId",
            1,
            "address",
            "TEST warehouse",
            "paymentTerms",
            "TEST terms",
            "enabled",
            false,
            "revision",
            1),
        200);
    call(buyer, "/portal", "GET", null, 403);
  }

  @Test
  void unusedDraftDeleteAndReferencedProductsProtected() throws Exception {
    long id = draft().get("id").asLong();
    call(buyer, "/orders/" + id + "?revision=1", "DELETE", null, 200);
    call(buyer, "/orders/" + id, "GET", null, 404);
    call(admin, "/master/products/" + productId + "?revision=1", "DELETE", null, 409);
  }

  @Test
  void anonymousCsrfAndLastAdminProtected() throws Exception {
    call(null, "/orders", "GET", null, 401);
    assertEquals(
        403,
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    var users = call(admin, "/admin/users", "GET", null, 200);
    long id = 0;
    for (var u : users) if (u.get("username").asString().equals("admin")) id = u.get("id").asLong();
    call(
        admin,
        "/admin/users/" + id,
        "PUT",
        m(
            "username",
            "admin",
            "displayName",
            "Admin",
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "enabled",
            false),
        409);
  }

  @Test
  void packUnitChangesRequireAnotherReview() throws Exception {
    long id = draft().get("id").asLong();
    call(
        admin,
        "/master/products/" + productId,
        "PUT",
        m(
            "sku",
            "P" + suffix,
            "name",
            "TEST product",
            "nameEn",
            "TEST product",
            "unit",
            "袋 / bag",
            "category",
            "GENERAL",
            "enabled",
            true,
            "revision",
            1),
        200);
    assertEquals("CATALOG_CHANGED", action(buyer, id, "submit", 409).get("code").asString());
  }

  @Test
  void passwordResetInvalidatesExistingSession() throws Exception {
    long uid = user("reset" + suffix, customerRole, 1, customerId);
    var session = login("reset" + suffix);
    call(
        admin,
        "/admin/users/" + uid,
        "PUT",
        m(
            "username",
            "reset" + suffix,
            "displayName",
            "TEST reset",
            "roleId",
            customerRole,
            "departmentId",
            1,
            "customerId",
            customerId,
            "enabled",
            true,
            "password",
            "Bb8" + UUID.randomUUID()),
        200);
    call(session, "/portal", "GET", null, 401);
  }
}
