package com.kuixotic.egnyte;

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MasterViewController extends ListActivity {
	private ArrayList<Boolean> checkedStates = new ArrayList<Boolean>();
	private File currentFile;
	private ArrayList<ArrayList<Object>> currentDirectoryItems;
	private StringBuilder currentAbsolutePath = new StringBuilder();//internal breadcrumb of parentpath
	private String newFileName;
	private SharedPreferences userInfo;
	private ProgressDialog mProgress = null;
	
	
	public void displayNewFileDialog() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
		final EditText input = new EditText(this);
		input.requestFocus();
		
		alert.setTitle("New folder name:");
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				final String parentPath = currentAbsolutePath.toString();
				newFileName = input.getText().toString().trim();
				createFolder(parentPath, newFileName);
			}
		});

		alert.setNegativeButton("Cancel",
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					dialog.cancel();
				}
			});
		alert.show();
	}

	public int createFolder(String relativePath, String folderName) {
		String newPath = relativePath + "/"+ folderName;
		
		//httpclient in case you want to perform manipulations while request is occuring
		//where you want the folder created
		//action type: add_folder
		//past context so handler can access user info for request 
		int sCode = HTTPHandler.fileAction(((EgnyteAppObject)getApplication()).httpclient, newPath.toString(), "add_folder", MasterViewController.this);

		if(processStatusCode(sCode)) {
			return sCode;
		} else if (sCode == HttpStatus.SC_OK || sCode == HttpStatus.SC_CREATED) {
			getDirectorySnapshot(currentAbsolutePath+"");			
		}
		
		
		runOnUiThread(new Runnable() {
			public void run() {
				setListAdapter(new DirectoryAdapter(MasterViewController.this));
				mProgress.dismiss();
			}
		});

		return sCode;
	}
	
	public int getSelectedFile(final String relativePath, final String fileName) {        
		int sCode = 0;		
		ArrayList<Object> fileItem = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowAtAbsolutePath(relativePath + "/" + fileName);
		final String eid = (String) fileItem.get(Constants.EID);
		
		//httpclient in case you want to perform manipulations while request is occuring
		//file's entry id of local copy, if there is one. returns 304 if the local file is already latest copy
		//file download parent path
		//file name
		//past context so handler can access user info for request 
		HttpResponse response = HTTPHandler.getFile(((EgnyteAppObject)getApplication()).httpclient, eid, relativePath, fileName, MasterViewController.this);

		if(response!=null) {//happens if network died in middle of get			
			sCode = response.getStatusLine().getStatusCode();
			if(sCode == HttpStatus.SC_OK) {
				try {
					Utils.createNewFile(fileName, relativePath, userInfo.getString(Constants.DOMAIN, ""), response.getEntity().getContent(), getBaseContext());
					openFile(relativePath+"/"+fileName); 
				} catch (IllegalStateException e) { e.printStackTrace();
				} catch (IOException e) { e.printStackTrace(); }
			}
		}
		
		mProgress.dismiss();
		return sCode;
	}


	public void updateBreadcrumb() {
		TextView label = (TextView) findViewById(R.id.BREADCRUMB_LABEL);
		label.setText("Main.." + currentAbsolutePath);
	}

	//get the fresh directory JSON from server
	public int getDirectorySnapshot(String path) { 
		HttpResponse sr;
		try {
			String jsonBody = null;
			int sCode = 0;

			sr = HTTPHandler.getDirectory(((EgnyteAppObject)getApplication()).httpclient, path, getBaseContext());
			
			if(sr!=null){
				sCode = sr.getStatusLine().getStatusCode();
				jsonBody = EntityUtils.toString(sr.getEntity(), "UTF-8");
				Log.v("getDirectorySnapshot SERVER RESPONSE", jsonBody);
			}
			
			if (jsonBody == null) {
				return 205;
			}
			if (sCode == 401) {
				return sCode;
			}

			Utils.processFreshDirectoryJSON(jsonBody, path, ((EgnyteAppObject)getApplication()), userInfo);
			updateMasterViewByPath(path);
			
			mProgress.dismiss();

			return sCode;
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();}

		return 0;
	}

	@Override
	public void onBackPressed() {
		
		if (currentDirectoryItems.get(0)!=null && (Integer) currentDirectoryItems.get(0).get(Constants.PARENT_ID) != 0) {// if == 0, it's from root
			// 1) get the first row from the current list and use it to find parent id
			int PID = (Integer) currentDirectoryItems.get(0).get(Constants.PARENT_ID);
			ArrayList<Object> aParentItem = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowByPID(PID);
			String parentDirName = (String) aParentItem.get(Constants.ROW_NAME);

			// update topbar breadcrumb label
			if(!currentAbsolutePath.toString().equals("")) {
				currentAbsolutePath.delete(currentAbsolutePath.length() - (parentDirName.length() + 1),currentAbsolutePath.length());
			}
			
			// 2) find parent path and set current view to all rows under parent path
			currentDirectoryItems = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID((Integer) aParentItem.get(Constants.PARENT_ID));

		} else {// querying past depth root, return back to domain view
			currentAbsolutePath.delete(0,currentAbsolutePath.length());
			finish();
		}
		
		updateBreadcrumb();
		refreshMasterView();

		return;
	}

	public void hideFileActionBar() {
		HorizontalScrollView fileActionBar = (HorizontalScrollView) findViewById(R.id.FILE_ACTION_BAR);
		fileActionBar.setVisibility(View.GONE);
	}
	
	public void resetCheckedStates() {
		checkedStates.clear();
		for (int i = 0; i < currentDirectoryItems.size(); i++) {
			checkedStates.add(false);
		}
	}

	
	
	protected void openFile(String filePath) {
		runOnUiThread(new Runnable() {
			public void run() {
				mProgress.dismiss();
			}
		});

		String localPath = Constants.SD_CLOUD+userInfo.getString(Constants.DOMAIN, "") + filePath;
		currentFile = new File(localPath);		
		String mType = Utils.getMimeType(filePath);
		Uri dataUri = Uri.fromFile(currentFile);
		
		//broadcast to android OS that you have a file open intent with mType mimetype
		final Intent packageIntent = new Intent(Intent.ACTION_VIEW);
		packageIntent.setDataAndType(dataUri, mType);
		startActivityForResult(Intent.createChooser(packageIntent, "File Open Request"),Constants.FILE_OPEN_REQUEST);
	}
	
	//
	private void uploadFile(final String filePath, final String destPath) {
    	mProgress = ProgressDialog.show(MasterViewController.this, "Please wait", "Uploading...", true, true);
    	new Thread(new Runnable(){
    		@Override
			public void run(){
    			File currentFile = new File(filePath);

    			HttpResponse response = HTTPHandler.putFile(((EgnyteAppObject)getApplication()).httpclient, destPath, currentFile.getName(), currentFile
    					, currentFile.lastModified()+"", currentFile.length(), getApplicationContext(), ((EgnyteAppObject)getApplication()).dirDbHelper);
    			
				if(response!=null) {
					int sCode = response.getStatusLine().getStatusCode();
					if(sCode==HttpStatus.SC_OK || sCode==HttpStatus.SC_CREATED) {
						runOnUiThread(new Runnable() {
						    public void run() {
						    	Utils.alertbox("File Upload Success", "", MasterViewController.this); 
						    	getDirectorySnapshot(currentAbsolutePath.toString());
						}});
					} else {
						//this is how you would process an error message
						try {
							final String r = EntityUtils.toString(response.getEntity());
							runOnUiThread(new Runnable() {
							    public void run() {
							    	//best way to do this is to parse it with JSON library
							    	Utils.alertbox("File Upload Error", r, MasterViewController.this); 
							}});
						} catch (ParseException e) { e.printStackTrace();
						} catch (IOException e) { e.printStackTrace(); }
					}
				}
				mProgress.dismiss(); 
    		}
    	}).start(); 
    }
	
	//when activity finishes, process any return responses
	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {		
		if(resultCode == RESULT_OK && requestCode == Constants.ACTIVITY_SELECT_IMAGE){  
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String fpath = cursor.getString(columnIndex);
            cursor.close();
            //upload the selected file after image is selected
            uploadFile(fpath, currentAbsolutePath.toString());
			
        } else if (requestCode == Constants.MOVE_PATH_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				runOnUiThread(new Runnable() {
					public void run() { // move the file
						moveCopyPath(data.getStringExtra(Constants._DEST_PATH),"move");
					}
				});
			}
		} else if (requestCode == Constants.COPY_PATH_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				runOnUiThread(new Runnable() {
					public void run() { // copy the file
						moveCopyPath(data.getStringExtra(Constants._DEST_PATH),"copy");
					}
				});
			}
		} 
	}
	
	//called when a row is clicked
	@Override 
	protected void onListItemClick(ListView l, View v, final int position, long id) {
		super.onListItemClick(l, v, position, id);
		didSelectRow(position);
	}
	
	//from onListItemClick
	public void didSelectRow(final int position){
		@SuppressWarnings("unchecked")
		ArrayList<Object> selectedItem = (ArrayList<Object>) this.getListAdapter().getItem(position);
		final String rowName = (String) selectedItem.get(Constants.ROW_NAME);		
		
		if(mProgress == null || !mProgress.isShowing()) {
			mProgress = ProgressDialog.show(MasterViewController.this,"Please wait", "loading...", true, true, null);		
		}
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (!currentDirectoryItems.get(position).get(Constants.ROW_IS_FOLDER).equals(0)) {//clicked on folder for browsing
					if(processStatusCode(getDirectorySnapshot(currentAbsolutePath + "/" + rowName))) {
						if(mProgress.isShowing()) {
							mProgress.dismiss();
						}
						return;
					}
				} else {//clicked on file for open
					getSelectedFile(currentAbsolutePath.toString(), rowName);
				}
			}
		}).start();
		
		resetCheckedStates();
		hideFileActionBar();
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.app_menu, menu);
		
		return true;
	}
	
	public void toMainView(){
		currentAbsolutePath.delete(0, currentAbsolutePath.length());
		currentAbsolutePath.append("");
		updateBreadcrumb();
		setListAdapter(new DirectoryAdapter(MasterViewController.this));
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch (item.getItemId()) {
			case R.id.MAIN_VIEW:
				toMainView();
				return true;
			case R.id.SIGNOUT: //logout
				signout();
				return true;
			case R.id.NEW_FOLDER:
				displayNewFileDialog();
				return true;
			case R.id.UPLOAD_VIEW:
				Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				startActivityForResult(i, Constants.ACTIVITY_SELECT_IMAGE); 
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	//on sign out, delete password, clear activity stack, and send user to login view
	public void signout() {
		((EgnyteAppObject)getApplication()).httpclient = new DefaultHttpClient();
		Utils.deleteUserPassword(getBaseContext());
		
		Intent intent = new Intent(this, LoginViewController.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Removes other Activities from stack
		startActivity(intent);
	}
	
	//when checkbox is marked, it is processed here
	public void initDirectoryCheckBox(final int position, View row) {

		CheckBox dirChkBox = (CheckBox) row.findViewById(R.id.DIR_CHECKBOX);
		dirChkBox.setTag(position);
		dirChkBox.setChecked(checkedStates.get(position));
		dirChkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				
				Button deleteBtn = (Button) findViewById(R.id.DELETE_BTN);
				Button copyBtn = (Button) findViewById(R.id.COPY_BTN);
				Button moveBtn = (Button) findViewById(R.id.MOVE_BTN);

				int checkedCnt = 0;
				boolean folderChecked = false;
				boolean fileChecked = false;
				
				HorizontalScrollView fileActionBar = (HorizontalScrollView) findViewById(R.id.FILE_ACTION_BAR);
				fileActionBar.setVisibility(View.VISIBLE);
				checkedStates.set((Integer) buttonView.getTag(), isChecked);
				
				for (int i = 0; i < checkedStates.size(); i++) {
					if (checkedStates.get(i)) {						
						int isFolder = (Integer) currentDirectoryItems.get(i).get(Constants.ROW_IS_FOLDER);
						if(isFolder!=0) {
							folderChecked=true;
						} else {
							fileChecked=true;
						}
						
						if(folderChecked && fileChecked && checkedCnt>1) {
							break;
						}
						
						checkedCnt++;
					}
				}
				
				deleteBtn.setVisibility(View.VISIBLE);
				
				if (checkedCnt == 0) {
					fileActionBar.setVisibility(View.GONE);
				} else if (checkedCnt > 1) {
					moveBtn.setVisibility(View.GONE);
					copyBtn.setVisibility(View.GONE);
				} else {
					moveBtn.setVisibility(View.VISIBLE);
					copyBtn.setVisibility(View.VISIBLE);
				}
			}
		});
	}

	
	
	@SuppressWarnings("rawtypes")
	class DirectoryAdapter extends ArrayAdapter {

		@SuppressWarnings("unchecked")
		DirectoryAdapter(Activity context) {
			super(context, R.layout.row, currentDirectoryItems);
			resetCheckedStates();
		}

		//used to setup directory view's rows
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			
			LayoutInflater inflater = getLayoutInflater();
			row = inflater.inflate(R.layout.row, parent, false);

			row.setBackgroundResource(android.R.drawable.menuitem_background);
			TextView label = (TextView) row.findViewById(R.id.text);
			ArrayList<Object> rowItem = currentDirectoryItems.get(position);
			String fName = rowItem.get(Constants.ROW_NAME).toString();
			label.setText(fName);

			TextView sublabel = (TextView) row.findViewById(R.id.subtext);
			initDirectoryCheckBox(position, row);
			CheckBox dirChkBox = (CheckBox) row.findViewById(R.id.DIR_CHECKBOX);

			
			if (rowItem.get(Constants.ROW_IS_FOLDER).equals(0)) { //it's a file
				
				String fileSize = Utils.humanFriendlyFileSize((Long) rowItem.get(Constants.SIZE));
				String owner = (String) rowItem.get(Constants.ROW_OWNER);
				String date = (String)rowItem.get(Constants.SERVER_TIMESTAMP);
				sublabel.setText(fileSize  + " | " + date+ " | " +owner);
			} else {//it's a folder
				TextView pPath = (TextView) findViewById(R.id.BREADCRUMB_LABEL);
				if(fName.equals("Shared") || fName.equals("Private")||  pPath.getText().toString().equals("Main../Private")) {
					dirChkBox.setVisibility(View.GONE);
				}				
				label.setTextSize(21);
				sublabel.setVisibility(View.GONE);
			}

			
			Resources r = getResources();
			Drawable[] layers = new Drawable[2];
			layers[1] = r.getDrawable(R.drawable.blank);
			
			// check isFolder flag for each row
			ImageView icon = (ImageView) row.findViewById(R.id.DIR_ICON);
			if (rowItem.get(Constants.ROW_IS_FOLDER).equals(0)) { //under the shared folder
				layers[0] = r.getDrawable(R.drawable.icon_img);
			} else {
				layers[0] = r.getDrawable(R.drawable.icon_shared);
			}
			
			LinearLayout rowLayout = (LinearLayout) row.findViewById(R.id.LISTVIEW_ROW);
			rowLayout.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			
			LayerDrawable layerDrawable = new LayerDrawable(layers);
			icon.setImageDrawable(layerDrawable);	
			
			return row;
		}
	}

	private void updateMasterViewByPath(final String path) {
		currentAbsolutePath = new StringBuilder(path);
		runOnUiThread(new Runnable() {
			public void run() {
				ArrayList<Object> parentItem = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowAtAbsolutePath(path);
				if(parentItem != null && parentItem.size()!=0) {
					ArrayList<ArrayList<Object>> updatedRows = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID((Integer) parentItem.get(Constants.ROW_ID));
					
					currentDirectoryItems = updatedRows;
					refreshMasterView();
				}
			}
		});
	}
	
	//reset master view with possibly new rows, update breadcrumb path, initialize button listeners and hide action bar
	public void refreshMasterView(){
		setContentView(R.layout.master_view);
		resetCheckedStates();
		setListAdapter(new DirectoryAdapter(MasterViewController.this));
		updateBreadcrumb(); //else breadcrumb ends up erased			
		hideFileActionBar();
		initButtons();
	}
	
	public void initDirDbHelper () {
		((EgnyteAppObject)getApplication()).dirDbHelper = new DirectoryDbHelper(this);// creates top level if db doesn't exist
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.master_view);
		ListView list = (ListView) findViewById(android.R.id.list);

		userInfo = Utils.getDefaultUserInfo(getBaseContext());
		if(((EgnyteAppObject)getApplication()).dirDbHelper == null) {
			initDirDbHelper();// creates top level if db doesn't exist
		}
		
		currentDirectoryItems = ((EgnyteAppObject)getApplication()).dirDbHelper.getRowsAsArraysByPID(0);
		setListAdapter(new DirectoryAdapter(MasterViewController.this));
		
		list.setItemsCanFocus(false);
		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	}
	
	
	private void initButtons(){
		Button delete_button = (Button) findViewById(R.id.DELETE_BTN);
		delete_button.setOnClickListener(deleteBtnListener);
		
		Button move_button = (Button) findViewById(R.id.MOVE_BTN);
		move_button.setOnClickListener(moveBtnListener);
		
		Button copy_button = (Button) findViewById(R.id.COPY_BTN);
		copy_button.setOnClickListener(copyBtnListener);
	}
	

	//true means errored out, false means let original method handle status code
	public boolean processStatusCode(int statusCode){
		if (statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {//503
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Server Down","Please try again later", MasterViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		}else if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
			return true;
		}else if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {//401 403
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Unauthorized","You do not have permission to perform this action.",MasterViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		} else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {//500
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Action failed","Please try again later",MasterViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		} else if (statusCode == HttpStatus.SC_NOT_FOUND) {//404
			runOnUiThread(new Runnable() {
				public void run() {
					Utils.alertbox("Action failed","File not found",MasterViewController.this);
				}
			});
			mProgress.dismiss();
			return true;
		} else if (statusCode == HttpStatus.SC_REQUEST_TIMEOUT || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT) {//408 504
			mProgress.dismiss();
			return false;
		} else if (statusCode==0){
			return true;
		}
		
		return false;
	}
	
	//move or copy the file or folder
	private void moveCopyPath(final String destPath, final String actionType) {
		if(mProgress==null || !mProgress.isShowing()) {
			mProgress = ProgressDialog.show(MasterViewController.this,"Please wait", "Syncing...", true, true);
		}
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				String currPath="";
				for (int i = 0; i < checkedStates.size(); i++) {
					if (checkedStates.get(i) == true) {
						currPath = (String) currentDirectoryItems.get(i).get(Constants.FULL_PATH);
						break;
					}
				}
						
				//httpclient in case you want to perform manipulations while request is occuring
				//file's current path
				//file's destination path
				//action type: move or copy
				//past context so handler can access user info for request 
				int sCode = HTTPHandler.fileMoveCopyAction(((EgnyteAppObject)getApplication()).httpclient, currPath, destPath, actionType, getBaseContext());

				if (actionType.equals("move") && sCode == HttpStatus.SC_OK) {
					((EgnyteAppObject)getApplication()).dirDbHelper.deleteRowByPath(currPath);

					updateMasterViewByPath(currentAbsolutePath.toString());
				} else {
					//failed
				}
				
				mProgress.dismiss();
			}
		}).start();
	}
	
	//find all of the boxes checked and delete them
	private void deleteSelectedRows(){
		if(mProgress==null || !mProgress.isShowing()) {
			mProgress = ProgressDialog.show(MasterViewController.this,"Please wait", "Syncing...", true, true);
		}
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				int shift = 0;
				for (int i = 0; i < checkedStates.size(); i++) {
					if (checkedStates.get(i) == true) {
						String fullPath="";
						fullPath = (String) currentDirectoryItems.get(i - shift).get(Constants.FULL_PATH);
						
						int sCode = HTTPHandler.fileAction(((EgnyteAppObject)getApplication()).httpclient, fullPath, "delete", getBaseContext());

						if (sCode == HttpStatus.SC_OK || sCode == HttpStatus.SC_NOT_FOUND) {
							File localFileOrFolder = new File(Constants.SD_CLOUD+userInfo.getString(Constants.DOMAIN, ""), fullPath);
							if(localFileOrFolder.isDirectory()) {//delete children nodes from sdcard and db
								Utils.deleteDirectory(fullPath,userInfo.getString(Constants.DOMAIN, ""));
							}
							
							Utils.deleteFile(localFileOrFolder);
							
							((EgnyteAppObject)getApplication()).dirDbHelper.deleteRowByPath(fullPath);
							
							currentDirectoryItems.remove(i - shift);
						} else {
							processStatusCode(sCode);
							return;//if error, leave
						}
						
						shift++;
					}

				}
				
				runOnUiThread(new Runnable() {
					public void run() {
						refreshMasterView();//list is manipulated in real time, now refresh view						
						Utils.alertbox("Task", "Delete complete", MasterViewController.this);
					}
				});

				mProgress.dismiss();
			}
		}).start();
	}
	
	private OnClickListener copyBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent myIntent = new Intent(MasterViewController.this, FolderBrowserViewController.class);
			myIntent.putExtra(Constants._PATH, currentAbsolutePath.toString());
			startActivityForResult(myIntent, Constants.COPY_PATH_ACTIVITY);
		}
	};
	
	private OnClickListener moveBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent myIntent = new Intent(MasterViewController.this, FolderBrowserViewController.class);
			myIntent.putExtra(Constants._PATH, currentAbsolutePath.toString());
			startActivityForResult(myIntent, Constants.MOVE_PATH_ACTIVITY);
		}
	};
	
	private OnClickListener deleteBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			deleteSelectedRows();
		}
	};
}
