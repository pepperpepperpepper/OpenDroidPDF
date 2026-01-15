package org.opendroidpdf.app.helpers;

/**
 * Central request/permission codes for the main activity.
 */
public final class RequestCodes {
    private RequestCodes() {}

    // Activity results
    public static final int OUTLINE = 0;
    public static final int PRINT = 1;
    public static final int FILE_PICK = 2;
    public static final int SAVE_AS = 3;
    public static final int EDIT = 4;
    public static final int IMPORT_ANNOTATIONS = 5;
    public static final int SAVE_LINEARIZED = 6;
    public static final int SAVE_ENCRYPTED = 7;

    // Runtime permission requests
    public static final int STORAGE_PERMISSION = 1001;
    public static final int MANAGE_STORAGE = 1002;
}
