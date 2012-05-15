// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

// Apache™ DS
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;

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
    private static final Logger LOG = LoggerFactory.getLogger(DMCCAuthenticationInterceptor.class)
    
    /**
     * Create a new instance of a DMCCAuthenticationInterceptor.
     * @see BaseInterceptor
     */
    public DMCCAuthenticationInterceptor() {
        super();
        // …?
    }

    /**
     * The initialization method …
     */
    public void init(DirectoryService directoryService) throws Throwable {
        super.init(directoryServices);
    }
    
    /**
     * The bind method…
     */
    public void bind(NextInterceptor next, BindOperationContext opContext) throws Throwable {
        LOG.info('IN MY AWESOME LOG');
        next.compare(next);
    }
}
