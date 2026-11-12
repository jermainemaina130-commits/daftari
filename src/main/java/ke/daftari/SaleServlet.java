package ke.daftari;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** FR-11, FR-12, FR-6, FR-27, NFR-9: a multi-item sale that lowers stock, all in one database transaction. */
@WebServlet("/sale")
public class SaleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        StringBuilder b = new StringBuilder();
        String err = req.getParameter("err");
        if (err != null) b.append("<div class='warn'>").append(Page.esc(err)).append("</div>");
        b.append("<form method='post'>");
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id,name,sell_price,quantity FROM product WHERE shop_id=? AND quantity>0 ORDER BY name")) {
                ps.setInt(1, w.shopId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        b.append("<label>").append(Page.esc(rs.getString("name"))).append(" - KES ")
                         .append(rs.getBigDecimal("sell_price").toPlainString()).append(" (").append(rs.getInt("quantity")).append(" left)</label>")
                         .append("<input type='number' name='q_").append(rs.getInt("id")).append("' min='0' value='0'>");
                }
            }
            b.append("<label>Payment</label><select name='pay'><option value='cash'>Cash</option>"
                + "<option value='mpesa'>M-Pesa</option><option value='credit'>Credit</option></select>"
                + "<label>If credit: customer</label><select name='customer'><option value=''>-- choose --</option>");
            try (PreparedStatement ps = c.prepareStatement("SELECT id,name,balance FROM customer WHERE shop_id=? ORDER BY name")) {
                ps.setInt(1, w.shopId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        b.append("<option value='").append(rs.getInt("id")).append("'>").append(Page.esc(rs.getString("name")))
                         .append(" (owes ").append(rs.getBigDecimal("balance").toPlainString()).append(")</option>");
                }
            }
            b.append("</select><input name='newname' placeholder='...or new customer name'>"
                + "<input name='newphone' placeholder='New customer phone' inputmode='tel'><button>Record sale</button></form>");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        Page.render(req, res, "New sale", b.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Who w = Page.who(req);
        String pay = req.getParameter("pay");
        if (!List.of("cash", "mpesa", "credit").contains(pay)) { res.sendError(400); return; }
        Map<Integer, Integer> items = new LinkedHashMap<>();
        for (String k : req.getParameterMap().keySet()) {
            if (!k.startsWith("q_")) continue;
            try {
                int q = Integer.parseInt(req.getParameter(k).trim());
                if (q > 0) items.put(Integer.parseInt(k.substring(2)), q);
            } catch (NumberFormatException ignored) { }
        }
        if (items.isEmpty()) { fail(req, res, "Choose at least one item."); return; }

        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                BigDecimal total = BigDecimal.ZERO;
                Map<Integer, BigDecimal> prices = new HashMap<>();
                for (Map.Entry<Integer, Integer> e : items.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT name,sell_price,quantity FROM product WHERE id=? AND shop_id=? FOR UPDATE")) {
                        ps.setInt(1, e.getKey());
                        ps.setInt(2, w.shopId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Unknown product.");
                            if (rs.getInt("quantity") < e.getValue())
                                throw new IllegalArgumentException("Not enough " + rs.getString("name") + " in stock.");
                            prices.put(e.getKey(), rs.getBigDecimal("sell_price"));
                            total = total.add(rs.getBigDecimal("sell_price").multiply(BigDecimal.valueOf(e.getValue())));
                        }
                    }
                }
                Integer cust = null;
                if (pay.equals("credit")) {
                    String name = req.getParameter("newname") == null ? "" : req.getParameter("newname").trim();
                    String cid = req.getParameter("customer");
                    if (!name.isEmpty()) {
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO customer(shop_id,name,phone) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                            ps.setInt(1, w.shopId());
                            ps.setString(2, name);
                            ps.setString(3, req.getParameter("newphone"));
                            ps.executeUpdate();
                            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); cust = k.getInt(1); }
                        }
                    } else if (cid != null && !cid.isEmpty()) {
                        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM customer WHERE id=? AND shop_id=?")) {
                            ps.setInt(1, Integer.parseInt(cid));
                            ps.setInt(2, w.shopId());
                            try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) throw new IllegalArgumentException("Unknown customer.");
                            }
                        }
                        cust = Integer.valueOf(cid);
                    } else {
                        throw new IllegalArgumentException("Choose or add a customer for a credit sale.");
                    }
                }
                int saleId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO sale(shop_id,user_id,pay_type,total,customer_id) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, w.shopId());
                    ps.setInt(2, w.id());
                    ps.setString(3, pay);
                    ps.setBigDecimal(4, total);
                    if (cust == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, cust);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); saleId = k.getInt(1); }
                }
                for (Map.Entry<Integer, Integer> e : items.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO sale_item(sale_id,product_id,quantity,price) VALUES(?,?,?,?)")) {
                        ps.setInt(1, saleId);
                        ps.setInt(2, e.getKey());
                        ps.setInt(3, e.getValue());
                        ps.setBigDecimal(4, prices.get(e.getKey()));
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE product SET quantity=quantity-? WHERE id=?")) {
                        ps.setInt(1, e.getValue());
                        ps.setInt(2, e.getKey());
                        ps.executeUpdate();
                    }
                }
                if (cust != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE customer SET balance=balance+? WHERE id=?")) {
                        ps.setBigDecimal(1, total);
                        ps.setInt(2, cust);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO credit_entry(customer_id,sale_id,amount,kind) VALUES(?,?,?,'sale')")) {
                        ps.setInt(1, cust);
                        ps.setInt(2, saleId);
                        ps.setBigDecimal(3, total);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (IllegalArgumentException e) {
                c.rollback();
                fail(req, res, e.getMessage());
                return;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/");
    }

    private void fail(HttpServletRequest req, HttpServletResponse res, String msg) throws IOException {
        res.sendRedirect(req.getContextPath() + "/sale?err=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
    }
}
