package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.Optional;

/** FR-15 to FR-20: paste SMS, read them, reject duplicates, suggest the matching open M-Pesa sale. */
@WebServlet("/mpesa")
public class MpesaServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        StringBuilder b = new StringBuilder();
        if (req.getParameter("msg") != null) b.append("<div class='warn'>").append(Page.esc(req.getParameter("msg"))).append("</div>");
        b.append("<form method='post'><input type='hidden' name='action' value='paste'>"
            + "<textarea name='text' rows='6' placeholder='Paste M-Pesa messages here' required></textarea><button>Read messages</button></form>"
            + "<h3>Not matched yet</h3>");
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("SELECT id,code,amount,sender,paid_at,raw FROM mpesa_payment WHERE shop_id=? AND sale_id IS NULL ORDER BY id DESC")) {
            ps.setInt(1, w.shopId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getBigDecimal("amount") == null) {
                        b.append("<div class='warn'>Could not read: ").append(Page.esc(rs.getString("raw"))).append("</div>");
                        continue;
                    }
                    b.append("<div class='warn'><b>KES ").append(rs.getBigDecimal("amount").toPlainString()).append("</b> from ")
                     .append(Page.esc(rs.getString("sender"))).append(" (").append(rs.getString("code")).append(")");
                    try (PreparedStatement q = c.prepareStatement(
                        "SELECT s.id FROM sale s WHERE s.shop_id=? AND s.pay_type='mpesa' AND s.status='ok' AND s.total=? "
                        + "AND NOT EXISTS (SELECT 1 FROM mpesa_payment p WHERE p.sale_id=s.id) ORDER BY s.sold_at DESC LIMIT 1")) {
                        q.setInt(1, w.shopId());
                        q.setBigDecimal(2, rs.getBigDecimal("amount"));
                        try (ResultSet m = q.executeQuery()) {
                            if (m.next())
                                b.append("<form method='post'><input type='hidden' name='action' value='accept'>"
                                    + "<input type='hidden' name='payment' value='").append(rs.getInt("id")).append("'>"
                                    + "<input type='hidden' name='sale' value='").append(m.getInt(1)).append("'>"
                                    + "<button>Match to sale #").append(m.getInt(1)).append("</button></form>");
                            else b.append("<br>No open sale of this amount.");
                        }
                    }
                    b.append("</div>");
                }
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        Page.render(req, res, "M-Pesa messages", b.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        String msg = "";
        try (Connection c = Db.get()) {
            if ("accept".equals(req.getParameter("action"))) {
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE mpesa_payment SET sale_id=? WHERE id=? AND shop_id=? AND sale_id IS NULL "
                    + "AND EXISTS (SELECT 1 FROM sale WHERE id=? AND shop_id=?)")) {
                    int sale = Integer.parseInt(req.getParameter("sale"));
                    ps.setInt(1, sale);
                    ps.setInt(2, Integer.parseInt(req.getParameter("payment")));
                    ps.setInt(3, w.shopId());
                    ps.setInt(4, sale);
                    ps.setInt(5, w.shopId());
                    msg = ps.executeUpdate() == 1 ? "Payment matched." : "Could not match that payment.";
                }
            } else {
                int saved = 0, dup = 0, bad = 0;
                String text = req.getParameter("text") == null ? "" : req.getParameter("text");
                for (String part : text.split("(?=\\b[A-Z0-9]{10}\\s+Confirmed)")) {
                    String raw = part.trim();
                    if (raw.isEmpty()) continue;
                    Optional<MpesaParser.Msg> m = MpesaParser.parse(raw);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO mpesa_payment(shop_id,code,amount,sender,paid_at,raw) VALUES(?,?,?,?,?,?)")) {
                        ps.setInt(1, w.shopId());
                        if (m.isPresent()) {
                            ps.setString(2, m.get().code());
                            ps.setBigDecimal(3, m.get().amount());
                            ps.setString(4, m.get().sender());
                            ps.setTimestamp(5, Timestamp.valueOf(m.get().time()));
                        } else {
                            ps.setNull(2, Types.VARCHAR);
                            ps.setNull(3, Types.DECIMAL);
                            ps.setNull(4, Types.VARCHAR);
                            ps.setNull(5, Types.TIMESTAMP);
                        }
                        ps.setString(6, raw);
                        try {
                            ps.executeUpdate();
                            if (m.isPresent()) saved++; else bad++;
                        } catch (SQLIntegrityConstraintViolationException dupe) {
                            dup++;
                        }
                    }
                }
                msg = saved + " read, " + dup + " already recorded (skipped), " + bad + " could not be read.";
            }
        } catch (NumberFormatException e) {
            res.sendError(400);
            return;
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/mpesa?msg=" + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8));
    }
}
