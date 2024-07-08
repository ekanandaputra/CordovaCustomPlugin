package ntech.custom.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.widget.Toast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import org.apache.cordova.PluginResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.IOException;

import android.os.Environment;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import android.util.Base64;

import android.database.Cursor;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.content.ContentUris;

import androidx.annotation.NonNull;

import org.apache.cordova.*;

import java.util.Arrays;
import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.LogCallback;
import com.arthenica.mobileffmpeg.LogMessage;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;


/**
 * This class echoes a string called from JavaScript.
 */
public class CordovaCustomPlugin extends CordovaPlugin {

   private static final int PICK_VIDEO_REQUEST = 1;
    private CallbackContext callbackContext;
  private static final String TAG = "YourPlugin";
    private static final String FFMPEG_BINARY = "ffmpeg";
    private static final String FFMPEG_DIR = "ffmpeg"; // Path relative to plugin

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("showShortToast".equals(action)) {
            String message = args.getString(0);
            showToast(message);
            callbackContext.success(message);
            return true;
        }

        if (action.equals("pickVideo")) {
            pickVideo();
            this.callbackContext = callbackContext;
            return true;
        }

        return false;
        
    }

    private void showToast(final String message) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(cordova.getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

     private void pickVideo() {
        Intent intent = new Intent();
        intent.setTypeAndNormalize("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        cordova.startActivityForResult(this, Intent.createChooser(intent, "Select Video"), PICK_VIDEO_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            Uri selectedVideoUri = intent.getData();
            if (selectedVideoUri != null) {
                // Uri fileUri = Uri.parse(fileUriString);
                // File file = new File(fileUri.getPath());
                // String fullPath = file.getAbsolutePath();

                String yourRealPath = getPath(cordova.getContext(), selectedVideoUri);
                executeCompressCommand(yourRealPath, callbackContext);
            } else {
                callbackContext.error("Failed to pick video");
            }
        } else {
            callbackContext.error("Video picking cancelled");
        }
    }

    public static String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String getDataColumn(@NonNull Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    private static boolean isExternalStorageDocument(@NonNull Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(@NonNull Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(@NonNull Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

     private void executeCompressCommand(String yourRealPath, CallbackContext callbackContext) {
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String filePrefix = "compress_video";
        String fileExtn = ".mp4";
        File dest = new File(moviesDir, filePrefix + fileExtn);
        int fileNo = 0;
        while (dest.exists()) {
            fileNo++;
            dest = new File(moviesDir, filePrefix + fileNo + fileExtn);
        }

        Log.d("TAG", "startTrim: src: " + yourRealPath);
        Log.d("TAG", "startTrim: dest: " + dest.getAbsolutePath());
        String filePath = dest.getAbsolutePath();
        String[] complexCommand = {"-y", "-i", yourRealPath, "-s", "480x360", "-r", "25", "-vcodec", "mpeg4", "-b:v", "150k", "-b:a", "48000", "-ac", "2", "-ar", "22050", filePath};
        execFFmpegBinary(complexCommand, callbackContext, filePath);
    }

    private void execFFmpegBinary(final String[] command, final CallbackContext callbackContext, final String filePath) {
        Config.enableLogCallback(new LogCallback() {
            @Override
            public void apply(LogMessage message) {
                Log.e(Config.TAG, message.getText());
            }
        });
        Config.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics newStatistics) {
                Log.e(Config.TAG, String.format("frame: %d, time: %d", newStatistics.getVideoFrameNumber(), newStatistics.getTime()));
                Log.d("TAG", "Started command : ffmpeg " + Arrays.toString(command));
                Log.d("TAG", "progress : " + newStatistics.toString());
            }
        });
        Log.d("TAG", "Started command : ffmpeg " + Arrays.toString(command));

        long executionId = FFmpeg.executeAsync(command, (executionId1, returnCode) -> {
            if (returnCode == Config.RETURN_CODE_SUCCESS) {
                Log.d("TAG", "Finished command : ffmpeg " + Arrays.toString(command));
                String base64String = convertFileToBase64(filePath);
                if (base64String != null) {
                    callbackContext.success(base64String);
                } else {
                    callbackContext.error("Failed to convert video to Base64.");
                }
            } else if (returnCode == Config.RETURN_CODE_CANCEL) {
                Log.e("TAG", "Async command execution cancelled by user.");
                callbackContext.error("Compression cancelled.");
            } else {
                Log.e("TAG", String.format("Async command execution failed with returnCode=%d.", returnCode));
                callbackContext.error("Compression failed.");
            }
        });
        Log.e("TAG", "execFFmpegMergeVideo executionId-" + executionId);
    }

        private String convertFileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fileInputStream.read(bytes);
            fileInputStream.close();
            String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
            return "data:video/mp4;base64," + base64;
        } catch (IOException e) {
            Log.e("TAG", "Error converting file to Base64", e);
            return null;
        }
    }

}
