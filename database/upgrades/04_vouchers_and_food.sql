-- Nâng cấp DB đang chạy: thẻ giảm giá + thêm quán/món. Chạy được nhiều lần, không tạo dữ liệu trùng.
-- DB tạo mới bằng 01_schema.sql + 02_seed.sql thì KHÔNG cần chạy file này.
-- Chạy: docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < database/upgrades/04_vouchers_and_food.sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS vouchers (
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

-- MySQL không có "ADD COLUMN IF NOT EXISTS" nên mượn thủ tục nhỏ để chạy lại được nhiều lần.
DROP PROCEDURE IF EXISTS goride_add_column;
DELIMITER $$
CREATE PROCEDURE goride_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE st FROM @sql;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

CALL goride_add_column('rides',       'discount',     '`discount` INT NOT NULL DEFAULT 0 AFTER `price`');
CALL goride_add_column('rides',       'voucher_code', '`voucher_code` VARCHAR(20) NULL AFTER `discount`');
CALL goride_add_column('food_orders', 'discount',     '`discount` INT NOT NULL DEFAULT 0 AFTER `delivery_fee`');
CALL goride_add_column('food_orders', 'voucher_code', '`voucher_code` VARCHAR(20) NULL AFTER `discount`');
DROP PROCEDURE goride_add_column;

-- ----- Thêm quán & món -----
INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Bún Bò Huế Đông Ba', 'Bún', '110A Nguyễn Du, Quận 1, TP.HCM', 10.7745, 106.6955, '02838273456', 'Bún bò Huế chuẩn vị cố đô, nước dùng ninh xương 8 tiếng.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Bún Bò Huế Đông Ba');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bún bò đặc biệt', 'Giò heo, chả cua, bò tái, huyết', 75000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bún bò đặc biệt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bún bò giò heo', 'Giò heo khoanh to', 65000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bún bò giò heo');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bún bò tái nạm', 'Bò tái, nạm bò', 60000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bún bò tái nạm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bún bò chay', 'Nấm, đậu hũ, chả chay', 50000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bún bò chay');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chả cua thêm', '1 viên chả cua', 15000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chả cua thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Giò heo thêm', '1 khoanh', 25000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Giò heo thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trà đá', 'Ly lớn', 5000 FROM restaurants r WHERE r.name='Bún Bò Huế Đông Ba'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trà đá');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Hủ Tiếu Nam Vang Nhân Quán', 'Mì / Hủ tiếu', '72 Nguyễn Trãi, Quận 5, TP.HCM', 10.7545, 106.664, '02838551234', 'Hủ tiếu Nam Vang gia truyền, topping đầy ắp.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Hủ Tiếu Nam Vang Nhân Quán');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Hủ tiếu Nam Vang khô', 'Tôm, gan, trứng cút, thịt bằm', 65000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Hủ tiếu Nam Vang khô');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Hủ tiếu Nam Vang nước', 'Nước lèo ngọt thanh', 60000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Hủ tiếu Nam Vang nước');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì hoành thánh', 'Hoành thánh tôm thịt', 55000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì hoành thánh');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Hủ tiếu mực', 'Mực tươi cắt khoanh', 70000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Hủ tiếu mực');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sườn non thêm', '2 miếng', 30000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sườn non thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trứng cút thêm', '5 quả', 10000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trứng cút thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Nước sâm lạnh', NULL, 12000 FROM restaurants r WHERE r.name='Hủ Tiếu Nam Vang Nhân Quán'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Nước sâm lạnh');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Bánh Xèo 46A Đinh Công Tráng', 'Ăn vặt', '46A Đinh Công Tráng, Quận 1, TP.HCM', 10.7893, 106.695, '02838241110', 'Bánh xèo giòn rụm, rau sống đầy rổ.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Bánh Xèo 46A Đinh Công Tráng');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh xèo tôm thịt', 'Bánh lớn, tôm sú và thịt ba chỉ', 95000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh xèo tôm thịt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh xèo hải sản', 'Tôm, mực, nghêu', 120000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh xèo hải sản');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh xèo chay', 'Nấm, giá, đậu xanh', 75000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh xèo chay');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh khọt tôm', '10 cái', 90000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh khọt tôm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Gỏi cuốn tôm thịt', '2 cuốn', 35000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Gỏi cuốn tôm thịt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Nước mía', NULL, 20000 FROM restaurants r WHERE r.name='Bánh Xèo 46A Đinh Công Tráng'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Nước mía');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Lẩu Dê Sáu Nhuận', 'Lẩu - Nướng', '233 Nguyễn Trãi, Quận 1, TP.HCM', 10.766, 106.683, '02839207788', 'Lẩu dê thuốc bắc, dê nướng ngũ vị.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Lẩu Dê Sáu Nhuận');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Lẩu dê thuốc bắc (2-3 người)', 'Kèm mì, rau, tàu hũ ky', 320000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Lẩu dê thuốc bắc (2-3 người)');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Dê nướng ngũ vị', 'Đĩa lớn', 185000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Dê nướng ngũ vị');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Dê xào lăn', 'Đậm đà, ăn với bánh mì', 165000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Dê xào lăn');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Ngọc dương hầm thuốc bắc', NULL, 220000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Ngọc dương hầm thuốc bắc');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Rau nhúng lẩu thêm', 'Tần ô, cải xanh', 45000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Rau nhúng lẩu thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì tươi thêm', '2 vắt', 20000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì tươi thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bia lon', NULL, 25000 FROM restaurants r WHERE r.name='Lẩu Dê Sáu Nhuận'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bia lon');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Gà Nướng Ò Ó O', 'Lẩu - Nướng', '24 Hồ Xuân Hương, Quận 3, TP.HCM', 10.7795, 106.689, '02839303030', 'Gà thả vườn nướng than hoa, cơm lam.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Gà Nướng Ò Ó O');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Gà nướng nguyên con', 'Gà thả vườn 1.2kg, nướng than', 380000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Gà nướng nguyên con');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Nửa con gà nướng', NULL, 195000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Nửa con gà nướng');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cơm lam', '2 ống', 45000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cơm lam');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Gỏi gà bắp cải', 'Đĩa lớn', 95000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Gỏi gà bắp cải');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cháo gà', 'Tô lớn nấu từ nước luộc', 60000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cháo gà');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Lòng gà xào mướp', NULL, 85000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Lòng gà xào mướp');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Rượu cần', 'Ché nhỏ', 120000 FROM restaurants r WHERE r.name='Gà Nướng Ò Ó O'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Rượu cần');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Cơm Chay Hoan Hỷ', 'Đồ chay', '128 Đặng Văn Ngữ, Phú Nhuận, TP.HCM', 10.7955, 106.672, '02838443322', 'Cơm chay thanh đạm, đổi món mỗi ngày.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Cơm Chay Hoan Hỷ');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cơm phần chay đặc biệt', 'Cơm, 3 món mặn chay, canh', 65000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cơm phần chay đặc biệt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cơm tấm chay', 'Sườn chay nướng, bì chay', 60000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cơm tấm chay');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bún riêu chay', 'Riêu đậu, cà chua', 55000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bún riêu chay');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì xào chay giòn', NULL, 58000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì xào chay giòn');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Đậu hũ kho tiêu', NULL, 45000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Đậu hũ kho tiêu');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Canh rong biển', NULL, 30000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Canh rong biển');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sữa hạt sen', NULL, 25000 FROM restaurants r WHERE r.name='Cơm Chay Hoan Hỷ'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sữa hạt sen');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Ốc Đào Nguyễn Trãi', 'Ăn vặt', '132 Nguyễn Trãi, Quận 1, TP.HCM', 10.768, 106.688, '02839256677', 'Quán ốc đông khách, sốt me và rang muối trứ danh.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Ốc Đào Nguyễn Trãi');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Ốc hương rang muối ớt', 'Đĩa 300g', 145000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Ốc hương rang muối ớt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sò huyết xào me', 'Đĩa 300g', 125000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sò huyết xào me');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Nghêu hấp sả', NULL, 85000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Nghêu hấp sả');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Ốc móng tay xào bơ tỏi', NULL, 135000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Ốc móng tay xào bơ tỏi');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Càng ghẹ rang muối', '4 càng', 165000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Càng ghẹ rang muối');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cút lộn xào me', '4 trứng', 60000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cút lộn xào me');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh mì ăn kèm', '2 ổ', 15000 FROM restaurants r WHERE r.name='Ốc Đào Nguyễn Trãi'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh mì ăn kèm');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Trà Sữa Nhà Làm Cô Hai', 'Trà sữa', '88 Sư Vạn Hạnh, Quận 10, TP.HCM', 10.771, 106.669, '02838668899', 'Trà sữa nấu mỗi ngày, trân châu tự luộc.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Trà Sữa Nhà Làm Cô Hai');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trà sữa trân châu đường đen', 'Size L', 45000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trà sữa trân châu đường đen');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trà sữa khoai môn', 'Size L, khoai môn dầm', 48000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trà sữa khoai môn');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Hồng trà sữa', 'Size L', 40000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Hồng trà sữa');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trà đào cam sả', 'Size L, đào miếng', 45000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trà đào cam sả');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trà vải', 'Size L', 42000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trà vải');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sữa tươi trân châu đường đen', NULL, 42000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sữa tươi trân châu đường đen');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Thêm topping trân châu', NULL, 8000 FROM restaurants r WHERE r.name='Trà Sữa Nhà Làm Cô Hai'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Thêm topping trân châu');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Cà Phê Sữa Đá Sài Gòn', 'Cà phê', '26 Lý Tự Trọng, Quận 1, TP.HCM', 10.779, 106.7, '02838221100', 'Cà phê phin pha máy, rang mộc.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Cà Phê Sữa Đá Sài Gòn');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cà phê sữa đá', 'Ly lớn, phin truyền thống', 35000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cà phê sữa đá');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cà phê đen đá', NULL, 30000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cà phê đen đá');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bạc xỉu', 'Nhiều sữa, ít cà phê', 38000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bạc xỉu');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cà phê muối', 'Kem muối béo', 45000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cà phê muối');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cà phê cốt dừa', NULL, 48000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cà phê cốt dừa');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh mì ốp la', 'Trứng, pate, xúc xích', 45000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh mì ốp la');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh flan', '1 hũ', 20000 FROM restaurants r WHERE r.name='Cà Phê Sữa Đá Sài Gòn'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh flan');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Chè Khúc Bạch Thanh', 'Tráng miệng', '68 Trần Hưng Đạo, Quận 5, TP.HCM', 10.7555, 106.679, '02839234455', 'Chè khúc bạch, chè Thái, tàu hũ nóng.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Chè Khúc Bạch Thanh');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chè khúc bạch', 'Hạnh nhân, vải, phô mai', 40000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chè khúc bạch');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chè Thái sầu riêng', NULL, 45000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chè Thái sầu riêng');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Tàu hũ nước đường gừng', NULL, 25000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Tàu hũ nước đường gừng');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chè bưởi', 'Cùi bưởi giòn', 35000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chè bưởi');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sương sáo hạt lựu', NULL, 30000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sương sáo hạt lựu');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chè đậu xanh nước cốt dừa', NULL, 28000 FROM restaurants r WHERE r.name='Chè Khúc Bạch Thanh'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chè đậu xanh nước cốt dừa');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Mì Quảng Bà Mua', 'Mì / Hủ tiếu', '19 Nguyễn Thị Minh Khai, Quận 1, TP.HCM', 10.786, 106.701, '02838282828', 'Mì Quảng Đà Nẵng, bánh tráng mè nướng.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Mì Quảng Bà Mua');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì Quảng gà ta', 'Gà ta, trứng cút, đậu phộng', 65000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì Quảng gà ta');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì Quảng tôm thịt', NULL, 68000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì Quảng tôm thịt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì Quảng sườn non', NULL, 72000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì Quảng sườn non');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Mì Quảng thập cẩm', 'Đủ loại topping', 85000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Mì Quảng thập cẩm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh tráng mè nướng', '2 cái', 10000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh tráng mè nướng');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chả bò Đà Nẵng', 'Đĩa nhỏ', 45000 FROM restaurants r WHERE r.name='Mì Quảng Bà Mua'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chả bò Đà Nẵng');

INSERT INTO restaurants (name, category, address, lat, lng, phone, description)
 SELECT 'Bánh Canh Cua 87', 'Bún', '87 Trần Khắc Chân, Quận 1, TP.HCM', 10.792, 106.693, '02838489999', 'Bánh canh cua đặc sệt, thịt cua tươi.'
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'Bánh Canh Cua 87');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh canh cua đặc biệt', 'Thịt cua, chả cua, tôm, giò heo', 85000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh canh cua đặc biệt');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh canh cua thường', NULL, 65000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh canh cua thường');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh canh giò heo', NULL, 60000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh canh giò heo');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Chả cua thêm', '2 viên', 25000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Chả cua thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Thịt cua thêm', '1 phần', 40000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Thịt cua thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Nước sâm bí đao', NULL, 15000 FROM restaurants r WHERE r.name='Bánh Canh Cua 87'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Nước sâm bí đao');

INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cơm sườn cây', 'Sườn cốt lết nguyên cây', 95000 FROM restaurants r WHERE r.name='Cơm Tấm Ba Ghiền'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cơm sườn cây');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cơm gà nướng mật ong', NULL, 70000 FROM restaurants r WHERE r.name='Cơm Tấm Ba Ghiền'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cơm gà nướng mật ong');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Sườn nướng thêm', '1 miếng', 45000 FROM restaurants r WHERE r.name='Cơm Tấm Ba Ghiền'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Sườn nướng thêm');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Phở gà xé', NULL, 75000 FROM restaurants r WHERE r.name='Phở Hòa Pasteur'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Phở gà xé');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Phở bò viên', NULL, 75000 FROM restaurants r WHERE r.name='Phở Hòa Pasteur'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Phở bò viên');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Trứng chần', '2 quả', 15000 FROM restaurants r WHERE r.name='Phở Hòa Pasteur'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Trứng chần');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh mì xíu mại', NULL, 50000 FROM restaurants r WHERE r.name='Bánh Mì Huỳnh Hoa'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh mì xíu mại');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Bánh mì heo quay', NULL, 60000 FROM restaurants r WHERE r.name='Bánh Mì Huỳnh Hoa'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Bánh mì heo quay');
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT r.id, 'Cà phê sữa đá', NULL, 25000 FROM restaurants r WHERE r.name='Bánh Mì Huỳnh Hoa'
   AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.restaurant_id=r.id AND m.name='Cà phê sữa đá');