package com.goride.server;

import com.goride.common.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.sql.*;
import java.time.temporal.TemporalAccessor;

/** JDBC gọn: mỗi dòng kết quả là một JsonObject để gửi thẳng về client. */
public final class Db {
    private static final String URL = String.format(
            "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8"
                    + "&serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true",
            Config.get("DB_HOST", "127.0.0.1"), Config.get("DB_PORT", "3306"), Config.get("DB_NAME", "goride"));

    public interface Work<T> { T run(Connection c) throws Exception; }

    private Db() {}

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, Config.get("DB_USER", "goride"), Config.get("DB_PASSWORD", ""));
    }

    /** Chạy nhiều câu lệnh trong 1 transaction. */
    public static <T> T tx(Work<T> work) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof RuntimeException re) throw re;
                throw new DbException(e);
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static JsonArray query(String sql, Object... params) {
        try (Connection c = connect()) { return query(c, sql, params); }
        catch (SQLException e) { throw new DbException(e); }
    }

    public static JsonArray query(Connection c, String sql, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                JsonArray out = new JsonArray();
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) out.add(row(rs, md));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static JsonObject one(String sql, Object... params) {
        JsonArray a = query(sql, params);
        return a.size() == 0 ? null : a.get(0).getAsJsonObject();
    }

    public static JsonObject one(Connection c, String sql, Object... params) {
        JsonArray a = query(c, sql, params);
        return a.size() == 0 ? null : a.get(0).getAsJsonObject();
    }

    public static int update(String sql, Object... params) {
        try (Connection c = connect()) { return update(c, sql, params); }
        catch (SQLException e) { throw new DbException(e); }
    }

    public static int update(Connection c, String sql, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static long insert(String sql, Object... params) {
        try (Connection c = connect()) { return insert(c, sql, params); }
        catch (SQLException e) { throw new DbException(e); }
    }

    public static long insert(Connection c, String sql, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    private static JsonObject row(ResultSet rs, ResultSetMetaData md) throws SQLException {
        JsonObject o = new JsonObject();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String k = md.getColumnLabel(i);
            Object v = rs.getObject(i);
            if (v == null) o.add(k, JsonNull.INSTANCE);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof TemporalAccessor || v instanceof java.util.Date)
                o.addProperty(k, v.toString().replace('T', ' '));
            else o.addProperty(k, v.toString());
        }
        return o;
    }

    public static boolean isConstraintViolation(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause())
            if (x instanceof SQLIntegrityConstraintViolationException) return true;
        return false;
    }

    public static class DbException extends RuntimeException {
        public DbException(Throwable cause) { super(cause.getMessage(), cause); }
    }
}
