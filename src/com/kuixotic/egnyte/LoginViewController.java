package com.kuixotic.egnyte;

import java.net.MalformedURLException;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;

public class LoginViewController extends Activity {
	private ProgressDialog mProgress = null;
	private static String TAG = "LoginViewController";   

	@Override
	public void onBackPressed() {		
		setContentView(R.layout.login_view);
		initButtons();
		return;
	}

	//user wants to link with egnyte, hide link button and display egnyte login fields
	private OnClickListener linkToEgnyteBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			EditText domainInput = (EditText) findViewById(R.id.DOMAIN_INPUT);
			domainInput.setVisibility(View.VISIBLE);
			
			Button validate_button = (Button) findViewById(R.id.SIGNIN_BTN);
			validate_button.setVisibility(View.VISIBLE);
			
			Button link_to_button = (Button) findViewById(R.id.EGNYTE_LINK_BTN);
			link_to_button.setVisibility(View.GONE);
		}
	};
	
	//when validation button is clicked. launch oauth webview
	private OnClickListener validateDomainBtnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			launchLoginSSO();
		}
	};
	
	protected void launchLoginSSO() {
		final EditText domainInput = (EditText) findViewById(R.id.DOMAIN_INPUT);

		//1. pull up embedded browser for oauth
		setContentView(R.layout.web_view);
		final WebView webview = (WebView) findViewById(R.id.BROWSER_PAGE);
		
		//2. load oauth url with your unique api_key
		webview.loadUrl("https://"+ domainInput.getText().toString() + Constants.SERVER + "com/puboauth/" +
				"token?client_id="+Constants.API_KEY+"&redirect_uri=https://www.egnyte.com/&mobile=1");
		CookieSyncManager.createInstance(getBaseContext());
		if(CookieManager.getInstance().hasCookies()) {
			CookieManager.getInstance().removeAllCookie();
		}
		CookieManager.getInstance().setAcceptCookie(true);
		webview.getSettings().setJavaScriptEnabled(true);
		webview.getSettings().setUseWideViewPort(true);		
		webview.requestFocus(View.FOCUS_DOWN);
	    webview.setOnTouchListener(new View.OnTouchListener() {
	       //fixes soft keyboard issue in 2.3.4
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
	                case MotionEvent.ACTION_DOWN:
	                case MotionEvent.ACTION_UP:
	                    if (!v.hasFocus()) {
	                        v.requestFocus();
	                    }
	                    break;
	            }
				return false;
			}
	    });
		webview.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				mProgress.dismiss();
			}
			
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				Log.v(TAG,"onPageStarted url=" + url);  
				if(mProgress==null || !mProgress.isShowing()) {
					mProgress = ProgressDialog.show(LoginViewController.this,"Please wait", "Loading...", true, true);
				}
				
				//3. if access_token appears in url, it means login successful, so pull the token out
				if (url.contains("access_token")) {
					try {
						URL finishedUrl = new URL(url);
						String query = finishedUrl.getRef();  
						Map<String, String> map = getQueryMap(query);  
						String domain = domainInput.getText().toString().trim();
						String client_id = UUID.randomUUID().toString();
						Utils.rememberSsoTokens(domain, map.get("access_token"), map.get("token_type"), client_id, getBaseContext());
					} catch (MalformedURLException e) { 
						Log.e(TAG,Constants.EXCEPTION_MSG,e);
					}
					
					setContentView(R.layout.login_view);
					initButtons();
					toMasterView();
				} else if (url.contains("access_denied")) {
					mProgress.dismiss();
					setContentView(R.layout.login_view);
					initButtons();
				}
			}
			
			public Map<String, String> getQueryMap(String query) {  
			    String[] params = query.split("&");  
			    Map<String, String> map = new HashMap<String, String>();  
			    for (String param : params) {  
			        String name = param.split("=")[0];  
			        String value = param.split("=")[1];  
			        map.put(name, value);  
			    }  
			    return map;  
			}

			
			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				Log.i("webview onReceivedError", "error code:" + errorCode);
				if(errorCode==-2) {
					//page loading error. ie if url does not exist
				}
				
		        super.onReceivedError(view, errorCode, description, failingUrl);
			}
		});
	}
	
	public void toMasterView() {
		((EgnyteAppObject)getApplication()).dirDbHelper = new DirectoryDbHelper(this);// creates top level if db doesn't exist
		Intent i = new Intent(getBaseContext(), MasterViewController.class);
		startActivityForResult(i, Constants.MAIN_APP);
	}
	
	public void initButtons() {
		// hook sign in button to listener
		Button validate_button = (Button) findViewById(R.id.SIGNIN_BTN);
		validate_button.setOnClickListener(validateDomainBtnListener);
		
		Button link_to_button = (Button) findViewById(R.id.EGNYTE_LINK_BTN);
		link_to_button.setOnClickListener(linkToEgnyteBtnListener);
		
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//set view controller to login view
		setContentView(R.layout.login_view);
		initButtons();
	}
}