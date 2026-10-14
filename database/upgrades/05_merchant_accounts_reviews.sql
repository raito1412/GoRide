-- Nâng cấp DB đang chạy: tài khoản chủ quán cho các quán có sẵn + đánh giá quán ăn.
-- Chạy lại nhiều lần không sao. DB tạo mới từ 01_schema.sql + 02_seed.sql thì KHÔNG cần chạy.
-- Docker compose: docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < database/upgrades/05_merchant_accounts_reviews.sql
-- Cụm k8s:        mysql -h127.0.0.1 -P13309 -ugoride -p --default-character-set=utf8mb4 goride < database/upgrades/05_merchant_accounts_reviews.sql
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS goride_add_column;
DELIMITER $$
CREATE PROCEDURE goride_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

-- ----- điểm đánh giá của quán -----
CALL goride_add_column('restaurants', 'rating_avg',   '`rating_avg` DECIMAL(3,2) NOT NULL DEFAULT 0 AFTER `is_open`');
CALL goride_add_column('restaurants', 'rating_count', '`rating_count` INT NOT NULL DEFAULT 0 AFTER `rating_avg`');

-- ----- reviews: mỗi đơn ăn cho phép 1 đánh giá tài xế + 1 đánh giá quán -----
CALL goride_add_column('reviews', 'target',        "`target` ENUM('DRIVER','RESTAURANT') NOT NULL DEFAULT 'DRIVER' AFTER `user_id`");
CALL goride_add_column('reviews', 'restaurant_id', '`restaurant_id` BIGINT NULL AFTER `driver_id`');
ALTER TABLE reviews MODIFY driver_id BIGINT NULL;

DROP PROCEDURE IF EXISTS goride_fix_review_keys;
DELIMITER $$
CREATE PROCEDURE goride_fix_review_keys()
BEGIN
  -- khoá cũ (ref_type, ref_id) chỉ cho 1 đánh giá/đơn -> đổi sang (ref_type, ref_id, target)
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'uq_review_ref' AND COLUMN_NAME = 'target') = 0 THEN
    ALTER TABLE reviews DROP INDEX uq_review_ref, ADD UNIQUE KEY uq_review_ref (ref_type, ref_id, target);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'reviews' AND CONSTRAINT_NAME = 'fk_review_restaurant') THEN
    ALTER TABLE reviews ADD CONSTRAINT fk_review_restaurant FOREIGN KEY (restaurant_id)
      REFERENCES restaurants(id) ON DELETE CASCADE;
  END IF;
END$$
DELIMITER ;
CALL goride_fix_review_keys();
DROP PROCEDURE goride_fix_review_keys;
DROP PROCEDURE goride_add_column;

-- ----- tài khoản chủ quán (mật khẩu 123456), gắn theo TÊN quán vì id mỗi DB có thể khác -----
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Cơm Tấm Ba Ghiền', '0901000001', 'quan01@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000001');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000001')
 WHERE name='Cơm Tấm Ba Ghiền' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Phở Hòa Pasteur', '0901000002', 'quan02@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000002');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000002')
 WHERE name='Phở Hòa Pasteur' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Bánh Mì Huỳnh Hoa', '0901000003', 'quan03@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000003');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000003')
 WHERE name='Bánh Mì Huỳnh Hoa' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Bún Bò Huế Đông Ba', '0901000004', 'quan04@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000004');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000004')
 WHERE name='Bún Bò Huế Đông Ba' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Hủ Tiếu Nam Vang Nhân Quán', '0901000005', 'quan05@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000005');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000005')
 WHERE name='Hủ Tiếu Nam Vang Nhân Quán' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Bánh Xèo 46A Đinh Công Tráng', '0901000006', 'quan06@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000006');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000006')
 WHERE name='Bánh Xèo 46A Đinh Công Tráng' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Lẩu Dê Sáu Nhuận', '0901000007', 'quan07@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000007');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000007')
 WHERE name='Lẩu Dê Sáu Nhuận' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Gà Nướng Ò Ó O', '0901000008', 'quan08@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000008');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000008')
 WHERE name='Gà Nướng Ò Ó O' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Cơm Chay Hoan Hỷ', '0901000009', 'quan09@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000009');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000009')
 WHERE name='Cơm Chay Hoan Hỷ' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Ốc Đào Nguyễn Trãi', '0901000010', 'quan10@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000010');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000010')
 WHERE name='Ốc Đào Nguyễn Trãi' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Trà Sữa Nhà Làm Cô Hai', '0901000011', 'quan11@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000011');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000011')
 WHERE name='Trà Sữa Nhà Làm Cô Hai' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Cà Phê Sữa Đá Sài Gòn', '0901000012', 'quan12@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000012');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000012')
 WHERE name='Cà Phê Sữa Đá Sài Gòn' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Chè Khúc Bạch Thanh', '0901000013', 'quan13@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000013');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000013')
 WHERE name='Chè Khúc Bạch Thanh' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Mì Quảng Bà Mua', '0901000014', 'quan14@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000014');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000014')
 WHERE name='Mì Quảng Bà Mua' AND owner_id IS NULL;
INSERT INTO users (role, full_name, phone, email, password_hash)
 SELECT 'USER', 'Chủ quán Bánh Canh Cua 87', '0901000015', 'quan15@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone='0901000015');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000015')
 WHERE name='Bánh Canh Cua 87' AND owner_id IS NULL;
