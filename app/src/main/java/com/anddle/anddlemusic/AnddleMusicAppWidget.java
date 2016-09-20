package com.anddle.anddlemusic;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.RemoteViews;

public class AnddleMusicAppWidget extends AppWidgetProvider {

    private static int [] sAppWidgetIds;

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId, String musicName, boolean isPlaying, Bitmap thumb) {

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.anddle_music_app_widget);
        views.setTextViewText(R.id.music_name, musicName);

        final ComponentName serviceName = new ComponentName(context, MusicService.class);

        Intent nextIntent = new Intent(MusicService.ACTION_PLAY_MUSIC_NEXT);
        nextIntent.setComponent(serviceName);
        PendingIntent nextPendingIntent = PendingIntent.getService(context,
                0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.next_btn2, nextPendingIntent);

        Intent preIntent = new Intent(MusicService.ACTION_PLAY_MUSIC_PRE);
        preIntent.setComponent(serviceName);
        PendingIntent prePendingIntent = PendingIntent.getService(context,
                0, preIntent, 0);
        views.setOnClickPendingIntent(R.id.pre_btn2, prePendingIntent);

        Intent toggleIntent = new Intent(MusicService.ACTION_PLAY_MUSIC_TOGGLE);
        toggleIntent.setComponent(serviceName);
        PendingIntent togglePendingIntent = PendingIntent.getService(context,
                0, toggleIntent, 0);
        views.setOnClickPendingIntent(R.id.play_btn2, togglePendingIntent);

        views.setInt(R.id.play_btn2, "setBackgroundResource", isPlaying ? R.mipmap.ic_pause : R.mipmap.ic_play);
        if(thumb != null) {
            views.setImageViewBitmap(R.id.image_thumb, thumb);
        }
        else {
            views.setImageViewResource(R.id.image_thumb, R.mipmap.default_cover);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void performUpdates(Context context, String musicName, boolean isPlaying, Bitmap thumb) {

        if(sAppWidgetIds == null || sAppWidgetIds.length == 0) {

            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        for (int appWidgetId : sAppWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, musicName, isPlaying, thumb);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        sAppWidgetIds = appWidgetIds;

        performUpdates(context, context.getString(R.string.no_song), false, null);

        Intent updateIntent = new Intent(MusicService.ACTION_PLAY_MUSIC_UPDATE);
        context.sendBroadcast(updateIntent);
    }

    @Override
    public void onEnabled(Context context) {
        Intent i = new Intent(context, MusicService.class);
        context.startService(i);
    }
}

