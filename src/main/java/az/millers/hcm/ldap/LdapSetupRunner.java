package az.millers.hcm.ldap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Post-startup hook that idempotently configures the Keycloak LDAP federation
 * provider (M54 — PRD §14.6).
 *
 * <p>Runs {@link LdapFederationService#setupIfNeeded()} in a background virtual
 * thread so that it never delays the HTTP server becoming ready. The setup is
 * retried up to three times with increasing delays (5 s, 10 s, 15 s) to tolerate
 * the OpenLDAP and Keycloak containers still initialising on a first
 * {@code compose up}.
 *
 * <p>Auto-setup can be disabled by setting {@code hcm.ldap.auto-setup=false}
 * (or {@code HCM_LDAP_AUTO_SETUP=false}); in that case the federation provider
 * can be created on demand via {@code POST /api/admin/ldap/setup}.
 */
@Component
public class LdapSetupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LdapSetupRunner.class);

    private final LdapFederationService ldapFederationService;
    private final LdapProperties props;

    public LdapSetupRunner(LdapFederationService ldapFederationService,
                            LdapProperties props) {
        this.ldapFederationService = ldapFederationService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.autoSetup()) {
            log.info("LDAP auto-setup is disabled (hcm.ldap.auto-setup=false). "
                    + "Configure manually via POST /api/admin/ldap/setup");
            return;
        }

        Thread.ofVirtual().name("ldap-setup").start(() -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    // Progressive delay: 5 s, 10 s, 15 s — gives time for Keycloak
                    // and OpenLDAP containers to finish their own startup sequences
                    // before the first Admin REST call is attempted.
                    Thread.sleep(5_000L * attempt);
                    ldapFederationService.setupIfNeeded();
                    return; // success — exit the retry loop
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("LDAP setup thread interrupted — giving up");
                    return;
                } catch (Exception ex) {
                    log.warn("LDAP setup attempt {}/3 failed: {}", attempt, ex.getMessage());
                }
            }
            log.warn("LDAP auto-setup gave up after 3 attempts. "
                    + "Configure manually via POST /api/admin/ldap/setup");
        });
    }
}
