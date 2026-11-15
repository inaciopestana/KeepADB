package de.hohnepeople.keepadb;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Fork addition: reports the current ADB endpoint to the webhook every {@link #INTERVAL_MS}, on
 * top of the report that already follows every network change (#867).
 *
 * <p>A scheduled report is useful when the destination only keeps messages for a limited time
 * (for example a public ntfy topic): without it, a stable network would leave the last message to
 * expire. The report itself is the existing manual-report path
 * ({@link KeepADBRegisterClient#reportNow}); it re-sends the verified Wi-Fi endpoint even when it
 * is unchanged, joins a POST that is already running, and does nothing while synchronization is
 * off or Wi-Fi is down.
 *
 * <p>The timer is an inexact {@link AlarmManager} alarm (no extra permission), so it also fires
 * while the device sleeps. It is a chain of one-shot alarms: the receiver arms the next one each
 * time one fires, and the monitoring service arms the first one when it starts. The chain ends
 * when the service is not meant to run any more.
 */
final class KeepADBPeriodicReport {
    /** Time between two scheduled reports. */
    static final long INTERVAL_MS = 4L * 60L * 60L * 1000L;

    private static final int REQUEST_CODE_PERIODIC_REPORT = 60;

    private KeepADBPeriodicReport() {}

    /**
     * Arms the next alarm unless one is already pending, so a service restart does not push the
     * due time back. After a reboot no alarm and no PendingIntent exist, so this arms a new one.
     */
    static void ensureArmed(Context context) {
        Context app = context.getApplicationContext();
        if (pendingIntent(app, PendingIntent.FLAG_NO_CREATE) != null) return;
        arm(app);
    }

    /** Arms the alarm {@link #INTERVAL_MS} from now, replacing a pending one. */
    static void arm(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        PendingIntent intent = pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarms == null || intent == null) return;
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS, intent);
    }

    /** Ends the chain: cancels the pending alarm and its PendingIntent (so {@link #ensureArmed} re-arms later). */
    static void cancel(Context context) {
        Context app = context.getApplicationContext();
        PendingIntent intent = pendingIntent(app, PendingIntent.FLAG_NO_CREATE);
        if (intent == null) return;
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(intent);
        intent.cancel();
    }

    private static PendingIntent pendingIntent(Context app, int creationFlag) {
        Intent intent = new Intent(app, KeepADBReceiver.class)
                .setAction(KeepADBReceiver.ACTION_PERIODIC_REPORT);
        return PendingIntent.getBroadcast(app, REQUEST_CODE_PERIODIC_REPORT, intent,
                creationFlag | PendingIntent.FLAG_IMMUTABLE);
    }
}
