package com.kuixotic.egnyte;

import java.util.Locale;
import android.os.Build.VERSION;
import android.os.Environment;

public class Constants {
	final public static int SDK_INT= android.os.Build.VERSION.SDK_INT;

	final public static String HELP_URL = "http://www.egnyte.com/corp/mobile/mobile_android_help.html";
	final public static String USER_AGENT = "PublicEgnyte/1.0 (Linux; Android; " + VERSION.SDK + "; " + Locale.getDefault() + ")";
	final public static String SERVER = ".egnyte.";
	final public static String SD_CLOUD =  Environment.getExternalStorageDirectory().getPath()+"/EgnyteMobileCloud/";

	
	public static final String UPLOAD_VIEW = "uploadView";
	public static final String UPDATE_DIR = "directoryUpdate";
	public static final String DOMAIN = "domainName";
	public static final String TOKEN_TYPE = "tokenType";
	public static final String ACCESS_TOKEN = "password";
	public static final String CLIENT_ID = "clientId";
	public static final String IS_SSO_USER = "isSsoUser";
	
	public static final int FILE_UPLOAD_ACTIVITY = 3;
	public static final int FILE_OPEN_REQUEST = 5;
	public static final int CREATE_FOLDER_REQUEST = 7;
	public static final int COPY_PATH_ACTIVITY = 14;
	public static final int MOVE_PATH_ACTIVITY = 15;
	
	final public static String HEADER_AUTH = "Authorization";
	public static final String _PATH = "rowPath";
	public static final String _DEST_PATH = "destPath";
	
	public static final String PID = "parentID";
	public static final String FILENAME = "egnyteLocalCloud";
	public static final String ACTION_TYPE = "actionType";
	public static final String FILE_NAME = "fileName";
	public static final String FILE_PATH = "filePath";
	public static final String FILE_UPLOAD_PATH = "fileUploadPath";
	public static final String FILE_EID = "fileEID";
	public static final String EXCEPTION_MSG = "exception";
	
	final public static int MAIN_APP = 333;
	final public static int ACTIVITY_SELECT_IMAGE = 1341;
	
	public static final int ROW_ID = 0;
	public static final int EID = 1;
	public static final int SERVER_TIMESTAMP = 2;
	public static final int ROW_NAME = 3;
	public static final int PARENT_ID = 4;
	public static final int SIZE = 5;
	public static final int ROW_IS_FOLDER = 6;
	public static final int FULL_PATH = 7;
	public static final int ROW_OWNER = 8;

	public static final int PLACE_HOLDER = -999;
}