package com.sanku.mobilecinema;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(3);

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

    thumbExecutor.execute(() -> {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            Uri videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            );

            Bitmap bitmap = null;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    bitmap = getContext().getContentResolver().loadThumbnail(
                        videoUri,
                        new Size(320, 320),
                        null
                    );
                } catch (Exception ignored) {}
            } else {
                try {
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        getContext().getContentResolver(),
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    );
                } catch (Exception ignored) {}
            }

            if (bitmap == null) {
                try {
                    retriever.setDataSource(getContext(), videoUri);
                    if (Build.VERSION.SDK_INT >= 27) {
                        bitmap = retriever.getScaledFrameAtTime(
                            0L,
                            MediaMetadataRetriever.OPTION_PREVIOUS_SYNC,
                            320,
                            320
                        );
                    } else {
                        bitmap = retriever.getFrameAtTime(
                            0L,
                            MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
                        );
                    }
                } catch (Exception ignored) {}
            }

            if (bitmap == null) {
                call.reject("No thumbnail available.");
                return;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out);
            bitmap.recycle();

            String base64 = Base64.encodeToString(
                out.toByteArray(),
                Base64.NO_WRAP
            );

            JSObject result = new JSObject();
            result.put("data", "data:image/jpeg;base64," + base64);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Unable to load thumbnail.", e);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    });
}

        @Override
    protected void handleOnDestroy() {
        thumbExecutor.shutdownNow();
        super.handleOnDestroy();
    }
}
