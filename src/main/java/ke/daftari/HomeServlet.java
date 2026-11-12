package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

/** Home screen: today's totals (FR-13), low-stock warnings (FR-23 to FR-25), unmatched payments (FR-21). */
@WebServlet("")
public class HomeServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        StringBuilder b = new StringBuilder("<p>Hello, " + Page.esc(w.name()) + " (" + w.role() + ")</p>");
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM mpesa_payment WHERE shop_id=? AND sale_id IS NULL")) {
                ps.setInt(1, w.shopId());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0)
                        b.append("<div class='warn'>").append(rs.getInt(1)).append(" M-Pesa payment(s) not matched. <a href='")
                         .append(req.getContextPath()).append("/mpesa'>Resolve</a></div>");
                }
            }
            // Low stock: stock <= average daily sales (last 14 days) x restock days
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.name,p.quantity,p.restock_days,"
                + "COALESCE((SELECT SUM(si.quantity) FROM sale_item si JOIN sale s ON s.id=si.sale_id "
                + "WHERE si.product_id=p.id AND s.status='ok' AND s.sold_at>=NOW()-INTERVAL 14 DAY),0)/14 AS daily "
                + "FROM product p WHERE p.shop_id=?")) {
                ps.setInt(1, w.shopId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double daily = rs.getDouble("daily");
                        if (daily > 0 && rs.getInt("quantity") <= daily * rs.getInt("restock_days"))
                            b.append("<div class='warn'>Low stock: ").append(Page.esc(rs.getString("name")))
                             .append(" - ").append(rs.getInt("quantity")).append(" left</div>");
                    }
                }
            }
            b.append("<h3>Today</h3><table><tr><th>Payment</th><th>Sales</th><th>KES</th></tr>");
            java.math.BigDecimal day = java.math.BigDecimal.ZERO;
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT pay_type,COUNT(*) n,SUM(total) t FROM sale WHERE shop_id=? AND status='ok' AND DATE(sold_at)=CURDATE() GROUP BY pay_type")) {
                ps.setInt(1, w.shopId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        day = day.add(rs.getBigDecimal("t"));
                        b.append("<tr><td>").append(rs.getString("pay_type")).append("</td><td>").append(rs.getInt("n"))
                         .append("</td><td>").append(rs.getBigDecimal("t").toPlainString()).append("</td></tr>");
                    }
                }
            }
            b.append("<tr><th>Total</th><th></th><th>").append(day.toPlainString()).append("</th></tr></table>");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        Page.render(req, res, "Daftari", b.toString());
    }
}
