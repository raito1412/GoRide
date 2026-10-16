# GoRide — Đặt xe & giao đồ ăn (Java Socket)

Đồ án Lập trình mạng: server Java (TCP + UDP) + client Swing tự đổi giao diện theo vai trò **Khách hàng / Chủ quán / Tài xế / Admin**, dữ liệu MySQL 8.

## Cần cài
- JDK 17+, Maven 3.8+
- Docker Desktop (chạy MySQL)

## Chạy lần đầu
```bash
git clone https://github.com/raito1412/GoRide.git && cd GoRide
cp .env.example .env          # Windows: copy .env.example .env
# mở .env: điền MAPBOX_TOKEN (bản đồ/chỉ đường thật) và GMAIL_USER + GMAIL_APP_PASSWORD (gửi OTP) — để trống vẫn chạy được
docker compose up -d          # MySQL cổng 3309, tự tạo bảng + dữ liệu mẫu + ảnh quán/món
./run.sh server 4             # bật server + mở 4 cửa sổ client (Windows: run.bat server 4)
```
Lần đầu cần có mạng để Maven tải thư viện.

## Những lần sau
```bash
docker compose up -d          # nếu Docker vừa khởi động lại
./run.sh server 4             # server + 4 client, Ctrl+C tắt hết
```
Chạy riêng: `mvn -Pserver exec:java` (server) và `./run.sh 4` (chỉ mở client). Log server ở `target/server.log`.

Hai máy dùng chung một server: máy client sửa `SERVER_HOST` trong `.env` thành IP LAN của máy server, mở firewall TCP 6060 / UDP 6061.

## Cập nhật DB đã có sẵn
Docker chỉ tự chạy file trong `database/` ở lần tạo DB đầu tiên. DB cũ thì chạy lần lượt (chạy lại nhiều lần không sao):
```bash
for f in database/upgrades/03_upgrade_merchant_otp.sql database/upgrades/04_vouchers_and_food.sql \
         database/upgrades/05_merchant_accounts_reviews.sql database/03_images.sql; do
  docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < "$f"
done
```
Làm lại DB từ đầu: `docker compose down -v && docker compose up -d`.

## Tài khoản demo
| Vai trò | SĐT | Mật khẩu |
|---|---|---|
| Admin | 0900000000 | admin123 |
| Khách hàng | 0911111111, 0911111112 | 123456 |
| Tài xế xe máy | 0922222221 | 123456 |
| Tài xế ô tô | 0922222222 | 123456 |
| Tài xế chờ duyệt | 0922222223 | 123456 |
| Chủ quán (15 quán) | 0901000001 … 0901000015 | 123456 |

## Chức năng chính
- **Khách**: đăng ký có OTP Gmail (5 phút) · đặt xe máy/ô tô · đặt đồ ăn 4 bước · thẻ giảm giá 10–15% (ngày đôi 20%) · theo dõi tài xế realtime · đánh giá tài xế và quán · mở quán (Admin duyệt).
- **Chủ quán**: nhận/từ chối đơn · đánh giá quán · đối soát tháng, xuất hoá đơn.
- **Tài xế**: tự Online + GPS · nhận chuyến hoặc **giao gộp tối đa 3 đơn** với lộ trình ngắn nhất (Pickup & Delivery TSP, duyệt nhánh-cận) · chỉ bấm "Đã tới / Hoàn thành / Đã giao" khi xe đã tới nơi · thu nhập, đối soát tháng.
- **Admin**: dashboard, duyệt tài xế/quán, quản lý khách, quán, thực đơn, bảng giá, thanh toán, đánh giá.

Tiền: tài xế nhận 80% giá chuyến/phí giao, quán nhận 75% tiền món, thuế khoán GTGT 3% + TNCN 1,5% khi thu nhập năm > 200 triệu, chi trả ngày 10 tháng sau — chỉnh trong `.env`.

## Kiến trúc
```
Client Swing ──TCP 6060 (JSON: request/response + push)──► Server ──JDBC──► MySQL 3309
Tài xế       ──UDP 6061 (GPS mỗi 2 giây)─────────────────►   │
                                                              ├─► Mapbox (bản đồ, chỉ đường, ma trận khoảng cách)
                                                              └─► Gmail SMTP (OTP)
```
Gói tin TCP: `[4 byte độ dài][JSON UTF-8]`. Mã nguồn: `src/main/java/com/goride/{server,client,common}`.

## Lỗi thường gặp
- **Server báo lỗi DB**: Docker Desktop chưa bật hoặc MySQL chưa lên — `docker compose up -d`, kiểm tra `DB_PORT` trong `.env`.
- **Client báo mất kết nối**: server chưa chạy hoặc sai `SERVER_HOST` / `TCP_PORT`.
- **Không có bản đồ thật / OTP không về mail**: chưa điền `MAPBOX_TOKEN` / `GMAIL_*` (mã OTP khi đó in ra log server).
- **Tài xế không thấy yêu cầu**: chỉ hiện chuyến đúng loại xe trong bán kính `MATCH_RADIUS_KM` (5 km) quanh vị trí tài xế.
