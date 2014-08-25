package com.pplive.tvmarket.dlmgr.data;

import com.pplive.tvmarket.dlmgr.DownloadTaskParam;

public final class DownloadTaskData {

	public static enum DownloadStatus {
		WAITING, PREPARE, DOWNLOADING, PAUSED, SUSPEND, FAILED, DONE
	}

	public String url;
	public DownloadTaskParam params;
	public long totalSize;
	public DownloadStatus status;// 状态
	public long createTime;

	public DownloadTaskData() {
		params = new DownloadTaskParam();
	}

	public DownloadTaskData copy() {

		DownloadTaskData interData = new DownloadTaskData();
		interData.url = new String(url);
		interData.params = params.copy();
		interData.totalSize = totalSize;
		interData.status = status;
		interData.createTime = createTime;

		return interData;
	}

	public String getParam(String key) {
		if (params != null)
			return params.getParam(key);
		else
			return null;
	}

	public void addParam(String key, String value) {
		if (params != null)
			params.addParam(key, value);
	}
}
