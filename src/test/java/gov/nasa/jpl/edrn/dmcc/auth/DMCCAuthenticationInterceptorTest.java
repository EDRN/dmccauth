// Copyright 2012 California Institute of Technology. ALL RIGHTS
// RESERVED. U.S. Government Sponsorship acknowledged.

package gov.nasa.jpl.edrn.dmcc.auth;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Unit test for the DMCC authentication interceptor.
 */
public class DMCCAuthenticationInterceptorTest {
    /** Test something—er, well, some THING.
     */
    @Test
    public void testSomething() throws Throwable {
        assertEquals(1, 1);
    }
    @Test
    public void testDefaultWebServiceURLValue() throws Throwable {
        DMCCAuthenticationInterceptor i = new DMCCAuthenticationInterceptor();
        assertEquals("https://www.compass.fhcrc.org/edrn_ws/ws_newcompass.asmx?WSDL", i.getAuthWebServiceURL());
    }
}
