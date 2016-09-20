package com.anddle.anddlemusic;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {

    public interface OnStateChangeListenr {

        void onPlayProgressChange(MusicItem item);
        void onPlay(MusicItem item);
        void onPause(MusicItem item);
    }

    private final int MSG_PROGRESS_UPDATE = 0;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PROGRESS_UPDATE: {
                    mCurrentMusicItem.playedTime = mMusicPlayer.getCurrentPosition();
                    mCurrentMusicItem.duration = mMusicPlayer.getDuration();

                    for(OnStateChangeListenr l : mListenerList) {
                        l.onPlayProgressChange(mCurrentMusicItem);
                    }

                    updateMusicItem(mCurrentMusicItem);

                    sendEmptyMessageDelayed(MSG_PROGRESS_UPDATE, 1000);
                }
                break;
            }
        }
    };

    public static final String ACTION_PLAY_MUSIC_PRE = "com.anddle.anddlemusic.playpre";
    public static final String ACTION_PLAY_MUSIC_NEXT = "com.anddle.anddlemusic.playnext";
    public static final String ACTION_PLAY_MUSIC_TOGGLE = "com.anddle.anddlemusic.playtoggle";
    public static final String ACTION_PLAY_MUSIC_UPDATE = "com.anddle.anddlemusic.playupdate";

    private List<OnStateChangeListenr> mListenerList = new ArrayList<OnStateChangeListenr>();
    private List<MusicItem> mPlayList;

    private MusicItem mCurrentMusicItem;
    private MediaPlayer mMusicPlayer;
    private ContentResolver mResolver;
    private boolean mPaused;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (ACTION_PLAY_MUSIC_UPDATE.equals(action)) {
                updateAppWidget(mCurrentMusicItem);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent != null) {
            String action = intent.getAction();
            if (action != null) {
                if (ACTION_PLAY_MUSIC_PRE.equals(action)) {
                    playPreInner();
                } else if (ACTION_PLAY_MUSIC_NEXT.equals(action)) {
                    playNextInner();
                } else if (ACTION_PLAY_MUSIC_TOGGLE.equals(action)) {
                    if (isPlayingInner()) {
                        pauseInner();
                    } else {
                        playInner();
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mMusicPlayer = new MediaPlayer();
        mResolver = getContentResolver();
        mPlayList = new ArrayList<MusicItem>();
        mPaused = false;
        mMusicPlayer.setOnCompletionListener(mOnCompletionListener);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(ACTION_PLAY_MUSIC_UPDATE);
        registerReceiver(mIntentReceiver, commandFilter);

        initPlayList();

        if(mCurrentMusicItem != null) {

            prepareToPlay(mCurrentMusicItem);
        }

        updateAppWidget(mCurrentMusicItem);

    }

    private void prepareToPlay(MusicItem item) {
        try {
            mMusicPlayer.reset();
            mMusicPlayer.setDataSource(MusicService.this, item.songUri);
            mMusicPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mMusicPlayer.isPlaying()) {
            mMusicPlayer.stop();
        }
        mMusicPlayer.release();

        unregisterReceiver(mIntentReceiver);
        mHandler.removeMessages(MSG_PROGRESS_UPDATE);
        mListenerList.clear();
        for(MusicItem item : mPlayList) {
            if(item.thumb != null) {
                item.thumb.recycle();
            }
        }

        mPlayList.clear();
    }

    public class MusicServiceIBinder extends Binder {

        public void addPlayList(List<MusicItem> items) {
            addPlayListInner(items);
        }

        public void addPlayList(MusicItem item) {
            addPlayListInner(item, true);
        }

        public void play() {
            playInner();
        }

        public void playNext() {
            playNextInner();
        }

        public void playPre() {
            playPreInner();
        }

        public void pause() {
            pauseInner();
        }

        public void seekTo(int pos) {
            seekToInner(pos);
        }

        public void registerOnStateChangeListener(OnStateChangeListenr l) {
            registerOnStateChangeListenerInner(l);

        }

        public void unregisterOnStateChangeListener(OnStateChangeListenr l) {
            unregisterOnStateChangeListenerInner(l);
        }

        public MusicItem getCurrentMusic() {
            return getCurrentMusicInner();
        }

        public boolean isPlaying() {
            return isPlayingInner();
        }

        public List<MusicItem> getPlayList() {
            return mPlayList;
        }

    }

    private final IBinder mBinder = new MusicServiceIBinder();

    @Override
    public IBinder onBind(Intent intent) {

        return mBinder;
    }

    private void addPlayListInner(List<MusicItem> items) {

        mResolver.delete(PlayListContentProvider.CONTENT_SONGS_URI, null, null);
        mPlayList.clear();

        for (MusicItem item : items) {
            addPlayListInner(item, false);
        }

        mCurrentMusicItem = mPlayList.get(0);
        playInner();
    }

    private void addPlayListInner(MusicItem item, boolean needPlay) {

        if(mPlayList.contains(item)) {
            return;
        }

        mPlayList.add(0, item);

        insertMusicItemToContentProvider(item);

        if(needPlay) {
            mCurrentMusicItem = mPlayList.get(0);
            playInner();
        }
    }

    private void playNextInner() {
        int currentIndex = mPlayList.indexOf(mCurrentMusicItem);
        if(currentIndex < mPlayList.size() -1 ) {

            mCurrentMusicItem = mPlayList.get(currentIndex + 1);
            playMusicItem(mCurrentMusicItem, true);
        }
    }

    private void playInner() {
        if(mCurrentMusicItem == null && mPlayList.size() > 0) {
            mCurrentMusicItem = mPlayList.get(0);
        }

        if(mPaused) {
            playMusicItem(mCurrentMusicItem, false);
        }
        else {
            playMusicItem(mCurrentMusicItem, true);
        }

    }

    private void playPreInner() {
        int currentIndex = mPlayList.indexOf(mCurrentMusicItem);
        if(currentIndex - 1 >= 0 ) {

            mCurrentMusicItem = mPlayList.get(currentIndex - 1);
            playMusicItem(mCurrentMusicItem, true);
        }
    }

    private void pauseInner() {

        mPaused = true;
        mMusicPlayer.pause();
        for(OnStateChangeListenr l : mListenerList) {
            l.onPause(mCurrentMusicItem);
        }
        mHandler.removeMessages(MSG_PROGRESS_UPDATE);
        updateAppWidget(mCurrentMusicItem);
    }

    private void seekToInner(int pos) {
        mMusicPlayer.seekTo(pos);
    }

    private void registerOnStateChangeListenerInner(OnStateChangeListenr l) {
        mListenerList.add(l);
    }

    private void unregisterOnStateChangeListenerInner(OnStateChangeListenr l) {
        mListenerList.remove(l);
    }

    private MusicItem getCurrentMusicInner() {
        return mCurrentMusicItem;
    }

    private boolean isPlayingInner() {
        return mMusicPlayer.isPlaying();
    }

    private void initPlayList() {
        mPlayList.clear();

        Cursor cursor = mResolver.query(
                PlayListContentProvider.CONTENT_SONGS_URI,
                null,
                null,
                null,
                null);

        while(cursor.moveToNext())
        {
            String songUri = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.SONG_URI));
            String albumUri = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.ALBUM_URI));
            String name = cursor.getString(cursor.getColumnIndex(DBHelper.NAME));
            long playedTime = cursor.getLong(cursor.getColumnIndexOrThrow(DBHelper.LAST_PLAY_TIME));
            long duration = cursor.getLong(cursor.getColumnIndexOrThrow(DBHelper.DURATION));

            MusicItem item = new MusicItem(Uri.parse(songUri), Uri.parse(albumUri), name, duration, playedTime/*, isLastPlaying*/);
            mPlayList.add(item);
        }

        cursor.close();

        if( mPlayList.size() > 0) {
            mCurrentMusicItem = mPlayList.get(0);
        }
    }

    private void playMusicItem(MusicItem item, boolean reload) {
            if(item == null) {
                return;
            }

            if(reload) {
                prepareToPlay(item);
            }

            mMusicPlayer.start();
            seekToInner((int)item.playedTime);
            for(OnStateChangeListenr l : mListenerList) {
                l.onPlay(item);
            }
            mPaused = false;

            mHandler.removeMessages(MSG_PROGRESS_UPDATE);
            mHandler.sendEmptyMessage(MSG_PROGRESS_UPDATE);

            updateAppWidget(mCurrentMusicItem);
    }

    private MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {

            mCurrentMusicItem.playedTime = 0;
            updateMusicItem(mCurrentMusicItem);
            playNextInner();
        }
    };

    private void insertMusicItemToContentProvider(MusicItem item) {

        ContentValues cv = new ContentValues();
        cv.put(DBHelper.NAME, item.name);
        cv.put(DBHelper.DURATION, item.duration);
        cv.put(DBHelper.LAST_PLAY_TIME, item.playedTime);
        cv.put(DBHelper.SONG_URI, item.songUri.toString());
        cv.put(DBHelper.ALBUM_URI, item.albumUri.toString());
        Uri uri = mResolver.insert(PlayListContentProvider.CONTENT_SONGS_URI, cv);
    }

    private void updateMusicItem(MusicItem item) {

        ContentValues cv = new ContentValues();
        cv.put(DBHelper.DURATION, item.duration);
        cv.put(DBHelper.LAST_PLAY_TIME, item.playedTime);

        String strUri = item.songUri.toString();
        mResolver.update(PlayListContentProvider.CONTENT_SONGS_URI, cv, DBHelper.SONG_URI + "=\"" + strUri + "\"", null);
    }

    private void updateAppWidget(MusicItem item) {
        if (item != null) {
            if(item.thumb == null) {
                ContentResolver res = getContentResolver();
                item.thumb = Utils.createThumbFromUir(res, item.albumUri);
            }
            AnddleMusicAppWidget.performUpdates(MusicService.this, item.name, isPlayingInner(), item.thumb);
        }
    }

}
