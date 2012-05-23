// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

// Apache™ DS
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.LdapPrincipal;

// Apache™ Shared LDAP API
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.util.StringTools;

// DMCCrap
import org.fhcrc.perdy.edrn_ws.ws_newcompass.WsNewcompass;
import org.fhcrc.perdy.edrn_ws.ws_newcompass.WsNewcompassSoap;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc
import java.net.URL;

/**
 * An interceptor that delegates authentication to the DMCC authentication web service,
 * and attempts to authenticate with that, if possible.  However, if it fails, it leaves
 * the state of authentication as unfinished so that the rest of Apache™ DS may attempt
 * to do it.
 */
public class DMCCAuthenticationInterceptor extends BaseInterceptor {
    /** Logging for debugging, informational messages… */
    private static final Logger LOG = LoggerFactory.getLogger(DMCCAuthenticationInterceptor.class);

    /** Where the authentication web service lives */
    private String authWebServiceURL = "https://www.compass.fhcrc.org/edrn_ws/ws_newcompass.asmx";

    /** What RDN attribute type to look for for user IDs. */
    private String rdnAttributeType = "0.9.2342.19200300.100.1.1";

    /** How long of an IBM check digit string to make. */
    private int checkDigitLength = 4096;

    /** Where we keep the soap. */
    private WsNewcompass soapService;

    /** IBM check digits required for some inane reason by methods in DMCC's soap service */
    private String checkDigits;

    /** The directory service we're … uhm … "servicing" */
    private DirectoryService directoryService;

    /** Get the location of the authentication web service.
     *
     * @return A string URL of the web service.
     */
    public String getAuthWebServiceURL() {
        return this.authWebServiceURL;
    }
    
    /** Set the location of the authentication web service.
     *
     * @param authWebServiceURL New URL of the web service.
     */
    public void setAuthWebServiceURL(String authWebServiceURL) {
        this.authWebServiceURL = authWebServiceURL;
    }
    
    /** Get the RDN attribute type we use for user ID authentication.
     *
     * @return The RDN attribute for user IDs.
     */
    public String getRdnAttributeType() {
        return this.rdnAttributeType;
    }
    
    /** Set the RDN attribute type for user ID authentication.
     *
     * @param rdnAttributeType The new attribute type to look for when authenticating users.
     */
    public void setRdnAttributeType(String rdnAttributeType) {
        this.rdnAttributeType = rdnAttributeType;
    }

    /** Get the length of the check digit string.
     *
     * @return The length of the check digit string.
     */
    public int getCheckDigitLength() {
        return this.checkDigitLength;
    }
    
    /** Set the length of the check digit string.
     *
     * @param checkDigitLength The new length of the check digit string.
     */
    public void setCheckDigitLength(int checkDigitLength) {
        this.checkDigitLength = checkDigitLength;
    }

    /**
     * The initialization method that … uhm … 
     */
    public void init(DirectoryService directoryService) throws Exception {
        super.init(directoryService);
        this.directoryService = directoryService;
        this.soapService = new WsNewcompass(new URL(this.authWebServiceURL)); // DMCC's service name is stupid.
        LOG.debug("Created SOAP service {}", this.soapService);
        StringBuffer b = new StringBuffer(this.checkDigitLength);
        for (int i = 0; i < this.checkDigitLength; ++i)
            b.append('0');
        this.checkDigits = b.toString();
        LOG.debug("Created {} zeros for the check digits (ugh)", this.checkDigitLength);
    }

    /**
     * Tell if a username + password combo is authentic, as far as the DMCC is concerned.
     *
     * @param username The username to authenticate.
     * @param password The password for the username.
     * @return True if authentic and valid, false if not.
     */
    public boolean isAuthentic(String username, String password) throws Exception {
        WsNewcompassSoap port = this.soapService.getWsNewcompassSoap12(); // DMCC's port names are stupid.
        String response = port.pwdVerification(username, password, this.checkDigits);
        LOG.debug("DMCC says that for username {} and password [REDACTED] the response is {}", username, response);
        return "valid".equals(response);
    }

    /**
     * Intercept the "bind" call and attempt to validate the password with the DMCC.  If success,
     * no need to continue the chain.  If not, then perhaps something else in the chain can do it.
     *
     * @param next Next interceptor in the chain.
     * @param opContext Context for the bind operation.
     * @throws Exception Should something—anything—go wrong.
     */
    @Override
    public void bind(NextInterceptor next, BindOperationContext opContext) throws Exception {
        // TODO: to be really anal retentive, we should check the entire DN and ensure it makes sense
        // for the DMCC to authenticate, on the off chance that "uid=dcrichto,ou=Sales,o=Amazon,c=US"
        // happens to authenticate with our LDAP server.
        try {
            LOG.debug("★ In DMCCAuthenticationInterceptor's bind");
            DN dn = opContext.getDn();
            RDN rdn = dn.getRdn();
            if (!rdn.getNormType().equals(this.rdnAttributeType))
                throw new DMCCAuthenticationException("Can't handle RDN attribute type " + rdn.getNormType() + "; expected "
                    + this.rdnAttributeType);
            String uid = rdn.getNormValue();
            String password = StringTools.utf8ToString(opContext.getCredentials());
            LOG.debug("Got uid '{}' with password 'NOPE! Not gonna show ya!'", uid);
            if (!this.isAuthentic(uid, password))
                throw new DMCCAuthenticationException("Uid '" + uid + "' and password not valid per DMCC");
            LOG.debug("Guess what: your DMCC username & password are OK!");
            LdapPrincipal principal = new LdapPrincipal(opContext.getDn(), AuthenticationLevel.SIMPLE);
            CoreSession session = new DefaultCoreSession(principal, this.directoryService);
            opContext.setSession(session);
            opContext.setCredentials(null);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // Can't authenticate via the DMCC.  Let another interceptor try.
            LOG.debug("Unable to authenticate with DMCC: {}, {}", ex.getClass().getName(), ex.getMessage());
            next.bind(opContext);
        }
    }
    
    
}
