-- Add courier handoff fields and status transitions
ALTER TABLE orders ADD COLUMN courier_notes TEXT;

-- Add courier-specific statuses
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (
    'PENDING','CONFIRMED','PREPARING','READY_FOR_PICKUP',
    'OUT_FOR_DELIVERY','DELIVERED','PICKED_UP','CANCELLED',
    'COURIER_ASSIGNED','COURIER_PICKED_UP','COURIER_DELIVERED'
));

-- Update fulfillment_mode constraint
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_fulfillment_mode_check;
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_mode_check CHECK (fulfillment_mode IN ('PICKUP','DELIVERY','COURIER_HANDOFF'));
