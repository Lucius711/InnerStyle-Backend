-- Optional gift extras at print checkout: a paid LED light base and a free greeting card with a message.
ALTER TABLE dtb_print_orders
    ADD COLUMN with_light   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN with_card    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN card_message VARCHAR(300);
