package com.kuixotic.egnyte;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

//essentially same as masterview but you can only select folders and pass it back to master view
public class FolderBrowserViewController extends ListActivity {
	private ProgressDialog mProgress = null;
	private ArrayList<ArrayList<Object>> currentDirectoryItems;
	private StringBuilder currentAbsolutePath = new StringBuilder();//internal breadcrumb of parentpath
	private  String TAG = "MasterViewController";
	private SharedPreferences userInfo;
	private static final String DOMAIN = "domain";
	
	
	//update the directory view that the user is currently seeing
	private void updateMasterView(final String path) {
		currentAbsolutePath = new StringBuilder(path);

		runOnUiThread(new Runnable() {
			@SuppressWarnings("unchecked")
			public void run() {
				ArrayList<Object> parentItem = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowAtAbsolutePath(path);
				if(parentItem != null && parentItem.size()!=0) {
					ArrayList<ArrayList<Object>> tempRows = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID((Integer) parentItem.get(Constants.ROW_ID));
					currentDirectoryItems = tempRows;
					Collections.sort(currentDirectoryItems, new Utils.SeparateByFolderVsFile());
					setListAdapter(new DirectoryAdapter(FolderBrowserViewController.this));
				}
			}
		});
	}

	public void updateBreadcrumb() {
		TextView label = (TextView) findViewById(R.id.BREADCRUMB_LABEL);
		String breadcrumb = "Main.." + currentAbsolutePath;
		label.setText(breadcrumb);
	}

	public int getSelectedSnapshot(String path) { 
		try {
			String jsonBody = null;
			HttpResponse sr = HTTPHandler.getDirectory(((EgnyteAppObject)getApplication()).httpclient, path, getBaseContext());
			if(sr!=null) {
				int sCode = sr.getStatusLine().getStatusCode();
				jsonBody = EntityUtils.toString(sr.getEntity(), "UTF-8"); 
				Utils.processFreshDirectoryJSON(jsonBody, path, ((EgnyteAppObject)getApplication()), userInfo);
				updateMasterView(path);
		
				return sCode;
			} else {
				return 0;
			}
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();}

		return 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onBackPressed() {
			
		if ((Integer) currentDirectoryItems.get(0).get(Constants.PARENT_ID) != 0) {// if == 0, it's from root
			// 1) get the first row from the current list and use it to find parent id
			int PID = (Integer) currentDirectoryItems.get(0).get(Constants.PARENT_ID);
			Log.v(TAG, "onBackPressed parent id " + PID);
			ArrayList<Object> aParentItem = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowByPID(PID);

			String parentDirName = (String) aParentItem.get(Constants.ROW_NAME);

			if(!currentAbsolutePath.toString().equals("")) {
				currentAbsolutePath.delete(currentAbsolutePath.length() - (parentDirName.length() + 1),currentAbsolutePath.length());
			}
			
			currentDirectoryItems = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID((Integer) aParentItem.get(Constants.PARENT_ID));

		} else {// querying past depth root, return back to domain view
			finish();
		}
			
		updateBreadcrumb();
		Collections.sort(currentDirectoryItems, new Utils.SeparateByFolderVsFile());
		setListAdapter(new DirectoryAdapter(FolderBrowserViewController.this));

		return;
	}

	@Override //didSelect
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// chosen row from listview
		super.onListItemClick(l, v, position, id);

		@SuppressWarnings("unchecked")
		ArrayList<Object> o = (ArrayList<Object>) this.getListAdapter().getItem(position);
		String rowName = (String) o.get(Constants.ROW_NAME);

		if (!currentDirectoryItems.get(position).get(Constants.ROW_IS_FOLDER).equals(0)) { // is a folder
			getSelectedSnapshot(currentAbsolutePath + "/" + rowName);
		} else { 
			return;
		}		
	}
	
	public void updateCurrentDirectoryView(int pid){
		currentDirectoryItems = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID(pid);
		setListAdapter(new DirectoryAdapter(FolderBrowserViewController.this));
	}
	
	@SuppressWarnings("unchecked")
	public void toMainView(){
		currentAbsolutePath.delete(0, currentAbsolutePath.length());
		currentAbsolutePath.append("");
		updateBreadcrumb();
		Collections.sort(currentDirectoryItems, new Utils.SeparateByFolderVsFile());
		setListAdapter(new DirectoryAdapter(FolderBrowserViewController.this));
	}
	
	@SuppressWarnings("rawtypes")
	class DirectoryAdapter extends ArrayAdapter {

		@SuppressWarnings("unchecked")
		DirectoryAdapter(Activity context) {
			super(context, R.layout.row, currentDirectoryItems);
		}

		//for regular directory view
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;

			LayoutInflater inflater = getLayoutInflater();
			row = inflater.inflate(R.layout.row, parent, false);

			row.setBackgroundResource(android.R.drawable.menuitem_background);
			TextView label = (TextView) row.findViewById(R.id.text);
			String fName = currentDirectoryItems.get(position).get(Constants.ROW_NAME).toString();
			label.setText(fName);//set file name on row
			
			TextView sublabel = (TextView) row.findViewById(R.id.subtext);
			CheckBox dirChkBox = (CheckBox) row.findViewById(R.id.DIR_CHECKBOX);
			dirChkBox.setVisibility(View.GONE);
			Button okBtn = (Button) findViewById(R.id.OK_BTN);
			okBtn.setEnabled(true);
			
			if (currentDirectoryItems.get(position).get(Constants.ROW_IS_FOLDER).equals(0)) { //it's a file
				label.setTextColor(Color.LTGRAY);
				sublabel.setTextColor(Color.LTGRAY);
				String fileSize = Utils.humanFriendlyFileSize((Long) currentDirectoryItems.get(position).get(Constants.SIZE));
				String owner = (String) currentDirectoryItems.get(position).get(Constants.ROW_OWNER);

				
				String date = "N/A ";
				date =  (String) currentDirectoryItems.get(position).get(Constants.SERVER_TIMESTAMP);

				sublabel.setText(fileSize  + " | " + date+ " | " +owner);
				
			} 

			
			Resources r = getResources();
			Drawable[] layers = new Drawable[2];
			layers[1] = r.getDrawable(R.drawable.blank);
			
			ImageView icon = (ImageView) row.findViewById(R.id.DIR_ICON);
			
			if (!currentDirectoryItems.get(position).get(Constants.ROW_IS_FOLDER).equals(0)) {//folder icon
				layers[0] = r.getDrawable(R.drawable.icon_shared);
			} else {//file icon
				layers[0] = r.getDrawable(R.drawable.icon_img);
			}
			
			LinearLayout rowLayout = (LinearLayout) row.findViewById(R.id.LISTVIEW_ROW);
			rowLayout.setBackgroundResource(R.color.white);
			rowLayout.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			
			LayerDrawable layerDrawable = new LayerDrawable(layers);
			icon.setImageDrawable(layerDrawable);
			
			return row;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG,"onCreate");
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.folder_browser_view);
		
		userInfo = Utils.getDefaultUserInfo(getBaseContext());
		if(((EgnyteAppObject)getApplication()).dirDbHelper==null) {
			((EgnyteAppObject)getApplication()).dirDbHelper = new DirectoryDbHelper(this);// creates top level if db doesn't exist
		}
		
		currentDirectoryItems = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID(0);		
		
		if (currentDirectoryItems != null){
			Collections.sort(currentDirectoryItems, new Utils.SeparateByFolderVsFile());
			setListAdapter(new DirectoryAdapter(FolderBrowserViewController.this));
		}
		
		
		LinearLayout browserBox = (LinearLayout) findViewById(R.id.FOLDER_BROWSER_VIEW);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
		layoutParams.setMargins(10, 10, 10, 10);
		browserBox.setLayoutParams(layoutParams);
		
		
		ListView list = (ListView) findViewById(android.R.id.list);
		list.setItemsCanFocus(false);
		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		
		Button okBtn = (Button) findViewById(R.id.OK_BTN);
		okBtn.setOnClickListener(selectBtnListener);
		
		Button cancelBtn = (Button) findViewById(R.id.CANCEL_BTN);
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}
	
	private OnClickListener selectBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.v(TAG, "selectBtnListener");
			Intent myIntent = new Intent(FolderBrowserViewController.this,MasterViewController.class);
			myIntent.putExtra(Constants._DEST_PATH, currentAbsolutePath.toString());
			Log.v(TAG, "okBtnListener: "+ currentAbsolutePath);
			setResult(RESULT_OK, myIntent);
			finish();
		}
	};
	
	//true means errored out
	//false means let original method handle status code
	public boolean processStatusCode(int statusCode){
		Log.v("processStatusCode", "status:" + statusCode);

		if (statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {//503
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Server Down","Please try again later",FolderBrowserViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		}else if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {//must be from getSelectedFile() because user needs sync before file open
			return true;
		}else if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN || statusCode == HttpStatus.SC_BAD_GATEWAY) {//401 403
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Unauthorized","You do not have permission to perform this action.",FolderBrowserViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		} else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {//500
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Action failed","Please try again later",FolderBrowserViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		} else if (statusCode == HttpStatus.SC_REQUEST_TIMEOUT || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT) {//408 504
			//let the original method take care of status
			mProgress.dismiss();
			return false;
		} else if (statusCode==0) {
			return true;
		}
		
		return false;
	}
}