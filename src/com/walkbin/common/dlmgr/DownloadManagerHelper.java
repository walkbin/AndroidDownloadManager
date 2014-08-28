package com.walkbin.common.dlmgr;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

public class DownloadManagerHelper {

	public static boolean isNetworkAvailable(Context context) {
		ConnectivityManager connectivity = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity == null) {
			return false;
		} else {
			NetworkInfo[] info = connectivity.getAllNetworkInfo();
			if (info != null) {
				for (int i = 0; i < info.length; i++) {
					if (info[i].getState() == NetworkInfo.State.CONNECTED
							|| info[i].getState() == NetworkInfo.State.CONNECTING) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static String getUrlFileName(String url) {

		if (TextUtils.isEmpty(url))
			return null;

		try {
			URL u = new URL(url);
			String fileName = new File(u.getFile()).getName();
			return fileName;
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public static File getFile(String url) {

		String fileName = getUrlFileName(url);
		if (TextUtils.isEmpty(fileName))
			return null;

		return new File(DownloadManagerDefaultConfig.FILE_ROOT, fileName);
	}

	public static File getTempFile(String url) {
		String fileName = getUrlFileName(url);
		if (TextUtils.isEmpty(fileName))
			return null;

		return new File(DownloadManagerDefaultConfig.FILE_ROOT, fileName
				+ DownloadManagerDefaultConfig.getFileTempSuffix());
	}

	public static boolean isSdCardWrittenable() {

		if (android.os.Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED)) {
			return true;
		}
		return false;
	}

	public static int getAndroidSDKVersion() {
		int version = 0;
		try {
			version = Integer.valueOf(android.os.Build.VERSION.SDK_INT);
		} catch (NumberFormatException e) {
		}
		return version;
	}

	public static long getAvailableStorage(String workingDir) {

		try {
			StatFs stat = new StatFs(workingDir);

			long avaliableSize = ((long) stat.getAvailableBlocks() * (long) stat
					.getBlockSize());

			return avaliableSize;
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	public static boolean checkLeftStorageEnough(String workingDir,long threshold) {
		return getAvailableStorage(workingDir) >= threshold;
	}

	public static boolean isSDCardPresent() {

		return Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED);
	}

	public static void mkdir(String dirPath) {

		File file = new File(dirPath);
		if (!file.exists() || !file.isDirectory())
			file.mkdirs();
	}

	// public static Bitmap getLoacalBitmap(String url) {
	//
	// try {
	// FileInputStream fis = new FileInputStream(url);
	// return BitmapFactory.decodeStream(fis); // /把流转化为Bitmap图片
	//
	// } catch (FileNotFoundException e) {
	// e.printStackTrace();
	// return null;
	// }
	// }

	public static String size(long size) {

		if (size / (1024 * 1024) > 0) {
			float tmpSize = (float) (size) / (float) (1024 * 1024);
			DecimalFormat df = new DecimalFormat("#.00");
			return "" + df.format(tmpSize) + "MB";
		} else if (size / 1024 > 0) {
			return "" + (size / (1024)) + "KB";
		} else
			return "" + size + "B";
	}

	public static String speed(long speed) {
		return size(speed) + "/s";
	}

	public static void installAPK(Context context, final String filePath) {

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(new File(filePath)),
				"application/vnd.android.package-archive");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setClassName("com.android.packageinstaller",
				"com.android.packageinstaller.PackageInstallerActivity");
		context.startActivity(intent);
	}

	public static boolean delete(File path) {

		boolean result = true;
		if (path.exists()) {
			if (path.isDirectory()) {
				for (File child : path.listFiles()) {
					result &= delete(child);
				}
				result &= path.delete(); // Delete empty directory.
			}
			if (path.isFile()) {
				result &= path.delete();
			}
			if (!result) {
				Log.e(null, "Delete failed;");
			}
			return result;
		} else {
			Log.e(null, "File does not exist.");
			return false;
		}
	}

	public static boolean isPathInSDCard(String dirPath) {
		return !TextUtils.isEmpty(dirPath)
				&& dirPath.contains(DownloadManagerDefaultConfig.getSDCardRoot());
	}
}
