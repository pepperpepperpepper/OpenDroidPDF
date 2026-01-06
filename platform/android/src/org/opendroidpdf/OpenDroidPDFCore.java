package org.opendroidpdf;
import java.util.ArrayList;

import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Base64;
import android.content.pm.PackageManager;
import android.content.UriPermission;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap.Config;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.lang.OutOfMemoryError;
import java.io.FileNotFoundException;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Environment;
import android.content.pm.PackageManager;
import android.content.Intent;

public class OpenDroidPDFCore extends MuPDFCore
{
    private Uri uri = null;
    private File tmpFile = null;
    private static final String TAG = "OpenDroidPDFCore";
    private static final long CACHE_PRUNE_THRESHOLD_MS = 3L * 24L * 60L * 60L * 1000L; // prune temp copies older than ~3 days

        /* File IO is terribly inconsistent and badly documented on Android
         * to make matters worse the native part of the Core stops beeing
         * useful once the method saveInternal() is call by MuPDFCore.
         * Here we try to abstract away the complexity this brings with it
         * by implementing three methods export() save() and saveAs() that
         * try to do what one would expect such methods to do.
         * Unoftunately this leads to terribly messy code that is really
         * hard to maintain...
         */
    
    public OpenDroidPDFCore(Context context, Uri uri) throws Exception
        {
            init(context, uri);
        }
    
    
    public synchronized void init(Context context, Uri uri) throws Exception
        {
            this.uri = uri;

            final boolean isFileUri = "file".equalsIgnoreCase(uri.getScheme());
            final boolean isContentUri = "content".equalsIgnoreCase(uri.getScheme());
            final String decodedPath = uri.getEncodedPath() != null ? Uri.decode(uri.getEncodedPath()) : null;
            final File targetFile = decodedPath != null ? new File(decodedPath) : null;

                /*Sometimes we can open a uri both as a file and via a content provider. On old versions of Android the former works better, whereas on new versions the latter works generally better. Hence we switch the order in which we try depending on the Android version.*/
            
            if(isFileUri && targetFile != null && targetFile.isFile())
            {
                    // Allow direct access only when the file lives in app-private storage or we run on pre-Marshmallow devices.
                if((Build.VERSION.SDK_INT < 23) || isPathInsideAppStorage(context, targetFile))
                {
                    super.init(context, targetFile.getAbsolutePath());
                    return;
                }
            }

            if (isContentUri || (isFileUri && targetFile != null && targetFile.isFile()))
            {
                File previousTemp = tmpFile;
                File materialized = materializeToCache(context, uri, isFileUri ? targetFile : null);
                tmpFile = materialized;
                cleanupPreviousMaterialization(previousTemp, tmpFile, new File(context.getCacheDir(), "content"));
                super.init(context, materialized.getAbsolutePath());
                return;
            }

            if (targetFile != null && targetFile.isFile())
            {
                super.init(context, targetFile.getAbsolutePath());
                return;
            }
        }

    private boolean isPathInsideAppStorage(Context context, File path)
    {
        try
        {
            File filesDir = context.getFilesDir();
            File cacheDir = context.getCacheDir();
            return isChildOf(path, filesDir) || isChildOf(path, cacheDir);
        }
        catch(Exception e)
        {
            return false;
        }
    }

    private boolean isChildOf(File child, File possibleParent) throws Exception
    {
        if (child == null || possibleParent == null)
            return false;
        File parent = child.getCanonicalFile();
        File targetParent = possibleParent.getCanonicalFile();
        while (parent != null)
        {
            if (parent.equals(targetParent))
                return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    private File materializeToCache(Context context, Uri uri, File fileFallback) throws Exception
    {
        String displayName = getFileName(context, uri);
        if (displayName == null || displayName.trim().length() == 0)
        {
            if (fileFallback != null)
                displayName = fileFallback.getName();
            else
                displayName = "document.pdf";
        }
        displayName = displayName.replace('/', '_').replace('\\', '_');

        File cacheRoot = new File(context.getCacheDir(), "content");
        if (!cacheRoot.exists() && !cacheRoot.mkdirs())
            throw new Exception("unable to create cache root at " + cacheRoot.getAbsolutePath());

        File uniqueDir = null;
        for (int attempt = 0; attempt < 32; attempt++)
        {
            File candidate = new File(cacheRoot, UUID.randomUUID().toString());
            if (!candidate.exists() && candidate.mkdirs())
            {
                uniqueDir = candidate;
                break;
            }
        }
        if (uniqueDir == null)
            throw new Exception("unable to create temporary directory for " + uri.toString());

        File contentCache = new File(uniqueDir, displayName);

        InputStream is = null;
        OutputStream os = null;
        ParcelFileDescriptor pfd = null;
        try
        {
            if ("content".equalsIgnoreCase(uri.getScheme()))
            {
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null)
                    is = new FileInputStream(pfd.getFileDescriptor());
                if (is == null)
                    is = context.getContentResolver().openInputStream(uri);
            }
            else if ("file".equalsIgnoreCase(uri.getScheme()))
            {
                if (fileFallback == null)
                    throw new Exception("unable to resolve file fallback for uri " + uri.toString());
                try
                {
                    is = new FileInputStream(fileFallback);
                }
                catch (SecurityException | FileNotFoundException fileException)
                {
                    ParcelFileDescriptor alternativePfd = openFileDescriptorForFileUri(context, fileFallback);
                    if (alternativePfd != null)
                    {
                        pfd = alternativePfd;
                        is = new FileInputStream(pfd.getFileDescriptor());
                    }
                    else
                    {
                        throw fileException;
                    }
                }
            }
            else
            {
                throw new Exception("unsupported uri scheme " + uri.getScheme());
            }

            if (is == null)
                throw new Exception("unable to open input stream to uri " + uri.toString());

            os = new FileOutputStream(contentCache, false);
            copyStream(is, os);
        }
        catch (SecurityException | FileNotFoundException securityException)
        {
            deleteRecursively(contentCache);
            deleteRecursively(uniqueDir);
            throw new Exception("Unable to read \"" + uri.toString() + "\". Please re-select the document using the system file picker.", securityException);
        }
        finally
        {
            try { if (os != null) os.close(); } catch (Exception ignore) {}
            try { if (is != null) is.close(); } catch (Exception ignore) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignore) {}
        }

        long now = System.currentTimeMillis();
        uniqueDir.setLastModified(now);
        contentCache.setLastModified(now);
        pruneOldCacheDirs(cacheRoot, uniqueDir, now);

        return contentCache;
    }

    private ParcelFileDescriptor openFileDescriptorForFileUri(Context context, File file)
    {
        if (file == null)
            return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            try
            {
                ParcelFileDescriptor docPfd = openFileDescriptorViaDocumentsContract(context, file);
                if (docPfd != null)
                    return docPfd;
            }
            catch (Exception ignored)
            {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            try
            {
                ParcelFileDescriptor mediaStorePfd = openFileDescriptorViaMediaStore(context, file);
                if (mediaStorePfd != null)
                    return mediaStorePfd;
            }
            catch (Exception ignored)
            {
            }
        }

        return null;
    }

    private ParcelFileDescriptor openFileDescriptorViaDocumentsContract(Context context, File file) throws FileNotFoundException
    {
        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot == null)
            return null;

        String rootPath = externalRoot.getAbsolutePath();
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.startsWith(rootPath))
            return null;

        String relativePath = absolutePath.substring(rootPath.length());
        if (relativePath.startsWith(File.separator))
            relativePath = relativePath.substring(1);

        try
        {
            String documentId = "primary:" + relativePath.replace(File.separatorChar, '/');
            Uri documentUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId);
            return context.getContentResolver().openFileDescriptor(documentUri, "r");
        }
        catch (IllegalArgumentException ignored)
        {
            return null;
        }
    }

    private ParcelFileDescriptor openFileDescriptorViaMediaStore(Context context, File file) throws FileNotFoundException
    {
        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot == null)
            return null;

        String rootPath = externalRoot.getAbsolutePath();
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.startsWith(rootPath))
            return null;

        String relativePath = absolutePath.substring(rootPath.length());
        if (relativePath.startsWith(File.separator))
            relativePath = relativePath.substring(1);

        int lastSlash = relativePath.lastIndexOf('/');
        String parent = lastSlash >= 0 ? relativePath.substring(0, lastSlash + 1) : "";
        String name = lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;

        Uri filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        Cursor cursor = null;
        try
        {
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{parent, name};
            cursor = context.getContentResolver().query(filesUri, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst())
            {
                long id = cursor.getLong(0);
                Uri contentUri = ContentUris.withAppendedId(filesUri, id);
                return context.getContentResolver().openFileDescriptor(contentUri, "r");
            }
        }
        finally
        {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

    private void cleanupPreviousMaterialization(File previousTemp, File currentTemp, File cacheRoot)
    {
        if (previousTemp == null || cacheRoot == null)
            return;
        if (currentTemp != null && previousTemp.equals(currentTemp))
            return;
        try
        {
            if (isChildOf(previousTemp, cacheRoot))
            {
                File parent = previousTemp.getParentFile();
                if (parent != null)
                    deleteRecursively(parent);
                else
                    previousTemp.delete();
            }
        }
        catch (Exception ignore)
        {
        }
    }

    private void pruneOldCacheDirs(File cacheRoot, File activeDir, long now)
    {
        if (cacheRoot == null || !cacheRoot.exists())
            return;
        File[] children = cacheRoot.listFiles();
        if (children == null)
            return;
        for (File child : children)
        {
            if (child == null || !child.isDirectory())
                continue;
            if (activeDir != null && child.equals(activeDir))
                continue;
            long age = now - child.lastModified();
            if (age > CACHE_PRUNE_THRESHOLD_MS)
            {
                deleteRecursively(child);
            }
        }
    }

    private void deleteRecursively(File fileOrDir)
    {
        if (fileOrDir == null || !fileOrDir.exists())
            return;
        if (fileOrDir.isDirectory())
        {
            File[] children = fileOrDir.listFiles();
            if (children != null)
            {
                for (File child : children)
                {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDir.delete();
    }

    public synchronized Uri export(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            Uri oldUri = this.uri;
            String oldPath = getPath();
            String oldFileName = getFileName();
            boolean oldHasChanges = hasChanges();
            
                //If no tmpflie has been created or the file name has changed,
                //creat a new tmpFile and, if appropriate, remeber the old tmpFile
                //to delete it after the core has saved to the new location. 
            File oldTmpFile = null;
            boolean needsNewTmp = (tmpFile == null) || !tmpFile.getName().equals(oldFileName);
            // Also rotate if the current tmpFile lives under the materialized content cache;
            // exports should go to cache/tmpfiles to avoid clobbering materialized source.
            if (!needsNewTmp) {
                try {
                    File cacheContentRoot = new File(context.getCacheDir(), "content").getCanonicalFile();
                    File current = tmpFile.getCanonicalFile();
                    needsNewTmp = isChildOf(current, cacheContentRoot);
                } catch (Exception ignore) { needsNewTmp = true; }
            }
            if (needsNewTmp)
            {
                oldTmpFile = tmpFile;
                File cacheDir = new File(context.getCacheDir(), "tmpfiles");
                cacheDir.mkdirs();
                File uniqueDirInCacheDir = null;
                int i = 0;
                do
                {
                    uniqueDirInCacheDir = new File(cacheDir, Integer.toString(i));
                    i++;
                }
                while(uniqueDirInCacheDir == null || uniqueDirInCacheDir.exists());
                
                uniqueDirInCacheDir.mkdirs();
                tmpFile = new File(uniqueDirInCacheDir, oldFileName);
            }
            
            // Native saveAsInternal returns 1 on success, 0 on failure.
            if(super.saveAs(tmpFile.getPath()) == 0)
                throw new java.io.IOException("native code failed to save to tmp file: "+tmpFile.getPath());

                //Delete old tmp file if we created a new one
            if(oldTmpFile!=null)
                oldTmpFile.delete();
            
                //reinit because the MuPDFCore core gets useless after saveIntenal()
            init(context, Uri.fromFile(tmpFile)); 
                //But now the Uri, as well as mFilenName and mPath in the superclass are wrong, so we repair this
            uri = oldUri;
            relocate(oldPath, oldFileName);
            setHasAdditionalChanges(oldHasChanges);
            
            return FileProvider.getUriForFile(context, "org.opendroidpdf.fileprovider", tmpFile);
        }
    
    public synchronized void save(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            saveAs(context, this.uri);
        }

    public synchronized void saveAs(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            ParcelFileDescriptor pfd = null;
            FileOutputStream fileOutputStream = null;
            FileInputStream fileInputStream = null;
            try
            {
                    //Export to tmpFile
                export(context);
                
                    //Open the result as fileInputStream
                fileInputStream = new FileInputStream(tmpFile);
                
                    //Open FileOutputStream to actual destination
                try
                {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                    if(pfd != null)
                        fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                }
                catch(Exception e)
                {
                    String path = uri.getPath();
                    File file = null;
                    if(path != null)
                        file = new File(path);
                    if(file != null)
                        fileOutputStream = new FileOutputStream(file);
                }
                finally
                {
                    if(fileOutputStream == null)
                        throw new java.io.IOException("Unable to open output stream to given uri: "+uri);
                }
                copyStream(fileInputStream,fileOutputStream);
//                Log.i(context.getString(R.string.app_name), "copyStream() succesfull");
            }
            catch (java.io.FileNotFoundException e) 
            {
                Log.e(TAG, "Exception for uri=" + uri);
                throw e;
            }
            catch (java.io.IOException e)
            {
                throw e;
            }
            finally
            {
                if(fileInputStream != null) fileInputStream.close();
                if(fileOutputStream != null) fileOutputStream.close();
                if(pfd != null) pfd.close();
            }
                //remeber the new uri and tell the core that all changes were saved
            this.uri = uri;
            
            relocate(uri.getPath(), getFileName(context, uri));
            
            setHasAdditionalChanges(false);
        }
    
    private synchronized static void copyStream(InputStream input, OutputStream output)
        throws java.io.IOException
        {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while((bytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, bytesRead);
            }
        }
    
    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToUriViaContentResolver(T context, Uri uri) {
        
        boolean haveWritePermissionToUri = false;
        try
        {
            for(TemporaryUriPermission permission : (context).getTemporaryUriPermissions()) {
//                Log.i(context.getString(R.string.app_name), "checking saved temporary permission for "+permission.getUri()+" while uri="+uri+" write permission is "+permission.isWritePermission()+" and uris are equal "+permission.getUri().equals(uri));
                if(permission.isWritePermission() && permission.getUri().equals(uri))
                {
                    haveWritePermissionToUri = true;
                    break;
                }
            }
            if(!haveWritePermissionToUri)
            {
                if (android.os.Build.VERSION.SDK_INT >= 19)
                {
                    for(UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                        if(permission.isWritePermission() && permission.getUri().equals(uri))
                        {
                            haveWritePermissionToUri = true;
                            break;
                        }
                    }
                }
                else
                {
                    if(context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    {
                        haveWritePermissionToUri = true;
                    }
                }
            }
        }
        catch(Exception e)
        {
            Log.e(context.getString(R.string.app_name), "exception while trying to figure out permissions: "+e);
            return false;
        }
        if(!haveWritePermissionToUri)
            return false;

            // If we have write permission, treat the URI as writable.
            //
            // Probing writability by opening an output stream/file descriptor is unreliable:
            // many SAF providers do not support "rw"/"wa" even though "w" (used by saveAs())
            // works. If saving later fails (revoked permission, provider error), we surface it
            // and fall back to Save As.
        return true;
    }
    
    public synchronized boolean canSaveToUriAsFile(Context context, Uri uri) {
        try
        {
                //The way we use here to determine whether we can write to a file is error prone but I have so far not found a better way
            if(uri.toString().startsWith("content:"))
                return false;
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.exists() && file.isFile() && file.canWrite())
                return true;
            else
                return false;
        }
        catch(Exception e)
        {
            return false;
        }
    }

    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToCurrentUri(T context) {
        return canSaveToUriViaContentResolver(context, getUri()) || canSaveToUriAsFile(context, getUri());
    }    

    public synchronized Uri getUri(){
        return uri;
    }
    
    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(tmpFile != null)
        {
            File cacheDirFile = (cachDir != null) ? new File(cachDir) : null;
            boolean deleted = false;
            if (cacheDirFile != null)
            {
                try
                {
                    File contentRoot = new File(cacheDirFile, "content");
                    File tmpfilesRoot = new File(cacheDirFile, "tmpfiles");
                    if (isChildOf(tmpFile, contentRoot) || isChildOf(tmpFile, tmpfilesRoot))
                    {
                        File parent = tmpFile.getParentFile();
                        if (parent != null && parent.exists() && parent.getParentFile() != null)
                        {
                            deleteRecursively(parent);
                            deleted = true;
                        }
                    }
                }
                catch (Exception ignore)
                {
                }
            }
            if (!deleted)
            {
                tmpFile.delete();
            }
            tmpFile = null;
        }
    }

    public synchronized boolean deleteDocument(Context context) {
        try
        {
            context.getContentResolver().delete(uri, null, null);
        }
        catch(Exception e)
        {
            try
            {
                File file = new File(Uri.decode(uri.getEncodedPath()));
                file.delete();
            }
            catch(Exception e2)
            {
                return false;
            }
        }
        return true;
    }
        
    
    public synchronized static void createEmptyDocument(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException {
        FileOutputStream fileOutputStream = null;
        try
        {
            String path = uri.getPath();
            File file = null;
            if(path != null)
                file = new File(path);
            if(file != null)
                fileOutputStream = new FileOutputStream(file);        

            if(fileOutputStream == null)
                throw new java.io.IOException("Unable to open output stream to given uri: "+uri.getPath());

            String newline = System.getProperty ("line.separator");
            String minimalPDF =
                "%PDF-1.1"+newline+
                "\u00a5\u00b1\u00eb"+newline+
                "1 0 obj "+newline+
                "<<"+newline+
                "/Type /Catalog"+newline+
                "/Pages 2 0 R"+newline+
                ">>"+newline+
                "endobj "+newline+
                "2 0 obj "+newline+
                "<<"+newline+
                "/Kids [3 0 R]"+newline+
                "/Type /Pages"+newline+
                "/MediaBox [0 0 595 841]"+newline+
                "/Count 1"+newline+
                ">>"+newline+
                "endobj "+newline+
                "3 0 obj "+newline+
                "<<"+newline+
                "/Resources "+newline+
                "<<"+newline+
                "/Font "+newline+
                "<<"+newline+
                "/F1 "+newline+
                "<<"+newline+
                "/Subtype /Type1"+newline+
                "/Type /Font"+newline+
                "/BaseFont /Times-Roman"+newline+
                ">>"+newline+
                ">>"+newline+
                ">>"+newline+
                "/Parent 2 0 R"+newline+
                "/Type /Page"+newline+
                "/MediaBox [0 0 595 841]"+newline+
                ">>"+newline+
                "endobj xref"+newline+
                "0 4"+newline+
                "0000000000 65535 f "+newline+
                "0000000015 00000 n "+newline+
                "0000000066 00000 n "+newline+
                "0000000149 00000 n "+newline+
                "trailer"+newline+
                ""+newline+
                "<<"+newline+
                "/Root 1 0 R"+newline+
                "/Size 4"+newline+
                ">>"+newline+
                "startxref"+newline+
                "314"+newline+
                "%%EOF"+newline;
            byte[] buffer = minimalPDF.getBytes();
            fileOutputStream.write(buffer, 0, buffer.length);
        }
        catch (java.io.FileNotFoundException e) 
        {
            throw e;
        }
        catch (java.io.IOException e)
        {
            throw e;
        }
        finally
        {
            if(fileOutputStream != null) fileOutputStream.close();
        }
    }

    @Override
    public synchronized boolean insertBlankPageBefore(int position) {
        setHasAdditionalChanges(true);
        return super.insertBlankPageBefore(position);
    }


    public static synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canReadFromUri(T context, Uri uri) {
        boolean haveReadPermissionToUri = false;
        try
        {
            if (android.os.Build.VERSION.SDK_INT >= 19)
            {
                for(UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                    if(permission.isReadPermission() && permission.getUri().equals(uri))
                    {
                        haveReadPermissionToUri = true;
                        break;
                    }
                }
            }

            if(!haveReadPermissionToUri) {
                if(context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    haveReadPermissionToUri = true;
            }
        }
        catch(Exception e)
        {
//            Log.i(context.getString(R.string.app_name), "exception while trying to figure out permissions: "+e);
            return false;
        }
        
        if(!haveReadPermissionToUri && uri.toString().startsWith("file://") )
        {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.isFile() && file.isFile() && file.canRead())
                haveReadPermissionToUri = true;
        }
        return haveReadPermissionToUri;
    }

    public synchronized String getFileName(Context context, Uri uri) {
        String displayName = null;
        if (uri.toString().startsWith("content://")) //Uri points to a content provider
        {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null); //This should be done asynchonously

            if (cursor != null && cursor.moveToFirst())
            {
                    //Try to get the display name/title
                int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if(displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
                if(displayName==null)
                {
                    int titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
                    if(titleIndex >= 0) displayName = cursor.getString(titleIndex);
                }       
                cursor.close();
            }
            
                //Some programms encode parts of the filename in utf-8 base 64 encoding if the filename contains special charcters. This can look like this: '=?UTF-8?B?[text here]==?=' Here we decode such cases:
            if(displayName!=null)
            {
                Pattern utf8BPattern = Pattern.compile("=\\?UTF-8\\?B\\?(.+)\\?=");
                Matcher matcher = utf8BPattern.matcher(displayName);
                while (matcher.find()) {
                    String base64 = matcher.group(1);
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    String decodedText = "";
                    try
                    {
                        decodedText = new String(data, "UTF-8");
                    }
                    catch(Exception e)
                    {}
                    displayName = displayName.replace(matcher.group(),decodedText);
                }
            }
        } else {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.isFile())
                displayName = file.getName();
        }
        
        if(displayName==null || displayName.equals(""))
            displayName=context.getString(R.string.unknown_file_name);
        
        return displayName;
    }
}
