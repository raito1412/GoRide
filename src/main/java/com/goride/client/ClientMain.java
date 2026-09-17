package com.goride.client;

import com.goride.client.ui.LoginFrame;
import com.goride.client.ui.Theme;

import javax.swing.SwingUtilities;

public final class ClientMain {
    public static void main(String[] args) {
        // OpenStreetMap yêu cầu User-Agent riêng khi tải tile bản đồ
        System.setProperty("http.agent", "GoRide-Student-Project/1.0");
        Theme.install();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
