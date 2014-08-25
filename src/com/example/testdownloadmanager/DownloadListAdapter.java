package com.example.testdownloadmanager;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.pplive.tvmarket.dlmgr.DownloadManager;
import com.pplive.tvmarket.dlmgr.DownloadTask;

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

	public void addItem(DownloadTask task) {
		HashMap<Integer, String> item = ViewHolder.getItemDataMap(task);
		dataList.add(item);
		this.notifyDataSetChanged();
	}

	public void updateItem(DownloadTask task){
		String tmp;
		for (int i = 0; i < dataList.size(); i++) {
			tmp = dataList.get(i).get(ViewHolder.KEY_URL);
			if (tmp.equals(task.getUrl())) {
				ViewHolder.updateDataMap(dataList.get(i),task);
				this.notifyDataSetChanged();
				break;
			}
		}
	}
	
	public void removeItem(String url) {
		String tmp;
		for (int i = 0; i < dataList.size(); i++) {
			tmp = dataList.get(i).get(ViewHolder.KEY_URL);
			if (tmp.equals(url)) {
				dataList.remove(i);
				this.notifyDataSetChanged();
				break;
			}
		}
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
			case R.id.btn_continue:
				mDLMgr.continueTask(url);
				mViewHolder.continueButton.setVisibility(View.GONE);
				mViewHolder.pauseButton.setVisibility(View.VISIBLE);
				break;
			case R.id.btn_pause:
				mDLMgr.pauseTask(url);
				mViewHolder.continueButton.setVisibility(View.VISIBLE);
				mViewHolder.pauseButton.setVisibility(View.GONE);
				break;
			case R.id.btn_delete:
				mDLMgr.deleteTask(url);
				removeItem(url);
				break;
			}
		}
	}
}