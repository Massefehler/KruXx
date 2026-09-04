package app.kreate.android.service.player

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporarily filters notification sounds while KruXx is actually playing music.
 *
 * Android 15+ turns the calls below into an app-owned, implicit DND rule. Older Android
 * versions still expose only the global interruption filter, so the previous filter and
 * policy are persisted before they are changed and restored on pause, stop, service shutdown,
 * app startup, or a handled crash.
 */
class PlaybackNotificationSilencer( context: Context ) {

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService( NotificationManager::class.java )
    private val state = appContext.getSharedPreferences( STATE_FILE, Context.MODE_PRIVATE )

    /** True only when this object successfully applied the currently persisted state. */
    private var appliedInThisProcess = false

    fun update( isPlaying: Boolean, isEnabled: Boolean ) {
        runSafely( "Unable to update the playback notification rule" ) {
            synchronized( LOCK ) {
                if( isPlaying && isEnabled )
                    activateLocked()
                else
                    restoreLocked()
            }
        }
    }

    /**
     * Reconciles state after the user grants or revokes Notification Policy access.
     * A stale state is restored before a new playback state is applied.
     */
    fun onPolicyAccessChanged( isPlaying: Boolean, isEnabled: Boolean ) {
        runSafely( "Unable to reconcile Notification Policy access" ) {
            synchronized( LOCK ) {
                if( !hasPolicyAccess( notificationManager ) ) {
                    appliedInThisProcess = false
                    return@synchronized
                }

                if( state.getBoolean( KEY_ACTIVE, false ) && !appliedInThisProcess )
                    restoreLocked()

                if( isPlaying && isEnabled )
                    activateLocked()
                else
                    restoreLocked()
            }
        }
    }

    fun release() {
        runSafely( "Unable to release the playback notification rule" ) {
            synchronized( LOCK ) {
                restoreLocked()
            }
        }
    }

    private fun activateLocked() {
        val manager = notificationManager ?: return
        if( !hasPolicyAccess( manager ) ) return

        // A previous process may have died before it could restore the system state.
        if( state.getBoolean( KEY_ACTIVE, false ) ) {
            if( appliedInThisProcess ) return
            if( !restoreLocked() ) return
        }

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM )
            activateImplicitRuleLocked( manager )
        else
            activateLegacyFilterLocked( manager )
    }

    private fun activateImplicitRuleLocked( manager: NotificationManager ) {
        if( !persistBaseState( STRATEGY_IMPLICIT_RULE ) ) return

        try {
            manager.notificationPolicy = playbackPolicy()
            manager.setInterruptionFilter( NotificationManager.INTERRUPTION_FILTER_PRIORITY )
            appliedInThisProcess = true
        } catch( error: RuntimeException ) {
            // The permission can be revoked while these two calls are in flight.
            val disabled = runCatching {
                manager.setInterruptionFilter( NotificationManager.INTERRUPTION_FILTER_ALL )
            }.isSuccess
            if( disabled ) clearStateLocked()
            Log.w( TAG, "Unable to activate the playback notification rule", error )
        }
    }

    private fun activateLegacyFilterLocked( manager: NotificationManager ) {
        val previousFilter = runCatching { manager.currentInterruptionFilter }
            .getOrElse {
                Log.w( TAG, "Unable to read the current interruption filter", it )
                return
            }

        // Never replace a DND mode that the user (or another app) already enabled.
        if( previousFilter != NotificationManager.INTERRUPTION_FILTER_ALL ) return

        val previousPolicy = runCatching { manager.notificationPolicy }
            .getOrElse {
                Log.w( TAG, "Unable to read the current notification policy", it )
                return
            }
        val requestedPolicy = playbackPolicy()

        if( !persistLegacyState( previousFilter, previousPolicy, requestedPolicy ) ) return

        try {
            manager.notificationPolicy = requestedPolicy

            // Some Android versions normalize unsupported policy bits. Persist what the
            // framework actually accepted so a later restore can detect user changes.
            val appliedPolicy = runCatching { manager.notificationPolicy }
                .getOrDefault( requestedPolicy )
            if( !writePolicy( state.edit(), APPLIED_POLICY_PREFIX, appliedPolicy ).commit() ) {
                manager.notificationPolicy = previousPolicy
                clearStateLocked()
                return
            }

            manager.setInterruptionFilter( NotificationManager.INTERRUPTION_FILTER_PRIORITY )
            appliedInThisProcess = true
        } catch( error: RuntimeException ) {
            Log.w( TAG, "Unable to activate the legacy playback notification filter", error )
            restoreLegacyFilterLocked( manager )
        }
    }

    /** Returns true when no persisted active state remains. */
    private fun restoreLocked(): Boolean {
        if( !state.getBoolean( KEY_ACTIVE, false ) ) {
            appliedInThisProcess = false
            return true
        }

        if( !stateBelongsToThisDevice() ) {
            clearStateLocked()
            return true
        }

        val manager = notificationManager ?: return false
        if( !hasPolicyAccess( manager ) ) return false

        return when( state.getInt( KEY_STRATEGY, STRATEGY_UNKNOWN ) ) {
            STRATEGY_IMPLICIT_RULE -> restoreImplicitRuleLocked( manager )
            STRATEGY_LEGACY_FILTER -> {
                // After an OS upgrade to Android 15+, KruXx is no longer allowed to restore
                // the old global policy. Deactivate any app-owned implicit rule and forget the
                // obsolete snapshot; Android owns migration of the former global DND state.
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM )
                    restoreImplicitRuleLocked( manager )
                else
                    restoreLegacyFilterLocked( manager )
            }
            else -> {
                clearStateLocked()
                true
            }
        }
    }

    private fun restoreImplicitRuleLocked( manager: NotificationManager ): Boolean =
        try {
            // On Android 15+ this deactivates only KruXx's implicit rule; it does not turn off
            // a DND mode or rule selected by the user.
            manager.setInterruptionFilter( NotificationManager.INTERRUPTION_FILTER_ALL )
            clearStateLocked()
            true
        } catch( error: RuntimeException ) {
            appliedInThisProcess = false
            Log.w( TAG, "Unable to deactivate the playback notification rule", error )
            false
        }

    private fun restoreLegacyFilterLocked( manager: NotificationManager ): Boolean {
        val previousPolicy = readPolicy( PREVIOUS_POLICY_PREFIX ) ?: return false
        val appliedPolicy = readPolicy( APPLIED_POLICY_PREFIX ) ?: return false
        val previousFilter = state.getInt(
            KEY_PREVIOUS_FILTER,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        )

        val currentFilter = runCatching { manager.currentInterruptionFilter }
            .getOrElse {
                Log.w( TAG, "Unable to read the interruption filter during restore", it )
                return false
            }
        val currentPolicy = runCatching { manager.notificationPolicy }
            .getOrElse {
                Log.w( TAG, "Unable to read the notification policy during restore", it )
                return false
            }

        if( !policiesEqual( currentPolicy, appliedPolicy ) ) {
            // The policy was changed after KruXx applied its snapshot. Treat that as an explicit
            // user/system decision and do not overwrite it.
            clearStateLocked()
            return true
        }

        try {
            // Restore the filter first. If restoring the policy subsequently fails, notification
            // sounds are no longer suppressed and the persisted snapshot can be retried later.
            if( currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
                previousFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN )
                manager.setInterruptionFilter( previousFilter )

            manager.notificationPolicy = previousPolicy
            clearStateLocked()
            return true
        } catch( error: RuntimeException ) {
            appliedInThisProcess = false
            Log.w( TAG, "Unable to restore the previous notification state", error )
            return false
        }
    }

    private fun persistBaseState( strategy: Int ): Boolean =
        state.edit()
            .clear()
            .putBoolean( KEY_ACTIVE, true )
            .putInt( KEY_STRATEGY, strategy )
            .putString( KEY_DEVICE_ID, deviceId() )
            .commit()

    private fun persistLegacyState(
        previousFilter: Int,
        previousPolicy: NotificationManager.Policy,
        requestedPolicy: NotificationManager.Policy
    ): Boolean {
        val editor = state.edit()
            .clear()
            .putBoolean( KEY_ACTIVE, true )
            .putInt( KEY_STRATEGY, STRATEGY_LEGACY_FILTER )
            .putInt( KEY_PREVIOUS_FILTER, previousFilter )
            .putString( KEY_DEVICE_ID, deviceId() )

        writePolicy( editor, PREVIOUS_POLICY_PREFIX, previousPolicy )
        writePolicy( editor, APPLIED_POLICY_PREFIX, requestedPolicy )
        return editor.commit()
    }

    private fun writePolicy(
        editor: SharedPreferences.Editor,
        prefix: String,
        policy: NotificationManager.Policy
    ): SharedPreferences.Editor {
        editor.putInt( "$prefix.categories", policy.priorityCategories )
        editor.putInt( "$prefix.callSenders", policy.priorityCallSenders )
        editor.putInt( "$prefix.messageSenders", policy.priorityMessageSenders )
        editor.putInt(
            "$prefix.visualEffects",
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
                policy.suppressedVisualEffects
            else
                0
        )
        editor.putInt(
            "$prefix.conversationSenders",
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R )
                policy.priorityConversationSenders
            else
                CONVERSATION_SENDERS_NONE
        )
        return editor
    }

    private fun readPolicy( prefix: String ): NotificationManager.Policy? {
        val categoryKey = "$prefix.categories"
        if( !state.contains( categoryKey ) ) return null

        return createPolicy(
            categories = state.getInt( categoryKey, 0 ),
            callSenders = state.getInt(
                "$prefix.callSenders",
                NotificationManager.Policy.PRIORITY_SENDERS_ANY
            ),
            messageSenders = state.getInt(
                "$prefix.messageSenders",
                NotificationManager.Policy.PRIORITY_SENDERS_ANY
            ),
            visualEffects = state.getInt( "$prefix.visualEffects", 0 ),
            conversationSenders = state.getInt(
                "$prefix.conversationSenders",
                CONVERSATION_SENDERS_NONE
            )
        )
    }

    private fun playbackPolicy(): NotificationManager.Policy {
        var categories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ) {
            categories = categories or
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
        }
        val suppressedVisualEffects = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                @Suppress("DEPRECATION")
                NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON
            }
            else -> 0
        }

        return createPolicy(
            categories = categories,
            callSenders = NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            messageSenders = NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            visualEffects = suppressedVisualEffects,
            conversationSenders = CONVERSATION_SENDERS_NONE
        )
    }

    private fun createPolicy(
        categories: Int,
        callSenders: Int,
        messageSenders: Int,
        visualEffects: Int,
        conversationSenders: Int
    ): NotificationManager.Policy = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> NotificationManager.Policy(
            categories,
            callSenders,
            messageSenders,
            visualEffects,
            conversationSenders
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> NotificationManager.Policy(
            categories,
            callSenders,
            messageSenders,
            visualEffects
        )
        else -> NotificationManager.Policy( categories, callSenders, messageSenders )
    }

    private fun policiesEqual(
        first: NotificationManager.Policy,
        second: NotificationManager.Policy
    ): Boolean {
        if( first.priorityCategories != second.priorityCategories ||
            first.priorityCallSenders != second.priorityCallSenders ||
            first.priorityMessageSenders != second.priorityMessageSenders )
            return false

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            first.suppressedVisualEffects != second.suppressedVisualEffects )
            return false

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            first.priorityConversationSenders == second.priorityConversationSenders
    }

    private fun stateBelongsToThisDevice(): Boolean {
        val storedId = state.getString( KEY_DEVICE_ID, null )
        val currentId = deviceId()
        return storedId == null || currentId == null || storedId == currentId
    }

    private fun deviceId(): String? = runCatching {
        Settings.Secure.getString( appContext.contentResolver, Settings.Secure.ANDROID_ID )
    }.getOrNull()

    private fun clearStateLocked() {
        state.edit().clear().commit()
        appliedInThisProcess = false
    }

    private inline fun runSafely( message: String, action: () -> Unit ) {
        runCatching { action() }.onFailure { Log.w( TAG, message, it ) }
    }

    companion object {
        private const val TAG = "PlaybackNotifSilencer"
        private const val STATE_FILE = "kruxx_playback_notification_silencer"
        private const val KEY_ACTIVE = "active"
        private const val KEY_STRATEGY = "strategy"
        private const val KEY_PREVIOUS_FILTER = "previousFilter"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val PREVIOUS_POLICY_PREFIX = "previousPolicy"
        private const val APPLIED_POLICY_PREFIX = "appliedPolicy"

        private const val STRATEGY_UNKNOWN = 0
        private const val STRATEGY_LEGACY_FILTER = 1
        private const val STRATEGY_IMPLICIT_RULE = 2

        // NotificationManager.Policy.CONVERSATION_SENDERS_NONE, inlined for API 23-29 safety.
        private const val CONVERSATION_SENDERS_NONE = 3

        private val LOCK = Any()
        private val _embeddedVideoPlaybackActive = MutableStateFlow( false )

        /** Additional playback that happens in YouTubePlayerView instead of ExoPlayer. */
        internal val embeddedVideoPlaybackActive: StateFlow<Boolean> =
            _embeddedVideoPlaybackActive.asStateFlow()

        internal fun setEmbeddedVideoPlaybackActive( isActive: Boolean ) {
            _embeddedVideoPlaybackActive.value = isActive
        }

        fun hasPolicyAccess( context: Context ): Boolean = hasPolicyAccess(
            context.applicationContext.getSystemService( NotificationManager::class.java )
        )

        /** Restore a state left behind by a killed service or a handled app crash. */
        fun restoreStaleState( context: Context ) {
            runCatching {
                val silencer = PlaybackNotificationSilencer( context )
                synchronized( LOCK ) {
                    silencer.restoreLocked()
                }
            }.onFailure {
                Log.w( TAG, "Unable to restore a stale playback notification rule", it )
            }
        }

        private fun hasPolicyAccess( manager: NotificationManager? ): Boolean =
            manager != null && runCatching {
                manager.isNotificationPolicyAccessGranted
            }.getOrDefault( false )
    }
}
