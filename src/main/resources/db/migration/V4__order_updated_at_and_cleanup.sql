-- Track when an order was last changed (status transitions). Set via the injected Clock in the
-- service, not a DB default, so tests stay deterministic. Add nullable, backfill from created_at,
-- then enforce NOT NULL so existing rows satisfy the constraint.
alter table orders add column updated_at timestamp(6) with time zone;
update orders set updated_at = created_at;
alter table orders alter column updated_at set not null;

-- Conflict traceability now lives entirely in the append-only payment_results log; the per-attempt
-- flag was redundant and never populated, so drop it.
alter table payment_attempts drop column conflict_detected;

-- Name the products SKU unique constraint explicitly to match the entity mapping
-- (@UniqueConstraint(name = "uniqueSku")). V1 created it inline, so PostgreSQL auto-named it
-- products_sku_key; rename by drop + re-add. Quoted to preserve the exact camelCase name.
alter table products drop constraint products_sku_key;
alter table products add constraint "uniqueSku" unique (sku);
