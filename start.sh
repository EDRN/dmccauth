#!/bin/sh -e
#
# Start the local slapd and populate it with all our configuration and directory data

PATH=/usr/local//bin:/usr/local/sbin:${PATH}
export PATH

[ -d etc/slapd.d ] || mkdir -p etc/slapd.d
[ -d var/openldap-data ] || mkdir -p var/openldap-data

# find etc/slapd.d var/openldap-data -type f -delete
# sbin/slapadd -n 0 -F etc/slapd.d -l etc/ldif/edrn-slapd.ldif
# exec libexec/slapd -d 1 -F ${PWD}/etc/slapd.d -h 'ldap://localhost:2389 ldapi:///'

find etc/slapd.d var/openldap-data -type f -delete
slapadd -d 64 -n 0 -F etc/slapd.d -l etc/ldif/edrn-slapd.ldif
exec /usr/local/libexec/slapd -d 4096 -F ${PWD}/etc/slapd.d -h 'ldap://0.0.0.0:2389 ldapi://%2Fhome%2Fkelly%2Fldapi'
