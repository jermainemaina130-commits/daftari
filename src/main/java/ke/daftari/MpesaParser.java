package ke.daftari;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.*;

/**
 * Reads one Safaricom M-Pesa "received" SMS (FR-16): code, amount, sender and time.
 * Written in Java for now; the SRS wants this reader in C++ (see README).
 */
public final class MpesaParser {
    public record Msg(String code, BigDecimal amount, String sender, LocalDateTime time) {}

    private static final Pattern CODE = Pattern.compile("^\\s*([A-Z0-9]{10})\\s+Confirmed");
    private static final Pattern AMT = Pattern.compile("Ksh\\s?([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM = Pattern.compile("from\\s+(.+?)\\s+(?:(?:\\+?254|0)[\\d*]{8,10}\\s+)?on\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("on\\s+(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\s+at\\s+(\\d{1,2}):(\\d{2})\\s*([AP]M)", Pattern.CASE_INSENSITIVE);

    private MpesaParser() {}

    public static Optional<Msg> parse(String sms) {
        try {
            if (sms == null || !sms.toLowerCase().contains("received")) return Optional.empty(); // skip sent/paid messages
            Matcher code = CODE.matcher(sms);
            Matcher amt = AMT.matcher(sms);
            Matcher time = TIME.matcher(sms);
            if (!code.find() || !amt.find() || !time.find()) return Optional.empty();
            Matcher from = FROM.matcher(sms);
            String sender = from.find() ? from.group(1).trim() : "";
            int y = Integer.parseInt(time.group(3));
            if (y < 100) y += 2000;
            int h = Integer.parseInt(time.group(4)) % 12;
            if (time.group(6).equalsIgnoreCase("PM")) h += 12;
            LocalDateTime when = LocalDateTime.of(y, Integer.parseInt(time.group(2)), Integer.parseInt(time.group(1)),
                h, Integer.parseInt(time.group(5)));
            return Optional.of(new Msg(code.group(1), new BigDecimal(amt.group(1).replace(",", "")), sender, when));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
