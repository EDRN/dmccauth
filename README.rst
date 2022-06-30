**************************************************************
 DMCC Authentication Interceptor for Apache™ Directory Server
**************************************************************

1.  ``env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home mvn package``
2.  Install target/dmccauth-X.Y.Z.jar into $APACHEDS_HOME/lib/ext
3.  Edit the Apache DS server.xml and insert the following *before*
    ``<authenticationInterceptor/>``::
    
      <s:bean class='gov.nasa.jpl.edrn.dmcc.auth.DMCCAuthenticationInterceptor'/> 

    Assuming that ``s`` = http://www.springframework.org/schema/beans.

4.  Restart Apache™ DS.


