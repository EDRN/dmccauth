LDAP_SRC = openldap
#CFLAGS = -fPIC
LDAP_BUILD = $(LDAP_SRC)
LDAP_INC = -I$(LDAP_BUILD)/include -I$(LDAP_SRC)/include -I$(LDAP_SRC)/servers/slapd
LDAP_LIB = $(LDAP_BUILD)/libraries/libldap/libldap.la $(LDAP_BUILD)/libraries/liblber/liblber.la
LIBTOOL = $(LDAP_BUILD)/libtool
INSTALL = /usr/bin/install
CC = gcc
OPT = -g -O2
DEFS = -DSLAPD_DMCCAUTH=SLAPD_MOD_DYNAMIC
INCS = $(LDAP_INC)
# LIBS = $(LDAP_LIB) -lgsoap -lgsoapck -lgsoapssl -lssl -lcrypto
LIBS = $(LDAB_LIB)

PROGRAMS = dmccauth.la
MANPAGES = slapo-dmccauth.5
LTVER = 0:0:0

prefix = /usr/local
exec_prefix = $(prefix)
ldap_subdir = /openldap

libdir = $(exec_prefix)/lib
libexecdir = $(exec_prefix)/libexec
moduledir = $(libexecdir)$(ldap_subdir)
mandir = $(exec_prefix)/share/man
man5dir = $(mandir)/man5

.SUFFIXES: .c .o .lo

.c.lo:
	$(LIBTOOL) --mode=compile $(CC) $(CFLAGS) $(OPT) $(CPPFLAGS) $(DEFS) $(INCS) -c $<

all: $(PROGRAMS)

dmccauth.la: dmccauth.lo
	$(LIBTOOL) --mode=link $(CC) $(LDFLAGS) -version-info $(LTVER) -rpath $(moduledir) -module -o $@ $? $(LIBS)

clean:
	rm -rf *.o *.lo *.la .libs

install: install-lib install-man FORCE

install-lib: $(PROGRAMS)
	mkdir -p $(DESTDIR)$(moduledir)
	for p in $(PROGRAMS); do \
		$(LIBTOOL) --mode=install cp $$p $(DESTDIR)$(moduledir); \
	done

install-man: $(MANPAGES)
	mkdir -p $(DESTDIR)$(man5dir)
	$(INSTALL) -m 644 $(MANPAGES) $(DESTDIR)$(man5dir)


# dmccauth.la: dmccauth.lo dmccauth.lo soapClient.lo soapC.lo passwd.lo
# 	$(LIBTOOL) --mode=link $(CC) $(LDFLAGS) -version-info $(LTVER) -rpath $(moduledir) -module -o $@ $? $(LIBS)

# CC = cc
# CPPFLAGS = -I$(PWD)/openldap/include -I$(PWD)/openldap/servers/slapd -I/usr/local/include -I/usr/local/opt/openssl/include -DWITH_OPENSSL
# LDFLAGS = 

# all: dmccauth

# dmccauth: dmccauth.o soapClient.o soapC.o passwd.o
# 	$(CC) $(LDFLAGS) -o dmccauth dmccauth.o soapClient.o soapC.o passwd.o -lgsoap -lgsoapck -lgsoapssl -lssl -lcrypto

# passwd.lo: passwd.c soapH.h ws_USCOREnewcompassSoap.nsmap passwd.h

# dmccauth.lo: passwd.h

# dmcc.h: typemap.dat
# 	wsdl2h -c -O4 -o dmcc.h 'https://www.compass.fhcrc.org/edrn_ws/ws_newcompass.asmx?WSDL'

# soapC.c soapClient.c soapH.h soapStub.h ws_USCOREnewcompassSoap.nsmap: dmcc.h
# 	soapcpp2 -x -c -C -L dmcc.h

# %.o: %.c
# 	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< -o $@

# clean:
# 	-rm -f dmcc.h dmccauth soapH.h soapStub.h soapClient.c soapC.c ws_USCOREnewcompassSoap.nsmap *.o

.PHONY: clean all

