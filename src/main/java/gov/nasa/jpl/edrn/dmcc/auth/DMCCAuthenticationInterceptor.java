// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

// Apache™ DS
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.LdapPrincipal;

// Apache™ Shared LDAP API
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    /** What RDN attribute type to look for for user IDs? */
    private String rdnAttributeType = "0.9.2342.19200300.100.1.1";

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

    /**
     * The initialization method that … uhm … 
     */
    public void init(DirectoryService directoryService) throws Exception {
        super.init(directoryService);
        // Establish connection?
    }

    /**
     * Tell if a username + password combo is authentic, as far as the DMCC is concerned.
     *
     * @param username The username to authenticate.
     * @param password The password for the username.
     * @return True if authentic and valid, false if not.
     */
    public boolean isAuthentic(String username, String password) throws Exception {
        // TODO: implement this as a web service call
        return false;
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
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // Can't authenticate via the DMCC.  Let another interceptor try.
            LOG.debug("Unable to authenticate with DMCC: {}, {}", ex.getClass().getName(), ex.getMessage());
            next.bind(opContext);
        }
    }
}
