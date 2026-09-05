-- Keep deployed migrations immutable. Resolve legacy recipients before dropping phone data.
ALTER TABLE gifticon_purchases ADD COLUMN recipient_email VARCHAR(255) NULL;
ALTER TABLE gifticon_purchases MODIFY COLUMN buyer_email VARCHAR(255) NULL;

-- Prefer the purchase-time email snapshot, including for withdrawn users.
UPDATE gifticon_purchases
SET buyer_email = COALESCE(
    NULLIF(TRIM(buyer_email), ''),
    (SELECT NULLIF(TRIM(u.email), '') FROM users u WHERE u.user_id = gifticon_purchases.user_id)
);

-- Only self-purchases can be converted automatically. A different recipient's
-- phone cannot establish their email; leave those recipients NULL for admin review.
-- Normalize only spaces/hyphens, and prefer the historical buyer phone snapshot.
UPDATE gifticon_purchases
SET recipient_email = buyer_email
WHERE NULLIF(TRIM(recipient_phone), '') IS NULL
   OR NULLIF(REPLACE(REPLACE(TRIM(recipient_phone), '-', ''), ' ', ''), '') = COALESCE(
       NULLIF(REPLACE(REPLACE(TRIM(buyer_phone), '-', ''), ' ', ''), ''),
       (SELECT NULLIF(REPLACE(REPLACE(TRIM(u.phone_num), '-', ''), ' ', ''), '')
          FROM users u WHERE u.user_id = gifticon_purchases.user_id)
   );

ALTER TABLE gifticon_purchases DROP COLUMN buyer_phone;
ALTER TABLE gifticon_purchases DROP COLUMN recipient_phone;
-- MySQL also removes the single-column unique index when its column is dropped.
ALTER TABLE users DROP COLUMN phone_num;
