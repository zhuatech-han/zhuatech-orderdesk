// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 可供订货的商品及包装单位，不公开其他客户价表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "product")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "sku", nullable = true, length = 60)
  public String sku;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "name_en", nullable = true, length = 160)
  public String nameEn;

  @Column(name = "unit", nullable = true, length = 60)
  public String unit;

  @Column(name = "category", nullable = true, length = 60)
  public String category;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
