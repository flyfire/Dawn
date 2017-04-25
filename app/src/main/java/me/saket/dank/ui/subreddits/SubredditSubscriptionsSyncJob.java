package me.saket.dank.ui.subreddits;

import static me.saket.dank.utils.RxUtils.applySchedulersCompletable;
import static me.saket.dank.utils.RxUtils.doOnStartAndComplete;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.jakewharton.rxrelay2.BehaviorRelay;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import me.saket.dank.R;
import me.saket.dank.di.Dank;
import timber.log.Timber;

/**
 * Syncs user's subreddit subscriptions in background. This brings in user's new subscriptions + executes
 * any pending subscribe or unsubscribe actions.
 */
public class SubredditSubscriptionsSyncJob extends JobService {

    private static final int ID_RECURRING_JOB = 0;
    private static final int ID_ONE_TIME_JOB = 1;

    /**
     * Emits true when bookmarks are being fetched. False when the process completes.
     * (regardless of the outcome).
     */
    private static final BehaviorRelay<Boolean> progressSubject = BehaviorRelay.create();

    private Disposable subscription = Disposables.disposed();

    /**
     * Sync subscriptions every ~6 hours.
     */
    public static void schedule(Context context) {
        JobInfo syncJob = new JobInfo.Builder(ID_RECURRING_JOB, new ComponentName(context, SubredditSubscriptionsSyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true)
                .setPersisted(true)
                .setPeriodic(DateUtils.HOUR_IN_MILLIS * 6)
                .build();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(syncJob);
    }

    public static void syncImmediately(Context context) {
        JobInfo syncJob = new JobInfo.Builder(ID_ONE_TIME_JOB, new ComponentName(context, SubredditSubscriptionsSyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(false)
                .setOverrideDeadline(0)
                .build();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(syncJob);
    }

    /**
     * See {@link #progressSubject}.
     */
    public static BehaviorRelay<Boolean> progressUpdates() {
        return progressSubject;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Timber.i("Syncing subs");

        subscription = Dank.subscriptionManager().refreshSubscriptions()
                .andThen(Dank.subscriptionManager().executePendingSubscribesAndUnsubscribes())
                .compose(applySchedulersCompletable())
                .compose(doOnStartAndComplete(ongoing -> progressSubject.accept(ongoing)))
                .subscribe(
                        () -> {
                            jobFinished(params, false /* needsReschedule */);
                            if (params.getJobId() == ID_RECURRING_JOB) {
                                displaySuccessNotification();
                            }
                        },
                        error -> {
                            Timber.e(error, "Couldn't sync subscriptions");
                            boolean canRetry = params.getJobId() == ID_RECURRING_JOB;
                            jobFinished(params, canRetry);
                        }
                );

        // Return true to indicate that the job is still being processed (in a background thread).
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Return true to indicate JobScheduler that the job should be rescheduled.
        return true;
    }

    @Override
    public void onDestroy() {
        subscription.dispose();
        super.onDestroy();
    }

    private void displaySuccessNotification() {
        Intent homeActivityIntent = new Intent(this, SubredditActivity.class);
        PendingIntent onClickPendingIntent = PendingIntent.getActivity(this, 0, homeActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle("Subreddits synced")
                .setContentIntent(onClickPendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setColor(ContextCompat.getColor(getBaseContext(), R.color.color_accent));

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

}
