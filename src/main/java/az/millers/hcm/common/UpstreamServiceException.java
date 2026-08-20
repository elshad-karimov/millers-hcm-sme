package az.millers.hcm.common;

/**
 * A service we depend on — Keycloak, LDAP — answered wrongly or not at all.
 *
 * <p>Distinct from the catch-all 500 on purpose. The catch-all is for faults we
 * did not see coming, so its message can only ever be "something went wrong";
 * this one is thrown at a known seam, where we can say which dependency failed
 * and what an operator should look at. It maps to 502, because the request was
 * fine and the thing behind us was not.
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
