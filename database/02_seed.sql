-- Dữ liệu mẫu. Mật khẩu: admin = admin123, các tài khoản khác = 123456
SET NAMES utf8mb4;

INSERT INTO users (id, role, full_name, phone, email, password_hash, address) VALUES
 (1, 'ADMIN',  'Quản trị viên',   '0900000000', 'admin@goride.local', 'pbkdf2$65536$Z29yaWRlLWFkbWluLXNhbHQ=$lH86ie8oaZJWejV/ZjKW2kglEZ8JD7BSei/6IPzzArE=', NULL),
 (2, 'USER',   'Nguyễn Minh Anh', '0911111111', 'anh@example.com',    'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=', '227 Nguyễn Văn Cừ, Quận 5'),
 (3, 'USER',   'Hoàng Gia Bảo',   '0911111112', 'bao@example.com',    'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=', NULL),
 (4, 'DRIVER', 'Trần Văn Bình',   '0922222221', 'binh@example.com',   'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=', NULL),
 (5, 'DRIVER', 'Lê Thị Cúc',      '0922222222', 'cuc@example.com',    'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=', NULL),
 (6, 'DRIVER', 'Phạm Quốc Dũng',  '0922222223', 'dung@example.com',   'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=', NULL);

INSERT INTO drivers (user_id, vehicle_type, plate_number, vehicle_brand, vehicle_color, license_number, approval, last_lat, last_lng, rating_avg, rating_count) VALUES
 (4, 'BIKE', '59X1-123.45', 'Honda Vision',  'Trắng', 'A1-790011', 'APPROVED', 10.7760, 106.7000, 5.00, 1),
 (5, 'CAR',  '51H-678.90',  'Toyota Vios',   'Bạc',   'B2-452210', 'APPROVED', 10.7800, 106.6950, 4.00, 1),
 (6, 'BIKE', '59P2-456.78', 'Yamaha Sirius', 'Đen',   'A1-118822', 'PENDING',  NULL,    NULL,     0.00, 0);

INSERT INTO pricing (service, base_fare, per_km, per_min, min_fare) VALUES
 ('BIKE',     10000, 4300, 300, 12000),
 ('CAR',      20000, 9500, 500, 29000),
 ('DELIVERY', 12000, 5000,   0, 15000);

INSERT INTO restaurants (id, name, category, address, lat, lng, phone) VALUES
 (1, 'Cơm Tấm Ba Ghiền',  'Cơm',     '84 Đặng Văn Ngữ, Phú Nhuận, TP.HCM', 10.7966, 106.6727, '02838461073'),
 (2, 'Phở Hòa Pasteur',   'Phở',     '260C Pasteur, Quận 3, TP.HCM',       10.7890, 106.6893, '02838297943'),
 (3, 'Bánh Mì Huỳnh Hoa', 'Bánh mì', '26 Lê Thị Riêng, Quận 1, TP.HCM',    10.7713, 106.6924, '02839250885');

INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 (1, 'Cơm sườn bì chả',  'Sườn nướng, bì, chả trứng',   75000),
 (1, 'Cơm sườn trứng',   'Sườn nướng, trứng ốp la',     65000),
 (1, 'Canh khổ qua',     'Khổ qua nhồi thịt',           25000),
 (1, 'Trà đá',           NULL,                           5000),
 (2, 'Phở tái nạm',      'Tô lớn',                      85000),
 (2, 'Phở đặc biệt',     'Tái, nạm, gầu, gân, bò viên', 105000),
 (2, 'Quẩy',             '2 cái',                       10000),
 (3, 'Bánh mì thập cẩm', 'Pate, chả lụa, thịt nguội',   68000),
 (3, 'Bánh mì chả lụa',  NULL,                          45000),
 (3, 'Sữa đậu nành',     NULL,                          15000);

-- ----- Thêm quán & món cho phong phú -----
INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Bún Bò Huế Đông Ba', 'Bún', '110A Nguyễn Du, Quận 1, TP.HCM', 10.7745, 106.6955, '02838273456', 'Bún bò Huế chuẩn vị cố đô, nước dùng ninh xương 8 tiếng.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Bún bò đặc biệt', 'Giò heo, chả cua, bò tái, huyết', 75000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Bún bò giò heo', 'Giò heo khoanh to', 65000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Bún bò tái nạm', 'Bò tái, nạm bò', 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Bún bò chay', 'Nấm, đậu hũ, chả chay', 50000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Chả cua thêm', '1 viên chả cua', 15000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Giò heo thêm', '1 khoanh', 25000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bún Bò Huế Đông Ba' LIMIT 1) t), 'Trà đá', 'Ly lớn', 5000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Hủ Tiếu Nam Vang Nhân Quán', 'Mì / Hủ tiếu', '72 Nguyễn Trãi, Quận 5, TP.HCM', 10.7545, 106.664, '02838551234', 'Hủ tiếu Nam Vang gia truyền, topping đầy ắp.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Hủ tiếu Nam Vang khô', 'Tôm, gan, trứng cút, thịt bằm', 65000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Hủ tiếu Nam Vang nước', 'Nước lèo ngọt thanh', 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Mì hoành thánh', 'Hoành thánh tôm thịt', 55000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Hủ tiếu mực', 'Mực tươi cắt khoanh', 70000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Sườn non thêm', '2 miếng', 30000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Trứng cút thêm', '5 quả', 10000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Hủ Tiếu Nam Vang Nhân Quán' LIMIT 1) t), 'Nước sâm lạnh', NULL, 12000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Bánh Xèo 46A Đinh Công Tráng', 'Ăn vặt', '46A Đinh Công Tráng, Quận 1, TP.HCM', 10.7893, 106.695, '02838241110', 'Bánh xèo giòn rụm, rau sống đầy rổ.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Bánh xèo tôm thịt', 'Bánh lớn, tôm sú và thịt ba chỉ', 95000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Bánh xèo hải sản', 'Tôm, mực, nghêu', 120000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Bánh xèo chay', 'Nấm, giá, đậu xanh', 75000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Bánh khọt tôm', '10 cái', 90000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Gỏi cuốn tôm thịt', '2 cuốn', 35000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Xèo 46A Đinh Công Tráng' LIMIT 1) t), 'Nước mía', NULL, 20000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Lẩu Dê Sáu Nhuận', 'Lẩu - Nướng', '233 Nguyễn Trãi, Quận 1, TP.HCM', 10.766, 106.683, '02839207788', 'Lẩu dê thuốc bắc, dê nướng ngũ vị.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Lẩu dê thuốc bắc (2-3 người)', 'Kèm mì, rau, tàu hũ ky', 320000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Dê nướng ngũ vị', 'Đĩa lớn', 185000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Dê xào lăn', 'Đậm đà, ăn với bánh mì', 165000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Ngọc dương hầm thuốc bắc', NULL, 220000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Rau nhúng lẩu thêm', 'Tần ô, cải xanh', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Mì tươi thêm', '2 vắt', 20000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Lẩu Dê Sáu Nhuận' LIMIT 1) t), 'Bia lon', NULL, 25000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Gà Nướng Ò Ó O', 'Lẩu - Nướng', '24 Hồ Xuân Hương, Quận 3, TP.HCM', 10.7795, 106.689, '02839303030', 'Gà thả vườn nướng than hoa, cơm lam.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Gà nướng nguyên con', 'Gà thả vườn 1.2kg, nướng than', 380000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Nửa con gà nướng', NULL, 195000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Cơm lam', '2 ống', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Gỏi gà bắp cải', 'Đĩa lớn', 95000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Cháo gà', 'Tô lớn nấu từ nước luộc', 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Lòng gà xào mướp', NULL, 85000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Gà Nướng Ò Ó O' LIMIT 1) t), 'Rượu cần', 'Ché nhỏ', 120000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Cơm Chay Hoan Hỷ', 'Đồ chay', '128 Đặng Văn Ngữ, Phú Nhuận, TP.HCM', 10.7955, 106.672, '02838443322', 'Cơm chay thanh đạm, đổi món mỗi ngày.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Cơm phần chay đặc biệt', 'Cơm, 3 món mặn chay, canh', 65000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Cơm tấm chay', 'Sườn chay nướng, bì chay', 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Bún riêu chay', 'Riêu đậu, cà chua', 55000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Mì xào chay giòn', NULL, 58000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Đậu hũ kho tiêu', NULL, 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Canh rong biển', NULL, 30000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cơm Chay Hoan Hỷ' LIMIT 1) t), 'Sữa hạt sen', NULL, 25000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Ốc Đào Nguyễn Trãi', 'Ăn vặt', '132 Nguyễn Trãi, Quận 1, TP.HCM', 10.768, 106.688, '02839256677', 'Quán ốc đông khách, sốt me và rang muối trứ danh.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Ốc hương rang muối ớt', 'Đĩa 300g', 145000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Sò huyết xào me', 'Đĩa 300g', 125000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Nghêu hấp sả', NULL, 85000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Ốc móng tay xào bơ tỏi', NULL, 135000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Càng ghẹ rang muối', '4 càng', 165000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Cút lộn xào me', '4 trứng', 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Ốc Đào Nguyễn Trãi' LIMIT 1) t), 'Bánh mì ăn kèm', '2 ổ', 15000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Trà Sữa Nhà Làm Cô Hai', 'Trà sữa', '88 Sư Vạn Hạnh, Quận 10, TP.HCM', 10.771, 106.669, '02838668899', 'Trà sữa nấu mỗi ngày, trân châu tự luộc.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Trà sữa trân châu đường đen', 'Size L', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Trà sữa khoai môn', 'Size L, khoai môn dầm', 48000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Hồng trà sữa', 'Size L', 40000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Trà đào cam sả', 'Size L, đào miếng', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Trà vải', 'Size L', 42000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Sữa tươi trân châu đường đen', NULL, 42000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Trà Sữa Nhà Làm Cô Hai' LIMIT 1) t), 'Thêm topping trân châu', NULL, 8000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Cà Phê Sữa Đá Sài Gòn', 'Cà phê', '26 Lý Tự Trọng, Quận 1, TP.HCM', 10.779, 106.7, '02838221100', 'Cà phê phin pha máy, rang mộc.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Cà phê sữa đá', 'Ly lớn, phin truyền thống', 35000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Cà phê đen đá', NULL, 30000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Bạc xỉu', 'Nhiều sữa, ít cà phê', 38000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Cà phê muối', 'Kem muối béo', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Cà phê cốt dừa', NULL, 48000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Bánh mì ốp la', 'Trứng, pate, xúc xích', 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Cà Phê Sữa Đá Sài Gòn' LIMIT 1) t), 'Bánh flan', '1 hũ', 20000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Chè Khúc Bạch Thanh', 'Tráng miệng', '68 Trần Hưng Đạo, Quận 5, TP.HCM', 10.7555, 106.679, '02839234455', 'Chè khúc bạch, chè Thái, tàu hũ nóng.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Chè khúc bạch', 'Hạnh nhân, vải, phô mai', 40000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Chè Thái sầu riêng', NULL, 45000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Tàu hũ nước đường gừng', NULL, 25000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Chè bưởi', 'Cùi bưởi giòn', 35000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Sương sáo hạt lựu', NULL, 30000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Chè Khúc Bạch Thanh' LIMIT 1) t), 'Chè đậu xanh nước cốt dừa', NULL, 28000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Mì Quảng Bà Mua', 'Mì / Hủ tiếu', '19 Nguyễn Thị Minh Khai, Quận 1, TP.HCM', 10.786, 106.701, '02838282828', 'Mì Quảng Đà Nẵng, bánh tráng mè nướng.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Mì Quảng gà ta', 'Gà ta, trứng cút, đậu phộng', 65000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Mì Quảng tôm thịt', NULL, 68000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Mì Quảng sườn non', NULL, 72000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Mì Quảng thập cẩm', 'Đủ loại topping', 85000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Bánh tráng mè nướng', '2 cái', 10000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Mì Quảng Bà Mua' LIMIT 1) t), 'Chả bò Đà Nẵng', 'Đĩa nhỏ', 45000);

INSERT INTO restaurants (name, category, address, lat, lng, phone, description) VALUES
 ('Bánh Canh Cua 87', 'Bún', '87 Trần Khắc Chân, Quận 1, TP.HCM', 10.792, 106.693, '02838489999', 'Bánh canh cua đặc sệt, thịt cua tươi.');
INSERT INTO menu_items (restaurant_id, name, description, price) VALUES
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Bánh canh cua đặc biệt', 'Thịt cua, chả cua, tôm, giò heo', 85000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Bánh canh cua thường', NULL, 65000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Bánh canh giò heo', NULL, 60000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Chả cua thêm', '2 viên', 25000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Thịt cua thêm', '1 phần', 40000),
 ((SELECT id FROM (SELECT id FROM restaurants WHERE name='Bánh Canh Cua 87' LIMIT 1) t), 'Nước sâm bí đao', NULL, 15000);

INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Cơm sườn cây', 'Sườn cốt lết nguyên cây', 95000 FROM restaurants WHERE name='Cơm Tấm Ba Ghiền';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Cơm gà nướng mật ong', NULL, 70000 FROM restaurants WHERE name='Cơm Tấm Ba Ghiền';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Sườn nướng thêm', '1 miếng', 45000 FROM restaurants WHERE name='Cơm Tấm Ba Ghiền';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Phở gà xé', NULL, 75000 FROM restaurants WHERE name='Phở Hòa Pasteur';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Phở bò viên', NULL, 75000 FROM restaurants WHERE name='Phở Hòa Pasteur';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Trứng chần', '2 quả', 15000 FROM restaurants WHERE name='Phở Hòa Pasteur';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Bánh mì xíu mại', NULL, 50000 FROM restaurants WHERE name='Bánh Mì Huỳnh Hoa';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Bánh mì heo quay', NULL, 60000 FROM restaurants WHERE name='Bánh Mì Huỳnh Hoa';
INSERT INTO menu_items (restaurant_id, name, description, price)
 SELECT id, 'Cà phê sữa đá', NULL, 25000 FROM restaurants WHERE name='Bánh Mì Huỳnh Hoa';

-- ----- Tài khoản chủ quán cho các quán có sẵn (mật khẩu 123456) -----
INSERT INTO users (role, full_name, phone, email, password_hash) VALUES
 ('USER', 'Chủ quán Cơm Tấm Ba Ghiền', '0901000001', 'quan01@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Phở Hòa Pasteur', '0901000002', 'quan02@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Bánh Mì Huỳnh Hoa', '0901000003', 'quan03@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Bún Bò Huế Đông Ba', '0901000004', 'quan04@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Hủ Tiếu Nam Vang Nhân Quán', '0901000005', 'quan05@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Bánh Xèo 46A Đinh Công Tráng', '0901000006', 'quan06@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Lẩu Dê Sáu Nhuận', '0901000007', 'quan07@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Gà Nướng Ò Ó O', '0901000008', 'quan08@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Cơm Chay Hoan Hỷ', '0901000009', 'quan09@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Ốc Đào Nguyễn Trãi', '0901000010', 'quan10@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Trà Sữa Nhà Làm Cô Hai', '0901000011', 'quan11@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Cà Phê Sữa Đá Sài Gòn', '0901000012', 'quan12@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Chè Khúc Bạch Thanh', '0901000013', 'quan13@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Mì Quảng Bà Mua', '0901000014', 'quan14@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM='),
 ('USER', 'Chủ quán Bánh Canh Cua 87', '0901000015', 'quan15@goride.local', 'pbkdf2$65536$Z29yaWRlLWRlbW8tc2FsdA==$3S+Iidw3ri07aj7I1Fk3bqh8uywdoq0uEn17T33AAMM=');
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000001') WHERE name='Cơm Tấm Ba Ghiền';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000002') WHERE name='Phở Hòa Pasteur';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000003') WHERE name='Bánh Mì Huỳnh Hoa';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000004') WHERE name='Bún Bò Huế Đông Ba';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000005') WHERE name='Hủ Tiếu Nam Vang Nhân Quán';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000006') WHERE name='Bánh Xèo 46A Đinh Công Tráng';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000007') WHERE name='Lẩu Dê Sáu Nhuận';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000008') WHERE name='Gà Nướng Ò Ó O';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000009') WHERE name='Cơm Chay Hoan Hỷ';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000010') WHERE name='Ốc Đào Nguyễn Trãi';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000011') WHERE name='Trà Sữa Nhà Làm Cô Hai';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000012') WHERE name='Cà Phê Sữa Đá Sài Gòn';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000013') WHERE name='Chè Khúc Bạch Thanh';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000014') WHERE name='Mì Quảng Bà Mua';
UPDATE restaurants SET owner_id = (SELECT id FROM users WHERE phone='0901000015') WHERE name='Bánh Canh Cua 87';

-- Lịch sử mẫu để dashboard có số liệu
INSERT INTO rides (id, user_id, driver_id, vehicle_type, pickup_address, pickup_lat, pickup_lng, drop_address, drop_lat, drop_lng, distance_m, duration_s, price, payment_method, status, created_at, updated_at) VALUES
 (1, 2, 4, 'BIKE', 'Chợ Bến Thành, Quận 1',  10.7725, 106.6980, 'ĐH Khoa học Tự nhiên, Quận 5', 10.7627, 106.6822, 2600,  540,  24000, 'CASH',   'COMPLETED', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
 (2, 3, 5, 'CAR',  'Nhà thờ Đức Bà, Quận 1', 10.7798, 106.6990, 'Sân bay Tân Sơn Nhất',         10.8185, 106.6588, 7800, 1500, 107000, 'WALLET', 'COMPLETED', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

INSERT INTO food_orders (id, user_id, driver_id, restaurant_id, delivery_address, delivery_lat, delivery_lng, items_total, delivery_fee, total, distance_m, duration_s, payment_method, status, created_at, updated_at) VALUES
 (1, 2, 4, 2, '227 Nguyễn Văn Cừ, Quận 5', 10.7627, 106.6822, 95000, 29000, 124000, 3400, 720, 'CASH', 'DELIVERED', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY);

INSERT INTO food_order_items (order_id, menu_item_id, item_name, unit_price, quantity) VALUES
 (1, 5, 'Phở tái nạm', 85000, 1),
 (1, 7, 'Quẩy',        10000, 1);

INSERT INTO payments (user_id, ref_type, ref_id, amount, method, status, created_at) VALUES
 (2, 'RIDE', 1,  24000, 'CASH',   'PAID', NOW() - INTERVAL 2 DAY),
 (3, 'RIDE', 2, 107000, 'WALLET', 'PAID', NOW() - INTERVAL 1 DAY),
 (2, 'FOOD', 1, 124000, 'CASH',   'PAID', NOW() - INTERVAL 3 DAY);

INSERT INTO reviews (user_id, driver_id, ref_type, ref_id, stars, comment) VALUES
 (2, 4, 'RIDE', 1, 5, 'Tài xế thân thiện, chạy cẩn thận'),
 (3, 5, 'RIDE', 2, 4, 'Xe sạch, đến hơi trễ');
