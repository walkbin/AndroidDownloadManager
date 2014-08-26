package com.example.testdownloadmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.walkbin.common.dlmgr.DownloadManager;
import com.walkbin.common.dlmgr.DownloadTask;
import com.walkbin.common.dlmgr.DownloadManager.DownloadManagerListener;
import com.walkbin.common.dlmgr.data.DownloadTaskData;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;

public class MainActivity extends Activity implements OnClickListener,
		DownloadManagerListener {

	private ListView mDownloadListView;
	private DownloadListAdapter mDownloadListAdapter;
	private Button mBtnAddAll;
	private Button mBtnAdd;
	private Button mBtnPauseAll;
	private Button mBtnResumeAll;
	private Button mBtnDeleteAll;

	private DownloadManager mDLMgr;

	private static final String TAG = "MainActivity";

	private static List<DownloadInfo> demoUrls;

	private static void initDownloadInfos() {
		demoUrls = new ArrayList<DownloadInfo>();
		demoUrls.add(new DownloadInfo(
				"消灭星星2粉碎糖果",
				"http://d1.apk8.com:8020/youxi/CrazyCandy_MMSMS_FREE_4_6_300004538018_3003956797.apk"));
		demoUrls.add(new DownloadInfo(
				"火箭飞人",
				"http://d.apk8.com:8020/CPS/JPJ-1366-paid_v1.3.7.5_s1.5.2-001100_AP0S0N10000.apk"));
		demoUrls.add(new DownloadInfo("手机QQ 官网版",
				"http://d1.apk8.com:8020/soft/QQ5.0.0.apk"));
		demoUrls.add(new DownloadInfo(
				"淘宝",
				"http://cdn2.down.apk.gfan.com/asdf/Pfiles/2014/7/17/11906_1e46fbac-b859-44f4-94ae-28eb0dc927b9.apk"));
		demoUrls.add(new DownloadInfo(
				"京东商城",
				"http://d1.apk8.com:8020/soft/%E4%BA%AC%E4%B8%9C%E5%95%86%E5%9F%8E%E5%AE%98%E7%BD%91%E7%89%88.apk.apk"));
		demoUrls.add(new DownloadInfo(
				"爱奇艺视频",
				"http://d1.apk8.com:8020/soft/%E7%88%B1%E5%A5%87%E8%89%BA%E8%A7%86%E9%A2%91.apk"));
		demoUrls.add(new DownloadInfo(
				"美图秀秀",
				"http://cdn6.down.apk.gfan.com/asdf/Pfiles/2014/8/7/92460_39a2bfda-a7ad-4e6d-9f6f-7da833160139.apk"));
		demoUrls.add(new DownloadInfo(
				"PPTV网络电视",
				"http://cdn2.down.apk.gfan.com/asdf/Pfiles/2014/8/15/171337_74600efe-7e30-45bd-86ea-903ce17ed258.apk"));
		demoUrls.add(new DownloadInfo(
				"讯飞语音输入法",
				"http://d1.apk8.com:8020/soft/%E8%AE%AF%E9%A3%9E%E8%BE%93%E5%85%A5%E6%B3%95.apk"));
	}

	static {
		initDownloadInfos();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mDLMgr = DownloadManager.INSTANCE.setContext(this);
		mDLMgr.startManage();

		mDownloadListView = (ListView) findViewById(R.id.download_list);
		mDownloadListAdapter = new DownloadListAdapter(this);
		mDownloadListView.setAdapter(mDownloadListAdapter);

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

		mDLMgr.deleteListener(this);
		mDLMgr.close();
		super.onDestroy();
	}

	public DownloadManager getDLMgr() {
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

	private DownloadTaskData createDownloadTaskData(DownloadInfo info) {
		DownloadTaskData data = new DownloadTaskData();

		data.url = info.url;
		data.params.addParam("title", info.title);
		return data;
	}

	/** UI priority should be highest */
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_add: {
			int idx = new Random().nextInt(demoUrls.size());
			DownloadTaskData data = createDownloadTaskData(demoUrls.get(idx));
			DownloadTask task = mDLMgr.addTaskSafely(data);
			if (task != null) {
				mDownloadListAdapter.addItem(task, true);
			}
		}
			break;
		case R.id.btn_add_all: {
			for (int i = 0; i < demoUrls.size(); i++) {
				DownloadTaskData data = createDownloadTaskData(demoUrls.get(i));
				DownloadTask task = mDLMgr.addTaskSafely(data);
				if (task != null) {
					mDownloadListAdapter.addItem(task, false);
				}
			}

			mDownloadListAdapter.notifyDataSetChanged();
		}
			break;
		case R.id.btn_delete_all:{
			mDownloadListAdapter.removeAllItems();
			mDLMgr.deleteAllTask();
		}
			break;
		case R.id.btn_pause_all:
			mDownloadListAdapter.pauseResumeAllItems(true);
			mDLMgr.pauseAllTask();
			break;
		case R.id.btn_resume_all:
			mDownloadListAdapter.pauseResumeAllItems(false);
			mDLMgr.continueAllTask();
			break;

		}
	}

	@Override
	public void onTaskAdded(DownloadTask task) {
		Log.e(TAG, "----onTaskAdded----" + task.getTitle());

	}

	@Override
	public void onTaskBegin(DownloadTask task) {
		mDownloadListAdapter.updateItem(task);
		Log.e(TAG, "----onTaskBegin----" + task.getTitle());
	}

	@Override
	public void onTaskUpdate(DownloadTask task) {
		mDownloadListAdapter.updateItem(task);

		// Log.e(TAG, "----onTaskUpdate----" + task.getTitle());
	}

	@Override
	public void onTaskDone(DownloadTask task) {
		// downloadListAdapter.removeItem(task.getUrl());
		Log.e(TAG, "----onTaskDone----" + task.getTitle());
	}

	@Override
	public void onTaskDeleted(DownloadTask task) {

		Log.e(TAG, "----onTaskDeleted----" + task.getTitle());
		// downloadListAdapter.removeItem(task.getUrl());
	}

	@Override
	public void onTaskError(DownloadTask task, Throwable error) {
		Log.e(TAG, "----onTaskError----" + task.getTitle() + "----error="
				+ error.getMessage());
	}
}
