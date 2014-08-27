package com.example.testdownloadmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.walkbin.common.dlmgr.DownloadManager;
import com.walkbin.common.dlmgr.DownloadTask;

public class DownloadListAdapter extends BaseAdapter {

	private Context mContext;
	private ArrayList<HashMap<Integer, String>> dataList;
	private DownloadManager mDLMgr;

	public DownloadListAdapter(Context context) {
		mContext = context;
		dataList = new ArrayList<HashMap<Integer, String>>();
		mDLMgr = ((MainActivity) mContext).getDLMgr();
	}

	@Override
	public int getCount() {
		return dataList.size();
	}

	@Override
	public Object getItem(int position) {
		return dataList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public void addItem(DownloadTask task, boolean ifNotify) {
		HashMap<Integer, String> item = ViewHolder.createItemDataMap(task);
		dataList.add(item);
		if (ifNotify)
			notifyDataSetChanged();
	}

	public void updateItem(DownloadTask task) {
		String tmp;
		for (int i = 0; i < dataList.size(); i++) {
			tmp = dataList.get(i).get(ViewHolder.KEY_URL);
			if (tmp.equals(task.getUrl())) {
				ViewHolder.updateDataMap(dataList.get(i), task);
				notifyDataSetChanged();
				break;
			}
		}
	}

	public void updateAllItems() {
		String tmp;
		DownloadTask task;
		Iterator<HashMap<Integer, String>> it = dataList.iterator();
		while (it.hasNext()) {
			tmp = it.next().get(ViewHolder.KEY_URL);
			task = mDLMgr.getTask(tmp);
			if (task != null)
				ViewHolder.updateDataMap(it.next(), task);
		}

		notifyDataSetChanged();
	}

	public void pauseResumeAllItems(boolean ifPause) {

		Iterator<HashMap<Integer, String>> it = dataList.iterator();
		while (it.hasNext()) {
			HashMap<Integer, String> item = it.next();
			item.put(ViewHolder.KEY_IS_PAUSED, String.valueOf(ifPause));
		}

		notifyDataSetChanged();
	}

	public void removeItem(String url) {
		String tmp;

		Iterator<HashMap<Integer, String>> it = dataList.iterator();

		while (it.hasNext()) {
			tmp = it.next().get(ViewHolder.KEY_URL);
			if (tmp.equals(url)) {
				it.remove();
			}
		}

		notifyDataSetChanged();
	}

	public void removeAllItems() {
		dataList.clear();
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = LayoutInflater.from(mContext).inflate(
					R.layout.download_list_item, null);
		}

		HashMap<Integer, String> itemData = dataList.get(position);
		String url = itemData.get(ViewHolder.KEY_URL);
		convertView.setTag(url);

		ViewHolder viewHolder = new ViewHolder(convertView);
		viewHolder.setData(itemData);

		viewHolder.continueButton.setOnClickListener(new DownloadBtnListener(
				url, viewHolder));
		viewHolder.pauseButton.setOnClickListener(new DownloadBtnListener(url,
				viewHolder));
		viewHolder.deleteButton.setOnClickListener(new DownloadBtnListener(url,
				viewHolder));

		return convertView;
	}

	private class DownloadBtnListener implements View.OnClickListener {
		private String url;
		private ViewHolder mViewHolder;

		public DownloadBtnListener(String url, ViewHolder viewHolder) {
			this.url = url;
			this.mViewHolder = viewHolder;
		}

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.btn_continue:{
				mDLMgr.continueTask(url);
				HashMap<Integer, String> itemData = mViewHolder.getData();
				itemData.put(ViewHolder.KEY_IS_PAUSED, String.valueOf(false));
				mViewHolder.setData(itemData);
			}
				break;
			case R.id.btn_pause: {
				mDLMgr.pauseTask(url);
				HashMap<Integer, String> itemData = mViewHolder.getData();
				itemData.put(ViewHolder.KEY_IS_PAUSED, String.valueOf(true));
				mViewHolder.setData(itemData);
			}
				break;
			case R.id.btn_delete:
				removeItem(url);
				mDLMgr.deleteTask(url);
				break;
			}
		}
	}
}