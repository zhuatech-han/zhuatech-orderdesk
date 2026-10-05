// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 人工登记的分批仓库交接，保留外部凭据与责任人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "dispatch")
public class Dispatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = true)
  public Long orderId;

  @Column(name = "reference", nullable = true, length = 160)
  public String reference;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
