#!/bin/sh -e

PATH=/usr/local/bin:${PATH}
export PATH

ldapadd -x -w secret -D uid=admin,ou=system -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -f edrn.ldif 
ldapadd -x -w secret -D uid=admin,ou=system -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -f mcl.ldif 
