// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可覆写的客户及供应商订单流程记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "order_event")
public class OrderEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = true)
  public Long orderId;

  @Column(name = "action", nullable = true, length = 60)
  public String action;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
