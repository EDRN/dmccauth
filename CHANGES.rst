Changelog
=========

Issue identifiers below (listed as CA-n, where n is a number) refer to the
Informatics Center's issue tracker at:

    https://oodt.jpl.nasa.gov/jira/browse/CA
    
Thanks, Object Oriented Data Technology, for hosting it.


1.1.2 — Ontology 1.8
--------------------

The DMCC published an updated SOAP service to support EDRN Ontology 1.8.  This
includes no changes to the "pwdVerification" SOAP function, but because this is
SOAP, we have to rebuild all API stubs.  SOAP sucks.


1.1.1 — #&*% the DMCC
---------------------

• CA-994 - If DMCC web service is down, Apache DS immediately gives up; should
  try its own database
• CA-995 - DMCC changed XML namespace of its web service


1.1.0 — Substrate Upgrade
-------------------------

• This release uses ApacheDS 2.0 (and Apache Shared LDAP API 1.0) as its
  basis. This is necessary for CA-960, due to a bug in Apache DS; see
  https://issues.apache.org/jira/browse/DIRSERVER-1548.
• This release also attempts to use Maven's automated release mechanisms.


1.0.2 — Defaults Cleanup
------------------------

• This release fixes the default URL for the EDRN DMCC web service.


1.0.1 — Release Cleanup
-----------------------

• This release adds an assembly to the project so that distributions can
  be easily made.


1.0.0 — Initial Release
-----------------------

• First release.
