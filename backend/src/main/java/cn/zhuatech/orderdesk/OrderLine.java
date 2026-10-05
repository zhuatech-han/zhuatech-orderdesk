// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 订单商品名称、单位、协议价及已交付数量快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "order_line")
public class OrderLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = true)
  public Long orderId;

  @Column(name = "product_id", nullable = true)
  public Long productId;

  @Column(name = "sku", nullable = true, length = 60)
  public String sku;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "name_en", nullable = true, length = 160)
  public String nameEn;

  @Column(name = "unit", nullable = true, length = 60)
  public String unit;

  @Column(name = "unit_price", nullable = true, precision = 18, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "qty", nullable = true)
  public int qty;

  @Column(name = "fulfilled", nullable = true)
  public int fulfilled;
}
