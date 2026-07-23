-- Remove the unreachable PAYMENT_FAILED order status. No flow ever set it: a declined payment leaves
-- the order RESERVED so it can be retried, and the only terminal states are PAID and CANCELLED.
-- Tighten the CHECK to the states the state machine can actually reach. No data migration is needed
-- because no row can hold the value being removed.
alter table orders
    drop constraint chk_orders_status,
    add constraint chk_orders_status
        check (status in ('RESERVED', 'PAID', 'CANCELLED'));
