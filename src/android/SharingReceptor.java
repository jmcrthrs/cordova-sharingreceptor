package org.apache.cordova.sharingreceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import java.lang.Integer;
import java.lang.reflect.Array;

import android.content.Intent;
import android.database.Cursor;
import android.provider.MediaStore;
import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.Manifest;
import android.Manifest.permission;

import android.content.pm.PackageManager;
import android.content.ClipData;
import android.os.Bundle;
import android.os.Build;
import android.content.ContentResolver;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.RuntimeException;

/**
 *
 * @author lorber.sebastien@gmail.com
 */
public class SharingReceptor extends CordovaPlugin {

    private static final String TAG = SharingReceptor.class.getSimpleName();

    // Constant that holds all the intent actions that we will handle.
    private static final Set<String> SEND_INTENTS = new HashSet<String>(Arrays.asList(
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    ));

    private static boolean isSendIntent(Intent intent) {
        return SEND_INTENTS.contains(intent.getAction());
    }

    private CallbackContext listenerCallback = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            if ( action.equals("listen") ) {
                installListener(callbackContext);
                return true;
            }
            else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION,"action ["+action+"] does not exist"));
                return false;
            }
        }
        catch (Exception e) {
            Log.e(TAG,"Error while executing action ["+action+"]",e);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,e.getMessage()));
            return false;
        }
    }



    private void installListener(CallbackContext callbackContext) {
        if ( this.listenerCallback != null ) {
            throw new RuntimeException("You already set a listener: it does not make sense to reset it.");
        }
        this.listenerCallback = callbackContext;

        // handle the case where the app is started by a new intent:
        // we publish the current intent (if needed) directly after the callback registration
        this.maybePublishIntent(this.cordova.getActivity().getIntent());
    }

    // handle the case when the app is already in background, and is "awakened" by a new intent sent from another app
    @Override
    public void onNewIntent(Intent intent) {
        Log.i(TAG, "onNewIntent -> " + intent);
        this.maybePublishIntent(intent);

        // TODO would it be useful to replace current activity intent by new intent? Can it messes things up?
    }

    // We try to publish in the JS callback the intent data, if the intent is a send intent, and if the callback was correctly setup
    private void maybePublishIntent(Intent intent) {
        if ( !SharingReceptor.isSendIntent(intent) ) {
            Log.i(TAG, "maybePublishIntent -> not publishing intent because the action name is not part of SEND_INTENTS=" + SEND_INTENTS);
        }
        else if ( this.listenerCallback == null ) {
            Log.w(TAG, "maybePublishIntent -> not publishing intent because listener callback not set");
        }
        else {

            JSONObject intentJson;
            try {
                intentJson = getIntentJson(intent);
            } catch (Exception e) {
                throw new RuntimeException("Can't serialize intent " + intent,e);
            }
            Log.i(TAG, "maybePublishIntent -> will publish intent -> " + intentJson.toString());
            PluginResult result = new PluginResult(PluginResult.Status.OK, intentJson);
            result.setKeepCallback(true);
            this.listenerCallback.sendPluginResult(result);
        }
    }

    /**
     * Return JSON representation of intent attributes
     *
     * @param intent
     * @return
     */
    private JSONObject getIntentJson(Intent intent) {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if(clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {

                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();

                        if(item.getUri() != null) {

                            items[i].put("uri", item.getUri());

                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    } catch (JSONException e) {
                        Log.d(TAG, TAG + " Error thrown during intent > JSON conversion");
                        Log.d(TAG, e.getMessage());
                        Log.d(TAG, Arrays.toString(e.getStackTrace()));
                    }

                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(items != null) {
                    intentJSON.put("items", new JSONArray(items));
                }
            }

            if(intent.getData() != null) {
                items = new JSONObject[1];
                items[0] = new JSONObject();
                items[0].put("uri", intent.getData());

                if(intent.getData() != null) {
                    String type = cR.getType(intent.getData());
                    String extension = mime.getExtensionFromMimeType(cR.getType(intent.getData()));

                    items[0].put("type", type);
                    items[0].put("extension", extension);
                }
                intentJSON.put("items", new JSONArray(items));
            }

            return intentJSON;
        } catch (JSONException e) {
            Log.d(TAG, TAG + " Error thrown during intent > JSON conversion");
            Log.d(TAG, e.getMessage());
            Log.d(TAG, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }


}
