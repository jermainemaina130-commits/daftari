package ke.daftari;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads one Safaricom M-Pesa "received" SMS (FR-16): code, amount, sender and time.
 * The reading is done by the C++ program native/mpesa_reader (see README). The path is
 * taken from the MPESA_READER environment variable. If the program is missing, this
 * throws IllegalStateException so the problem is visible instead of every message
 * being reported as unreadable.
 */
public final class MpesaParser {
    public record Msg(String code, BigDecimal amount, String sender, LocalDateTime time) {}

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long TIMEOUT_SECONDS = 5;
    private static final int UNREADABLE_EXIT = 2;

    private MpesaParser() {}

    public static Optional<Msg> parse(String sms) {
        if (sms == null || sms.isBlank()) return Optional.empty();
        String reader = System.getenv("MPESA_READER");
        if (reader == null || reader.isBlank()) {
            throw new IllegalStateException("MPESA_READER is not set; point it at native/bin/mpesa_reader.exe");
        }
        try {
            Process p = new ProcessBuilder(reader)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            try (OutputStream in = p.getOutputStream()) {
                in.write(sms.getBytes(StandardCharsets.UTF_8));
            }
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            if (p.exitValue() == UNREADABLE_EXIT) return Optional.empty();
            if (p.exitValue() != 0) {
                throw new IllegalStateException("M-Pesa reader failed with exit code " + p.exitValue());
            }
            String[] f = new String(out, StandardCharsets.UTF_8).strip().split("\t", -1);
            if (f.length != 4) throw new IllegalStateException("M-Pesa reader returned an unexpected line");
            return Optional.of(new Msg(f[0], new BigDecimal(f[1]), f[2], LocalDateTime.parse(f[3], TIME_FORMAT)));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run the M-Pesa reader at " + reader, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
