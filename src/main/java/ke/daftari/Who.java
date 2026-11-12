package ke.daftari;

/** The logged-in user, kept in the session. */
public record Who(int id, int shopId, String name, String role) implements java.io.Serializable {
    public boolean owner() { return "owner".equals(role); }
}
