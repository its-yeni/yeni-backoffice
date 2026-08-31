ALTER TABLE commerce_order_item ADD COLUMN option_summary VARCHAR(500);
ALTER TABLE commerce_order_item ADD COLUMN option_additional_amount DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE commerce_order_item ADD COLUMN selected_option_value_ids VARCHAR(500);
ALTER TABLE commerce_order_item ADD COLUMN addon_item BOOLEAN NOT NULL DEFAULT FALSE;
