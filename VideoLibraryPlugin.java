
package com.sanku.mobilecinema;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

@CapacitorPlugin(
    name = "VideoLibrary",
    permissions = {
        @Permission(
            alias = "legacyStorage",
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE }
        ),
        @Permission(
            alias = "videos",
            strings = { Manifest.permission.READ_MEDIA_VIDEO }
        ),
        @Permission(
            alias = "selectedVideos",
            strings = { Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        )
    }
)
public class VideoLibraryPlugin extends Plugin {

    @PluginMethod
    public void getVideos(PluginCall call) {
        if (hasVideoAccess()) {
            loadVideos(call);
            return;
        }

        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissionForAliases(
                new String[] { "videos", "selectedVideos" },
                call,
                "permissionResult"
            );
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionForAlias("videos", call, "permissionResult");
        } else {
            requestPermissionForAlias(
                "legacyStorage",
                call,
                "permissionResult"
            );
        }
    }

    @PermissionCallback
    private void permissionResult(PluginCall call) {
        if (hasVideoAccess()) {
            loadVideos(call);
        } else {
            call.reject("Video permission was not granted.");
        }
    }

    private boolean hasVideoAccess() {
        if (Build.VERSION.SDK_INT >= 34) {
            return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return ContextCompat.checkSelfPermission(
            getContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadVideos(PluginCall call) {
        JSArray videos = new JSArray();

        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        };

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContext().getContentResolver().query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media._ID
                );
                int nameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DISPLAY_NAME
                );
                int durationColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DURATION
                );

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long duration = cursor.getLong(durationColumn);

                    Uri videoUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    );

                    JSObject video = new JSObject();
                    video.put("id", id);
                    video.put("name", name);
                    video.put("duration", duration);
                    video.put("uri", videoUri.toString());

                    videos.put(video);
                }
            }

            JSObject result = new JSObject();
            result.put("videos", videos);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Unable to load videos.", e);
        }
    }
}
