package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Log;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gửi email qua SMTP của Gmail bằng thư viện có sẵn trong JDK (không cần dependency).
 * Cấu hình trong .env:
 *   GMAIL_USER          địa chỉ Gmail gốc dùng để gửi (vd goride.otp@gmail.com)
 *   GMAIL_APP_PASSWORD  "mật khẩu ứng dụng" 16 ký tự lấy ở Google Account > Bảo mật
 *   GMAIL_OAUTH_TOKEN   (tùy chọn) access token OAuth2, dùng thay cho app password
 *   SMTP_HOST/SMTP_PORT (tùy chọn) mặc định smtp.gmail.com:465 — cổng 587 sẽ dùng STARTTLS
 * Chưa cấu hình thì server chỉ in mã ra console để dev chạy thử.
 */
public final class Mailer {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private Mailer() {}

    public static String user()  { return Config.get("GMAIL_USER", ""); }
    private static String pass() { return Config.get("GMAIL_APP_PASSWORD", ""); }
    private static String token(){ return Config.get("GMAIL_OAUTH_TOKEN", ""); }
    private static String host() { return Config.get("SMTP_HOST", "smtp.gmail.com"); }
    private static int port()    { return Config.getInt("SMTP_PORT", 465); }

    /** Đã có đủ biến môi trường để gửi thật hay chưa. */
    public static boolean configured() {
        return !user().isBlank() && (!pass().isBlank() || !token().isBlank());
    }

    public static String status() {
        return configured()
                ? "Email OTP: gửi qua " + host() + ":" + port() + " bằng tài khoản " + user()
                : "Email OTP: CHƯA cấu hình GMAIL_USER/GMAIL_APP_PASSWORD — mã sẽ được in ra console này";
    }

    /** Gửi mail HTML. Ném IOException nếu SMTP từ chối để tầng trên báo lỗi cho người dùng. */
    public static void sendHtml(String to, String subject, String html) throws IOException {
        Session s = null;
        try {
            s = open();
            s.cmd("MAIL FROM:<" + user() + ">", 250);
            s.cmd("RCPT TO:<" + to + ">", 250, 251);
            s.cmd("DATA", 354);
            s.write(message(to, subject, html));
            s.cmd(".", 250);
            s.quiet("QUIT");
        } finally {
            if (s != null) s.close();
        }
        // không log subject: tiêu đề mail OTP có chứa chính mã xác thực
        Log.info("Đã gửi email tới " + to);
    }

    // ---------- Phiên SMTP ----------

    private static Session open() throws IOException {
        boolean implicitTls = port() == 465;
        Socket raw = implicitTls
                ? sslFactory().createSocket()
                : new Socket();
        raw.connect(new InetSocketAddress(host(), port()), CONNECT_TIMEOUT_MS);
        raw.setSoTimeout(READ_TIMEOUT_MS);
        Session s = new Session(raw);
        s.expect(220);
        s.cmd("EHLO goride", 250);
        if (!implicitTls) {
            s.cmd("STARTTLS", 220);
            s.upgrade(sslFactory(), host(), port());
            s.cmd("EHLO goride", 250);
        }
        auth(s);
        return s;
    }

    private static void auth(Session s) throws IOException {
        if (!token().isBlank()) {
            String blob = "user=" + user() + "auth=Bearer " + token() + "";
            s.cmd("AUTH XOAUTH2 " + b64(blob), 235);
        } else {
            s.cmd("AUTH LOGIN", 334);
            s.cmd(b64(user()), 334);
            // Google hiển thị app password thành 4 nhóm 4 ký tự, người dùng hay copy cả dấu cách
            s.cmd(b64(pass().replace(" ", "")), 235);
        }
    }

    private static SSLSocketFactory sslFactory() { return (SSLSocketFactory) SSLSocketFactory.getDefault(); }

    private static final class Session implements AutoCloseable {
        private Socket socket;
        private BufferedReader in;
        private Writer out;

        Session(Socket socket) throws IOException { bind(socket); }

        private void bind(Socket s) throws IOException {
            this.socket = s;
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            this.out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
        }

        void upgrade(SSLSocketFactory factory, String host, int port) throws IOException {
            SSLSocket tls = (SSLSocket) factory.createSocket(socket, host, port, true);
            tls.setSoTimeout(READ_TIMEOUT_MS);
            tls.startHandshake();
            bind(tls);
        }

        void cmd(String line, int... okCodes) throws IOException {
            write(line + "\r\n");
            expect(okCodes);
        }

        void quiet(String line) {
            try { write(line + "\r\n"); } catch (IOException ignored) { }
        }

        void write(String text) throws IOException {
            out.write(text);
            out.flush();
        }

        /** Đọc hết phản hồi (nhiều dòng "250-..." rồi "250 ...") và kiểm tra mã trả về. */
        void expect(int... okCodes) throws IOException {
            String line = in.readLine();
            if (line == null) throw new IOException("Máy chủ SMTP đóng kết nối");
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = in.readLine();
                if (line == null) throw new IOException("Máy chủ SMTP đóng kết nối giữa chừng");
            }
            int code;
            try { code = Integer.parseInt(line.substring(0, 3)); }
            catch (RuntimeException e) { throw new IOException("Phản hồi SMTP lạ: " + line); }
            for (int ok : okCodes) if (code == ok) return;
            throw new IOException("SMTP trả về: " + line);
        }

        @Override
        public void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ---------- Soạn thư ----------

    private static String message(String to, String subject, String html) {
        String from = Config.get("MAIL_FROM_NAME", "GoRide");
        return "From: " + encodeWord(from) + " <" + user() + ">\r\n"
                + "To: <" + to + ">\r\n"
                + "Subject: " + encodeWord(subject) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + wrap(b64(html))
                + "\r\n";
    }

    /** Mã hóa tiêu đề/tên tiếng Việt theo RFC 2047 để Gmail hiển thị đúng. */
    private static String encodeWord(String text) {
        return "=?UTF-8?B?" + b64(text) + "?=";
    }

    private static String wrap(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 76)
            sb.append(base64, i, Math.min(base64.length(), i + 76)).append("\r\n");
        return sb.toString();
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
