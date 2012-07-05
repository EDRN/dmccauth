// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Unit test for the DMCC authentication interceptor.
 */
public class DMCCAuthenticationInterceptorTest {
    /** Ensure the default authentication web service URL is what we expect.
     */
    @Test
    public void testDefaultWebServiceURLValue() throws Throwable {
        DMCCAuthenticationInterceptor i = new DMCCAuthenticationInterceptor();
        assertEquals("https://www.compass.fhcrc.org/edrn_ws/ws_newcompass.asmx?WSDL", i.getAuthWebServiceURL());
    }
}
