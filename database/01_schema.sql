-- GoRide schema (MySQL 8, utf8mb4)
-- Chạy tay: mysql -u goride -p --default-character-set=utf8mb4 goride < database/01_schema.sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS reviews, payments, food_order_items, food_orders, menu_items,
                     restaurant_application_items, restaurant_applications,
                     restaurants, vouchers, rides, pricing, drivers, email_otps, images, users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  role          ENUM('USER','DRIVER','ADMIN') NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  phone         VARCHAR(20)  NOT NULL UNIQUE,
  email         VARCHAR(120) NULL,
  password_hash VARCHAR(255) NOT NULL,
  address       VARCHAR(255) NULL,
  status        ENUM('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ảnh (ảnh quán, ảnh món). Lưu thẳng trong DB cho gọn — client tải theo id qua lệnh IMAGE_GET.
CREATE TABLE images (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  mime       VARCHAR(40) NOT NULL DEFAULT 'image/jpeg',
  bytes      MEDIUMBLOB NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mã OTP gửi qua email khi đăng ký. payload = JSON hồ sơ đăng ký đang chờ xác thực
-- (mật khẩu đã băm sẵn). Tài khoản chỉ được tạo sau khi nhập đúng mã.
CREATE TABLE email_otps (
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

CREATE TABLE drivers (
  user_id        BIGINT PRIMARY KEY,
  vehicle_type   ENUM('BIKE','CAR') NOT NULL,
  plate_number   VARCHAR(20) NOT NULL UNIQUE,
  vehicle_brand  VARCHAR(60) NULL,
  vehicle_color  VARCHAR(30) NULL,
  license_number VARCHAR(30) NULL,
  approval       ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  is_online      TINYINT(1) NOT NULL DEFAULT 0,
  last_lat       DOUBLE NULL,
  last_lng       DOUBLE NULL,
  rating_avg     DECIMAL(3,2) NOT NULL DEFAULT 0,
  rating_count   INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_driver_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BIKE / CAR: giá chuyến xe. DELIVERY: phí giao đồ ăn
CREATE TABLE pricing (
  service    ENUM('BIKE','CAR','DELIVERY') PRIMARY KEY,
  base_fare  INT NOT NULL,
  per_km     INT NOT NULL,
  per_min    INT NOT NULL DEFAULT 0,
  min_fare   INT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Thẻ giảm giá của từng khách. Server tự phát thêm khi khách còn ít thẻ (VoucherService).
CREATE TABLE vouchers (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  code         VARCHAR(20) NOT NULL UNIQUE,
  title        VARCHAR(120) NOT NULL,
  percent      INT NOT NULL,
  max_discount INT NOT NULL,
  min_order    INT NOT NULL DEFAULT 0,
  scope        ENUM('ALL','RIDE','FOOD') NOT NULL DEFAULT 'ALL',
  expires_at   DATETIME NOT NULL,
  used_at      DATETIME NULL,
  used_ref     VARCHAR(20) NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_voucher_user (user_id, used_at),
  CONSTRAINT fk_voucher_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rides (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  driver_id       BIGINT NULL,
  vehicle_type    ENUM('BIKE','CAR') NOT NULL,
  pickup_address  VARCHAR(255) NOT NULL,
  pickup_lat      DOUBLE NOT NULL,
  pickup_lng      DOUBLE NOT NULL,
  drop_address    VARCHAR(255) NOT NULL,
  drop_lat        DOUBLE NOT NULL,
  drop_lng        DOUBLE NOT NULL,
  distance_m      INT NOT NULL,
  duration_s      INT NOT NULL,
  price           INT NOT NULL,
  discount        INT NOT NULL DEFAULT 0,
  voucher_code    VARCHAR(20) NULL,
  payment_method  ENUM('CASH','WALLET') NOT NULL DEFAULT 'CASH',
  status          ENUM('SEARCHING','ACCEPTED','ARRIVED','PICKED_UP','COMPLETED','CANCELLED') NOT NULL,
  cancel_reason   VARCHAR(255) NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_rides_status (status),
  CONSTRAINT fk_ride_user   FOREIGN KEY (user_id)   REFERENCES users(id),
  CONSTRAINT fk_ride_driver FOREIGN KEY (driver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurants (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id    BIGINT NULL,
  name        VARCHAR(120) NOT NULL,
  category    VARCHAR(60)  NULL,
  address     VARCHAR(255) NOT NULL,
  lat         DOUBLE NOT NULL,
  lng         DOUBLE NOT NULL,
  phone       VARCHAR(20) NULL,
  description VARCHAR(500) NULL,
  image_id    BIGINT NULL,
  is_open     TINYINT(1) NOT NULL DEFAULT 1,
  rating_avg  DECIMAL(3,2) NOT NULL DEFAULT 0,
  rating_count INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_restaurant_owner FOREIGN KEY (owner_id) REFERENCES users(id)  ON DELETE SET NULL,
  CONSTRAINT fk_restaurant_image FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_items (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT NOT NULL,
  name          VARCHAR(120) NOT NULL,
  description   VARCHAR(255) NULL,
  price         INT NOT NULL,
  image_id      BIGINT NULL,
  is_available  TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_menu_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
  CONSTRAINT fk_menu_image      FOREIGN KEY (image_id)      REFERENCES images(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Hồ sơ đăng ký mở quán do khách gửi lên, Admin duyệt thì mới sinh ra restaurants + menu_items
CREATE TABLE restaurant_applications (
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

CREATE TABLE restaurant_application_items (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  name           VARCHAR(120) NOT NULL,
  description    VARCHAR(255) NULL,
  price          INT NOT NULL,
  image_id       BIGINT NULL,
  CONSTRAINT fk_appitem_app   FOREIGN KEY (application_id) REFERENCES restaurant_applications(id) ON DELETE CASCADE,
  CONSTRAINT fk_appitem_image FOREIGN KEY (image_id)       REFERENCES images(id)                  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE food_orders (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  driver_id        BIGINT NULL,
  restaurant_id    BIGINT NOT NULL,
  delivery_address VARCHAR(255) NOT NULL,
  delivery_lat     DOUBLE NOT NULL,
  delivery_lng     DOUBLE NOT NULL,
  note             VARCHAR(255) NULL,
  items_total      INT NOT NULL,
  delivery_fee     INT NOT NULL,
  discount         INT NOT NULL DEFAULT 0,
  voucher_code     VARCHAR(20) NULL,
  total            INT NOT NULL,
  distance_m       INT NOT NULL,
  duration_s       INT NOT NULL,
  payment_method   ENUM('CASH','WALLET') NOT NULL DEFAULT 'CASH',
  status           ENUM('PLACED','CONFIRMED','ACCEPTED','AT_RESTAURANT','PICKED_UP','DELIVERING','DELIVERED','CANCELLED') NOT NULL,
  cancel_reason    VARCHAR(255) NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_food_status (status),
  CONSTRAINT fk_food_user       FOREIGN KEY (user_id)       REFERENCES users(id),
  CONSTRAINT fk_food_driver     FOREIGN KEY (driver_id)     REFERENCES users(id),
  CONSTRAINT fk_food_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE food_order_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id     BIGINT NOT NULL,
  menu_item_id BIGINT NULL,
  item_name    VARCHAR(120) NOT NULL,
  unit_price   INT NOT NULL,
  quantity     INT NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id)     REFERENCES food_orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_menu  FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  ref_type   ENUM('RIDE','FOOD') NOT NULL,
  ref_id     BIGINT NOT NULL,
  amount     INT NOT NULL,
  method     ENUM('CASH','WALLET') NOT NULL,
  status     ENUM('PENDING','PAID','REFUNDED') NOT NULL DEFAULT 'PAID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_payment_ref (ref_type, ref_id),
  CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Một đơn đồ ăn có thể có 2 đánh giá: cho tài xế (target=DRIVER) và cho quán (target=RESTAURANT)
CREATE TABLE reviews (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  target        ENUM('DRIVER','RESTAURANT') NOT NULL DEFAULT 'DRIVER',
  driver_id     BIGINT NULL,
  restaurant_id BIGINT NULL,
  ref_type      ENUM('RIDE','FOOD') NOT NULL,
  ref_id        BIGINT NOT NULL,
  stars         TINYINT NOT NULL,
  comment       VARCHAR(500) NULL,
  is_hidden     TINYINT(1) NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_review_ref (ref_type, ref_id, target),
  CONSTRAINT fk_review_user       FOREIGN KEY (user_id)       REFERENCES users(id),
  CONSTRAINT fk_review_driver     FOREIGN KEY (driver_id)     REFERENCES users(id),
  CONSTRAINT fk_review_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
