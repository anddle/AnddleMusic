package com.anddle.anddlemusic;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MusicListActivity extends AppCompatActivity {

    static public String TAG = "MusicListActivity";
    private List<MusicItem> mMusicList;
    private ListView mMusicListView;
    private Button mPlayBtn;
    private Button mPreBtn;
    private Button mNextBtn;
    private TextView mMusicTitle;
    private TextView mPlayedTime;
    private TextView mDurationTime;
    private SeekBar mMusicSeekBar;
    private MusicUpdateTask mMusicUpdateTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_music_list);

        mMusicList = new ArrayList<MusicItem>();
        mMusicListView = (ListView) findViewById(R.id.music_list);
        MusicItemAdapter adapter = new MusicItemAdapter(this, R.layout.music_item, mMusicList);
        mMusicListView.setAdapter(adapter);
        mMusicListView.setOnItemClickListener(mOnMusicItemClickListener);
        mMusicListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mMusicListView.setMultiChoiceModeListener(mMultiChoiceListener);

        mPlayBtn = (Button) findViewById(R.id.play_btn);
        mPreBtn = (Button) findViewById(R.id.pre_btn);
        mNextBtn = (Button) findViewById(R.id.next_btn);

        mMusicTitle = (TextView) findViewById(R.id.music_title);

        mDurationTime = (TextView) findViewById(R.id.duration_time);
        mPlayedTime = (TextView) findViewById(R.id.played_time);
        mMusicSeekBar = (SeekBar) findViewById(R.id.seek_music);
        mMusicSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mMusicUpdateTask = new MusicUpdateTask();
        mMusicUpdateTask.execute();

        Intent i = new Intent(this, MusicService.class);
        startService(i);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mMusicUpdateTask != null && mMusicUpdateTask.getStatus() == AsyncTask.Status.RUNNING) {
            mMusicUpdateTask.cancel(true);
        }
        mMusicUpdateTask = null;
        mMusicService.unregisterOnStateChangeListener(mStateChangeListenr);
        unbindService(mServiceConnection);

        for(MusicItem item : mMusicList) {
            if( item.thumb != null ) {
                item.thumb.recycle();
                item.thumb = null;
            }
        }
        mMusicList.clear();
    }

    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

            if(mMusicService != null) {
                mMusicService.seekTo(seekBar.getProgress());
            }
        }
    };

    private AdapterView.OnItemClickListener mOnMusicItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            if(mMusicService != null) {
                mMusicService.addPlayList(mMusicList.get(position));
            }
        }
    };

    private ListView.MultiChoiceModeListener mMultiChoiceListener = new AbsListView.MultiChoiceModeListener() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.music_choice_actionbar, menu);
            enableControlPanel(false);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch(item.getItemId()) {
                case R.id.menu_play: {
                    List musicList = new ArrayList<MusicItem>();
                    SparseBooleanArray checkedResult = mMusicListView.getCheckedItemPositions();
                    for (int i = 0; i < checkedResult.size(); i++) {
                        if(checkedResult.valueAt(i)) {
                            int pos = checkedResult.keyAt(i);
                            MusicItem music = mMusicList.get(pos);
                            musicList.add(music);
                        }
                    }

                    mMusicService.addPlayList(musicList);

                    mode.finish();
                }
                break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            enableControlPanel(true);
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {

        }
    };

    private void enableControlPanel(boolean enabled) {
        mPlayBtn.setEnabled(enabled);
        mPreBtn.setEnabled(enabled);
        mNextBtn.setEnabled(enabled);
        mMusicSeekBar.setEnabled(enabled);
    }

    private void updatePlayingInfo(MusicItem item) {
        String times = Utils.convertMSecendToTime(item.duration);
        mDurationTime.setText(times);

        times = Utils.convertMSecendToTime(item.playedTime);
        mPlayedTime.setText(times);

        mMusicSeekBar.setMax((int) item.duration);
        mMusicSeekBar.setProgress((int) item.playedTime);

        mMusicTitle.setText(item.name);
    }

    private MusicService.OnStateChangeListenr mStateChangeListenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(MusicItem item) {

            updatePlayingInfo(item);
        }

        @Override
        public void onPlay(MusicItem item) {
            mPlayBtn.setBackgroundResource(R.mipmap.ic_pause);
            updatePlayingInfo(item);
            enableControlPanel(true);
        }

        @Override
        public void onPause(MusicItem item) {
            mPlayBtn.setBackgroundResource(R.mipmap.ic_play);
            enableControlPanel(true);
        }
    };

    private class MusicUpdateTask extends AsyncTask<Object, MusicItem, Void> {

        List<MusicItem> mDataList = new ArrayList<MusicItem>();

        @Override
        protected Void doInBackground(Object... params) {

            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] searchKey = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Albums.ALBUM_ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };

            String where = MediaStore.Audio.Media.DATA + " like \"%"+getString(R.string.search_path)+"%\"";
            String [] keywords = null;
            String sortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER;

            ContentResolver resolver = getContentResolver();
            Cursor cursor = resolver.query(uri, searchKey, where, keywords, sortOrder);

            if(cursor != null)
            {
                while(cursor.moveToNext() && ! isCancelled())
                {
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                    Uri musicUri = Uri.withAppendedPath(uri, id);

                    String name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                    long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));


                    int albumId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ID));
                    Uri albumUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
                    MusicItem data = new MusicItem(musicUri, albumUri, name, duration, 0);
                    if (uri != null) {
                        ContentResolver res = getContentResolver();
                        data.thumb = Utils.createThumbFromUir(res, albumUri);
                    }

                    Log.d(TAG, "real music found: " + path);

                    publishProgress(data);

                }

                cursor.close();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(MusicItem... values) {

            MusicItem data = values[0];

            mMusicList.add(data);
            MusicItemAdapter adapter = (MusicItemAdapter) mMusicListView.getAdapter();
            adapter.notifyDataSetChanged();

        }
    }

    private MusicService.MusicServiceIBinder mMusicService;

    private ServiceConnection mServiceConnection = new ServiceConnection()
    {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mMusicService = (MusicService.MusicServiceIBinder) service;
            mMusicService.registerOnStateChangeListener(mStateChangeListenr);

            MusicItem item = mMusicService.getCurrentMusic();
            if(item == null) {
                enableControlPanel(false);
                return;
            }
            else {
                updatePlayingInfo(item);
            }
            if(mMusicService.isPlaying()) {
                mPlayBtn.setBackgroundResource(R.mipmap.ic_pause);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.play_btn: {
                if(mMusicService != null) {
                    if(!mMusicService.isPlaying()) {
                        mMusicService.play();
                    }
                    else {
                        mMusicService.pause();
                    }
                }
            }
            break;

            case R.id.next_btn: {
                if(mMusicService != null) {
                    mMusicService.playNext();
                }
            }
            break;

            case R.id.pre_btn: {
                if(mMusicService != null) {
                    mMusicService.playPre();
                }
            }
            break;
        }
    }

    private void showPlayList() {

        final AlertDialog.Builder builder=new AlertDialog.Builder(this);
        builder.setIcon(R.mipmap.ic_playlist);
        builder.setTitle(R.string.play_list);

        List<MusicItem> playList = mMusicService.getPlayList();
        ArrayList<String> data = new ArrayList<String>();
        for(MusicItem music : playList) {
            data.add(music.name);
        }
        if(data.size() > 0) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, data);
            builder.setAdapter(adapter, null);
        }
        else {
            builder.setMessage(getString(R.string.no_song));
        }

        builder.setCancelable(true);
        builder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.play_list_menu: {

                showPlayList();
            }
            break;

        }

        return true;
    }
}
