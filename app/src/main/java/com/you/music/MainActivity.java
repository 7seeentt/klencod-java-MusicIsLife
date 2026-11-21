package com.you.music;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayList<String> songNames = new ArrayList<>();
    private ArrayList<String> songUris = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView currentSongText, tabPlayer, tabLibrary;
    private SeekBar seekBar;
    private ImageView btnPlayPause, btnNext, btnPrev, albumArt;
    private LinearLayout screenPlayer;
    private MusicService musicService;
    private boolean isBound = false;
    private Handler handler = new Handler();
    private boolean isPlayingState = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) { isBound = false; }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        // ربط العناصر مع حماية
        albumArt = findViewById(R.id.albumArt);
        currentSongText = findViewById(R.id.songTitle);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        listView = findViewById(R.id.songList);
        tabPlayer = findViewById(R.id.tabPlayer);
        tabLibrary = findViewById(R.id.tabLibrary);
        screenPlayer = findViewById(R.id.screenPlayer);

        // حماية من الأخطاء إذا كان التصميم ناقص
        if (tabPlayer == null || tabLibrary == null || screenPlayer == null) {
            Toast.makeText(this, "خطأ في التصميم: بعض العناصر غير موجودة", Toast.LENGTH_LONG).show();
            return; // إذا كان التصميم ناقص، نوقف هنا عشان ما يتوقف التطبيق
        }

        tabPlayer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                screenPlayer.setVisibility(View.VISIBLE);
                if (listView != null) listView.setVisibility(View.GONE);
                tabPlayer.setTextColor(Color.parseColor("#00E5FF"));
                tabLibrary.setTextColor(Color.parseColor("#8B949E"));
            }
        });
        tabLibrary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                screenPlayer.setVisibility(View.GONE);
                if (listView != null) listView.setVisibility(View.VISIBLE);
                tabLibrary.setTextColor(Color.parseColor("#00E5FF"));
                tabPlayer.setTextColor(Color.parseColor("#8B949E"));
            }
        });

        if (listView != null) {
            adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, songNames) {
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = (TextView) v;
                    tv.setTextColor(Color.WHITE); tv.setTextSize(16); tv.setPadding(24, 32, 24, 32);
                    return v;
                }
            };
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    playSong(position);
                    tabPlayer.performClick();
                }
            });
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (musicService != null) {
                        if (musicService.isPlaying()) musicService.pauseMusic();
                        else musicService.playMusic();
                    }
                }
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (musicService != null) musicService.playNext(); }
            });
        }
        if (btnPrev != null) {
            btnPrev.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (musicService != null) musicService.playPrev(); }
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && musicService != null) musicService.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isBound && musicService != null) {
                    if (seekBar != null) {
                        seekBar.setMax(musicService.getDuration());
                        seekBar.setProgress(musicService.getCurrentPosition());
                    }
                    boolean playing = musicService.isPlaying();
                    if (playing != isPlayingState) {
                        isPlayingState = playing;
                        if (btnPlayPause != null) {
                            btnPlayPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                        }
                    }
                }
                handler.postDelayed(this, 1000);
            }
        }, 0);

        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            loadSongs();
        }

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void playSong(int index) {
        if (index < 0 || index >= songUris.size()) return; // حماية من IndexOutOfBounds
        if (currentSongText != null) currentSongText.setText(songNames.get(index));
        Intent i = new Intent(this, MusicService.class);
        i.putStringArrayListExtra("ALL_URIS", songUris);
        i.putStringArrayListExtra("ALL_TITLES", songNames);
        i.putExtra("START_INDEX", index);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
    }

    private void loadSongs() {
        songNames.clear(); songUris.clear();
        String[] projection = {MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media._ID};
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                songNames.add(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)));
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                songUris.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
            }
            cursor.close();
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) unbindService(connection);
        handler.removeCallbacksAndMessages(null);
    }
}