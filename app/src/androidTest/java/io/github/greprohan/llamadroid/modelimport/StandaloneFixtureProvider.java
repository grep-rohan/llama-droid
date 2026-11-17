package io.github.greprohan.llamadroid.modelimport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Test-package process: Android/JDK dependencies only, never the target APK's classpath. */
public final class StandaloneFixtureProvider extends ContentProvider {
    private final Set<String> ids = new HashSet<>(Arrays.asList("regular", "slice", "pipe", "pipe-error",
        "null-open", "missing", "empty", "null-query", "overflow", "unknown", "denied"));
    private static void check(boolean value) { if (!value) throw new IllegalStateException("Invalid fixture state"); }
    private static void require(boolean value) { if (!value) throw new IllegalArgumentException("Invalid fixture request"); }
    private Context caller() {
        Context ctx = Objects.requireNonNull(getContext());
        int uid = Binder.getCallingUid();
        try {
            int target = ctx.getPackageManager().getPackageUid("io.github.greprohan.llamadroid", 0);
            check(uid == ctx.getApplicationInfo().uid || (uid == target && ctx.getPackageManager()
                .checkSignatures(uid, ctx.getApplicationInfo().uid) == PackageManager.SIGNATURE_MATCH));
        } catch (PackageManager.NameNotFoundException failure) { throw new SecurityException("Fixture caller denied"); }
        return ctx;
    }
    private String fixture(Uri uri) {
        Context ctx = caller();
        require("content".equals(uri.getScheme()) && (ctx.getPackageName() + ".modelimport.fixture").equals(uri.getAuthority())
            && uri.getQuery() == null && uri.getFragment() == null && uri.getPathSegments().size() == 2
            && "fixture".equals(uri.getPathSegments().get(0)));
        String id = uri.getPathSegments().get(1); require(ids.contains(id));
        if ("denied".equals(id)) throw new SecurityException("Fixture denied");
        return id;
    }
    private static byte[] payload() {
        byte[] bytes = new byte[50];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 17);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{'G','G','U','F'}).putInt(3).putLong(0).putLong(0);
        return bytes;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String id = fixture(uri);
        if ("null-query".equals(id)) return null;
        String[] columns = "missing".equals(id) ? new String[]{"unused"} : projection == null
            ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        if (!"empty".equals(id)) {
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "fixture.gguf";
                if (OpenableColumns.SIZE.equals(columns[i])) row[i] = "unknown".equals(id) ? null
                    : "overflow".equals(id) ? "9223372036854775808" : 50L;
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order, CancellationSignal signal) {
        if (signal != null) signal.throwIfCanceled(); return query(uri, projection, selection, args, order);
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (signal != null) signal.throwIfCanceled(); return openAssetFile(uri, mode);
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        String id = fixture(uri); require("r".equals(mode));
        if ("null-open".equals(id)) return null;
        File file = null; ParcelFileDescriptor descriptor = null;
        try {
            file = File.createTempFile("model-import-fixture-", ".bin", Objects.requireNonNull(getContext()).getCacheDir());
            try (FileOutputStream output = new FileOutputStream(file)) {
                if ("slice".equals(id)) output.write(new byte[]{1,2,3,4,5});
                output.write(payload()); output.write(new byte[]{6,7,8});
            }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); check(file.delete());
            return new AssetFileDescriptor(descriptor, "slice".equals(id) ? 5L : 0L, 50L);
        } catch (IOException | RuntimeException failure) {
            if (descriptor != null) try { descriptor.close(); } catch (IOException close) { failure.addSuppressed(close); }
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw fileFailure(failure);
        } finally { if (file != null && file.exists()) check(file.delete()); }
    }
    private static FileNotFoundException fileFailure(Exception cause) {
        FileNotFoundException result = new FileNotFoundException("Generated fixture I/O failed"); result.initCause(cause); return result;
    }
    @Override public String getType(Uri uri) { fixture(uri); return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
