package az.millers.hcm.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Scans uploaded file bytes against a ClamAV daemon ({@code clamd}) using the
 * INSTREAM protocol over TCP (M50 — PRD 14.8 "Virus-scanned on upload").
 *
 * <h3>Protocol</h3>
 * <ol>
 *   <li>Open TCP connection to {@code clamd} host:port.</li>
 *   <li>Send {@code zINSTREAM\0} — the {@code z} prefix asks clamd to use
 *       null-termination rather than newlines, which is safer in binary
 *       contexts.</li>
 *   <li>Send the file as one chunk: 4-byte big-endian length followed by the
 *       raw bytes.</li>
 *   <li>Send a zero-length terminator ({@code \0\0\0\0}) to signal EOF.</li>
 *   <li>Read clamd's response:
 *       <ul>
 *         <li>{@code stream: OK} → file is clean.</li>
 *         <li>{@code stream: <VirusName> FOUND} → infected.</li>
 *         <li>Anything else → unexpected; treated as {@link ScanOutcome#ERROR}.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <pre>
 *   hcm.security.virus-scan.enabled   (default true)
 *   hcm.security.virus-scan.host      (default localhost)
 *   hcm.security.virus-scan.port      (default 3310)
 *   hcm.security.virus-scan.timeout-ms (default 30000)
 * </pre>
 *
 * When {@code enabled=false} every call returns {@link ScanResult#SKIPPED}.
 * When {@code enabled=true} but clamd is unreachable the caller receives an
 * {@link IOException} and should surface a 503 — blocking uploads is safer
 * than silently skipping the scan.
 */
@Service
public class ClamAvScanService {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanService.class);

    // ------------------------------------------------ public surface

    public enum ScanOutcome { CLEAN, INFECTED, SKIPPED, ERROR }

    /**
     * Outcome of a single scan request.
     *
     * @param outcome  one of {@link ScanOutcome}
     * @param detail   virus name when {@code INFECTED}; raw clamd response when
     *                 {@code ERROR}; {@code null} otherwise
     */
    public record ScanResult(ScanOutcome outcome, String detail) {

        static final ScanResult SKIPPED = new ScanResult(ScanOutcome.SKIPPED, null);
        static final ScanResult CLEAN   = new ScanResult(ScanOutcome.CLEAN, null);

        static ScanResult infected(String virusName) {
            return new ScanResult(ScanOutcome.INFECTED, virusName);
        }
        static ScanResult error(String rawResponse) {
            return new ScanResult(ScanOutcome.ERROR, rawResponse);
        }

        public boolean isInfected() { return outcome == ScanOutcome.INFECTED; }
        public boolean isClean()    { return outcome == ScanOutcome.CLEAN; }
        public boolean isSkipped()  { return outcome == ScanOutcome.SKIPPED; }
    }

    // ------------------------------------------------ state

    private final boolean enabled;
    private final String  host;
    private final int     port;
    private final int     timeoutMs;

    public ClamAvScanService(
            @Value("${hcm.security.virus-scan.enabled:true}")     boolean enabled,
            @Value("${hcm.security.virus-scan.host:localhost}")   String  host,
            @Value("${hcm.security.virus-scan.port:3310}")        int     port,
            @Value("${hcm.security.virus-scan.timeout-ms:30000}") int     timeoutMs) {
        this.enabled   = enabled;
        this.host      = host;
        this.port      = port;
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() { return enabled; }

    // ------------------------------------------------ scan

    /**
     * Scans {@code data} via ClamAV INSTREAM.
     *
     * @throws IOException if the TCP connection could not be established or the
     *                     socket timed out — caller should surface a 503
     */
    public ScanResult scan(byte[] data) throws IOException {
        if (!enabled) {
            log.debug("Virus scan disabled — skipping ({} bytes)", data.length);
            return ScanResult.SKIPPED;
        }

        log.debug("Scanning {} bytes via ClamAV at {}:{}", data.length, host, port);

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);

            OutputStream out = socket.getOutputStream();

            // Handshake: zINSTREAM\0
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

            // Chunk: 4-byte BE length + payload
            out.write(ByteBuffer.allocate(4).putInt(data.length).array());
            out.write(data);

            // Terminator: zero-length chunk
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            // Read response (clamd sends at most one response line)
            InputStream in  = socket.getInputStream();
            byte[]      buf = in.readAllBytes();
            // Strip trailing null / newline
            String response = new String(buf, StandardCharsets.UTF_8)
                    .replace("\0", "").strip();

            log.debug("ClamAV response: '{}'", response);

            // "stream: OK"  or  "stream: <VirusName> FOUND"
            if (response.endsWith("OK")) {
                return ScanResult.CLEAN;
            }
            if (response.endsWith("FOUND")) {
                // Extract virus name: "stream: Eicar-Test-Signature FOUND"
                String virus = response
                        .replaceFirst("^stream:\\s*", "")
                        .replaceFirst("\\s+FOUND$", "")
                        .strip();
                log.warn("ClamAV detected virus '{}' in upload", virus);
                return ScanResult.infected(virus);
            }
            // Unexpected response
            log.error("ClamAV returned unexpected response: '{}'", response);
            return ScanResult.error(response);
        }
    }
}
