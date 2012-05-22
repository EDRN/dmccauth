// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;

/** Exception indicating a problem while trying to authenticate with the DMCC.
 */
public class DMCCAuthenticationException extends LdapAuthenticationException {
    /** Make a new exception.
     *
     * @param message Handy exceptional message.
     */
    public DMCCAuthenticationException(String message) {
        super(message);
    }
    
    /** Make a new, message-less exception.
     */
    public DMCCAuthenticationException() {
        this(null);
    }
}
