create sequence products_seq start with 1 increment by 50;
create table products (active boolean not null, reserved_stock integer not null, total_stock integer not null, id bigint not null, net_price_in_cents bigint not null, name varchar(255), sku varchar(255), primary key (id), unique (sku));
