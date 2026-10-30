-- Names are labels; account/key remains the stable identity for every association.
CREATE UNIQUE INDEX categories_account_name_unique ON categories(account_id, lower(display_name));
