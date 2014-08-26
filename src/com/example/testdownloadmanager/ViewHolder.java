package com.example.testdownloadmanager;

import java.util.HashMap;

import com.walkbin.common.dlmgr.DownloadManagerHelper;
import com.walkbin.common.dlmgr.DownloadTask;
import com.walkbin.common.dlmgr.data.DownloadTaskData.DownloadStatus;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ViewHolder {

	public static final int KEY_URL = 0;
	public static final int KEY_SPEED = 1;
	public static final int KEY_PROGRESS = 2;
	public static final int KEY_IS_PAUSED = 3;
	public static final int KEY_TITLE = 4;
	public static final int KEY_SIZE = 5;

	public TextView titleText;
	public ProgressBar progressBar;
	public TextView speedText;
	public TextView sizeText;
	public TextView percentText;

	public Button pauseButton;
	public Button deleteButton;
	public Button continueButton;

	private boolean hasInited = false;

	public ViewHolder(View parentView) {
		if (parentView != null) {

			titleText = (TextView) parentView.findViewById(R.id.text_title);
			speedText = (TextView) parentView.findViewById(R.id.text_state);
			sizeText = (TextView) parentView.findViewById(R.id.text_size);
			percentText = (TextView) parentView.findViewById(R.id.text_percent);

			progressBar = (ProgressBar) parentView
					.findViewById(R.id.progress_bar);
			pauseButton = (Button) parentView.findViewById(R.id.btn_pause);
			deleteButton = (Button) parentView.findViewById(R.id.btn_delete);
			continueButton = (Button) parentView
					.findViewById(R.id.btn_continue);
			hasInited = true;
		}
	}

	public static void updateDataMap(HashMap<Integer, String> item,
			DownloadTask task) {
		item.put(KEY_TITLE, task.getData().params.getParam("title"));
		item.put(KEY_URL, task.getData().url);
		item.put(KEY_SPEED,
				DownloadManagerHelper.speed(task.getDownloadSpeed()));
		item.put(KEY_PROGRESS, task.getDownloadPercent() + "");
		item.put(KEY_IS_PAUSED,
				(task.getData().status == DownloadStatus.PAUSED) + "");
		item.put(KEY_SIZE, DownloadManagerHelper.size(task.getDownloadSize())
				+ "/" + DownloadManagerHelper.size(task.getTotalSize()));
	}

	public static HashMap<Integer, String> createItemDataMap(DownloadTask task) {
		HashMap<Integer, String> item = new HashMap<Integer, String>();
		updateDataMap(item, task);
		return item;
	}

	public void setData(HashMap<Integer, String> item) {
		if (hasInited) {
			titleText.setText(item.get(KEY_TITLE));

			percentText.setText(item.get(KEY_PROGRESS) + "%");
			sizeText.setText(item.get(KEY_SIZE));
			String progress = item.get(KEY_PROGRESS);
			if (TextUtils.isEmpty(progress)) {
				progressBar.setProgress(0);
			} else {
				progressBar.setProgress(Integer.parseInt(progress));
			}
			if (Boolean.parseBoolean(item.get(KEY_IS_PAUSED))) {
				speedText.setText("已暂停");
				pauseButton.setVisibility(View.GONE);
				continueButton.setVisibility(View.VISIBLE);
			} else {
				speedText.setText(item.get(KEY_SPEED));
				pauseButton.setVisibility(View.VISIBLE);
				continueButton.setVisibility(View.GONE);
			}
		}
	}

	// public void setData(String url, String speed, String progress) {
	// setData(url, speed, progress, false + "");
	// }

	// public void setData(String url, String speed, String progress,
	// String isPaused) {
	// if (hasInited) {
	// HashMap<Integer, String> item = getItemDataMap(url, speed,
	// progress, isPaused);
	//
	// titleText
	// .setText(item.get(KEY_TITLE));
	// speedText.setText(speed);
	// if (TextUtils.isEmpty(progress)) {
	// progressBar.setProgress(0);
	// } else {
	// progressBar
	// .setProgress(Integer.parseInt(item.get(KEY_PROGRESS)));
	// }
	//
	// }
	// }

	// public void bindTask(DownloadTask task) {
	// if (hasInited) {
	// titleText.setText(NetworkUtils.getFileNameFromUrl(task.getUrl()));
	// speedText.setText(task.getDownloadSpeed() + "kbps | "
	// + task.getDownloadSize() + " / " + task.getTotalSize());
	// progressBar.setProgress((int) task.getDownloadPercent());
	// if (task.isInterrupt()) {
	// onPause();
	// }
	// }
	// }

}
