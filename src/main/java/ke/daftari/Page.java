package ke.daftari;

import jakarta.servlet.http.*;
import java.io.IOException;

/** Shared phone-first page layout, so each servlet only builds its own body. */
public final class Page {
    private Page() {}

    public static String esc(Object o) {
        if (o == null) return "";
        return o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static Who who(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        return s == null ? null : (Who) s.getAttribute("who");
    }

    public static void render(HttpServletRequest r, HttpServletResponse w, String title, String body) throws IOException {
        String c = r.getContextPath();
        String nav = who(r) == null ? "" :
            "<nav><a href='" + c + "/'>Home</a><a href='" + c + "/sale'>Sale</a><a href='" + c + "/stock'>Stock</a>"
            + "<a href='" + c + "/mpesa'>M-Pesa</a><a href='" + c + "/login?out=1'>Logout</a></nav>";
        w.setContentType("text/html;charset=UTF-8");
        w.getWriter().write("<!doctype html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>" + esc(title) + " - Daftari</title>"
            + "<style>body{font:18px sans-serif;max-width:640px;margin:auto;padding:12px}"
            + "input,select,button,textarea{font:inherit;width:100%;padding:12px;margin:4px 0;box-sizing:border-box}"
            + "button{background:#0a7d3b;color:#fff;border:0;border-radius:6px}nav a{margin-right:14px}"
            + ".warn{background:#fff3cd;padding:8px;margin:6px 0}table{width:100%;border-collapse:collapse}"
            + "td,th{border-bottom:1px solid #ddd;padding:6px;text-align:left}</style></head><body>"
            + nav + "<h2>" + esc(title) + "</h2>" + body + "</body></html>");
    }
}
