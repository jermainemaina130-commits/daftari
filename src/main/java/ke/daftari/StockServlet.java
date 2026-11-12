package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;

/** FR-5, FR-7: owner adds products and restocks. Attendants only view (buying price hidden). */
@WebServlet("/stock")
public class StockServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        StringBuilder b = new StringBuilder("<table><tr><th>Item</th><th>Left</th><th>Sell</th>");
        if (w.owner()) b.append("<th>Buy</th><th>Add</th>");
        b.append("</tr>");
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("SELECT id,name,buy_price,sell_price,unit,quantity FROM product WHERE shop_id=? ORDER BY name")) {
            ps.setInt(1, w.shopId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    b.append("<tr><td>").append(Page.esc(rs.getString("name"))).append(" (").append(rs.getString("unit")).append(")</td><td>")
                     .append(rs.getInt("quantity")).append("</td><td>").append(rs.getBigDecimal("sell_price").toPlainString()).append("</td>");
                    if (w.owner())
                        b.append("<td>").append(rs.getBigDecimal("buy_price").toPlainString()).append("</td><td>")
                         .append("<form method='post'><input type='hidden' name='action' value='restock'>")
                         .append("<input type='hidden' name='id' value='").append(rs.getInt("id")).append("'>")
                         .append("<input name='qty' type='number' min='1' placeholder='+' required><button>Add</button></form></td>");
                    b.append("</tr>");
                }
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        b.append("</table>");
        if (w.owner())
            b.append("<h3>New product</h3><form method='post'><input type='hidden' name='action' value='add'>"
                + "<input name='name' placeholder='Name' required>"
                + "<input name='buy' type='number' step='0.01' placeholder='Buying price' required>"
                + "<input name='sell' type='number' step='0.01' placeholder='Selling price' required>"
                + "<select name='unit'><option>unit</option><option>packet</option><option>heap</option></select>"
                + "<input name='qty' type='number' min='0' placeholder='Opening quantity' required>"
                + "<input name='days' type='number' min='1' value='2' placeholder='Restock days' required>"
                + "<button>Save product</button></form>");
        Page.render(req, res, "Stock", b.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        if (!w.owner()) { res.sendError(403); return; }
        try (Connection c = Db.get()) {
            if ("restock".equals(req.getParameter("action"))) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE product SET quantity=quantity+? WHERE id=? AND shop_id=?")) {
                    ps.setInt(1, Integer.parseInt(req.getParameter("qty")));
                    ps.setInt(2, Integer.parseInt(req.getParameter("id")));
                    ps.setInt(3, w.shopId());
                    ps.executeUpdate();
                }
            } else {
                String unit = req.getParameter("unit");
                if (!java.util.List.of("unit", "packet", "heap").contains(unit)) { res.sendError(400); return; }
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO product(shop_id,name,buy_price,sell_price,unit,quantity,restock_days) VALUES(?,?,?,?,?,?,?)")) {
                    ps.setInt(1, w.shopId());
                    ps.setString(2, req.getParameter("name"));
                    ps.setBigDecimal(3, new BigDecimal(req.getParameter("buy")));
                    ps.setBigDecimal(4, new BigDecimal(req.getParameter("sell")));
                    ps.setString(5, unit);
                    ps.setInt(6, Integer.parseInt(req.getParameter("qty")));
                    ps.setInt(7, Integer.parseInt(req.getParameter("days")));
                    ps.executeUpdate();
                }
            }
        } catch (NumberFormatException e) {
            res.sendError(400, "Check the numbers you entered");
            return;
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/stock");
    }
}
