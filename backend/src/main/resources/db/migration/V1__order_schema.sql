-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(6000) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);





CREATE TABLE customer (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(60) NOT NULL UNIQUE,
 name varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 address varchar(1000) NOT NULL,
 payment_terms varchar(300) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE product (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 sku varchar(60) NOT NULL UNIQUE,
 name varchar(160) NOT NULL,
 name_en varchar(160) NOT NULL,
 unit varchar(60) NOT NULL,
 category varchar(60) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL
);

CREATE TABLE customer_price (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 customer_id bigint NOT NULL,
 product_id bigint NOT NULL,
 unit_price decimal(18,2) NOT NULL,
 min_qty int NOT NULL,
 step_qty int NOT NULL,
 max_qty int NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(customer_id) REFERENCES customer(id),
 FOREIGN KEY(product_id) REFERENCES product(id),
 UNIQUE(customer_id,product_id),
 CHECK(unit_price > 0 AND min_qty > 0 AND step_qty > 0 AND max_qty >= min_qty)
);

CREATE TABLE sales_order (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL UNIQUE,
 customer_id bigint NOT NULL,
 customer_name varchar(160) NOT NULL,
 address varchar(1000) NOT NULL,
 delivery_date date NOT NULL,
 note varchar(1000) NOT NULL,
 currency varchar(3) NOT NULL,
 status varchar(20) NOT NULL,
 total decimal(18,2) NOT NULL,
 creator_id bigint NOT NULL,
 created_at timestamp(6) NOT NULL,
 submitted_at timestamp(6) NULL,
 confirmed_at timestamp(6) NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(customer_id) REFERENCES customer(id),
 FOREIGN KEY(creator_id) REFERENCES account(id),
 INDEX ix_order_customer_status(customer_id,status),
 INDEX ix_order_submitted(submitted_at)
);

CREATE TABLE order_line (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL,
 product_id bigint NOT NULL,
 sku varchar(60) NOT NULL,
 name varchar(160) NOT NULL,
 name_en varchar(160) NOT NULL,
 unit varchar(60) NOT NULL,
 unit_price decimal(18,2) NOT NULL,
 qty int NOT NULL,
 fulfilled int NOT NULL,
 FOREIGN KEY(order_id) REFERENCES sales_order(id),
 FOREIGN KEY(product_id) REFERENCES product(id),
 UNIQUE(order_id,product_id),
 CHECK(qty > 0 AND fulfilled >= 0 AND fulfilled <= qty)
);

CREATE TABLE order_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL,
 action varchar(60) NOT NULL,
 actor varchar(60) NOT NULL,
 note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES sales_order(id)
);

CREATE TABLE order_template (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 customer_id bigint NOT NULL,
 name varchar(160) NOT NULL,
 creator_id bigint NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(customer_id) REFERENCES customer(id),
 FOREIGN KEY(creator_id) REFERENCES account(id),
 UNIQUE(customer_id,name)
);

CREATE TABLE template_line (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 template_id bigint NOT NULL,
 product_id bigint NOT NULL,
 qty int NOT NULL,
 FOREIGN KEY(template_id) REFERENCES order_template(id),
 FOREIGN KEY(product_id) REFERENCES product(id),
 UNIQUE(template_id,product_id),
 CHECK(qty > 0)
);

CREATE TABLE dispatch (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL,
 reference varchar(160) NOT NULL UNIQUE,
 note varchar(1000) NOT NULL,
 actor varchar(60) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES sales_order(id)
);

CREATE TABLE dispatch_line (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 dispatch_id bigint NOT NULL,
 order_line_id bigint NOT NULL,
 qty int NOT NULL,
 FOREIGN KEY(dispatch_id) REFERENCES dispatch(id),
 FOREIGN KEY(order_line_id) REFERENCES order_line(id),
 UNIQUE(dispatch_id,order_line_id),
 CHECK(qty > 0)
);

ALTER TABLE account ADD customer_id bigint NULL;
ALTER TABLE account ADD FOREIGN KEY(customer_id) REFERENCES customer(id);
