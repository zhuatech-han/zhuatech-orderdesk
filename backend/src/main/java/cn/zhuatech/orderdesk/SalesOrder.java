// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 客户订单与冻结的收货资料、金额及状态版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "sales_order")
public class SalesOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 60)
  public String reference;

  @Column(name = "customer_id", nullable = true)
  public Long customerId;

  @Column(name = "customer_name", nullable = true, length = 160)
  public String customerName;

  @Column(name = "address", nullable = true, length = 1000)
  public String address;

  @Column(name = "delivery_date", nullable = true)
  public LocalDate deliveryDate;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "currency", nullable = true, length = 3)
  public String currency;

  @Column(name = "status", nullable = true, length = 20)
  public String status;

  @Column(name = "total", nullable = true, precision = 18, scale = 2)
  public BigDecimal total;

  @Column(name = "creator_id", nullable = true)
  public Long creatorId;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "submitted_at", nullable = false)
  public Instant submittedAt;

  @Column(name = "confirmed_at", nullable = false)
  public Instant confirmedAt;

  @Column(name = "revision", nullable = true)
  public long revision;
}
