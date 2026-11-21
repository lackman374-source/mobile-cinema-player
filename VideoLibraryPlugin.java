package com.sanku.mobilecinema;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;

import androidx.core.content.ContextCompat;

import androidx.core.content.ContextCompat;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.ByteArrayOutputStream;

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
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED
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
                int takenColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_TAKEN
                );
                int addedColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_ADDED
                );

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long duration = cursor.getLong(durationColumn);
                    long taken = cursor.getLong(takenColumn);
                    long added = cursor.getLong(addedColumn);

                    // Date taken is in milliseconds; date added is in seconds.
                    long date = taken > 0 ? taken : added * 1000L;

                    Uri videoUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    );

                    JSObject video = new JSObject();
                    video.put("id", id);
                    video.put("name", name);
                    video.put("duration", duration);
                    video.put("date", date);
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

    @PluginMethod
    public void getThumbnail(PluginCall call) {
        Long id = call.getLong("id");
        if (id == null) {
            call.reject("Missing video id.");
            return;
        }

        if (!hasVideoAccess()) {
            call.reject("Video permission was not granted.");
            return;
        }

        try {
            Uri videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            );

            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= 29) {
                bitmap = getContext().getContentResolver().loadThumbnail(
                    videoUri,
                    new Size(320, 320),
                    null
                );
            } else {
                bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                    getContext().getContentResolver(),
                    id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                );
            }

            if (bitmap == null) {
                call.reject("No thumbnail available.");
                return;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out);
            String base64 = Base64.encodeToString(
                out.toByteArray(),
                Base64.NO_WRAP
            );

            JSObject result = new JSObject();
            result.put("data", "data:image/jpeg;base64," + base64);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Unable to load thumbnail.", e);
        }
    }
}
