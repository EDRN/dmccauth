/* coding: utf-8 */

#include "portable.h"
#include "slap.h"
#include "slap-config.h"
#include "ldif.h"
#include <stdio.h>
#include <ac/string.h>
#include <ac/socket.h>
#include <errno.h>

#ifdef SLAPD_DMCCAUTH

/* DEBUG_DMCCAUTH
 * --------------
 *
 * This debug level has the same value as LDAP_DEBUG_CACHE which is no longer used—so I'm taking it over.
 * To get this logging, start `slapd` with `-d 4096.
 */
#define DEBUG_DMCCAUTH 0x1000


/* Information about this overlay, dmccauth 
 * ----------------------------------------
 *
 * The `slap_overinst` is used by OpenLDAP for the overlay instance.
 * The `dmccauth_data` holds private information used by an instance of the overlay.
  */
static slap_overinst dmccauth_overlay;

typedef struct dmccauth_data {
    ldap_pvt_thread_mutex_t dmcc_mutex;
    char* dmcc_socketpath;
} dmccauth_data;


/* Schemata
 * --------
 *
 * This lets the LDIF configuration for OpenLDAP tell us where the socket is located.
 */
static ConfigTable dmccauthcfg[] = {
    { "dmccauth", "filename", 2, 2, 0,
      ARG_STRING|ARG_OFFSET,
      (void *)offsetof(dmccauth_data, dmcc_socketpath),
      "( 1.3.6.1.4.1.1306.20.2.1 NAME 'olcDmccauthFile' "
      "DESC 'Path to relay socket' "
      "EQUALITY caseExactMatch "
      "SYNTAX OMsDirectoryString )", NULL, NULL },
    { NULL, NULL, 0, 0, 0, ARG_IGNORED }
};

static ConfigOCs dmccauthocs[] = {
    { "( 1.3.6.1.4.1.1306.20.5.1 "
      "NAME 'olcDmccauthConfig' "
      "DESC 'Dmccauth configuration' "
      "SUP olcOverlayConfig "
      "MAY ( olcDmccauthFile ) )",
      Cft_Overlay, dmccauthcfg },
    { NULL, 0, NULL }
};

const char* _default_socketpath = "/tmp/dmcc.socket";


/* Write to a file descriptor
 * --------------------------
 *
 * Write the given character `buf` to `fd`, repeating as often as needed to ensure it's all written out
 * and return 0 on success, -1 on error (leaving `errno` set).
 */
static int do_write(int fd, char* buf) {
    int left = strlen(buf);
    int index = 0;
    int rc;
    while (left > 0) {
        rc = write(fd, (void*) buf + index, left);
        if (rc == -1)
            return -1;
        index += rc;
        left -= rc;
    }
    return 0;
}


/* Database Initialization
 * -----------------------
 *
 * When our database backend is initialized, we perform these steps.
 */
static int initialize_db(BackendDB* be, ConfigReply* cr) {
    slap_overinst* on = (slap_overinst*) be->bd_info;
    dmccauth_data* data = ch_malloc(sizeof(dmccauth_data));
    on->on_bi.bi_private = data;
    ldap_pvt_thread_mutex_init(&data->dmcc_mutex);
    return 0;
}


/* Database Destruction
 * --------------------
 *
 * When our database backend is shut down, we do some clean up.
 */
static int destroy_db(BackendDB* be, ConfigReply* cr) {
    slap_overinst* on = (slap_overinst*) be->bd_info;
    dmccauth_data* data = on->on_bi.bi_private;
    ldap_pvt_thread_mutex_destroy(&data->dmcc_mutex);
    ch_free(data->dmcc_socketpath);
    ch_free(data);
    return 0;
}


/* Authenticate if possible
 * ------------------------
 *
 * With the credentials in the given `op`, try to authenticate them by writen the credentials to the
 * DMCC socket. It'll write back either a `1` for valid or `0` for not. In the case of `0`, let other
 * overlays and the backend try to authenticate. But for a `1`, we can stop here and say success.
 */
static int authenticate_with_dmcc(Operation* op, SlapReply* rs) {
    int rc;
    int socketnum;
    struct sockaddr_un socket_address;
    char* dn = BER_BVISNULL(&op->o_req_ndn)? NULL : op->o_req_ndn.bv_val;
    char* password = BER_BVISNULL(&op->orb_cred)? NULL: op->orb_cred.bv_val;
    char* message;
    char response;
    slap_overinst* on = (slap_overinst*) op->o_bd->bd_info;
    dmccauth_data* data = on->on_bi.bi_private;

    if (be_isroot(op)) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Root user, not doing anything\n");
        return SLAP_CB_CONTINUE;
    }

    if (dn == NULL || password == NULL) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Got a null dn or null password so punting\n");
        return SLAP_CB_CONTINUE;
    }

    socketnum = socket(AF_UNIX, SOCK_STREAM, 0);
    if (socketnum == -1) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_ERR, "Cannot create socket, errno=%d\n", errno);
        return SLAP_CB_CONTINUE;
    }


    socket_address.sun_family = AF_UNIX;
    ldap_pvt_thread_mutex_lock(&data->dmcc_mutex);
    if (data->dmcc_socketpath == NULL) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Socket path not in config so using default %s\n", _default_socketpath);
        strcpy(socket_address.sun_path, _default_socketpath);
    } else {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Socket path in config=%s\n", data->dmcc_socketpath);
        strcpy(socket_address.sun_path, data->dmcc_socketpath);
    }
    if (connect(socketnum, (const struct sockaddr*) &socket_address, sizeof(struct sockaddr_un)) == -1) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_ERR, "Cannot connect socket, errno=%d\n", errno);
        ldap_pvt_thread_mutex_unlock(&data->dmcc_mutex);
        return SLAP_CB_CONTINUE;
    }

    message = ch_malloc(strlen(dn) + strlen(password) + 3);
    sprintf(message, "%s\n%s\n", dn, password);
    if (do_write(socketnum, message) == -1) {
        Log(DEBUG_DMCCAUTH, LDAP_LEVEL_ERR, "Cannot write credentials to socket, errno=%d\n", errno);
        ch_free(message);
        ldap_pvt_thread_mutex_unlock(&data->dmcc_mutex);
        return SLAP_CB_CONTINUE;
    }
    ch_free(message);

    if ((rc = read(socketnum, &response, 1)) != 1) {
        if (rc == -1)
            Log(DEBUG_DMCCAUTH, LDAP_LEVEL_ERR, "Cannot read response from socket, errno=%d\n", errno);
        else
            Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Got strange response of %d bytes from socket\n", rc);
        ldap_pvt_thread_mutex_unlock(&data->dmcc_mutex);
        return SLAP_CB_CONTINUE;
    }
    ldap_pvt_thread_mutex_unlock(&data->dmcc_mutex);
    Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "Response from socket=%c\n", response);

    if (response == '1') {
        rs->sr_err = LDAP_SUCCESS;
        send_ldap_result(op, rs);
        return SLAP_CB_BYPASS;
    }
    return SLAP_CB_CONTINUE;
}


/* Initialize this overlay, dmccauth
 * ---------------------------------
 */
#if SLAPD_DMCCAUTH == SLAPD_MOD_DYNAMIC && defined(PIC)
static
#endif
int initialize_dmccauth() {
    int rc;

    dmccauth_overlay.on_bi.bi_type       = "dmccauth";
    dmccauth_overlay.on_bi.bi_flags      = SLAPO_BFLAG_SINGLE;
    dmccauth_overlay.on_bi.bi_db_init    = initialize_db;
    dmccauth_overlay.on_bi.bi_db_destroy = destroy_db;
    dmccauth_overlay.on_bi.bi_op_bind    = authenticate_with_dmcc;
    dmccauth_overlay.on_bi.bi_cf_ocs     = dmccauthocs;

    rc = config_register_schema(dmccauthcfg, dmccauthocs);
    Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "`dmccauth` overlay schema registration completed with code %d\n", rc);
    if (rc) return rc;

    rc = overlay_register(&dmccauth_overlay);
    Log(DEBUG_DMCCAUTH, LDAP_LEVEL_INFO, "`dmccauth` overlay registration completed with code %d\n", rc);
    return rc;
}


/* Initialize this dynamic module
 * ------------------------------
 */
#if SLAPD_DMCCAUTH == SLAPD_MOD_DYNAMIC && defined(PIC)
int init_module(int argc, char *argv[]) {
    return initialize_dmccauth();
}
#endif

#endif /* defined(SLAPD_DMCCAUTH) */
