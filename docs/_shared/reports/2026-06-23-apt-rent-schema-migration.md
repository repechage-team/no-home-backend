# Apartment Rent Schema Migration

For an existing local database, apply these columns before importing apartment jeonse/monthly rent data.

```sql
ALTER TABLE house_deals
    ADD COLUMN rent_type VARCHAR(30) NULL AFTER deal_amount_manwon,
    ADD COLUMN deposit VARCHAR(32) NULL AFTER rent_type,
    ADD COLUMN deposit_manwon INT NULL AFTER deposit,
    ADD COLUMN monthly_rent VARCHAR(32) NULL AFTER deposit_manwon,
    ADD COLUMN monthly_rent_manwon INT NULL AFTER monthly_rent,
    ADD COLUMN contract_term VARCHAR(30) NULL AFTER land_leasehold_gbn,
    ADD COLUMN contract_type VARCHAR(30) NULL AFTER contract_term,
    ADD COLUMN use_rr_right VARCHAR(30) NULL AFTER contract_type,
    ADD COLUMN pre_deposit VARCHAR(32) NULL AFTER use_rr_right,
    ADD COLUMN pre_deposit_manwon INT NULL AFTER pre_deposit,
    ADD COLUMN pre_monthly_rent VARCHAR(32) NULL AFTER pre_deposit_manwon,
    ADD COLUMN pre_monthly_rent_manwon INT NULL AFTER pre_monthly_rent,
    ADD COLUMN roadnm VARCHAR(100) NULL AFTER pre_monthly_rent_manwon,
    ADD COLUMN apt_seq VARCHAR(30) NULL AFTER roadnm,
    ADD INDEX idx_house_deals_deal_mode (deal_type, lawd_cd, deal_ymd);
```

The `public_data_import_batches` table already stores the source API and deal type, so no schema change is needed there.
