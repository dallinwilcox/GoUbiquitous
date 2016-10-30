package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.SunshineWearValues;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

import static com.example.android.sunshine.app.data.SunshineWearValues.SUNSHINE_WEAR_DATA_PATH;

/**
 * Created by dcwilcox on 10/25/2016.
 */

public class SunshineWearIntentService extends IntentService implements
        DataApi.DataListener {

    private static final String TAG = "WearIntentService";
    private GoogleApiClient googleApiClient;
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;

    public SunshineWearIntentService() { super("SunshineWearIntentService"); }

    @Override
    protected void onHandleIntent(Intent intent) {
        //Init Wear Data GoogleAPI, and handle if no wear is present by stopping the service
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                        if (result.getErrorCode() == ConnectionResult.API_UNAVAILABLE) {
                            // The Wearable API is unavailable
                            SunshineWearIntentService.this.stopSelf();
                        }
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        String description = data.getString(INDEX_SHORT_DESC);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), weatherArtResourceId);
        Asset asset = createAssetFromBitmap(bitmap);

        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(SUNSHINE_WEAR_DATA_PATH );
        DataMap putDataMap = putDataMapReq.getDataMap();
        putDataMap.putString(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
        putDataMap.putString(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, formattedMaxTemperature);
        putDataMap.putString(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, formattedMinTemperature);
        putDataMap.putAsset(SunshineWearValues.WEATHER_IMAGE, asset);

        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        //TODO Handle initial sync request based on wear device init.

    }
}
