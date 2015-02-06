package com.kuixotic.egnyte;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;


import android.app.Activity;
import android.app.Application;

@SuppressWarnings("unused")
public class EgnyteAppObject extends Application{
	
	//so you can cancel the client if you want to
	public DefaultHttpClient httpclient;
	//so it doesn't get lost/broken on rotation and etc..
	public DirectoryDbHelper dirDbHelper;
    
    @Override
	public void onCreate() {
    	System.out.println("It is HERE where the application object sprung forth");
    	
    	httpclient = new DefaultHttpClient();
    }
}
