package ke.daftari;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;

/** FR-1 / NFR-7: every page except login and first-time setup needs a logged-in user. */
@WebFilter("/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest rq, ServletResponse rs, FilterChain chain) throws IOException, ServletException {
        rq.setCharacterEncoding("UTF-8");
        HttpServletRequest req = (HttpServletRequest) rq;
        HttpServletResponse res = (HttpServletResponse) rs;
        String p = req.getServletPath();
        boolean open = p.equals("/login") || p.equals("/setup");
        if (!open && Page.who(req) == null) {
            res.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        chain.doFilter(rq, rs);
    }
}
