# GoRide — Đặt xe & giao đồ ăn (Java Socket)

Đồ án môn Lập trình mạng: 1 server Java (TCP + UDP) và 1 ứng dụng client Swing tự đổi giao diện theo vai trò **Khách hàng / Tài xế / Admin**.

## Yêu cầu
- JDK 17+ và Maven 3.8+
- Docker Desktop (để chạy MySQL giống nhau trên mọi máy)
- (Khuyến nghị) Mapbox access token tại https://account.mapbox.com/access-tokens, chỉ máy chạy server cần
- (Khuyến nghị) Một tài khoản Gmail + **mật khẩu ứng dụng** để gửi mã OTP xác thực đăng ký, chỉ máy chạy server cần

## Chạy lần đầu
```bash
git clone <repo> && cd goride
cp .env.example .env          # Windows: copy .env.example .env
# mở .env, điền MAPBOX_TOKEN (có thể để trống)
docker compose up -d          # MySQL 8 ở cổng 3309, tự tạo bảng + dữ liệu mẫu
mvn -q compile
mvn -Pserver exec:java        # terminal 1: server (TCP 6060, UDP 6061)
mvn exec:java                 # terminal 2, 3, ...: mỗi lệnh là một client
```

Mở **nhiều client một lúc** bằng một lệnh (mỗi cửa sổ là một tiến trình riêng, xếp lệch nhau, tiêu đề có số `[#1]`, `[#2]`...):
```bash
./run.sh 4            # mở 4 client (server đang chạy sẵn)
./run.sh server 4     # bật server (log ở target/server.log) rồi mở 4 client; Ctrl+C tắt hết
run.bat 4             # Windows; run.bat server 4 để mở kèm server
```

Reset dữ liệu về ban đầu: `docker compose down -v && docker compose up -d`.

Nếu DB **đã tồn tại từ trước** (Docker chỉ chạy các file `.sql` ngay trong `database/` ở lần khởi tạo đầu tiên), chạy lần lượt các file nâng cấp — chạy lại nhiều lần cũng không sao:
```bash
docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < database/upgrades/03_upgrade_merchant_otp.sql
docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < database/upgrades/04_vouchers_and_food.sql
docker compose exec -T mysql mysql -ugoride -pgoride123 --default-character-set=utf8mb4 goride < database/upgrades/05_merchant_accounts_reviews.sql
```

Hai máy dùng chung một DB: cả hai clone cùng repo và chạy `docker compose up -d`. Schema và seed trong `database/` sẽ giống hệt nhau. Nếu muốn **cùng một** DB và server để demo, chỉ một máy chạy Docker và server. Máy còn lại chỉ chạy client, sửa `SERVER_HOST` trong `.env` thành IP LAN của máy server. Nhớ mở firewall cổng TCP 6060 và UDP 6061.

## Tài khoản demo
| Vai trò | SĐT | Mật khẩu |
|---|---|---|
| Admin | 0900000000 | admin123 |
| Khách hàng | 0911111111, 0911111112 | 123456 |
| Tài xế xe máy (đã duyệt) | 0922222221 | 123456 |
| Tài xế ô tô (đã duyệt) | 0922222222 | 123456 |
| Tài xế xe máy (chờ duyệt) | 0922222223 | 123456 |

## Giao diện
Khách hàng mở lên là **trang chủ chỉ có 3 việc chính**: Đặt xe · Đặt đồ ăn · Mở quán ăn. Các thứ tra cứu (**Lịch sử & thanh toán**, **Tài khoản**) nằm ở góc phải thanh tiêu đề; **thẻ giảm giá** không có mục riêng mà chọn ngay tại chỗ dùng (bước Giỏ hàng khi đặt đồ ăn, ô chọn xe khi đặt xe). Thanh trên cùng luôn có nút **← Quay lại**.

Bản đồ không nằm sẵn một góc mà chỉ bật lên ở những chỗ thật sự cần:

| Màn hình | Có bản đồ? |
|---|---|
| Trang chủ, Giỏ hàng, Lịch sử & thanh toán, Tài khoản | không |
| Đặt xe, Mở quán (chọn địa chỉ) | có |
| Đồ ăn — bước 1 Chọn quán, bước 2 Chọn món, bước 3 Giỏ hàng | không |
| Chủ quán — màn theo dõi đơn của quán | không |
| Đồ ăn — bước 4 Giao tới, theo dõi đơn | có |
| Tài xế — khi chưa có việc | ẩn sẵn, bấm **🗺 Xem trên bản đồ** để xem chỗ mình đang đứng hoặc xem trước một yêu cầu |
| Tài xế — sau khi bấm **Nhận việc** | có |
| Mở quán — màn điền hồ sơ (chọn vị trí quán) | có |

**Thông báo nổi**: tài xế và chủ quán không phải ngồi canh bảng. Có việc mới là một thẻ thông báo trượt vào góc phải trên, kèm nút hành động ngay (*Nhận việc* / *Xem đơn*), tự tắt sau vài giây và không chặn thao tác như hộp thoại.

**Tài xế**: đăng nhập xong tài khoản đã duyệt **tự vào Online**, GPS chạy luôn — không phải bấm hay tick gì. Trạng thái Online là một **công tắc gạt** (bấm hoặc kéo núm qua lại), thông tin xe xem ở tab *Tài khoản* cho màn làm việc đỡ rối. Thẻ *Trạng thái* hiện **địa chỉ tài xế đang đứng** (server reverse-geocode toạ độ GPS) kèm toạ độ bên dưới, và nút **🗺 Xem trên bản đồ** mở bản đồ bất cứ lúc nào để nhìn vị trí mình hoặc bấm chọn lại chỗ đứng. Khi nhận việc, xe tự di chuyển theo tuyến để demo; nút **⏸ Tạm dừng di chuyển** dùng khi muốn tự đặt vị trí.

**Chủ quán**: quán được duyệt rồi thì mục *Mở quán ăn* đổi thành bảng theo dõi **Quán của bạn** — danh sách quán đang bán và bảng đơn tự cập nhật mỗi khi khách đặt hoặc tài xế đổi trạng thái. Nút *+ Đăng ký thêm quán* quay lại biểu mẫu hồ sơ.

Đặt đồ ăn đi theo 4 bước tách riêng: **Chọn quán** (lưới thẻ có ảnh + ô tìm kiếm) → **Chọn món** (lưới thẻ món, nút *+ Thêm*, thanh giỏ hàng dưới cùng) → **Giỏ hàng** (tăng/giảm số lượng, chọn thẻ giảm giá) → **Giao tới** (địa chỉ, ghi chú, thanh toán, tổng tiền). Món/quán chưa có ảnh thì hiện emoji theo loại món.

## Kịch bản demo nhanh
1. Mở 3 client: Khách hàng, Tài xế xe máy, Admin.
2. Tài xế bật **Online** (vị trí xe có thể đặt bằng cách bấm lên bản đồ, gần khu vực khách).
3. Khách chọn điểm đón / điểm đến, xem giá rồi **Đặt xe (Xe máy)**. Tài xế nhận yêu cầu và bấm **Nhận việc**.
4. Nhận việc xong xe tự chạy theo tuyến (nút **⏸ Tạm dừng di chuyển** để dừng). Khách thấy vị trí tài xế di chuyển realtime (GPS qua UDP).
5. Tài xế lần lượt bấm Đã tới điểm đón → Đã đón khách → Hoàn thành. Khách đánh giá, Admin xem doanh thu tăng.
6. Đồ ăn: khách chọn nhà hàng, thêm món vào giỏ rồi đặt. Chủ quán đang mở app thì bấm *Nhận đơn* (quán tắt app thì hệ thống xác nhận hộ sau 3 giây), tài xế nhận đơn và cập nhật Tới nhà hàng → Lấy món → Đang giao → Đã giao. Muốn thử giao gộp: đặt 3 đơn từ 3 tài khoản khách khác nhau rồi cho cùng một tài xế nhận.
7. Thông báo tức thời: mở song song client Khách hàng và client Tài xế; khách vừa đặt là thông báo nhảy lên ở màn tài xế, bấm *Nhận việc* ngay trên thông báo. Chủ quán mở mục **Mở quán ăn** cũng thấy thông báo đơn mới y như vậy.
8. Đăng ký tài khoản mới: điền form → server gửi mã 6 số tới email → nhập mã trong 5 phút → tài khoản mới được tạo.
9. Thẻ giảm giá: ở bước **Giỏ hàng** mở ô *Thẻ giảm giá* (luôn có sẵn 3 mã) — chọn xong tổng tiền giảm ngay.
10. Mở quán: khách vào **🏪 Mở quán** ở trang chủ, điền tên quán / địa chỉ / ảnh quán, thêm từng món kèm ảnh rồi gửi hồ sơ. Admin vào tab **🏪 Duyệt quán**, bấm *Xem chi tiết* để xem ảnh và thực đơn, rồi **Duyệt** (hệ thống tạo quán + menu thật) hoặc **Từ chối** kèm lý do. Duyệt xong quán xuất hiện ngay trong tab Đồ ăn của mọi khách.

## Chủ quán, đối soát & đánh giá quán
- 15 quán có sẵn đều có tài khoản chủ quán: SĐT `0901000001` … `0901000015`, mật khẩu `123456` (thứ tự theo `02_seed.sql`). Đăng nhập bằng tài khoản có quán là vào thẳng màn **Quán của bạn** gồm 4 tab: Đơn hàng · Quán của tôi · Đánh giá · Đối soát tháng.
- **Quán tự nhận đơn**: khách đặt → chủ quán nhận thông báo nổi có nút *Nhận đơn ngay* → nhận xong hệ thống mới tìm tài xế. Quán có thể từ chối kèm lý do (khách đọc được). Không nhận trong 3 phút thì đơn tự hủy. Quán **đang tắt app** thì hệ thống xác nhận hộ sau 3 giây để khách không phải chờ.
- **Đối soát / hoá đơn tháng** cho cả tài xế (tab *Đối soát tháng*) và chủ quán: doanh thu → trừ chiết khấu nền tảng (tài xế 20%, quán 25% tiền món) → trừ thuế khoán GTGT 3% + TNCN 1,5% nếu doanh thu được hưởng cả năm vượt 200 triệu → tài xế trừ thêm tiền mặt đã thu của khách → số được chi trả (âm nghĩa là phải nộp lại). Chi trả ngày 10 tháng sau. Nút *Xuất hoá đơn tháng* lưu file HTML, mở bằng trình duyệt để in / lưu PDF. Các tỷ lệ đều chỉnh được trong `.env`.
- **Giao gộp tối đa 3 đơn**: tài xế đang giao đồ ăn vẫn thấy và nhận thêm đơn (tối đa 3); đủ 3 đơn thì ngừng nhận. Không trộn chở khách với giao đồ ăn. Màn tài xế hiện **Lộ trình tối ưu**: thứ tự đi đánh số trên bản đồ (1 · Lấy #55, 2 · Giao #55…), giờ tới dự kiến từng điểm và số km/phút tiết kiệm so với giao lần lượt từng đơn. Xong mỗi điểm thì lộ trình tự tính lại từ chỗ đang đứng.
- **Đánh giá quán**: đơn đã giao có thể chấm riêng tài xế và quán. Điểm quán hiện trên thẻ quán; chủ quán thấy và nhận thông báo khi có đánh giá mới; Admin ẩn/hiện được.

## Kiến trúc
```
Client Swing ──TCP 6060 (JSON, request/response + push)──► Server ──JDBC──► MySQL
Tài xế      ──UDP 6061 (GPS;token;lat;lng;seq mỗi 2s)────►   │
                                                             └──HTTPS──► Mapbox API
```
- **TCP**: mỗi gói là `[4 byte độ dài][JSON UTF-8]`. Request `{type, rid, data}`, response `{type:"RESPONSE", rid, ok, message, data}`, server chủ động đẩy `{type:"PUSH", event, data}` (yêu cầu mới, trạng thái chuyến, vị trí tài xế...).
- **UDP** cho GPS: mất gói không sao vì gói sau ghi đè; gói đến trễ (seq nhỏ hơn) bị bỏ. Server lưu DB tối đa 15 giây/lần và chuyển vị trí cho khách qua TCP push. Riêng lúc tài xế **tự đặt lại chỗ đứng** (bấm bản đồ / lấy theo IP) thì client gửi thêm `DRIVER_SET_LOCATION` qua **TCP**: vị trí này quyết định việc lọc yêu cầu theo bán kính nên không thể phụ thuộc vào gói UDP có tới được hay không.
- **Đa luồng**: mỗi client một thread (`ClientHandler`); trạng thái realtime nằm trong `ConcurrentHashMap` (`Sessions`).
- **Tranh chấp nhận chuyến**: `UPDATE ... WHERE status='SEARCHING' AND driver_id IS NULL`, nên chỉ 1 tài xế thắng. Danh sách việc của tài xế giữ ở `Sessions.ACTIVE_JOBS` và được giành chỗ bằng `compute()` nguyên tử, nên bấm nhận 4 đơn cùng lúc cũng chỉ lọt đúng 3.
- **Thuật toán lộ trình giao gộp** (`RoutePlanner`): bài toán *Pickup and Delivery* (TSP có ràng buộc "lấy món ở quán trước rồi mới giao cho khách"). Chi phí là thời gian chạy xe theo đường thật lấy từ **Mapbox Matrix API** (không có token thì ước lượng đường chim bay × 1,3 ở 25 km/h). Tối đa 3 đơn = 6 điểm dừng, chỉ có 6!/2³ = 90 thứ tự hợp lệ nên giải **chính xác** bằng duyệt nhánh-cận (branch and bound) — cắt nhánh khi thời gian đã vượt nghiệm tốt nhất; bằng thời gian thì chọn đường ngắn hơn. Lệnh `DRIVER_ROUTE_PLAN`.
- **Hoàn thẻ giảm giá**: chuyến/đơn bị huỷ (khách huỷ, quán từ chối, không có tài xế, Admin huỷ, server khởi động lại) thì thẻ được trả lại cho khách.
- **Giá tính ở server** theo bảng `pricing` (Admin sửa được), làm tròn 1.000đ; tài xế nhận `DRIVER_SHARE` (80%).
- **Xác thực email khi đăng ký**: `REGISTER` không tạo tài khoản ngay mà sinh mã OTP 6 số, băm bằng PBKDF2 rồi lưu cùng hồ sơ đăng ký (mật khẩu đã băm) trong bảng `email_otps`. Mã sống 5 phút, sai tối đa 5 lần, gửi lại cách nhau 60 giây. `REGISTER_VERIFY` đúng mã mới thật sự `INSERT INTO users`, nên không có tài khoản "treo" chưa xác thực. Mail gửi bằng SMTP thuần JDK (`Mailer`), không thêm thư viện; chưa cấu hình Gmail thì server in mã ra console để dev chạy thử.
- **Đăng ký mở quán**: khách gửi hồ sơ (`restaurant_applications` + `restaurant_application_items`) ở trạng thái `PENDING`. Admin duyệt thì server tạo `restaurants` + `menu_items` trong một transaction và push `MERCHANT_UPDATE` về cho chủ quán; từ chối thì lưu lý do để chủ quán sửa và gửi lại.
- **Thẻ giảm giá** (`vouchers`): server tự phát, luôn giữ cho mỗi khách 3 mã giảm **10-15%** (trần 30.000đ, hạn 7 ngày); vào các **ngày đôi** (1/1, 2/2, ... 12/12) phát thêm một mã **20%** (trần 60.000đ, hết hạn cuối ngày). Tiền giảm tính trên *tiền món + phí giao* (hoặc giá chuyến xe) và **do server chốt** — client chỉ hiện tạm tính. Mã được đánh dấu đã dùng ngay trong transaction đặt đơn nên không thể xài hai lần.
- **Thông báo đẩy cho chủ quán**: khi đơn của quán đổi trạng thái, server đẩy `MERCHANT_ORDER` tới `restaurants.owner_id` (`kind=NEW` cho đơn vừa đặt để client bật thông báo nổi, `UPDATE` cho các lần sau chỉ làm mới bảng).
- **Ảnh quán / ảnh món** lưu trong bảng `images` (MEDIUMBLOB). Client thu nhỏ còn tối đa 1000px + nén JPEG rồi `IMAGE_UPLOAD`, các danh sách chỉ mang `image_id` cho nhẹ, ảnh tải lười bằng `IMAGE_GET` và được nhớ lại ở client.
- **Mapbox** (Geocoding v6, Directions v5) chỉ gọi từ server, token không lộ ra client. Mapbox không có chế độ xe máy nên Bike/Car dùng chung tuyến `driving`, chỉ khác giá. Không có token: quãng đường ước lượng = đường chim bay × 1.3, tốc độ 25 km/h. Bản đồ nền dùng tile OpenStreetMap.
- Máy tính không có GPS nên "vị trí hiện tại" lấy gần đúng theo IP, người dùng có thể bấm bản đồ để chỉnh.

## Cấu trúc
```
database/            01_schema.sql, 02_seed.sql (Docker tự chạy lần đầu)
database/upgrades/   các file nâng cấp DB cũ, chạy tay (để ngoài database/ để Docker không chạy nhầm)
src/main/java/com/goride/
  common/            Config (.env), Json, Geo, Protocol, Log
  server/            ServerMain, ClientHandler, Router,
                     *Service (Auth/Ride/Food/Merchant/Otp/Voucher/Account/Admin),
                     Sessions (push realtime),
                     Mailer (SMTP Gmail), Images, MapboxClient, UdpGpsServer, Db
  client/            ClientMain, Net (TCP), GpsSender (UDP), MyLocation
  client/ui/         LoginFrame, UserFrame (điều hướng + trang chủ), RidePanel, FoodPanel (4 bước),
                     MerchantPanel, DriverFrame, AdminFrame, MapPanel, PlaceField, DataTable,
                     ProfilePanel, VoucherBox, Toast (thông báo nổi), ImageField, ImageStore,
                     BaseFrame, Theme, Ui
```

## Lỗi thường gặp
- **Không kết nối được server**: chạy server trước; kiểm tra `SERVER_HOST` / firewall.
- **Server báo lỗi DB**: `docker compose ps` xem MySQL đã chạy chưa; `DB_PORT` trong `.env` phải trùng cổng đã map (mặc định 3309).
- **Tìm địa chỉ báo chưa cấu hình Mapbox**: điền `MAPBOX_TOKEN` rồi khởi động lại server, hoặc bấm trực tiếp lên bản đồ để chọn điểm.
- **Không nhận được email OTP**: kiểm tra `GMAIL_USER` / `GMAIL_APP_PASSWORD` trong `.env` (phải là *mật khẩu ứng dụng* 16 ký tự, không phải mật khẩu Gmail thường, và tài khoản phải bật xác minh 2 bước). Khi chưa cấu hình, server in mã ra console — dùng mã đó để đăng ký thử. Nhớ xem cả thư mục Spam.
- **Tài xế không thấy yêu cầu**: phải được duyệt, đang Online, cùng loại xe, và cách điểm đón / nhà hàng ≤ `MATCH_RADIUS_KM`. Khi danh sách trống, khung *Yêu cầu gần bạn* ghi rõ lý do ngay bên dưới. Hay gặp nhất là **chỗ đứng sai**: "vị trí hiện tại" lấy gần đúng theo IP nên có thể lệch hàng chục km — xem dòng địa chỉ trong thẻ *Trạng thái*, nếu sai thì bấm **🗺 Xem trên bản đồ** rồi bấm đúng chỗ bạn đang đứng (client báo ngay cho server qua TCP và tự tải lại danh sách).
