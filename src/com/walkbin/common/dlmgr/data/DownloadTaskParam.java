package com.walkbin.common.dlmgr.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import android.text.TextUtils;

public class DownloadTaskParam {

	protected HashMap<String, String> mParams;

	public DownloadTaskParam() {
		mParams = new HashMap<String, String>();
	}
	
	public DownloadTaskParam copy(){
		DownloadTaskParam param = new DownloadTaskParam();
		param.mParams = new HashMap<String, String>(mParams);
		return param;
	}

	public String getParam(String key) {
		return mParams.get(key);
	}

	public void addParam(String key, String value) {
		mParams.put(key, value);
	}

	public String tranToString() {

		StringBuilder sb = new StringBuilder();

		Iterator<Entry<String, String>> iter = mParams.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, String> entry = iter.next();
			String key = entry.getKey();
			String val = entry.getValue();

			sb.append(key + "=" + val + ";");
		}

		int len = sb.length();
		if (len > 2)
			sb.deleteCharAt(len - 1);

		return sb.toString();
	}

	public void restoreFromString(String str) {

		if (TextUtils.isEmpty(str))
			return;

		String[] kvs = str.split(";");

		for (String kv : kvs) {
			String[] skv = kv.split("=");
			if (skv.length == 2) {
				mParams.put(skv[0], skv[1]);
			}
		}
	}
}
