package com.example.android.sunshine.app.wear;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Created by dcwilcox on 11/3/2016.
 */

//referenced https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService
// https://developer.android.com/training/wearables/data-layer/events.html
public class SunshineWearListenerService extends WearableListenerService {

    public static final String TAG = "SunshineWearListener";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "received message with path" + messageEvent.getPath());
        if (getString(R.string.wear_init_path).equals(messageEvent.getPath()))
        {
            Context context = getApplicationContext();
            context.startService(new Intent()
                    .setClass(context, SunshineWearIntentService.class));
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.v(TAG, "dataChanged " + dataEvents);

        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEvents);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            Uri uri = event.getDataItem().getUri();

            // Get the node id from the host value of the URI
            String nodeId = uri.getHost();
            // Set the data of the message to be the bytes of the URI
            byte[] payload = uri.toString().getBytes();

            // Send the RPC
            Wearable.MessageApi.sendMessage(googleApiClient, nodeId,
                    DATA_ITEM_RECEIVED_PATH, payload);

            //Launch Service to handle event
//            if (getString(R.string.wear_init_path).equals(event.getDataItem().getDat))
//            {
                Context context = getApplicationContext();
                context.startService(new Intent()
                        .setClass(context, SunshineWearIntentService.class));
//            }
        }

    }
}
