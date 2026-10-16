-- Shopee-style order tracking, advanced by staff: carrier + tracking code on the order, and a
-- timeline of status updates (each with an optional note) the customer can follow.
ALTER TABLE dtb_print_orders
    ADD COLUMN carrier       VARCHAR(100),
    ADD COLUMN tracking_code VARCHAR(100);

CREATE TABLE dtb_print_order_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    print_order_id UUID         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    note           VARCHAR(500),
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_print_order_events_status
        CHECK (status IN ('PENDING', 'PAID', 'IN_PRODUCTION', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_dtb_print_order_events_order
        FOREIGN KEY (print_order_id) REFERENCES dtb_print_orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_print_order_events_order_created ON dtb_print_order_events (print_order_id, created_at);

-- Backfill existing orders: when they were placed, plus their current status.
INSERT INTO dtb_print_order_events (print_order_id, status, created_at)
SELECT id, 'PENDING', created_at FROM dtb_print_orders;

INSERT INTO dtb_print_order_events (print_order_id, status, created_at)
SELECT id, status, updated_at FROM dtb_print_orders WHERE status <> 'PENDING';
