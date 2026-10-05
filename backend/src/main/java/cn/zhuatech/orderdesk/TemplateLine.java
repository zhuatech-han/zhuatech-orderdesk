// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 常购商品及数量，不保存旧协议价格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "template_line")
public class TemplateLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "template_id", nullable = true)
  public Long templateId;

  @Column(name = "product_id", nullable = true)
  public Long productId;

  @Column(name = "qty", nullable = true)
  public int qty;
}
