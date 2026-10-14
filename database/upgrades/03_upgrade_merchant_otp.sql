-- Nâng cấp DB đang chạy lên bản có: ảnh, OTP email khi đăng ký, đăng ký mở quán chờ duyệt.
-- Nếu bạn tạo DB mới bằng 01_schema.sql thì KHÔNG cần chạy file này.
-- Chạy: mysql -u goride -p --default-character-set=utf8mb4 goride < database/03_upgrade_merchant_otp.sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS images (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  mime       VARCHAR(40) NOT NULL DEFAULT 'image/jpeg',
  bytes      MEDIUMBLOB NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_otps (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  email        VARCHAR(120) NOT NULL,
  purpose      VARCHAR(20)  NOT NULL DEFAULT 'REGISTER',
  code_hash    VARCHAR(255) NOT NULL,
  payload      TEXT NOT NULL,
  attempts     INT NOT NULL DEFAULT 0,
  expires_at   DATETIME NOT NULL,
  last_sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_otp_email_purpose (email, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE restaurants
  ADD COLUMN owner_id    BIGINT NULL AFTER id,
  ADD COLUMN description VARCHAR(500) NULL AFTER phone,
  ADD COLUMN image_id    BIGINT NULL AFTER description,
  ADD CONSTRAINT fk_restaurant_owner FOREIGN KEY (owner_id) REFERENCES users(id)  ON DELETE SET NULL,
  ADD CONSTRAINT fk_restaurant_image FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL;

ALTER TABLE menu_items
  ADD COLUMN image_id BIGINT NULL AFTER price,
  ADD CONSTRAINT fk_menu_image FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS restaurant_applications (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  name          VARCHAR(120) NOT NULL,
  category      VARCHAR(60)  NULL,
  address       VARCHAR(255) NOT NULL,
  lat           DOUBLE NOT NULL,
  lng           DOUBLE NOT NULL,
  phone         VARCHAR(20)  NULL,
  description   VARCHAR(500) NULL,
  image_id      BIGINT NULL,
  owner_name    VARCHAR(100) NOT NULL,
  owner_phone   VARCHAR(20)  NOT NULL,
  owner_email   VARCHAR(120) NULL,
  status        ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  admin_note    VARCHAR(255) NULL,
  restaurant_id BIGINT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at   DATETIME NULL,
  INDEX idx_app_status (status),
  CONSTRAINT fk_app_user       FOREIGN KEY (user_id)       REFERENCES users(id)       ON DELETE CASCADE,
  CONSTRAINT fk_app_image      FOREIGN KEY (image_id)      REFERENCES images(id)      ON DELETE SET NULL,
  CONSTRAINT fk_app_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS restaurant_application_items (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  name           VARCHAR(120) NOT NULL,
  description    VARCHAR(255) NULL,
  price          INT NOT NULL,
  image_id       BIGINT NULL,
  CONSTRAINT fk_appitem_app   FOREIGN KEY (application_id) REFERENCES restaurant_applications(id) ON DELETE CASCADE,
  CONSTRAINT fk_appitem_image FOREIGN KEY (image_id)       REFERENCES images(id)                  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
