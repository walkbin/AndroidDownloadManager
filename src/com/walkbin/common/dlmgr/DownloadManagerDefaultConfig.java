package com.walkbin.common.dlmgr;

import java.io.File;

import android.os.Environment;

public class DownloadManagerDefaultConfig {

    public static final boolean DEBUG = true;
	/**同时下载的任务数*/
	public static final int MAX_CONCURRENT_COUNT = 3;
	
	/**下载文件的存放的目录名*/
	public static final String DIR_DOWNLOAD = "testtest";

	private static final String SDCARD_ROOT = Environment
			.getExternalStorageDirectory().getAbsolutePath() + File.separator;
	
	/**下载文件的根目录*/
	public static final String FILE_ROOT = SDCARD_ROOT + DIR_DOWNLOAD
			+ File.separator;

	/**下载时，低存储空间的阈值*/
	public static final long LOW_STORAGE_THRESHOLD = 1024 * 1024 * 10;
	
	/**下载的临时文件后缀*/
	public static final String DEFAULT_TEMP_FILE_SUFFIX = ".dltmp";
	/**下载后文件的后缀*/
	public static final String DEFAULT_FILE_SUFFIX = ".apk";
	/**下载的超时时间*/
	public final static int DOWNLOAD_TIME_OUT = 30000;
	/**下载的缓存大小*/
    public final static int DOWNOAD_BUFFER_SIZE = 1024 * 8;
    
    /**保存下载相关信息的数据库文件*/
    public final static String DB_FILE = "dlmgr.db";
    
    /**下载表中保存的记录上限,-1 表示不设上限*/
	public final static int MAX_RECORD_COUNT = -1;
	
	public static final String getFileSuffix(String url){
		return DEFAULT_FILE_SUFFIX;
	}
	
	public static final String getFileTempSuffix(){
		return DEFAULT_TEMP_FILE_SUFFIX;
	}
	
	public static final String getSDCardRoot(){
		return SDCARD_ROOT;
	}
}
