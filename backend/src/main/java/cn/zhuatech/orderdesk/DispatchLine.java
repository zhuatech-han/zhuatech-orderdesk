// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 每次人工交接的商品数量，禁止超量和重复行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "dispatch_line")
public class DispatchLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "dispatch_id", nullable = true)
  public Long dispatchId;

  @Column(name = "order_line_id", nullable = true)
  public Long orderLineId;

  @Column(name = "qty", nullable = true)
  public int qty;
}
