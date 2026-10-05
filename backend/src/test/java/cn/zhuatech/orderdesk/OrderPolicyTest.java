// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 包装起订量、精准金额与过期写入的业务规则测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class OrderPolicyTest {
  @Test
  void packQuantityStartsAtMinimum() {
    OrderPolicy.quantity(8, 5, 3, 20);
    assertThrows(Problem.class, () -> OrderPolicy.quantity(9, 5, 3, 20));
    assertThrows(Problem.class, () -> OrderPolicy.quantity(2, 5, 3, 20));
    assertThrows(Problem.class, () -> OrderPolicy.quantity(23, 5, 3, 20));
  }

  @Test
  void priceDoesNotSilentlyRound() {
    assertEquals(new BigDecimal("0.10"), OrderPolicy.price(new BigDecimal("0.1")));
    assertThrows(Problem.class, () -> OrderPolicy.price(new BigDecimal("1.001")));
    assertThrows(Problem.class, () -> OrderPolicy.price(BigDecimal.ZERO));
    assertThrows(Problem.class, () -> OrderPolicy.price(new BigDecimal("1000000")));
  }

  @Test
  void versionRequired() {
    OrderPolicy.revision(3, 3L);
    assertThrows(Problem.class, () -> OrderPolicy.revision(3, null));
    assertThrows(Problem.class, () -> OrderPolicy.revision(3, 2L));
  }
}
