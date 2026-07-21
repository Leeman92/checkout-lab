create sequence orders_seq start with 1 increment by 50;
create sequence order_items_seq start with 1 increment by 50;

create table orders (
    id bigint not null,
    idempotency_key varchar(255) not null,
    request_fingerprint varchar(64) not null,
    status varchar(32) not null,
    total_net_in_cents bigint not null,
    currency varchar(3) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    unique (idempotency_key),
    constraint chk_orders_status
        check (status in ('RESERVED', 'PAID', 'PAYMENT_FAILED', 'CANCELLED'))
);

create table order_items (
    id bigint not null,
    order_id bigint not null,
    sku varchar(255) not null,
    quantity integer not null,
    unit_net_price_in_cents bigint not null,
    line_net_in_cents bigint not null,
    primary key (id),
    constraint fk_order_items_order foreign key (order_id) references orders (id),
    constraint chk_order_items_quantity_positive check (quantity > 0)
);

create index idx_order_items_order_id on order_items (order_id);

-- Defense in depth for FR-009: the database itself refuses to let available stock go negative,
-- independent of the application-level atomic reservation update.
alter table products
    add constraint chk_products_reserved_nonneg check (reserved_stock >= 0),
    add constraint chk_products_reserved_le_total check (reserved_stock <= total_stock);
