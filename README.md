# GoRide — Đặt xe & giao đồ ăn (Java Socket)

Đồ án môn Lập trình mạng: 1 server Java (TCP + UDP) và 1 ứng dụng client Swing tự đổi giao diện theo vai trò **Khách hàng / Tài xế / Admin**.

## Yêu cầu
- JDK 17+ và Maven 3.8+
- Docker Desktop (để chạy MySQL giống nhau trên mọi máy)
- (Khuyến nghị) Mapbox access token tại https://account.mapbox.com/access-tokens, chỉ máy chạy server cần

## Chạy lần đầu
```bash
git clone <repo> && cd goride
cp .env.example .env          # Windows: copy .env.example .env
# mở .env, điền MAPBOX_TOKEN (có thể để trống)
docker compose up -d          # MySQL 8 ở cổng 3307, tự tạo bảng + dữ liệu mẫu
mvn -q compile
mvn -Pserver exec:java        # terminal 1: server (TCP 5050, UDP 5051)
mvn exec:java                 # terminal 2, 3, ...: mỗi lệnh là một client
```

Reset dữ liệu về ban đầu: `docker compose down -v && docker compose up -d`.

Hai máy dùng chung một DB: cả hai clone cùng repo và chạy `docker compose up -d`. Schema và seed trong `database/` sẽ giống hệt nhau. Nếu muốn **cùng một** DB và server để demo, chỉ một máy chạy Docker và server. Máy còn lại chỉ chạy client, sửa `SERVER_HOST` trong `.env` thành IP LAN của máy server. Nhớ mở firewall cổng TCP 5050 và UDP 5051.

## Tài khoản demo
| Vai trò | SĐT | Mật khẩu |
|---|---|---|
| Admin | 0900000000 | admin123 |
| Khách hàng | 0911111111, 0911111112 | 123456 |
| Tài xế xe máy (đã duyệt) | 0922222221 | 123456 |
| Tài xế ô tô (đã duyệt) | 0922222222 | 123456 |
| Tài xế xe máy (chờ duyệt) | 0922222223 | 123456 |

## Kịch bản demo nhanh
1. Mở 3 client: Khách hàng, Tài xế xe máy, Admin.
2. Tài xế bật **Online** (vị trí xe có thể đặt bằng cách bấm lên bản đồ, gần khu vực khách).
3. Khách chọn điểm đón / điểm đến, xem giá rồi **Đặt xe (Xe máy)**. Tài xế nhận yêu cầu và bấm **Nhận việc**.
4. Tài xế tick **Mô phỏng xe chạy theo tuyến**. Khách thấy vị trí tài xế di chuyển realtime (GPS qua UDP).
5. Tài xế lần lượt bấm Đã tới điểm đón → Đã đón khách → Hoàn thành. Khách đánh giá, Admin xem doanh thu tăng.
6. Đồ ăn: khách chọn nhà hàng, thêm món vào giỏ rồi đặt. Nhà hàng giả lập tự xác nhận sau 3 giây, tài xế nhận đơn và cập nhật Tới nhà hàng → Lấy món → Đang giao → Đã giao.

## Kiến trúc
```
Client Swing ──TCP 5050 (JSON, request/response + push)──► Server ──JDBC──► MySQL
Tài xế      ──UDP 5051 (GPS;token;lat;lng;seq mỗi 2s)────►   │
                                                             └──HTTPS──► Mapbox API
```
- **TCP**: mỗi gói là `[4 byte độ dài][JSON UTF-8]`. Request `{type, rid, data}`, response `{type:"RESPONSE", rid, ok, message, data}`, server chủ động đẩy `{type:"PUSH", event, data}` (yêu cầu mới, trạng thái chuyến, vị trí tài xế...).
- **UDP** cho GPS: mất gói không sao vì gói sau ghi đè; gói đến trễ (seq nhỏ hơn) bị bỏ. Server lưu DB tối đa 15 giây/lần và chuyển vị trí cho khách qua TCP push.
- **Đa luồng**: mỗi client một thread (`ClientHandler`); trạng thái realtime nằm trong `ConcurrentHashMap` (`Sessions`).
- **Tranh chấp nhận chuyến**: `UPDATE ... WHERE status='SEARCHING' AND driver_id IS NULL`, nên chỉ 1 tài xế thắng. `putIfAbsent` đảm bảo 1 tài xế không nhận 2 việc.
- **Giá tính ở server** theo bảng `pricing` (Admin sửa được), làm tròn 1.000đ; tài xế nhận `DRIVER_SHARE` (80%).
- **Mapbox** (Geocoding v6, Directions v5) chỉ gọi từ server, token không lộ ra client. Mapbox không có chế độ xe máy nên Bike/Car dùng chung tuyến `driving`, chỉ khác giá. Không có token: quãng đường ước lượng = đường chim bay × 1.3, tốc độ 25 km/h. Bản đồ nền dùng tile OpenStreetMap.
- Máy tính không có GPS nên "vị trí hiện tại" lấy gần đúng theo IP, người dùng có thể bấm bản đồ để chỉnh.

## Cấu trúc
```
database/            01_schema.sql, 02_seed.sql (Docker tự chạy lần đầu)
src/main/java/com/goride/
  common/            Config (.env), Json, Geo, Protocol, Log
  server/            ServerMain, ClientHandler, Router, *Service, MapboxClient, UdpGpsServer, Db
  client/            ClientMain, Net (TCP), GpsSender (UDP), MyLocation
  client/ui/         LoginFrame, UserFrame, RidePanel, FoodPanel, DriverFrame, AdminFrame,
                     MapPanel, PlaceField, DataTable, ProfilePanel, BaseFrame, Theme, Ui
```

## Lỗi thường gặp
- **Không kết nối được server**: chạy server trước; kiểm tra `SERVER_HOST` / firewall.
- **Server báo lỗi DB**: `docker compose ps` xem MySQL đã chạy chưa; `DB_PORT` trong `.env` phải trùng cổng đã map (mặc định 3307).
- **Tìm địa chỉ báo chưa cấu hình Mapbox**: điền `MAPBOX_TOKEN` rồi khởi động lại server, hoặc bấm trực tiếp lên bản đồ để chọn điểm.
- **Tài xế không thấy yêu cầu**: phải được duyệt, đang Online, cùng loại xe, và cách điểm đón / nhà hàng ≤ `MATCH_RADIUS_KM`.
