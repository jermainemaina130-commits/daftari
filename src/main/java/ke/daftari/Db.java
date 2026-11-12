package ke.daftari;

import java.sql.*;

/** Connection settings come from environment variables so no password lives in Git. */
public final class Db {
    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException e) { throw new ExceptionInInitializerError(e); }
    }
    private Db() {}

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null ? d : v;
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(
            env("DB_URL", "jdbc:mysql://localhost:3306/daftari?useUnicode=true&characterEncoding=utf8"),
            env("DB_USER", "root"), env("DB_PASS", ""));
    }
}
