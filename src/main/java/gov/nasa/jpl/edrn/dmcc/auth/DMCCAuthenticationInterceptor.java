// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

// Apache™ DS
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.shared.DefaultCoreSession;

// Apache™ Shared LDAP API
import org.apache.directory.api.ldap.model.constants.AuthenticationLevel;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.util.Strings;

// DMCCrap
import org.fhcrc.compass.edrn_ws.ws_newcompass.WsNewcompass;
import org.fhcrc.compass.edrn_ws.ws_newcompass.WsNewcompassSoap;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.ws.WebServiceException;

/**
 * An interceptor that delegates authentication to the DMCC authentication web service,
 * and attempts to authenticate with that, if possible.  However, if it fails, it leaves
 * the state of authentication as unfinished so that the rest of Apache™ DS may attempt
 * to do it.
 */
public class DMCCAuthenticationInterceptor extends BaseInterceptor {
    /** Are we properly initialized? */
    private boolean initialized = false;

    /** Logging for debugging, informational messages… */
    private static final Logger LOG = LoggerFactory.getLogger(DMCCAuthenticationInterceptor.class);

    /** Where the authentication web service lives */
    private String authWebServiceURL = "https://www.compass.fhcrc.org/edrn_ws/ws_newcompass.asmx?WSDL";

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
     * Construct a DMCCAuthenticationInterceptor.
     */
    public DMCCAuthenticationInterceptor() {
        super("dmccAuthenticationInterceptor");
    }

    /**
     * The initialization method that … uhm … 
     */
    public void init(DirectoryService directoryService) throws LdapException {
        super.init(directoryService);
        try {
            this.directoryService = directoryService;
            LOG.debug("Trying to create SOAP at {}", this.authWebServiceURL);
            this.soapService = new WsNewcompass(new URL(this.authWebServiceURL)); // DMCC's service name is stupid.
            LOG.debug("Created SOAP service {}", this.soapService);
            StringBuffer b = new StringBuffer(this.checkDigitLength);
            for (int i = 0; i < this.checkDigitLength; ++i)
                b.append('0');
            this.checkDigits = b.toString();
            LOG.debug("Created {} zeros for the check digits (ugh)", this.checkDigitLength);
            this.initialized = true;
        } catch (WebServiceException ex) {
            LOG.debug("The exception class is '{}'' and message is '{}'", ex.getClass().getName(), ex.getMessage());
            LOG.error("DMCCAuthenticationInterceptor got an exception trying to create SOAP interface", ex);
            this.initialized = false;
        } catch (MalformedURLException ex) {
            throw new LdapException(ex);
        }
    }

    /**
     * Tell if a username + password combo is authentic, as far as the DMCC is concerned.
     *
     * @param username The username to authenticate.
     * @param password The password for the username.
     * @return True if authentic and valid, false if not.
     */
    public boolean isAuthentic(String username, String password) throws Exception {
        try {
            WsNewcompassSoap port = this.soapService.getWsNewcompassSoap12(); // DMCC's port names are stupid.
            String response = port.pwdVerification(username, password, this.checkDigits);
            LOG.debug("DMCC says that for username {} and password [REDACTED] the response is {}", username, response);
            return "valid".equals(response);
        } catch (RuntimeException ex) {
            // These should never be handled
            throw ex;
        } catch (Exception ex) {
            // SSL certificates at the DMCC are dodgy at best; treat as a soft error
            return false;
        }
    }

    /**
     * Intercept the "bind" call and attempt to validate the password with the DMCC.  If success,
     * no need to continue the chain.  If not, then perhaps something else in the chain can do it.
     *
     * @param bindContext Context for the bind operation.
     * @throws Exception Should something—anything—go wrong.
     */
    @Override
    public void bind(BindOperationContext bindContext) throws LdapException {
        // See if we got properly initialized.  If not, no point in trying.
        if (!this.initialized) {
            LOG.debug("DMCCAuthenticationInterceptor not initialized, so passing right onto the next interceptor");
            next(bindContext);
            return;
        }
        
        // TODO: to be really anal retentive, we should check the entire DN and ensure it makes sense
        // for the DMCC to authenticate, on the off chance that "uid=dcrichto,ou=Sales,o=Amazon,c=US"
        // happens to authenticate with our LDAP server.
        try {
            LOG.debug("★ In DMCCAuthenticationInterceptor's bind");
            Dn dn = bindContext.getDn();
            Rdn rdn = dn.getRdn();
            if (!rdn.getNormType().equals(this.rdnAttributeType))
                throw new DMCCAuthenticationException("Can't handle RDN attribute type " + rdn.getNormType() + "; expected "
                    + this.rdnAttributeType);
            String uid = rdn.getValue().toString();
            if (uid.equals("admin") || uid.equals("root")) {
                // Skip these
                LOG.debug("Skipping admin and root users; they will never need to be authenticated with SOAP");
                next(bindContext);
            } else {
                String password = Strings.utf8ToString(bindContext.getCredentials());
                LOG.debug("Got uid '{}' with password 'NOPE! Not gonna show ya!'", uid);
                if (!this.isAuthentic(uid, password))
                    throw new DMCCAuthenticationException("Uid '" + uid + "' and password not valid per DMCC");
                LOG.debug("Guess what: your DMCC username & password are OK!");
                LdapPrincipal principal = new LdapPrincipal(this.schemaManager, bindContext.getDn(), AuthenticationLevel.SIMPLE);
                CoreSession session = new DefaultCoreSession(principal, this.directoryService);
                bindContext.setSession(session);
                bindContext.setCredentials(null);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // Can't authenticate via the DMCC.  Let another interceptor try.
            LOG.debug("Unable to authenticate with DMCC: {}, {}", ex.getClass().getName(), ex.getMessage());
            next(bindContext);
        }
    }
}
