package com.example.testdownloadmanager;


import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.pplive.tvmarket.dlmgr.DownloadManager;
import com.pplive.tvmarket.dlmgr.DownloadManager.DownloadManagerListener;
import com.pplive.tvmarket.dlmgr.DownloadTask;
import com.pplive.tvmarket.dlmgr.data.DownloadTaskData;
import com.pplive.tvmarket.dlmgr.error.SDCardCannotWriteException;
import com.pplive.tvmarket.dlmgr.error.SDCardNotFoundException;
import com.pplive.tvmarket.dlmgr.error.TaskAlreadyExistException;
import com.pplive.tvmarket.dlmgr.error.TaskFullException;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;

public class MainActivity extends Activity implements OnClickListener,DownloadManagerListener{

	private ListView mDownloadListView;
	private DownloadListAdapter downloadListAdapter;
	private Button mBtnAddAll;
	private Button mBtnAdd;
	private Button mBtnPauseAll;
	private Button mBtnResumeAll;
	private Button mBtnDeleteAll;
	
	private DownloadManager mDLMgr;
	
	private static List<DownloadInfo> demoUrls;
	
	private static void initDownloadInfos(){
		demoUrls = new ArrayList<DownloadInfo>();
		demoUrls.add(new DownloadInfo("天天动听","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/14/5818_ef11076a-5685-4ad6-893d-98c78705c4ea.apk"));
		demoUrls.add(new DownloadInfo("咪咕音乐播放器","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/8/461977_0ee9139d-f62c-415e-96d9-a8874df5ba10.apk"));
		demoUrls.add(new DownloadInfo("百度手机卫士","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/19/4780_5b3a045e-7aad-4934-a334-5be74acc7903.apk"));
		demoUrls.add(new DownloadInfo("淘宝","http://cdn2.down.apk.gfan.com/asdf/Pfiles/2014/7/17/11906_1e46fbac-b859-44f4-94ae-28eb0dc927b9.apk"));
		demoUrls.add(new DownloadInfo("QQ空间","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/14/5512_50b4398c-1582-4055-a500-232a2de9247c.apk"));
		demoUrls.add(new DownloadInfo("有道词典","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/7/22/4354_440f592f-850a-4e5e-9360-0c8e5df1ee2b.apk"));
		demoUrls.add(new DownloadInfo("美图秀秀","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/7/92460_39a2bfda-a7ad-4e6d-9f6f-7da833160139.apk"));
		demoUrls.add(new DownloadInfo("PPTV网络电视","http://cdn2.down.apk.gfan.com/asdf/Pfiles/2014/8/15/171337_74600efe-7e30-45bd-86ea-903ce17ed258.apk"));
		demoUrls.add(new DownloadInfo("搜狐视频","http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/19/154682_3617ad4d-a3db-4e35-812f-e7aed3bea4d5.apk"));
	}
	
	static{
		initDownloadInfos();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mDLMgr = new DownloadManager(this);
		mDLMgr.startManage();
		
		mDownloadListView = (ListView) findViewById(R.id.download_list);
		downloadListAdapter = new DownloadListAdapter(this);
		mDownloadListView.setAdapter(downloadListAdapter);
		
		mBtnAddAll = (Button) findViewById(R.id.btn_add_all);
		mBtnAdd = (Button) findViewById(R.id.btn_add);
		mBtnPauseAll = (Button) findViewById(R.id.btn_pause_all);
		mBtnResumeAll = (Button) findViewById(R.id.btn_resume_all);
		mBtnDeleteAll = (Button) findViewById(R.id.btn_delete_all);
		
		mBtnAddAll.setOnClickListener(this);
		mBtnAdd.setOnClickListener(this);
		mBtnPauseAll.setOnClickListener(this);
		mBtnResumeAll.setOnClickListener(this);
		mBtnDeleteAll.setOnClickListener(this);
		
		mDLMgr.addListener(this);
	}

	@Override
	protected void onDestroy() {
		
		mDLMgr.close();
		super.onDestroy();
	}
	
	public DownloadManager getDLMgr(){
		return mDLMgr;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	
	private DownloadTaskData createDownloadTaskData(DownloadInfo info){
		DownloadTaskData data = new DownloadTaskData();
		
		data.url = info.url;
		data.params.addParam("title", info.title);
		return data;
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.btn_add:
		{
			int idx = new Random().nextInt(demoUrls.size());
			DownloadTaskData data = createDownloadTaskData(demoUrls.get(idx));
			try {
				mDLMgr.addTask(data);
			}catch (Exception e) {
				// TODO: handle exception
			}
		}
			break;
		case R.id.btn_add_all:
			
			for(int i=0 ; i < demoUrls.size(); i++){
				DownloadTaskData data = createDownloadTaskData(demoUrls.get(i));
				try {
					mDLMgr.addTask(data);
				} catch (SDCardNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SDCardCannotWriteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TaskFullException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace(); 
				} catch (TaskAlreadyExistException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			break;
		case R.id.btn_delete_all:
			mDLMgr.deleteAllTask();
			break;
		case R.id.btn_pause_all:
			mDLMgr.pauseAllTask();
			break;
		case R.id.btn_resume_all:
			mDLMgr.continueAllTask();
			break;
			
		}
	}

	@Override
	public void onTaskAdded(DownloadTask task) {
		downloadListAdapter.addItem(task);
	}

	@Override
	public void onTaskBegin(DownloadTask task) {
		downloadListAdapter.updateItem(task);
	}

	@Override
	public void onTaskUpdate(DownloadTask task) {
		downloadListAdapter.updateItem(task);
	}

	@Override
	public void onTaskDone(DownloadTask task) {
//		downloadListAdapter.removeItem(task.getUrl());
	}

	@Override
	public void onTaskDeleted(DownloadTask task) {
//		downloadListAdapter.removeItem(task.getUrl());
	}

	@Override
	public void onTaskError(DownloadTask task, Throwable error) {
		// TODO Auto-generated method stub
	}
}
