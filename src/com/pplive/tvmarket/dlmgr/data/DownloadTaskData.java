package com.pplive.tvmarket.dlmgr.data;

import com.pplive.tvmarket.dlmgr.DownloadTaskParam;

public final class DownloadTaskData {

	public static final int PREPARE = 1;
	public static final int DOWNLOADING = 2;
	public static final int PAUSED = 3;
	public static final int DOWNLOADFAIL = 4;
	public static final int DOWNLOADED = 5;

	public String url;
	public DownloadTaskParam params;
	public long totalSize;
	public int status;// 状态
	public long createTime;
	
	public DownloadTaskData(){
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
	
	public void addParam(String key,String value){
		if (params != null)
			params.addParam(key, value);
	}
}
