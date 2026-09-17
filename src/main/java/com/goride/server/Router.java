package com.goride.server;

import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

/**
 * Điều phối lệnh -> service và kiểm tra quyền.
 * Lệnh bắt đầu bằng ADMIN_ chỉ cho Admin, DRIVER_ chỉ cho Driver, còn lại (trừ nhóm COMMON) cho User.
 */
public final class Router {
    private static final Set<String> PUBLIC = Set.of("PING", "LOGIN", "REGISTER");
    private static final Set<String> COMMON = Set.of("LOGOUT", "PROFILE_GET", "PROFILE_UPDATE",
            "GEO_SEARCH", "GEO_DETAIL", "GEO_REVERSE", "GEO_ROUTE");

    private Router() {}

    static JsonElement handle(ClientHandler c, String type, JsonObject d) {
        if (!PUBLIC.contains(type)) {
            if (!c.loggedIn()) throw new AppException("Bạn cần đăng nhập");
            if (!COMMON.contains(type)) {
                String need = type.startsWith("ADMIN_") ? "ADMIN" : type.startsWith("DRIVER_") ? "DRIVER" : "USER";
                if (!need.equals(c.role)) throw new AppException("Tài khoản không có quyền dùng chức năng này");
            }
        }
        return switch (type) {
            case "PING" -> Json.obj("pong", System.currentTimeMillis());

            // ----- Tài khoản -----
            case "REGISTER" -> AuthService.register(d);
            case "LOGIN" -> AuthService.login(c, d);
            case "LOGOUT" -> AuthService.logout(c);
            case "PROFILE_GET" -> AuthService.profile(c.userId);
            case "PROFILE_UPDATE" -> AuthService.updateProfile(c, d);

            // ----- Bản đồ (Mapbox) -----
            case "GEO_SEARCH" -> MapboxClient.search(Json.req(d, "input", "Từ khóa"),
                    Json.dbl(d, "lat", Double.NaN), Json.dbl(d, "lng", Double.NaN));
            case "GEO_DETAIL" -> MapboxClient.detail(Json.req(d, "place_id", "Địa điểm"));
            case "GEO_REVERSE" -> Json.obj("address", MapboxClient.reverse(Json.reqDbl(d, "lat"), Json.reqDbl(d, "lng")));
            case "GEO_ROUTE" -> MapboxClient.route(Json.reqDbl(d, "from_lat"), Json.reqDbl(d, "from_lng"),
                    Json.reqDbl(d, "to_lat"), Json.reqDbl(d, "to_lng"), Json.str(d, "vehicle", "bike"));

            // ----- User: đặt xe -----
            case "RIDE_QUOTE" -> RideService.quote(d);
            case "RIDE_BOOK" -> RideService.book(c, d);
            case "RIDE_CANCEL" -> RideService.cancelByUser(c, d);
            case "RIDE_CURRENT" -> RideService.currentForUser(c.userId);
            case "RIDE_HISTORY" -> RideService.history("r.user_id", c.userId);

            // ----- User: đồ ăn -----
            case "RESTAURANT_LIST" -> FoodService.restaurants();
            case "MENU_LIST" -> FoodService.menu(Json.reqLong(d, "restaurant_id"));
            case "FOOD_QUOTE" -> FoodService.quote(d);
            case "FOOD_ORDER" -> FoodService.order(c, d);
            case "FOOD_CANCEL" -> FoodService.cancelByUser(c, d);
            case "FOOD_CURRENT" -> FoodService.currentForUser(c.userId);
            case "FOOD_HISTORY" -> FoodService.history("o.user_id", c.userId);
            case "FOOD_DETAIL" -> FoodService.detailForUser(c.userId, Json.reqLong(d, "id"));

            // ----- User: thanh toán / đánh giá -----
            case "PAYMENT_LIST" -> AccountService.payments(c.userId);
            case "REVIEW_CREATE" -> AccountService.review(c, d);

            // ----- Driver -----
            case "DRIVER_ONLINE" -> AuthService.setOnline(c, d);
            case "DRIVER_VEHICLE_UPDATE" -> AuthService.updateVehicle(c, d);
            case "DRIVER_PENDING" -> DriverJobs.pending(c);
            case "DRIVER_CURRENT" -> DriverJobs.current(c.userId);
            case "DRIVER_RIDE_ACCEPT" -> RideService.accept(c, d);
            case "DRIVER_RIDE_REJECT" -> { c.rejectedRides.add(Json.reqLong(d, "id")); yield null; }
            case "DRIVER_RIDE_STATUS" -> RideService.driverStatus(c, d);
            case "DRIVER_FOOD_ACCEPT" -> FoodService.accept(c, d);
            case "DRIVER_FOOD_REJECT" -> { c.rejectedOrders.add(Json.reqLong(d, "id")); yield null; }
            case "DRIVER_FOOD_STATUS" -> FoodService.driverStatus(c, d);
            case "DRIVER_HISTORY" -> Json.obj("rides", RideService.history("r.driver_id", c.userId),
                    "orders", FoodService.history("o.driver_id", c.userId));
            case "DRIVER_EARNINGS" -> AccountService.earnings(c.userId);
            case "DRIVER_REVIEWS" -> AccountService.driverReviews(c.userId);

            // ----- Admin -----
            case "ADMIN_DASHBOARD" -> AdminService.dashboard();
            case "ADMIN_LIST" -> AdminService.list(d);
            case "ADMIN_USER_STATUS" -> AdminService.setUserStatus(d);
            case "ADMIN_DRIVER_APPROVAL" -> AdminService.setDriverApproval(d);
            case "ADMIN_VEHICLE_UPDATE" -> AdminService.updateVehicle(d);
            case "ADMIN_RIDE_CANCEL" -> RideService.cancelByAdmin(Json.reqLong(d, "id"));
            case "ADMIN_FOOD_CANCEL" -> FoodService.cancelByAdmin(Json.reqLong(d, "id"));
            case "ADMIN_FOOD_DETAIL" -> FoodService.view(Json.reqLong(d, "id"));
            case "ADMIN_RESTAURANT_SAVE" -> AdminService.saveRestaurant(d);
            case "ADMIN_RESTAURANT_DELETE" -> AdminService.deleteRestaurant(Json.reqLong(d, "id"));
            case "ADMIN_MENU_SAVE" -> AdminService.saveMenuItem(d);
            case "ADMIN_MENU_DELETE" -> AdminService.deleteMenuItem(Json.reqLong(d, "id"));
            case "ADMIN_PRICING_SAVE" -> AdminService.savePricing(d);
            case "ADMIN_PAYMENT_STATUS" -> AdminService.setPaymentStatus(d);
            case "ADMIN_REVIEW_TOGGLE" -> AdminService.toggleReview(Json.reqLong(d, "id"));

            default -> throw new AppException("Lệnh không được hỗ trợ: " + type);
        };
    }
}
