// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.orderdesk;

import jakarta.persistence.*;
import java.time.*;

/** 客户订货范围与配送资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = true, length = 60)
  public String code;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "address", nullable = true, length = 1000)
  public String address;

  @Column(name = "payment_terms", nullable = true, length = 300)
  public String paymentTerms;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
