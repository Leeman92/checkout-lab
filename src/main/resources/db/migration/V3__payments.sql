-- Optimistic-locking version on orders. Default 0 backfills existing rows so ddl-auto=validate
-- passes; Hibernate manages the value from here on (FR-024 / FR-029).
alter table orders add column version bigint not null default 0;

create sequence payment_attempts_seq start with 1 increment by 50;
create sequence payment_results_seq start with 1 increment by 50;

create table payment_attempts (
    id bigint not null,
    order_id bigint not null,
    attempt_number integer not null,
    status varchar(16) not null,
    gateway_reference varchar(255) not null,
    amount_in_cents bigint not null,
    created_at timestamp(6) with time zone not null,
    resolved_at timestamp(6) with time zone,
    conflict_detected boolean not null,
    primary key (id),
    unique (gateway_reference),
    -- At most one attempt per (order, number): the guard that makes near-simultaneous
    -- start-payment requests resolve to one winner and one conflict (FR-024).
    constraint uq_payment_attempts_order_number unique (order_id, attempt_number),
    constraint fk_payment_attempts_order foreign key (order_id) references orders (id),
    constraint chk_payment_attempts_status check (status in ('PENDING', 'SUCCESS', 'DECLINED'))
);

create index idx_payment_attempts_order_id on payment_attempts (order_id);

create table payment_results (
    id bigint not null,
    attempt_id bigint not null,
    message_id varchar(255) not null,
    result varchar(16) not null,
    received_at timestamp(6) with time zone not null,
    primary key (id),
    -- Append-only log: the unique message id makes a redelivered result a no-op (FR-021), and the
    -- full history keeps conflicting results traceable rather than overwritten (FR-022).
    unique (message_id),
    constraint fk_payment_results_attempt foreign key (attempt_id) references payment_attempts (id),
    -- Only terminal results are ever logged (stricter than the enum's DB check).
    constraint chk_payment_results_result check (result in ('SUCCESS', 'DECLINED'))
);

create index idx_payment_results_attempt_id on payment_results (attempt_id);
