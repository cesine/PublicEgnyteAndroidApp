package com.kuixotic.egnyte;

import java.util.ArrayList;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
 
public class DirectoryDbHelper{
	private static String TAG = "DirectoryDbHelper";
	public SQLiteDatabase db;
	private SharedPreferences userinfo;
	private static final String DB_NAME = "public_egnyte_db_v1.0";
	private static final int DB_VERSION = 1;
	private final String DIRECTORY_TABLE;
	private static final String DIRECTORY_RID = "rid";
	private static final String EID = "EID";
	private static final String TIMESTAMP = "timestamp";
	private static final String ROW_NAME = "name";
	private static final String FULL_PATH = "full_path";
	private static final String PARENT_ID = "parent_id";
	private static final String SIZE = "size";
	private static final String ROW_IS_FOLDER = "isFolder";
	private static final String ROW_OWNER = "owner";
	private static final String DOMAIN = "domain";
	private static final String USERNAME = "uName";
	
	public DirectoryDbHelper(Context context){
		userinfo = Utils.getDefaultUserInfo(context);
		DIRECTORY_TABLE = "z_"+userinfo.getString(DOMAIN, "")+"_"+userinfo.getString(USERNAME, "")+"_file_lookup_table";
		CustomSQLiteOpenHelper helper = new CustomSQLiteOpenHelper(context);
		this.db = helper.getWritableDatabase();
		
		createNewDirTable();
	}
 
	public void close() {
        try {
        	 db.close();
        } catch (SQLException e) {
            Log.d(TAG,"close exception: " + e.getLocalizedMessage());
        }
    }
 
 
	/**********************************************************************
	 * ADDING A ROW TO THE DATABASE TABLE
	 * ie: dirDbHelper.addRow("qwerqwer",234234234, "qwer", 4, 56, "sfdl", 0, 1);
	 */
	public void addFileRow(String eid, String timestamp, String rName, int pid, 
			String path, long size, int isFolder, String firstName, String lastName){
		
		ContentValues values = new ContentValues();
		values.put(EID, eid);
		values.put(TIMESTAMP, timestamp);
		values.put(ROW_NAME, rName);
		values.put(PARENT_ID, pid);
		values.put(SIZE, size);
		values.put(ROW_IS_FOLDER, isFolder);
		values.put(FULL_PATH, path);
		if(firstName!=null & lastName!=null) {
			values.put(ROW_OWNER, firstName+" "+lastName);
		} else {
			values.put(ROW_OWNER, "");
		}
		db.beginTransaction();
		try{
			db.insert(DIRECTORY_TABLE, null, values);
			db.setTransactionSuccessful();
		} catch(Exception e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
	}
 
	public void addFolderRow(String eid, String rName, int pid, String path, int isLocal, int isFolder){
		
		ContentValues values = new ContentValues();
		values.put(EID, eid);
		values.put(ROW_NAME, rName);
		values.put(PARENT_ID, pid);
		values.put(ROW_IS_FOLDER, isFolder);
		values.put(FULL_PATH, path);
		
		db.beginTransaction();
		try{
			db.insert(DIRECTORY_TABLE, null, values);
			db.setTransactionSuccessful();
		} catch(Exception e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
	}
 
	public void deleteAllChildNodesByPath(String path){
		Log.v(TAG,"deleting:"+path);
		path = path.replace("'", "''");
		
		try {
			db.delete(DIRECTORY_TABLE, FULL_PATH + " LIKE '" + path + "%'", null);
		} catch (Exception e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
	}
	
	public void deleteRowByPath(String path){
		path = path.replace("'", "''");
		
		try {
			db.delete(DIRECTORY_TABLE, FULL_PATH + "= '" + path + "'", null);
		} catch (Exception e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
	}
	
	/**********************************************************************
	 * UPDATING A ROW IN THE DATABASE TABLE
	 */ 
	public void updateRow(String eid,String timestamp,
			String rName, int pid, long size, String path, String eEtag, String firstName, String lastName){
		
		
		ContentValues values = new ContentValues();
		
		if(path==null) {
			return;//do not insert without path
		} else {
			values.put(FULL_PATH, path);
		}
		
		if(eid!=null) {
			values.put(EID, eid);
		}
		if(timestamp!=null) {
			values.put(TIMESTAMP, timestamp);
		}
		if(rName!=null) {
			values.put(ROW_NAME, rName);
		}
		if(pid!=Constants.PLACE_HOLDER) {
			values.put(PARENT_ID, pid);
		}
		if(size!=Constants.PLACE_HOLDER) {
			values.put(SIZE, size);
		}
		if(firstName!=null && lastName!=null) {
			values.put(ROW_OWNER,firstName+" "+lastName);
		}
		db.beginTransaction();
		try {
			path = path.replace("'", "''");
			db.update(DIRECTORY_TABLE, values, FULL_PATH + "= '" + path+"'", null);
			db.setTransactionSuccessful();
		} catch (Exception e) {
			Log.e("updateRow DB Error", e.toString());
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
	}
 
	
	/**********************************************************************
	 * RETRIEVING A ROW FROM THE DATABASE TABLE
	 * @param rowID the id of the row to retrieve
	 * @return an array containing the data from the row
	 */
	public ArrayList<Object> getRowByPID(long rowID){
		ArrayList<Object> rowArray = new ArrayList<Object>();
		Cursor cursor = null;
 
		try{
			cursor = db.query
			(
					DIRECTORY_TABLE,
					new String[] { DIRECTORY_RID, EID, TIMESTAMP, ROW_NAME,
							PARENT_ID, SIZE, ROW_IS_FOLDER, FULL_PATH, ROW_OWNER},
					DIRECTORY_RID + "=" + rowID +" LIMIT 1",
					null, null, null, null, null
			);
 
			cursor.moveToFirst();
			if (!cursor.isAfterLast()){
					rowArray.add(cursor.getInt(0));
					rowArray.add(cursor.getString(1));
					rowArray.add(cursor.getString(2));
					rowArray.add(cursor.getString(3));
					rowArray.add(cursor.getInt(4));
					rowArray.add(cursor.getLong(5));
					rowArray.add(cursor.getInt(6));
					rowArray.add(cursor.getString(7));
					rowArray.add(cursor.getString(8));
			}
 
		} catch (SQLException e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
			
		} finally {
			if(cursor!=null) {
				cursor.close();
			}
		}
 
		return rowArray;
	}
 
	public ArrayList<ArrayList<Object>> getRowsAsArraysAtParentPath(String parentPath){
		ArrayList<ArrayList<Object>> dataArrays = new ArrayList<ArrayList<Object>>();
		Cursor cursor = null;
 
		try{
			cursor = db.query(DIRECTORY_TABLE, new String[]{DIRECTORY_RID, EID, TIMESTAMP, ROW_NAME,
							PARENT_ID, SIZE, ROW_IS_FOLDER, FULL_PATH, ROW_OWNER},
							FULL_PATH + "= '" + parentPath + "'", null, null, null, null);
 
			cursor.moveToFirst();
 
			if (!cursor.isAfterLast()){
				do{
					ArrayList<Object> dataList = new ArrayList<Object>();
 
					dataList.add(cursor.getInt(0));
					dataList.add(cursor.getString(1));
					dataList.add(cursor.getString(2));
					dataList.add(cursor.getString(3));
					dataList.add(cursor.getInt(4));
					dataList.add(cursor.getLong(5));
					dataList.add(cursor.getInt(6));
					dataList.add(cursor.getString(7));
					dataList.add(cursor.getString(8));
					
					dataArrays.add(dataList);
				}

				while (cursor.moveToNext());
			}

		} catch (SQLException e) { e.printStackTrace();
		} finally {
			if(cursor!=null) {
				cursor.close();
			}
		}
 
		return dataArrays;
	}
	
	public ArrayList<ArrayList<Object>> getRowsAsArraysByRowName(String rowName){
		ArrayList<ArrayList<Object>> dataArrays = new ArrayList<ArrayList<Object>>();
		
		Cursor cursor = null;
 
		try{
			
			cursor = db.query(DIRECTORY_TABLE, new String[]{DIRECTORY_RID, EID, TIMESTAMP, ROW_NAME,
							PARENT_ID, SIZE, ROW_IS_FOLDER, FULL_PATH, ROW_OWNER},
							ROW_NAME + "= '" + rowName + "'", null, null, null, null);
 
			
			cursor.moveToFirst();
 
			
			if (!cursor.isAfterLast()){
				do{
					ArrayList<Object> dataList = new ArrayList<Object>();
 
					dataList.add(cursor.getInt(0));
					dataList.add(cursor.getString(1));
					dataList.add(cursor.getString(2));
					dataList.add(cursor.getString(3));
					dataList.add(cursor.getInt(4));
					dataList.add(cursor.getLong(5));
					dataList.add(cursor.getInt(6));
					dataList.add(cursor.getString(7));
					dataList.add(cursor.getString(8));
					
					dataArrays.add(dataList);
					
				} while (cursor.moveToNext());
			}

		} catch (SQLException e) {
			Log.e("getRowsAsArraysAtParentPath DB Error", e.toString());
			e.printStackTrace();
		} finally {
			if(cursor!=null) {
				cursor.close();
			}
		}
 
		return dataArrays;
	}
	

	public ArrayList<Object> getRowAtAbsolutePath(String path){
		ArrayList<Object> rowArray = new ArrayList<Object>();
		Cursor cursor = null;
 
		try{
			
			cursor = db.query(DIRECTORY_TABLE, new String[]{DIRECTORY_RID, EID, TIMESTAMP, ROW_NAME,
					PARENT_ID, SIZE, ROW_IS_FOLDER, FULL_PATH, ROW_OWNER},
					FULL_PATH + "= '" + path + "'", null, null, null, null);

			cursor.moveToFirst();
 
			if (!cursor.isAfterLast()){
				rowArray.add(cursor.getInt(0));
				rowArray.add(cursor.getString(1));
				rowArray.add(cursor.getString(2));
				rowArray.add(cursor.getString(3));
				rowArray.add(cursor.getInt(4));
				rowArray.add(cursor.getLong(5));
				rowArray.add(cursor.getInt(6));
				rowArray.add(cursor.getString(7));
				rowArray.add(cursor.getString(8));
			}
 
		} catch (SQLException e) {
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		} finally {
			if(cursor!=null) {
				cursor.close();
			}
		}
 
		
		return rowArray;
	}
	
	
	public ArrayList<ArrayList<Object>> getRowsAsArraysByPID(int pid){
		ArrayList<ArrayList<Object>> dataArrays = new ArrayList<ArrayList<Object>>();
		Cursor cursor = null;
		
		try{
			
			cursor = db.query( DIRECTORY_TABLE, new String[]{DIRECTORY_RID, EID, TIMESTAMP, ROW_NAME,
								PARENT_ID, SIZE, ROW_IS_FOLDER,
								FULL_PATH, ROW_OWNER},
								PARENT_ID + "=" + pid, null, null, null, null);
 
			cursor.moveToFirst();
			if (!cursor.isAfterLast()){
				do{
					ArrayList<Object> dataList = new ArrayList<Object>();
					dataList.add(cursor.getInt(0));
					dataList.add(cursor.getString(1));
					dataList.add(cursor.getString(2));
					dataList.add(cursor.getString(3));
					dataList.add(cursor.getInt(4));
					dataList.add(cursor.getLong(5));
					dataList.add(cursor.getInt(6));
					dataList.add(cursor.getString(7));
					dataList.add(cursor.getString(8));
					
					dataArrays.add(dataList);
					
				} while (cursor.moveToNext());
			}

		} catch (SQLException e) {
			Log.e("getRowsAsArraysByPID DB Error", e.toString());
			e.printStackTrace();
		} finally {
			if(cursor!=null) {
				cursor.close();
			}
		}

		return dataArrays;
	}
	

	private class CustomSQLiteOpenHelper extends SQLiteOpenHelper{
		public CustomSQLiteOpenHelper(Context context){
			super(context, DB_NAME, null, DB_VERSION);
	}
		
	
	
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.v(TAG,"inside onCreate SQLiteDatabase");
		}


		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
	
	}
	
	public void createNewDirTable() {
		String newTableQueryString =  "CREATE TABLE IF NOT EXISTS " +
										DIRECTORY_TABLE +
										" (" +
										DIRECTORY_RID + " integer primary key autoincrement not null," +
										EID + " text," +
										TIMESTAMP + " text," +
										ROW_NAME + " text," +
										PARENT_ID + " integer," +
										SIZE + "  long," +
										ROW_IS_FOLDER + " tinyint," +
										FULL_PATH + " text," +
										ROW_OWNER + " text);";

		String sharedRow = "INSERT INTO " + DIRECTORY_TABLE +
				" ("+ROW_NAME+","+PARENT_ID+","+ROW_IS_FOLDER+","+FULL_PATH+") " +
				"VALUES ('Shared', 0, 1, '/Shared')";
		String privateRow = "INSERT INTO " + DIRECTORY_TABLE +
				" ("+ROW_NAME+","+PARENT_ID+","+ROW_IS_FOLDER+","+FULL_PATH+") " +
				"VALUES ('Private', 0, 2, '/Private')";
		
		db.execSQL(newTableQueryString);
		if(getRowsAsArraysByPID(0).size() == 0) {
			db.execSQL(sharedRow);
			db.execSQL(privateRow);
		}
	}
}