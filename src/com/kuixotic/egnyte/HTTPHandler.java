package com.kuixotic.egnyte;

import java.io.File;




import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


 
public class HTTPHandler {
	
	public static void initHttpHeader(HttpRequestBase httpReq, SharedPreferences userInfo){
		String accessToken = userInfo.getString(Constants.ACCESS_TOKEN, "");
		//expected syntax "Authorization: Bearer tqd6gddjv637taa8hgf2cgjy"
		httpReq.setHeader(Constants.HEADER_AUTH, "Bearer "+ accessToken);
	}

	
	
	public static int fileMoveCopyAction(DefaultHttpClient httpclient, String currPath, String destPath, String fileAction, Context ctx){		
		HttpResponse response = null;
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
		
		try {
			HttpPost httpPost = new HttpPost(Utils.getURL("/pubapi/v1/fs"+currPath, userInfo));  
			initHttpHeader(httpPost,userInfo);
        
        	JSONObject jsonObj = new JSONObject();
			jsonObj.put("action", fileAction);
			
			String folders[] = currPath.split("/");
			jsonObj.put("destination", destPath+"/"+folders[folders.length-1]);
			
	        StringEntity entity = new StringEntity(jsonObj.toString(), HTTP.UTF_8);
	        entity.setContentType("application/json");
        	httpPost.setEntity(entity);    
        	
			response = httpclient.execute(httpPost);
			return response.getStatusLine().getStatusCode();
		} catch (UnsupportedEncodingException e) {e.printStackTrace();
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();
		} catch (JSONException e1) { e1.printStackTrace(); 
		} finally {
			if(response!=null)
				return response.getStatusLine().getStatusCode();
			else
				return 0;
		}

	}
	
	public static int fileAction(DefaultHttpClient httpclient, String newPath, String fileAction, Context ctx){		
		HttpResponse response = null;
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
		 try {
	        HttpPost httpPost = new HttpPost(Utils.getURL("/pubapi/v1/fs"+newPath, userInfo));    
	        initHttpHeader(httpPost,userInfo);
        	JSONObject jsonObj = new JSONObject();
			jsonObj.put("action", fileAction);//add_folder

	        StringEntity entity = new StringEntity(jsonObj.toString(), HTTP.UTF_8);
	        entity.setContentType("application/json");
        	httpPost.setEntity(entity);    
        	
			response = httpclient.execute(httpPost);
		} catch (UnsupportedEncodingException e) {e.printStackTrace();
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();
		} catch (JSONException e1) { e1.printStackTrace(); 
		} finally {
			if(response!=null)
				return response.getStatusLine().getStatusCode();
			else
				return 0;
		}

	}
	
	public static int fileActionDelete(DefaultHttpClient httpclient, String newPath, String fileAction, Context ctx){		
		HttpResponse response = null;
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
		 try {
			HttpDelete httpDelete = new HttpDelete(Utils.getURL("/pubapi/v1/fs"+newPath, userInfo));    
			initHttpHeader(httpDelete,userInfo);   
        	
			response = httpclient.execute(httpDelete);
		} catch (UnsupportedEncodingException e) {e.printStackTrace();
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();
		} finally {
			if(response!=null)
				return response.getStatusLine().getStatusCode();
			else
				return 0;
		}

	}
	
	
	public static HttpResponse putFile(DefaultHttpClient httpclient, String relativePath, String fileName, File data, String timestamp, Long size, Context ctx, DirectoryDbHelper dbHelper){
		
		HttpResponse response = null;
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
        String absolutePath = relativePath+"/"+fileName;
        String mimeType = Utils.getMimeType(fileName);
        
        try {
        	
	        HttpPost httpPost = new HttpPost(Utils.getURL("/pubapi/v1/fs-content/"+absolutePath, userInfo));    
	        initHttpHeader(httpPost,userInfo);
			FileEntity fileEntity = new FileEntity(data, mimeType);
			httpPost.setEntity(fileEntity);
			response = httpclient.execute(httpPost);            
            
            return response;

		} catch (UnsupportedEncodingException e) {e.printStackTrace();
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();
		} catch (IllegalArgumentException e) {e.printStackTrace();
		} catch (URISyntaxException e) { e.printStackTrace(); }
        
        return null;
	}
	
	public static void ignoreRequest(DefaultHttpClient httpclient){
		if(httpclient!=null){
			httpclient.clearRequestInterceptors();
			httpclient.getConnectionManager().closeIdleConnections(0, TimeUnit.MILLISECONDS);
		}
	}

	public static HttpResponse getFile(DefaultHttpClient httpclient,String EID, String relativePath, String fileName, Context ctx){
		String TAG = "HTTPGetter";

		if(httpclient==null)
			httpclient = new DefaultHttpClient();
		
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
		Log.v(TAG,"getFile:"+relativePath+" "+fileName+" "+EID + userInfo.getString(Constants.DOMAIN, ""));
		StringBuilder absolutePath = new StringBuilder();
		absolutePath.append(relativePath+"/"+fileName);

		HttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(Utils.getURL("/pubapi/v1/fs-content"+absolutePath, userInfo));    

            initHttpHeader(httpGet,userInfo);	
            httpGet.setHeader("Content-Type", "text/plain");
            File currentFile = new File(Constants.SD_CLOUD+userInfo.getString(Constants.DOMAIN, ""),absolutePath.toString());
            if(currentFile.exists()){
        		//if file exists locally, pass EID to header and if server has same, we get 304 back
        		Log.v(TAG,"yes there is a local copy:"+EID);
        		httpGet.setHeader("Etag", EID);
        	}
            	
            response = httpclient.execute(httpGet);
            
            return response;
            
        } catch (ClientProtocolException e) {e.printStackTrace();
        } catch (IOException e) {e.printStackTrace();
		} catch (URISyntaxException e) {e.printStackTrace();} 
			
		return null;
	}
	
	public static HttpResponse getDirectory(DefaultHttpClient httpclient,String path, Context ctx){		
		Log.v("getDirectory",path);
		SharedPreferences userInfo = Utils.getDefaultUserInfo(ctx);
		
		try {
			HttpGet httpGet = new HttpGet(Utils.getURLWithParameter("/pubapi/v1/fs"+path, "list_content=true", userInfo));    			
	        initHttpHeader(httpGet,userInfo);	
	        httpGet.setHeader("Content-Type", "text/plain");
	        
        	return httpclient.execute(httpGet);
		} catch (UnsupportedEncodingException e) {e.printStackTrace();
		} catch (ClientProtocolException e) {e.printStackTrace();
		} catch (IOException e) {e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
			return null;
		}
        
		
        return null;
	}

}