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

## Runtime Configuration

Apartment trade and apartment rent use separate public-data service keys.

```properties
PUBLIC_DATA_SERVICE_KEY=
PUBLIC_DATA_APT_RENT_SERVICE_KEY=
```

- `PUBLIC_DATA_SERVICE_KEY`: `RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade`
- `PUBLIC_DATA_APT_RENT_SERVICE_KEY`: `RTMSDataSvcAptRent/getRTMSDataSvcAptRent`

## Search Contract

`GET /api/houses/search` supports `dealMode=sale|jeonse|monthly|rent|all`.

| Mode | Imported source | Enabled price filters | Enabled price sorts |
| --- | --- | --- | --- |
| `sale` | apartment trade API | `minPrice`, `maxPrice` | price ascending/descending |
| `jeonse` | apartment rent API | `minDeposit`, `maxDeposit` | deposit ascending/descending |
| `monthly` | apartment rent API | `minDeposit`, `maxDeposit`, `minMonthlyRent`, `maxMonthlyRent` | deposit and monthly-rent ascending/descending |
| `rent` | apartment rent API | none | none |
| `all` | trade API + rent API | none | none |

The frontend disables unsupported filters and sort options by mode. `rent` and `all` keep only date and exclusive-area sorting.

## Import Behavior

- Single-month search imports the requested `dealYmd`.
- Month-range search imports every month from `startDealYmd` through `endDealYmd`, inclusive.
- Rent rows are stored once using the rent API batch key (`source_api=RTMSDataSvcAptRent`, `deal_type=rent`).
- The saved row response exposes `dealType=jeonse` when `monthlyRent == 0`, and `dealType=monthly` when `monthlyRent > 0`.
- Public-data response code `03` is treated as a successful empty response, not an auto-import failure.

## Conflict Resolution Note

While applying this feature on top of the latest `master`, `HouseService` and `AptTradeApiResponse` conflicted with the existing auto-import failure logging changes.

- `HouseService` keeps the new rent/all/month-range import flow and also preserves safe failure diagnostics from `PublicDataApiException` (`resultCode`, `resultMsg`).
- `AptTradeApiResponse` keeps the existing `NORMAL_RESULT_CODES` set and adds `03` for the public-data "no data" response.

## Verification

- `.\mvnw.cmd -q test`
- Direct API check:
  - `dealMode=rent`, `lawdCd=11590`, `202601~202606` returned results.
  - `dealMode=all`, `lawdCd=11590`, `202601~202606` returned results.
