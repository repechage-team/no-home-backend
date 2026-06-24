CREATE TABLE IF NOT EXISTS regions (
    region_id BIGINT NOT NULL AUTO_INCREMENT,
    lawd_cd VARCHAR(5) NOT NULL,
    legal_dong_code VARCHAR(10) NULL,
    sido VARCHAR(50) NOT NULL,
    sigungu VARCHAR(50) NOT NULL,
    umd_nm VARCHAR(50) NOT NULL,
    lat DECIMAL(10, 7) NULL,
    lng DECIMAL(10, 7) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (region_id),
    UNIQUE KEY uq_regions_lawd_umd (lawd_cd, umd_nm),
    KEY idx_regions_lawd_cd (lawd_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS houses (
    house_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    sgg_cd VARCHAR(5) NOT NULL,
    umd_nm VARCHAR(50) NOT NULL,
    jibun VARCHAR(30) NOT NULL,
    apt_nm VARCHAR(100) NOT NULL,
    build_year INT NULL,
    lat DECIMAL(10, 7) NULL,
    lng DECIMAL(10, 7) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (house_id),
    UNIQUE KEY uq_houses_source_identity (sgg_cd, umd_nm, jibun, apt_nm, build_year),
    KEY idx_houses_apt_nm (apt_nm),
    KEY idx_houses_region_id (region_id),
    CONSTRAINT fk_houses_region FOREIGN KEY (region_id) REFERENCES regions (region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS house_deals (
    deal_id BIGINT NOT NULL AUTO_INCREMENT,
    house_id BIGINT NOT NULL,
    source_api VARCHAR(64) NOT NULL,
    lawd_cd VARCHAR(5) NOT NULL,
    deal_ymd CHAR(6) NOT NULL,
    house_type VARCHAR(30) NOT NULL DEFAULT 'apartment',
    deal_type VARCHAR(30) NOT NULL DEFAULT 'sale',
    deal_year INT NOT NULL,
    deal_month INT NOT NULL,
    deal_day INT NOT NULL,
    deal_date DATE NOT NULL,
    deal_amount VARCHAR(32) NOT NULL,
    deal_amount_manwon INT NULL,
    rent_type VARCHAR(30) NULL,
    deposit VARCHAR(32) NULL,
    deposit_manwon INT NULL,
    monthly_rent VARCHAR(32) NULL,
    monthly_rent_manwon INT NULL,
    exclu_use_ar DECIMAL(10, 3) NULL,
    floor INT NULL,
    apt_dong VARCHAR(50) NULL,
    buyer_gbn VARCHAR(50) NULL,
    sler_gbn VARCHAR(50) NULL,
    dealing_gbn VARCHAR(50) NULL,
    estate_agent_sgg_nm VARCHAR(100) NULL,
    cdeal_type VARCHAR(30) NULL,
    cdeal_day VARCHAR(30) NULL,
    rgst_date VARCHAR(30) NULL,
    land_leasehold_gbn VARCHAR(30) NULL,
    contract_term VARCHAR(30) NULL,
    contract_type VARCHAR(30) NULL,
    use_rr_right VARCHAR(30) NULL,
    pre_deposit VARCHAR(32) NULL,
    pre_deposit_manwon INT NULL,
    pre_monthly_rent VARCHAR(32) NULL,
    pre_monthly_rent_manwon INT NULL,
    roadnm VARCHAR(100) NULL,
    apt_seq VARCHAR(30) NULL,
    api_row_hash CHAR(64) NOT NULL,
    raw_response JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (deal_id),
    UNIQUE KEY uq_house_deals_api_row_hash (api_row_hash),
    KEY idx_house_deals_lawd_ymd (lawd_cd, deal_ymd),
    KEY idx_house_deals_house_date (house_id, deal_date),
    KEY idx_house_deals_deal_mode (deal_type, lawd_cd, deal_ymd),
    CONSTRAINT fk_house_deals_house FOREIGN KEY (house_id) REFERENCES houses (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS public_data_import_batches (
    import_batch_id BIGINT NOT NULL AUTO_INCREMENT,
    source_api VARCHAR(64) NOT NULL,
    lawd_cd VARCHAR(5) NOT NULL,
    deal_ymd CHAR(6) NOT NULL,
    house_type VARCHAR(30) NOT NULL DEFAULT 'apartment',
    deal_type VARCHAR(30) NOT NULL DEFAULT 'sale',
    status VARCHAR(20) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    imported_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (import_batch_id),
    UNIQUE KEY uq_import_batches_request (source_api, lawd_cd, deal_ymd, house_type, deal_type),
    KEY idx_import_batches_lawd_ymd (lawd_cd, deal_ymd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS members (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uq_members_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_refresh_tokens (
    member_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uq_member_refresh_tokens_hash (token_hash),
    CONSTRAINT fk_member_refresh_tokens_member
        FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notices (
    notice_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id),
    KEY idx_notices_created_at (created_at),
    KEY idx_notices_member_id (member_id),
    CONSTRAINT fk_notices_member
        FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO members (email, password_hash, name, phone)
VALUES (
    'admin@nohome.local',
    '$2a$10$OBOz1AyK4cFPGtQ5q7tccOkpEt5WS.3JRzHZQ537mW4HNEvJZBUuG',
    'NoHome 관리자',
    '010-0000-0000'
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    phone = VALUES(phone),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO notices (member_id, title, content, created_at, updated_at)
SELECT member_id,
       'NoHome 서비스 이용 안내',
       'NoHome에서는 서울시 아파트 실거래가를 거래 유형, 지역, 거래월, 가격 조건으로 검색할 수 있습니다. 검색 결과는 목록과 지도 마커로 함께 제공됩니다.',
       CURRENT_TIMESTAMP - INTERVAL 2 DAY,
       CURRENT_TIMESTAMP - INTERVAL 2 DAY
FROM members
WHERE email = 'admin@nohome.local'
  AND NOT EXISTS (
      SELECT 1 FROM notices WHERE title = 'NoHome 서비스 이용 안내'
  );

INSERT INTO notices (member_id, title, content, created_at, updated_at)
SELECT member_id,
       '검색 결과 지도 표시 안내',
       '검색 결과 목록의 현재 페이지에 포함된 아파트는 지도 위에 마커로 표시됩니다. 페이지를 이동하거나 조건을 변경하면 지도 표시도 함께 갱신됩니다.',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY,
       CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM members
WHERE email = 'admin@nohome.local'
  AND NOT EXISTS (
      SELECT 1 FROM notices WHERE title = '검색 결과 지도 표시 안내'
  );

INSERT INTO notices (member_id, title, content)
SELECT member_id,
       'AI 도우미 사용 안내',
       '로그인 후 AI 도우미를 사용하면 자연어로 시세를 질문하거나 검색 조건 변경, 정렬, 페이지 이동을 요청할 수 있습니다.'
FROM members
WHERE email = 'admin@nohome.local'
  AND NOT EXISTS (
      SELECT 1 FROM notices WHERE title = 'AI 도우미 사용 안내'
  );

CREATE TABLE IF NOT EXISTS interest_regions (
    interest_region_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (interest_region_id),
    UNIQUE KEY uq_interest_regions_member_region (member_id, region_id),
    KEY idx_interest_regions_member_id (member_id),
    KEY idx_interest_regions_region_id (region_id),
    CONSTRAINT fk_interest_regions_member
        FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_interest_regions_region
        FOREIGN KEY (region_id) REFERENCES regions (region_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
