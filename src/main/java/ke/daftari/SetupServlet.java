package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

/** One-time page that creates the first shop and owner. It locks itself once any user exists. */
@WebServlet("/setup")
public class SetupServlet extends HttpServlet {
    private boolean hasUsers() throws SQLException {
        try (Connection c = Db.get(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            if (hasUsers()) { res.sendRedirect(req.getContextPath() + "/login"); return; }
        } catch (SQLException e) { throw new ServletException(e); }
        Page.render(req, res, "Set up your shop",
            "<form method='post'><input name='shop' placeholder='Shop name' required>"
            + "<input name='location' placeholder='Location, e.g. Icipe Road' required>"
            + "<input name='name' placeholder='Your name' required>"
            + "<input name='phone' placeholder='Phone number' inputmode='tel' required>"
            + "<input name='password' type='password' minlength='6' placeholder='Password (6+ characters)' required>"
            + "<button>Create owner account</button></form>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            if (hasUsers()) { res.sendRedirect(req.getContextPath() + "/login"); return; }
            String pw = req.getParameter("password");
            if (pw == null || pw.length() < 6) { res.sendError(400, "Password too short"); return; }
            try (Connection c = Db.get()) {
                c.setAutoCommit(false);
                int shopId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO shop(name,location) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, req.getParameter("shop"));
                    ps.setString(2, req.getParameter("location"));
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); shopId = k.getInt(1); }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(shop_id,name,phone,password_hash,role) VALUES(?,?,?,?,'owner')")) {
                    ps.setInt(1, shopId);
                    ps.setString(2, req.getParameter("name"));
                    ps.setString(3, req.getParameter("phone").trim());
                    ps.setString(4, BCrypt.hashpw(pw, BCrypt.gensalt()));
                    ps.executeUpdate();
                }
                c.commit();
            }
        } catch (SQLException e) { throw new ServletException(e); }
        res.sendRedirect(req.getContextPath() + "/login");
    }
}
