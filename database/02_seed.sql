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
