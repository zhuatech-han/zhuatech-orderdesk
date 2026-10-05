// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 同客户共享的常购清单，价格在再次下单时重新获取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "order_template")
public class OrderTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "customer_id", nullable = true)
  public Long customerId;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "creator_id", nullable = true)
  public Long creatorId;

  @Column(name = "revision", nullable = true)
  public long revision;
}
