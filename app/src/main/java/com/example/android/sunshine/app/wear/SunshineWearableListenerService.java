package com.example.android.sunshine.app.wear;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import static com.example.android.sunshine.app.sync.SunshineSyncAdapter.ACTION_DATA_UPDATED;

/**
 * Created by dcwilcox on 11/3/2016.
 */

//referenced https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService
public class SunshineWearableListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.v("SunshineWearListener", "received message" + messageEvent.toString());
        if (getString(R.string.wear_init_path).equals(messageEvent.getPath()))
        {
            Context context = getApplicationContext();
            context.startService(new Intent()
                    .setClass(context, SunshineWearIntentService.class));
        }
    }
}
