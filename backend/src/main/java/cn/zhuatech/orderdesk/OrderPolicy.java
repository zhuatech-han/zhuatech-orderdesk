// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import java.math.*;

/** 纯业务规则：十进制金额、订货包装增量与乐观版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class OrderPolicy {
  private OrderPolicy() {}

  /** 协议价严格两位以内、正值且有上限，拒绝静默舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal price(BigDecimal v) {
    if (v == null
        || v.signum() <= 0
        || v.scale() > 2
        || v.compareTo(new BigDecimal("999999.99")) > 0) throw new Problem(400, "INVALID_PRICE");
    return v.setScale(2, RoundingMode.UNNECESSARY);
  }

  /** 数量按最小订购量起按增量递增，不以增量倍数误判最小量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void quantity(int qty, int min, int step, int max) {
    if (min < 1
        || step < 1
        || max < min
        || max > 100000
        || qty < min
        || qty > max
        || (qty - min) % step != 0) throw new Problem(400, "INVALID_QUANTITY");
  }

  /** 写操作必须携带读到的版本，拒绝过期或缺失版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void revision(long current, Long submitted) {
    if (submitted == null || submitted != current) throw new Problem(409, "STALE_VERSION");
  }
}
