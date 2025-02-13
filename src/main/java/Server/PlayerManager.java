package Server;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlayerManager {
    private static final String URL = "jdbc:sqlite:mydb.sqlite";
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL);
            initializeDatabase();
        }
        return connection;
    }

    private static void initializeDatabase() {
        try (var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "playerID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "online INTEGER NOT NULL DEFAULT 0)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getOnlinePlayers() {
        List<String> onlinePlayers = new ArrayList<>();
        String sql = "SELECT username FROM users WHERE online = 1";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                onlinePlayers.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return onlinePlayers;
    }

    public static void addUser(String username) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void setUserOnline(String username) {
        String sql = "UPDATE users SET online = 1 WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement prepstate = connection.prepareStatement(sql)) {
            prepstate.setString(1, username);
            prepstate.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void setUserOffline(String username) {
        String sql = "UPDATE users SET online = 0 WHERE username = ?";
        try (var conn = getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isUsernameTaken(String username) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateUsername(String oldUsername, String newUsername) {
        try (Connection connection = getConnection();
            PreparedStatement stmt = connection.prepareStatement("UPDATE users SET username = ? WHERE username = ?")) {
            stmt.setString(1, newUsername);
            stmt.setString(2, oldUsername);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
