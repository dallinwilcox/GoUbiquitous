/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.example.android.sunshine.app.data.SunshineWearValues;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Referenced:
 * https://developer.android.com/training/wearables/watch-faces/
 * https://developer.android.com/training/wearables/data-layer/
 * https://catinean.com/2015/03/28/creating-a-watch-face-with-android-wear-api-part-2/
 *
 *
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private GoogleApiClient googleApiClient;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint timeTextPaint;
        Paint weatherTextPaint;
        Paint mImagePaint;
        boolean mAmbient;
        Calendar mCalendar;

        public void setWeatherImage(Bitmap weatherImage) {
            this.weatherImage = weatherImage;
        }

        Bitmap weatherImage;
        String minTemp;
        String maxTemp;

        String weatherDescription;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mBackgroundPaint = new Paint();
            //referenced http://stackoverflow.com/a/32149275/2169923
            mBackgroundPaint.setColor(ContextCompat.getColor(SunshineWatchFace.this, com.example.android.sunshine.app.R.color.background));

            timeTextPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, com.example.android.sunshine.app.R.color.digital_text), 0);
            weatherTextPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, com.example.android.sunshine.app.R.color.digital_text), SunshineWatchFace.this.getResources().getDimensionPixelSize(R.dimen.abc_text_size_small_material));
            mImagePaint = new Paint();
            mCalendar = Calendar.getInstance();

            googleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            //call to device for inital sync to have data to display.
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(getString(com.example.android.sunshine.app.R.string.wear_init_path));
            putDataMapReq.getDataMap().putLong("CurrentTime", System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(final DataApi.DataItemResult result) {
                    if(result.getStatus().isSuccess()) {
                        Log.d("SunshineWatchFace", "Data item set: " + result.getDataItem().getUri());
                    }
                    else {
                        Log.e("SunshineWatchFace", "Status is not success");
                    }
                }
            });
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            releaseGoogleApiClient();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, float textSize) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            if (textSize != 0){
                paint.setTextSize(textSize);
            }
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
                googleApiClient.connect();
            } else {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_x_offset_round : com.example.android.sunshine.app.R.dimen.digital_x_offset);
            mYOffset = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_text_size_round : com.example.android.sunshine.app.R.dimen.digital_text_size);
            timeTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    timeTextPaint.setAntiAlias(!inAmbientMode);
                    weatherTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), com.example.android.sunshine.app.R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String time = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(time, mXOffset, mYOffset, timeTextPaint);


            if (null != weatherDescription)
            {
                String weatherInfo = String.format("%s %s/%s", weatherDescription, maxTemp, minTemp);
                canvas.drawText(weatherInfo, mXOffset, (mYOffset + 60), weatherTextPaint);
            }
            else
            {
                canvas.drawText("Sync Pending", mXOffset, (mYOffset + 60), weatherTextPaint);
            }
            if (!mAmbient && null != weatherImage)
            {
                canvas.drawBitmap(weatherImage, mXOffset + 80, (mYOffset + 80), mImagePaint);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void releaseGoogleApiClient() {
            if (null != googleApiClient && googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d("SunshineWatchFace","onConnected");
            Wearable.DataApi.addListener(googleApiClient, onWeatherDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);
        }

        private final DataApi.DataListener onWeatherDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents){
                Log.v("SunshineWatchFace", "onDataChanged" + dataEvents.toString());
                for (DataEvent event : dataEvents){
                    if(event.getType() == DataEvent.TYPE_CHANGED){
                        DataItem item = event.getDataItem();
                        processDataFor(item);
                    }
                }
                dataEvents.release();
                //invalidateIfNecessary
            }
        };

        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>(){
            @Override
            public void onResult(DataItemBuffer dataItems){
                Log.v("ResultCallback", dataItems.toString());
                for (DataItem item : dataItems) {
                    Log.d("ResultCallback", "item:" + item);
                    Log.d("ResultCallback", "item Data:" + item.getData());
                    processDataFor(item);
                }
                dataItems.release();
                //invalidateIfNecessary
            }
        };
        
        @Override
        public void onConnectionSuspended(int i) {
            Log.v("onConnectionSuspended","Connection suspended" + i);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e("onConnectionFailed","Connection failed" + connectionResult);

        }

        // see https://developer.android.com/training/wearables/watch-faces/information.html
        private void processDataFor(DataItem item)
        {
            if (SunshineWearValues.SUNSHINE_WEAR_DATA_PATH.equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC)) {
                    weatherDescription = dataMap.getString(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC);
                }
                if (dataMap.containsKey(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)) {
                    maxTemp = dataMap.getString(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
                }
                if (dataMap.containsKey(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP)) {
                    minTemp = dataMap.getString(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
                }
                if (dataMap.containsKey(SunshineWearValues.WEATHER_IMAGE)) {
                    Asset weatherImageAsset = dataMap.getAsset(SunshineWearValues.WEATHER_IMAGE);
                    BitmapWorkerTask task = new BitmapWorkerTask();
                    task.execute(weatherImageAsset);
                }
            }
        }
        class BitmapWorkerTask extends AsyncTask<Asset, Void, Bitmap> {
            private final WeakReference<SunshineWatchFace.Engine> engineReference;
            public BitmapWorkerTask() {
                engineReference = new WeakReference<>(Engine.this);
            }

            // Decode image in background.
            @Override
            protected Bitmap doInBackground(Asset... params) {
                Asset asset = params[0];
                return loadBitmapFromAsset(asset);
            }

            // Once complete, set bitmap safely
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (engineReference != null && bitmap != null) {
                    final SunshineWatchFace.Engine engine = engineReference.get();
                    if (bitmap != null) {
                        engine.setWeatherImage(bitmap);
                    }
                }
            }
        }
        //Needs to be run off of the main thread
        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    googleApiClient.blockingConnect(30000, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    googleApiClient, asset).await().getInputStream();
            googleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w("loadBitmapFromAsset", "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
    }
}
