ALTER TABLE option_group_template ADD COLUMN customer_display_name VARCHAR(100);
UPDATE option_group_template SET customer_display_name = template_name WHERE customer_display_name IS NULL;
