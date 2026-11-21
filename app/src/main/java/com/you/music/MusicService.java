package com.you.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import java.util.ArrayList;

public class MusicService extends Service {
    private MediaPlayer player;
    private boolean isPlaying = false;
    private String currentTitle = "MyMusic";
    private ArrayList<String> uris = new ArrayList<>();
    private ArrayList<String> titles = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isLooping = false;
    private Handler sleepHandler = new Handler();
    private Runnable sleepRunnable;

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    private BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("ACTION_PLAY".equals(action)) playMusic();
            else if ("ACTION_PAUSE".equals(action)) pauseMusic();
            else if ("ACTION_STOP".equals(action)) stopMusic();
            else if ("ACTION_NEXT".equals(action)) playNext();
            else if ("ACTION_PREV".equals(action)) playPrev();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_PLAY"); filter.addAction("ACTION_PAUSE"); filter.addAction("ACTION_STOP");
        filter.addAction("ACTION_NEXT"); filter.addAction("ACTION_PREV");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(actionReceiver, filter);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra("ALL_URIS") && intent.hasExtra("ALL_TITLES")) {
                uris = intent.getStringArrayListExtra("ALL_URIS");
                titles = intent.getStringArrayListExtra("ALL_TITLES");
                currentIndex = intent.getIntExtra("START_INDEX", 0);
                playSongByIndex(currentIndex);
            } else if (intent.getData() != null) {
                playNewSong(intent.getData());
            } else {
                String action = intent.getAction();
                if ("ACTION_PLAY".equals(action)) playMusic();
                else if ("ACTION_PAUSE".equals(action)) pauseMusic();
                else if ("ACTION_STOP".equals(action)) stopMusic();
                else if ("ACTION_NEXT".equals(action)) playNext();
                else if ("ACTION_PREV".equals(action)) playPrev();
            }
        }
        return START_STICKY;
    }

    private void playSongByIndex(int index) {
        if (index < 0 || index >= uris.size()) return;
        currentIndex = index;
        currentTitle = titles.get(index);
        if (player != null) { player.stop(); player.release(); }
        player = MediaPlayer.create(this, Uri.parse(uris.get(index)));
        if (player != null) {
            player.setLooping(isLooping);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { if (!isLooping) playNext(); }
            });
            player.start(); isPlaying = true; showNotification();
        }
    }

    public void playNext() { if (currentIndex + 1 < uris.size()) playSongByIndex(currentIndex + 1); }
    public void playPrev() { if (currentIndex - 1 >= 0) playSongByIndex(currentIndex - 1); }
    public void playMusic() { if (player != null && !isPlaying) { player.start(); isPlaying = true; showNotification(); } }
    public void pauseMusic() { if (player != null && isPlaying) { player.pause(); isPlaying = false; showNotification(); } }
    public boolean isPlaying() { return isPlaying; }
    public int getCurrentPosition() { return player != null ? player.getCurrentPosition() : 0; }
    public int getDuration() { return player != null ? player.getDuration() : 0; }
    public void seekTo(int pos) { if (player != null) player.seekTo(pos); }

    public void setLooping(boolean loop) {
        isLooping = loop;
        if (player != null) player.setLooping(loop);
    }

    public void startSleepTimer(long millis) {
        if (sleepRunnable != null) sleepHandler.removeCallbacks(sleepRunnable);
        sleepRunnable = new Runnable() {
            @Override public void run() { pauseMusic(); }
        };
        sleepHandler.postDelayed(sleepRunnable, millis);
    }

    public void cancelTimer() {
        if (sleepRunnable != null) sleepHandler.removeCallbacks(sleepRunnable);
    }

    public void stopMusic() {
        if (player != null) { player.stop(); player.release(); player = null; }
        isPlaying = false; stopForeground(true); stopSelf();
    }

    private void playNewSong(Uri uri) {
        if (player != null) { player.stop(); player.release(); }
        player = MediaPlayer.create(this, uri);
        if (player != null) { player.start(); isPlaying = true; showNotification(); }
    }

    private void showNotification() {
        String CH = "music_ch";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Music", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        PendingIntent playIntent = PendingIntent.getBroadcast(this, 1, new Intent("ACTION_PLAY"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 2, new Intent("ACTION_PAUSE"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getBroadcast(this, 3, new Intent("ACTION_STOP"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getBroadcast(this, 4, new Intent("ACTION_NEXT"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevIntent = PendingIntent.getBroadcast(this, 5, new Intent("ACTION_PREV"), PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CH)
            .setContentTitle(currentTitle)
            .setContentText(isPlaying ? "قيد التشغيل" : "متوقف مؤقتاً")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .setStyle(new Notification.BigTextStyle().bigText(currentTitle + "\n" + (isPlaying ? "قيد التشغيل" : "متوقف مؤقتاً")));

        if (isPlaying) builder.addAction(android.R.drawable.ic_media_pause, "إيقاف", pauseIntent);
        else builder.addAction(android.R.drawable.ic_media_play, "تشغيل", playIntent);
        builder.addAction(android.R.drawable.ic_media_previous, "السابق", prevIntent);
        builder.addAction(android.R.drawable.ic_media_next, "التالي", nextIntent);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", stopIntent);

        startForeground(1, builder.build());
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(actionReceiver); } catch (Exception e) {}
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return binder; }
}