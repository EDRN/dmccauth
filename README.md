# ☎️ DMCC Authentication Overlay

The DMCC Authentication Overlay for [OpenLDAP](https://www.openldap.org/) provides password verification for members of the [Early Detection Research Network](https://edrn.nci.nih.gov/) using the DMCC's antediluvian [SOAP](https://www.infoq.com/articles/rest-soap/) service. These are the so-called "secure site" users, named for EDRN's "secure site" 🤭.

As an [OpenLDAP overlay](https://www.openldap.org/doc/admin26/overlays.html), the `dmccauth` overlay provides a "plug-in" that handles authentication for these EDRN "secure site" users. It requires the [JPL EDRN DMCC Password Relay](https://github.com/EDRN/jpl.edrn.dmcc.passwordrelay) in order to function.


## 💽 Installation into Production

To install this software, you need…


## 🧱 Building the Overlay

Building the DMCC Authentication Overlay requires the source code to OpenLDAP version 2.6 as well as a recent Linux operating system with various essential build tools (including `make`, `gcc`, `libssl`, `libsasl2`) and development headers and libraries. It also requires a dump of the EDRN (and for completeness, the MCL) directory trees from the current EDRN Directory Service.


### 💩 Dumping the Current Directory Trees

The first thing we need are dumps of the EDRN and MCL data from any currently running EDRN Directory Service. For EDRN, run:

    ldapsearch -x -W -H ldaps://edrn-ds.jpl.nasa.gov -D uid=admin,ou=system \
        -b dc=edrn,dc=jpl,dc=nasa,dc=gov -s sub '(objectClass=*)' > edrn.ldif

Then edit the `edrn.ldif` file and move the context entry `dc=edrn,dc=jpl,dc=nasa,dc=gov` to the top of the file. Next, for MCL, run:

    ldapsearch -x -W -H ldaps://edrn-ds.jpl.nasa.gov -D uid=admin,ou=system \
        -b o=MCL -s sub '(objectClass=*)' > mcl.ldif

You'll need to edit this file too and move three entries to the top of the file so they appear before all other entries in the following order:

1. `o=MCL`
2. `ou=users,o=MCL`
3. `ou=groups,o=MCL`


### 🗃 OpenLDAP

And we need the OpenLDAP source since OpenLDAP does not install a developer-friendly package (with header files, for example). Grab it:

    curl --location https://www.openldap.org/software/download/OpenLDAP/openldap-release/openldap-2.6.3.tgz | tar xzf -

Then build it:

    cd openldap-2.6.3
    ./configure \
        --enable-shared \
        --enable-dynamic \
        --enable-ldap=mod \
        --enable-mdb=mod \
        --enable-meta=mod \
        --enable-modules \
        --enable-null=mod \
        --enable-overlays=mod \
        --with-tls=openssl \
        --with-cyrus-sasl 
    make depend
    make
    sudo make install
    make clean
    cd ..
    mkdir -p var/openldap-data/system var/openldap-data/edrn var/openldap-data/mcl

The local `var` and `etc/slapd.d` directories are used to hold developer-friendly copies of the directory trees and configuration data.

### 🔌 Creating the Overlay Shared Object

To compile and link the `dmccauth` overlay, we need access to the OpenLDAP source code. Make a symlink then run `make`:

    ln -s openldap-2.6.3 openldap
    make

This will produce `dmccauth.o`, `dmccauth.lo`, and `dmccauth.la`. These files implement the overlay. The `edrn-slapd.ldif` file included references the `dmccauth.la` (which in turn references the others).


### 🚀 Starting the OpenLDAP Server, slapd

Run:

    ./start.sh

This creates the initial LDAP configuration in `etc/slapd.d` from `etc/ldif/edrn-slapd.ldif` (and its included files) as well as empty directories for the directory tree databases in `var`. It stays in the foreground.

Check it to see if it's working from another terminal session:

    ldapsearch  -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -x -b '' -s base '(objectclass=*)' namingContexts

You should get back:

-   `namingContexts: ou=system`
-   `namingContexts: dc=edrn,dc=jpl,dc=nasa,dc=gov`
-   `namingContexts: o=MCL`

Note that `./start.sh`—as a developer convenience-completely decimates and recreates the `var` and `etc/slapd.d` directories, so any changes made to running configuration or the directory databases (via `ldapmodify`, for example) are lost. If you just want to restart `slapd` without losing any changes to the directory tree—or, more likely, without having to repopulate their contents—run:

    /usr/local/libexec/slapd -d 4096 -F ${PWD}/etc/slapd.d -h 'ldap://0.0.0.0:2389 ldapi://%2Fhome%2Fkelly%2Fldapi'

Note that the debug level `4096` enables messages for the `dmccauth` overlay.


#### 🚛 Populating EDRN- and MCL-Specific Data

Using the dump files you made and edited earlier, load 'em up:

    ldapadd -x -w secret -D uid=admin,ou=system -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -f edrn.ldif 
    ldapadd -x -w secret -D uid=admin,ou=system -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -f mcl.ldif 

Or use the handy `./populate.sh` script.


### 🏃 Starting the Relay

Note that the `dmccauth` overlay must communicate with the [JPL EDRN DMCC Password Relay](https://github.com/EDRN/jpl.edrn.dmcc.passwordrelay) to work. On the same system running OpenLDAP, start the relay. By default, the relay opens a Unix domain socket in `/tmp/dmcc.socket`. But you can change the location with its `--socket` command-line option. Just don't forget to make the `olcDmccauthFile` in the OpenLDAP configuration match!


### 🩺 Test it Out

Using a known EDRN DMCC "secure site" username and password, run:

    ldapsearch -x -W -D uid=USERNAME,dc=edrn,dc=jpl,dc=nasa,dc=gov -H 'ldapi://%2Fhome%2Fkelly%2Fldapi' -b dc=edrn,dc=jpl,dc=nasa,dc=gov -s one '(uid=kelly)' dn

Replace `USERNAME` with a known "secure site" username and when prompted, enter its password. You should get back a single `dn` for user `kelly`. Try again with an improperly entered password to ensure you get `ldap_bind: Invalid credentials (49)`. Also, try with a known Informatics Center—_not_ "secure site"—username and password to ensure that succeeds too.

👉 **Note:** OpenLDAP's `{CRYPT}` password algorithm relies on the platform's `crypt(3)` library API which may vary from the Apache Directory Service's implementation. If you've loaded data from Apache Directory Service and the Informatics Center password uses `{CRYPT}`, you may get invalid credentials. Reset the password using `{SSHA}` to test for sure.

