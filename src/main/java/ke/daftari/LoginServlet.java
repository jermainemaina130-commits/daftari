package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getParameter("out") != null) {
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
        }
        form(req, res, "");
    }

    private void form(HttpServletRequest req, HttpServletResponse res, String msg) throws IOException {
        Page.render(req, res, "Log in",
            (msg.isEmpty() ? "" : "<div class='warn'>" + Page.esc(msg) + "</div>")
            + "<form method='post'><input name='phone' placeholder='Phone number' inputmode='tel' required>"
            + "<input name='password' type='password' placeholder='Password' required><button>Log in</button></form>"
            + "<p><a href='" + req.getContextPath() + "/setup'>First time? Set up your shop</a></p>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String phone = req.getParameter("phone");
        String pw = req.getParameter("password");
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("SELECT id,shop_id,name,role,password_hash FROM users WHERE phone=?")) {
            ps.setString(1, phone == null ? "" : phone.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && pw != null && BCrypt.checkpw(pw, rs.getString("password_hash"))) {
                    HttpSession s = req.getSession(true);
                    req.changeSessionId();
                    s.setAttribute("who", new Who(rs.getInt("id"), rs.getInt("shop_id"), rs.getString("name"), rs.getString("role")));
                    res.sendRedirect(req.getContextPath() + "/");
                    return;
                }
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        form(req, res, "Wrong phone number or password.");
    }
}
