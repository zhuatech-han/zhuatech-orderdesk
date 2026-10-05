// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 客户专属价格与最小量、增量和上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer_price")
public class CustomerPrice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "customer_id", nullable = true)
  public Long customerId;

  @Column(name = "product_id", nullable = true)
  public Long productId;

  @Column(name = "unit_price", nullable = true, precision = 18, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "min_qty", nullable = true)
  public int minQty;

  @Column(name = "step_qty", nullable = true)
  public int stepQty;

  @Column(name = "max_qty", nullable = true)
  public int maxQty;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
