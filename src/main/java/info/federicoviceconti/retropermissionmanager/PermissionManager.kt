package info.federicoviceconti.retropermissionmanager

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.*

/**
 * PermissionManger allow you to handle permissions in a wasy way. This is a singleton class
 * @suppress NewApi is used to hide warning, cause before android M devices are supported
 *
 */
@SuppressLint("NewApi")
object PermissionManager {
    private const val TAG = "PermissionManager"
    private const val REQUEST_CODE = 10112
    const val PERMISSION_PREFIX = "android.permission."
    private lateinit var config: ConfigParameter

    private lateinit var onSuccessListener: OnSuccessListener
    private lateinit var onFailureListener: OnFailureListener
    private lateinit var onNeverAskAgainListener: OnNeverAskAgainListener
    private var allPermissionRecap: AllPermissionRecap? = null

    private const val MESSAGE_ERROR_MANIFEST: String = "---- FAIL ----\n\nDid you specified permission in your manifest?\n\n"
    private const val MESSAGE_SUCCESS_MANIFEST: String = "Permission are declared in the right manner"

    /**
     * You can call this when you have a refernce to an activity. First, it checks if all the
     * permissions are declared into AndroidManifest.xml, otherwise it will show a log.
     * This allow to show dialog, to ask the requested permissions for Android M or major android
     * version.
     *
     * For device that are older than Android L, we have the same behaviour, but all requested
     * permission are true by default.
     *
     * @param config see @link{ConfigParameter} class to have more details
     * @param onSuccessListener called when the specific permission is grant
     * @param onFailureListener called when the specific permission is denied
     * @param onNeverAskAgainListener call when 'never ask' is checked
     * @param allPermissionRecap not require to be @NotNull, if is not null, it will be called when
     * all the operation are completed. It cointains the entire list of all required permissions
     */
    fun requestPermission(config: ConfigParameter,
                          onSuccessListener: OnSuccessListener,
                          onFailureListener: OnFailureListener,
                          onNeverAskAgainListener: OnNeverAskAgainListener,
                          allPermissionRecap: AllPermissionRecap?) {
        Log.d(TAG, if (!hasManifestDeclaredPermissions(config)) MESSAGE_ERROR_MANIFEST else MESSAGE_SUCCESS_MANIFEST)

        this.onSuccessListener = onSuccessListener
        this.onFailureListener = onFailureListener
        this.onNeverAskAgainListener = onNeverAskAgainListener
        this.allPermissionRecap = allPermissionRecap
        this.config = config

        if (config.isAfterOrEqualAndroidM) {
            config.activity.requestPermissions(config.getPermissionsArray(), REQUEST_CODE)
        } else {
            handleBeforeM()
        }
    }

    private fun handleBeforeM() {
        config.requestedPermissionTypes.forEach { onSuccessListener.onOperationCompleted(it) }
        allPermissionRecap?.onAllOperationCompleted(convertToList(config.requestedPermissionTypes))
    }

    private fun convertToList(requestedPermissionTypes: ArrayList<PermissionType>): List<Pair<PermissionType, Boolean>> {
        val convertedList: MutableList<Pair<PermissionType, Boolean>> = mutableListOf()
        requestedPermissionTypes.forEach { convertedList.add(Pair(it, true)) }

        return convertedList
    }

    /**
     * Checks if all permissions are declared into AndroidManifest.xml
     *
     * @param config used to get the permissions list
     *
     * @return boolean, if permissions are declared into manifest
     */
    private fun hasManifestDeclaredPermissions(config: ConfigParameter): Boolean {
        val activity = config.activity
        val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
        val permissionManifest = packageInfo.requestedPermissions

        var found = true

        if(permissionManifest != null && permissionManifest.isNotEmpty())
            for (permissionRequested in config.getPermissionsArray()) {
                if(!permissionManifest.contains(permissionRequested)) {
                    found = false
                    break
                }
            }
        else found = false

        return found
    }

    /**
     * REQUIRED into your activity.
     *
     * It handles the permission request by REQUEST_CODE and call specific listener
     *
     * @param permissionResult the activity's data when onPermissionResult callback is called
     */
    fun onRequestPermissionsResult(permissionResult: PermissionResult) {
        if(permissionResult.requestCode == REQUEST_CODE) {
            val list: MutableList<Pair<PermissionType, Boolean>> = mutableListOf()

            for((index, permission) in permissionResult.permissions.withIndex()) {
                val type = PermissionType.valueOf(permission.getPermissionSuffix())
                val grant = permissionResult.grantResult[index]
                val isGrant = isPermissionGranted(grant)

                when {
                    isGrant -> onSuccessListener.onOperationCompleted(type)
                    config.activity.shouldShowRequestPermissionRationale(permission) -> onFailureListener.onOperationCompleted(type)
                    else -> onNeverAskAgainListener.onOperationCompleted(type)
                }

                list.add(Pair(type, isGrant))
            }

            allPermissionRecap?.onAllOperationCompleted(list)
        }
    }

    data class PermissionResult(val requestCode: Int, val permissions: Array<out String>, val grantResult: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PermissionResult

            if (requestCode != other.requestCode) return false
            if (!Arrays.equals(permissions, other.permissions)) return false
            if (!Arrays.equals(grantResult, other.grantResult)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = requestCode
            result = 31 * result + Arrays.hashCode(permissions)
            result = 31 * result + Arrays.hashCode(grantResult)
            return result
        }
    }

    /**
     * Specify this parameters to handle permission requests
     *
     * @constructor takes a list of permission type, which are the permission requested and the current activity
     * to call specific method
     *
     * @param requestedPermissionTypes the permissions requested
     * @param activity current, which is used to call specific permission method
     */
    class ConfigParameter(val requestedPermissionTypes: ArrayList<PermissionType>, val activity: Activity) {
        internal val isAfterOrEqualAndroidM = isAfterOrEqualM()

        internal fun getPermissionsArray(): Array<String?> {
            val array = arrayOfNulls<String>(requestedPermissionTypes.size)

            for ((index, req) in requestedPermissionTypes.withIndex()) {
                array[index] = "$PERMISSION_PREFIX${req.name}"
            }

            return array
        }

    }

    interface PermissionListener { fun onOperationCompleted(type: PermissionType) }
    interface OnSuccessListener: PermissionListener
    interface OnFailureListener: PermissionListener
    interface OnNeverAskAgainListener: PermissionListener
    interface AllPermissionRecap { fun onAllOperationCompleted(listPermissions: List<Pair<PermissionType, Boolean>>) }

    /**
     * check if permission is grant or denied
     *
     * @return boolean grant or denied
     */
    private fun isPermissionGranted(x: Int) = x == PackageManager.PERMISSION_GRANTED

    /**
     * get the permission name without prefix (see PERMISSION_PREFIX constant)
     */
    private fun String.getPermissionSuffix(): String = this.replace(PERMISSION_PREFIX, "")

    /**
     * version check, if is equal or after android marshmallow
     */
    private fun isAfterOrEqualM() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}

/**
 * This enum classified all the allowed permissions into Android
 *
 */
enum class PermissionType {
    ACCESS_CACHE_FILESYSTEM,
    ACCESS_CHECKIN_PROPERTIES,
    ACCESS_COARSE_LOCATION,
    ACCESS_CONTENT_PROVIDERS_EXTERNALLY,
    ACCESS_DRM_CERTIFICATES,
    ACCESS_FINE_LOCATION,
    ACCESS_FM_RADIO,
    ACCESS_IMS_CALL_SERVICE,
    ACCESS_INPUT_FLINGER,
    ACCESS_KEYGUARD_SECURE_STORAGE,
    ACCESS_LOCATION_EXTRA_COMMANDS,
    ACCESS_MOCK_LOCATION,
    ACCESS_MTP,
    ACCESS_NETWORK_CONDITIONS,
    ACCESS_NETWORK_STATE,
    ACCESS_NOTIFICATIONS,
    ACCESS_NOTIFICATION_POLICY,
    ACCESS_PDB_STATE,
    ACCESS_SURFACE_FLINGER,
    ACCESS_VOICE_INTERACTION_SERVICE,
    ACCESS_WIFI_STATE,
    ACCESS_WIMAX_STATE,
    ACCOUNT_MANAGER,
    ADD_VOICEMAIL,
    ALLOW_ANY_CODEC_FOR_PLAYBACK,
    ASEC_ACCESS,
    ASEC_CREATE,
    ASEC_DESTROY,
    ASEC_MOUNT_UNMOUNT,
    ASEC_RENAME,
    AUTHENTICATE_ACCOUNTS,
    BACKUP,
    BATTERY_STATS,
    BIND_ACCESSIBILITY_SERVICE,
    BIND_APPWIDGET,
    BIND_CARRIER_MESSAGING_SERVICE,
    BIND_CARRIER_SERVICES,
    BIND_CHOOSER_TARGET_SERVICE,
    BIND_CONDITION_PROVIDER_SERVICE,
    BIND_CONNECTION_SERVICE,
    BIND_DEVICE_ADMIN,
    BIND_DIRECTORY_SEARCH,
    BIND_DREAM_SERVICE,
    BIND_INCALL_SERVICE,
    BIND_INPUT_METHOD,
    BIND_INTENT_FILTER_VERIFIER,
    BIND_JOB_SERVICE,
    BIND_KEYGUARD_APPWIDGET,
    BIND_MIDI_DEVICE_SERVICE,
    BIND_NFC_SERVICE,
    BIND_NOTIFICATION_LISTENER_SERVICE,
    BIND_PACKAGE_VERIFIER,
    BIND_PRINT_SERVICE,
    BIND_PRINT_SPOOLER_SERVICE,
    BIND_REMOTEVIEWS,
    BIND_REMOTE_DISPLAY,
    BIND_ROUTE_PROVIDER,
    BIND_TELECOM_CONNECTION_SERVICE,
    BIND_TEXT_SERVICE,
    BIND_TRUST_AGENT,
    BIND_TV_INPUT,
    BIND_VOICE_INTERACTION,
    BIND_VPN_SERVICE,
    BIND_WALLPAPER,
    BLUETOOTH,
    BLUETOOTH_ADMIN,
    BLUETOOTH_MAP,
    BLUETOOTH_PRIVILEGED,
    BLUETOOTH_STACK,
    BODY_SENSORS,
    BRICK,
    BROADCAST_NETWORK_PRIVILEGED,
    BROADCAST_PACKAGE_REMOVED,
    BROADCAST_SMS,
    BROADCAST_STICKY,
    BROADCAST_WAP_PUSH,
    C2D_MESSAGE,
    CALL_PHONE,
    CALL_PRIVILEGED,
    CAMERA,
    CAMERA_DISABLE_TRANSMIT_LED,
    CAMERA_SEND_SYSTEM_EVENTS,
    CAPTURE_AUDIO_HOTWORD,
    CAPTURE_AUDIO_OUTPUT,
    CAPTURE_SECURE_VIDEO_OUTPUT,
    CAPTURE_TV_INPUT,
    CAPTURE_VIDEO_OUTPUT,
    CARRIER_FILTER_SMS,
    CHANGE_APP_IDLE_STATE,
    CHANGE_BACKGROUND_DATA_SETTING,
    CHANGE_COMPONENT_ENABLED_STATE,
    CHANGE_CONFIGURATION,
    CHANGE_DEVICE_IDLE_TEMP_WHITELIST,
    CHANGE_NETWORK_STATE,
    CHANGE_WIFI_MULTICAST_STATE,
    CHANGE_WIFI_STATE,
    CHANGE_WIMAX_STATE,
    CLEAR_APP_CACHE,
    CLEAR_APP_USER_DATA,
    CONFIGURE_DISPLAY_COLOR_TRANSFORM,
    CONFIGURE_WIFI_DISPLAY,
    CONFIRM_FULL_BACKUP,
    CONNECTIVITY_INTERNAL,
    CONTROL_INCALL_EXPERIENCE,
    CONTROL_KEYGUARD,
    CONTROL_LOCATION_UPDATES,
    CONTROL_VPN,
    CONTROL_WIFI_DISPLAY,
    COPY_PROTECTED_DATA,
    CREATE_USERS,
    CRYPT_KEEPER,
    DELETE_CACHE_FILES,
    DELETE_PACKAGES,
    DEVICE_POWER,
    DIAGNOSTIC,
    DISABLE_KEYGUARD,
    DISPATCH_NFC_MESSAGE,
    DUMP,
    DVB_DEVICE,
    EXPAND_STATUS_BAR,
    FACTORY_TEST,
    FILTER_EVENTS,
    FLASHLIGHT,
    FORCE_BACK,
    FORCE_STOP_PACKAGES,
    FRAME_STATS,
    FREEZE_SCREEN,
    GET_ACCOUNTS,
    GET_ACCOUNTS_PRIVILEGED,
    GET_APP_OPS_STATS,
    GET_DETAILED_TASKS,
    GET_PACKAGE_IMPORTANCE,
    GET_PACKAGE_SIZE,
    GET_TASKS,
    GET_TOP_ACTIVITY_INFO,
    GLOBAL_SEARCH,
    GLOBAL_SEARCH_CONTROL,
    GRANT_RUNTIME_PERMISSIONS,
    HARDWARE_TEST,
    HDMI_CEC,
    INJECT_EVENTS,
    INSTALL_GRANT_RUNTIME_PERMISSIONS,
    INSTALL_LOCATION_PROVIDER,
    INSTALL_PACKAGES,
    INSTALL_SHORTCUT,
    INTENT_FILTER_VERIFICATION_AGENT,
    INTERACT_ACROSS_USERS,
    INTERACT_ACROSS_USERS_FULL,
    INTERNAL_SYSTEM_WINDOW,
    INTERNET,
    INVOKE_CARRIER_SETUP,
    KILL_BACKGROUND_PROCESSES,
    KILL_UID,
    LAUNCH_TRUST_AGENT_SETTINGS,
    LOCAL_MAC_ADDRESS,
    LOCATION_HARDWARE,
    LOOP_RADIO,
    MANAGE_ACCOUNTS,
    MANAGE_ACTIVITY_STACKS,
    MANAGE_APP_TOKENS,
    MANAGE_CA_CERTIFICATES,
    MANAGE_DEVICE_ADMINS,
    MANAGE_DOCUMENTS,
    MANAGE_FINGERPRINT,
    MANAGE_MEDIA_PROJECTION,
    MANAGE_NETWORK_POLICY,
    MANAGE_PROFILE_AND_DEVICE_OWNERS,
    MANAGE_USB,
    MANAGE_USERS,
    MANAGE_VOICE_KEYPHRASES,
    MASTER_CLEAR,
    MEDIA_CONTENT_CONTROL,
    MODIFY_APPWIDGET_BIND_PERMISSIONS,
    MODIFY_AUDIO_ROUTING,
    MODIFY_AUDIO_SETTINGS,
    MODIFY_NETWORK_ACCOUNTING,
    MODIFY_PARENTAL_CONTROLS,
    MODIFY_PHONE_STATE,
    MOUNT_FORMAT_FILESYSTEMS,
    MOUNT_UNMOUNT_FILESYSTEMS,
    MOVE_PACKAGE,
    NET_ADMIN,
    NET_TUNNELING,
    NFC,
    NFC_HANDOVER_STATUS,
    NOTIFY_PENDING_SYSTEM_UPDATE
}