-- docker-entrypoint가 init 스크립트를 적재할 때 클라이언트 charset이 utf8mb4가 아니면
-- 한글이 더블 인코딩되어 저장된다. 세션 charset을 명시해 방지한다.
SET NAMES utf8mb4;

INSERT INTO regions (lawd_cd, legal_dong_code, sido, sigungu, umd_nm)
VALUES
    ('11590', '1159010500', '서울특별시', '동작구', '흑석동'),
    ('11590', '1159010700', '서울특별시', '동작구', '상도동')
ON DUPLICATE KEY UPDATE
    legal_dong_code = VALUES(legal_dong_code),
    sido = VALUES(sido),
    sigungu = VALUES(sigungu),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO houses (region_id, sgg_cd, umd_nm, jibun, apt_nm, build_year)
SELECT region_id, '11590', '흑석동', '10', '흑석한강센트레빌', 2011
FROM regions
WHERE lawd_cd = '11590' AND umd_nm = '흑석동'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO houses (region_id, sgg_cd, umd_nm, jibun, apt_nm, build_year)
SELECT region_id, '11590', '흑석동', '335', '아크로리버하임', 2018
FROM regions
WHERE lawd_cd = '11590' AND umd_nm = '흑석동'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO houses (region_id, sgg_cd, umd_nm, jibun, apt_nm, build_year)
SELECT region_id, '11590', '상도동', '431', '상도래미안', 2004
FROM regions
WHERE lawd_cd = '11590' AND umd_nm = '상도동'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO house_deals (
    house_id, source_api, lawd_cd, deal_ymd, house_type, deal_type,
    deal_year, deal_month, deal_day, deal_date, deal_amount, deal_amount_manwon,
    exclu_use_ar, floor, api_row_hash
)
SELECT house_id, 'RTMSDataSvcAptTrade', '11590', '202405', 'apartment', 'sale',
       2024, 5, 12, '2024-05-12', '150,000', 150000, 84.970, 12,
       '7c55c6660859c6907fe3abdd88442ac6e7cd9884ec8c6af5e7f9319ed7816c33'
FROM houses
WHERE sgg_cd = '11590' AND umd_nm = '흑석동' AND jibun = '10' AND apt_nm = '흑석한강센트레빌'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO house_deals (
    house_id, source_api, lawd_cd, deal_ymd, house_type, deal_type,
    deal_year, deal_month, deal_day, deal_date, deal_amount, deal_amount_manwon,
    exclu_use_ar, floor, api_row_hash
)
SELECT house_id, 'RTMSDataSvcAptTrade', '11590', '202405', 'apartment', 'sale',
       2024, 5, 21, '2024-05-21', '205,000', 205000, 84.920, 18,
       '2388c7a729aa411e814d5c44bcafab7a18e7fe463e2d52d07ab4670889155410'
FROM houses
WHERE sgg_cd = '11590' AND umd_nm = '흑석동' AND jibun = '335' AND apt_nm = '아크로리버하임'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO house_deals (
    house_id, source_api, lawd_cd, deal_ymd, house_type, deal_type,
    deal_year, deal_month, deal_day, deal_date, deal_amount, deal_amount_manwon,
    exclu_use_ar, floor, api_row_hash
)
SELECT house_id, 'RTMSDataSvcAptTrade', '11590', '202405', 'apartment', 'sale',
       2024, 5, 4, '2024-05-04', '118,500', 118500, 59.880, 7,
       'dc38813e3b6d52e20921110aabb5f5289a3abea0b09f4a601d5e12d51621e41b'
FROM houses
WHERE sgg_cd = '11590' AND umd_nm = '상도동' AND jibun = '431' AND apt_nm = '상도래미안'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO public_data_import_batches (
    source_api, lawd_cd, deal_ymd, house_type, deal_type, status,
    total_count, imported_count, skipped_count, completed_at
)
VALUES (
    'RTMSDataSvcAptTrade', '11590', '202405', 'apartment', 'sale', 'success',
    3, 3, 0, CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    total_count = VALUES(total_count),
    imported_count = VALUES(imported_count),
    skipped_count = VALUES(skipped_count),
    completed_at = VALUES(completed_at);
