package com.kuixotic.egnyte;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.util.Log;

public class Utils {
	final public static String ELLIPSE = "...";
	
	@SuppressWarnings({ "rawtypes", "serial" })
	public static class SeparateByFolderVsFile implements java.util.Comparator, Serializable {
		@SuppressWarnings("unchecked")
		@Override
		public int compare(Object object1, Object object2) {
			ArrayList<Object> o1 = (ArrayList<Object>) object1;
			ArrayList<Object> o2 = (ArrayList<Object>) object2;
			
			return (Integer)o2.get(Constants.ROW_IS_FOLDER) - (Integer)o1.get(Constants.ROW_IS_FOLDER);
		}
	}

	//this will neutralize url with parameters and its encoding issues
	static String getURLWithParameter(String apiPath, String param, SharedPreferences userInfo) throws URISyntaxException { 
		String domainPath = userInfo.getString(Constants.DOMAIN, "")+Constants.SERVER+"com";
		 
		URI uriPath = new URI("https",domainPath,apiPath,param,null);
		return uriPath.toString();
    }
	
	static String getURL(String apiPath, SharedPreferences userInfo) throws URISyntaxException { 

    	return Utils.getURLWithParameter(apiPath,null,userInfo);
    }
	
	//only delete unedited local files
	static boolean removePathAndChildren(File directory, EgnyteAppObject eao, SharedPreferences userInfo) {
		
		  if (directory == null) {
		    return false;
		  }
		  if (!directory.exists()) {
		    return true;
		  }
		  if (!directory.isDirectory()) {
		    return false;
		  }
		  String[] list = directory.list();

		  if (list != null) {
			  for (int i = 0; i < list.length; i++) {
			      File entry = new File(directory, list[i]);
	
			      if (entry.isDirectory()) {
			    	  if (!Utils.removePathAndChildren(entry, eao, userInfo)) {
			    		  return false;
			    	  }
			      } else { //is file
			    	  String actualFilePath = entry.getPath().replace(Constants.SD_CLOUD+userInfo.getString(Constants.DOMAIN, ""), "");
			    	  ArrayList<Object> dbEntry = (eao.dirDbHelper.getRowAtAbsolutePath(actualFilePath));
			    	  
			    	  if(dbEntry.size()!=0) {
			    		  if (!entry.delete()) {
			    			  return false;
			    		  }
			    	  }//if it doesn't exist in db, do not delete; might be personal data  
			      }
			    }
		  }

		  return directory.delete();
	}
		
	//insert/update the fresh JSON into local db
		public static boolean processFreshDirectoryJSON(String dirSnapshot, String parentPath, EgnyteAppObject eao, SharedPreferences userInfo) {
			try {
				JSONObject snapShot = new JSONObject(dirSnapshot);

				int pid = (Integer) eao.dirDbHelper.getRowAtAbsolutePath(parentPath).get(Constants.ROW_ID);
				ArrayList<ArrayList<Object>> localDbRowsList = eao.dirDbHelper.getRowsAsArraysByPID(pid);
				HashMap<String, ArrayList<Object>> localDbDictionary = new HashMap<String, ArrayList<Object>>();
				HashMap<String, Boolean> serverSideFoldersDictionary = new HashMap<String, Boolean>();
				HashMap<String, Boolean> serverSideFilesDictionary = new HashMap<String, Boolean>();

				
				for(int position=0;position<localDbRowsList.size();position++){
					String rowName = localDbRowsList.get(position).get(Constants.ROW_NAME).toString();
					localDbDictionary.put(rowName, localDbRowsList.get(position));
				}
				
				if(snapShot.has("folders")) {
					JSONArray serverSideFolders = snapShot.getJSONArray("folders");

					for(int position=0;position<serverSideFolders.length();position++){
						String rowName = serverSideFolders.getJSONObject(position).getString("name");
						serverSideFoldersDictionary.put(rowName, true);
					}
					
					
					// PROCESS ALL FOLDERS
					for (int i = 0; i < serverSideFolders.length(); i++) {
						String folderName = serverSideFolders.getJSONObject(i).getString("name");
						
						int isFolder = 1;
		
						if (!localDbDictionary.containsKey(folderName)) {
							eao.dirDbHelper.addFolderRow("", folderName, pid, parentPath + "/" + folderName, 0, isFolder);
						}
					}
				}
				
				if(snapShot.has("files")) {
					
					JSONArray serverSideFiles = snapShot.getJSONArray("files");
					for(int position=0;position<serverSideFiles.length();position++){
						String rowName = serverSideFiles.getJSONObject(position).getString("name");
						serverSideFilesDictionary.put(rowName, true);
					}
					
					// PROCESS ALL FILES
					//Enumeration e = serverSideFilesDictionary.keys();
					for (int i = 0; i < serverSideFiles.length(); i++) {
						String fileName = serverSideFiles.getJSONObject(i).getString("name");
						String eid = serverSideFiles.getJSONObject(i).getString("entry_id");
						String timestamp = serverSideFiles.getJSONObject(i).getString("last_modified");
						long size = serverSideFiles.getJSONObject(i).getLong("size");
						String firstName=null;
						String lastName=null;
			
						if (!localDbDictionary.containsKey(fileName)) {
							eao.dirDbHelper.addFileRow(eid, timestamp, fileName, pid, parentPath + "/" + fileName, size, 0,firstName,lastName);
						} else {
							String fileLocalEDI = (String) localDbDictionary.get(fileName).get(Constants.EID);
													
							if(!fileLocalEDI.equals(eid)) {
								eao.dirDbHelper.updateRow(null, timestamp, fileName, Constants.PLACE_HOLDER, size,parentPath + "/" + fileName, "",firstName,lastName);
							}
						}
					}
				}
				
				// DELETE ANTIQUATED FILES/FOLDERS that don't exist in server snapshot anymore
				for(int position=0;position<localDbRowsList.size();position++){
					String rowName = localDbRowsList.get(position).get(Constants.ROW_NAME).toString();
					if(!(serverSideFoldersDictionary.containsKey(rowName) || serverSideFilesDictionary.containsKey(rowName))) {

						String antiquatedCloudPath = localDbRowsList.get(position).get(Constants.FULL_PATH).toString();
						File antiquatedLocalPath = new File(Constants.SD_CLOUD+userInfo.getString(Constants.DOMAIN, ""), antiquatedCloudPath);
						
						if(antiquatedLocalPath.exists()) {
							Utils.removePathAndChildren(antiquatedLocalPath, eao, userInfo);
						}
						eao.dirDbHelper.deleteAllChildNodesByPath(antiquatedCloudPath);
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
				return false;
			} 

			return true;
	}
		
	public static void alertbox(String title, String mymessage, Context ctx){
	   new AlertDialog.Builder(ctx)
	      .setMessage(mymessage)
	      .setTitle(title)
	      .setCancelable(true)
	      .setNeutralButton(android.R.string.ok,
	         new DialogInterface.OnClickListener() {
	         public void onClick(DialogInterface dialog, int whichButton){}
	      }).show();
	}
	
	public static void deleteDirectory(String path, String domain){
		
		File dir = new File(Constants.SD_CLOUD+domain+path);
		String[] children = dir.list(); 
		if (children != null) { 	
			for (int i=0; i<children.length; i++) {// iterate through local filenames 
				String filename = children[i]; 
				File fileOrFolder = new File(Constants.SD_CLOUD+domain,path+"/"+filename);
				if(fileOrFolder.isDirectory()) {
					deleteDirectory(path+"/"+filename, domain);
				} else if(fileOrFolder.isFile()) {
					Utils.deleteFile(fileOrFolder);
				}
			}
		}

		Utils.deleteFile(dir);
	}
	
	public static boolean createNewFile(String fileName,String relativePath, String domain, InputStream is, Context ctx){
		String TAG = "createNewFile";
		StringBuilder absolutePath = new StringBuilder(relativePath+"/"+fileName);
		final int BUFFER_SIZE = 23 * 1024;
		Log.v(TAG, relativePath);
		File sdCard = Environment.getExternalStorageDirectory();
		File internal = ctx.getFilesDir();
		if (sdCard.canWrite() || internal.canWrite()) {

            Utils.makeDirectory(Constants.SD_CLOUD+domain+relativePath);
            File currentFile = new File(Constants.SD_CLOUD+domain,absolutePath.toString());
            Utils.deleteFile(currentFile);

	    	try {
	    		BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
	    		// create a File object for the parent directory
		    	// have the object build the directory structure, if needed.
		    	// create a File object for the output file
		    	File newDir = new File(Constants.SD_CLOUD+domain, relativePath);
	            Utils.makeDirectory(newDir.getAbsolutePath());
		    	File outputFile = new File(newDir, fileName);

	    		FileOutputStream fos = new FileOutputStream(outputFile);
	    		byte[] baf = new byte[BUFFER_SIZE];
	    		int actual = 0;
	    		while (actual != -1) {
	    		    fos.write(baf, 0, actual);
	    		    actual = bis.read(baf, 0, BUFFER_SIZE);
	    		}
	    		fos.close();
	    		is.close();
	    		bis.close();
	    		
		    } catch (IOException e) { e.printStackTrace(); }
            return true;
	    } else {
	    	return false;
	    }
	}
	public static void makeDirectory(String path) {
		File newDir = new File(path);
		if(newDir.mkdirs()) {
			 Log.v("Utils", "local directory creation failed "  + path);
		}
	}
	
	public static void deleteFile(File file) {
		if(file.exists() && !file.delete()) {
			 Log.v("Utils", "local file delete failed "  + file.getPath());
		}
	}
	 
	public static String getMimeType(String fileName){
		String mimeType = null;
		try {
			String fileExt = getFileExt(URLEncoder.encode(fileName,"UTF-8"));
			mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt.toLowerCase());
			//Log.v(fileName,"fileExt"+fileExt+" mimeType="+ mimeType);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return mimeType;
	}
	
	public static String getFileExt(String fileName){
		String[] tempNameArray = fileName.split("\\.");
		
		if(tempNameArray.length > 0) {
			return tempNameArray[tempNameArray.length-1];
		} else {
			return "";
		}
	}
	
	public String readFile( String file ) throws IOException {
	    BufferedReader reader = new BufferedReader( new FileReader (file));
	    String line  = null;
	    StringBuilder stringBuilder = new StringBuilder();
	    String ls = System.getProperty("line.separator");
	    
	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append( line );
	        stringBuilder.append( ls );
	    }
	    
	    reader.close();
	    
	    return stringBuilder.toString();
	}
	
	public static double roundFloat(double unrounded, int precision) {
	    BigDecimal bd = new BigDecimal(unrounded);
	    BigDecimal rounded = bd.setScale(precision, BigDecimal.ROUND_HALF_UP);
	    
	    return rounded.doubleValue();
	}

	public static SharedPreferences getDefaultUserInfo(Context ctx){
		SharedPreferences prefs = ctx.getSharedPreferences("MELCPreferences", Context.MODE_PRIVATE);
		
		return prefs;
	}
	

	public static void rememberSsoTokens(String domain, String accessToken, String tokenType, String client_id, Context ctx) {
		SharedPreferences settings = ctx.getSharedPreferences("MELCPreferences", Context.MODE_PRIVATE); 
		Editor e = settings.edit();

	    e.putString(Constants.DOMAIN, domain);
	    e.putString(Constants.ACCESS_TOKEN, accessToken);
	    e.putString(Constants.TOKEN_TYPE, tokenType);
	    e.putString(Constants.CLIENT_ID, client_id);

	    e.commit();
	}
	
	public static String humanFriendlyFileSize(long bytes) {
		if(bytes < 1024) {
			return bytes+" bytes";
		} else if(bytes < 1048575) {
			return bytes/1024+" KB";
		} else if(bytes < 1073741823) {
			return Utils.roundFloat((float)bytes/(1024*1024),1)+" MB";
		} else {
			return Utils.roundFloat((float)bytes/(1024*1024*1024),1)+" GB";
		}
	}
	
	public static void deleteUserPassword(Context ctx) {
		SharedPreferences settings = ctx.getSharedPreferences("MELCPreferences", Context.MODE_PRIVATE); 
		Editor e = settings.edit();
	    e.putString(Constants.ACCESS_TOKEN, "");
	    e.commit();			
	}
}