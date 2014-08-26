package com.walkbin.common.dlmgr;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.util.Log;

import com.walkbin.common.dlmgr.data.DownloadTaskData;
import com.walkbin.common.dlmgr.data.DownloadTaskData.DownloadStatus;
import com.walkbin.common.dlmgr.error.FileAlreadyExistException;
import com.walkbin.common.dlmgr.error.NoMemoryException;
import com.walkbin.common.dlmgr.http.AndroidHttpClient;

public class DownloadTask implements Runnable {

	private static final String TAG = "DownloadTask";

	private File mFile;
	private File mTempFile;
	private RandomAccessFile mOutputStream;
	private DownloadTaskListener mListener;
	private Context mContext;

	private DownloadTaskData mData;

	private long mDownloadSize;
	private long mPreviousFileSize;
	private long mDownloadPercent;
	private long mNetworkSpeed;// byte per second
	private long mPreviousTime;
	private long mTotalTime;
	private Throwable mError = null;
	private boolean mCanceled = false;

	private final class ProgressReportingRandomAccessFile extends
			RandomAccessFile {

		private int progress = 0;

		public ProgressReportingRandomAccessFile(File file, String mode)
				throws FileNotFoundException {

			super(file, mode);
		}

		@Override
		public void write(byte[] buffer, int offset, int count)
				throws IOException {

			super.write(buffer, offset, count);
			progress += count;
			publishProgress(progress);
		}
	}

	public DownloadTask(Context context, DownloadTaskData data, String path) {
		this(context, data, path, null);
	}

	public DownloadTask(Context context, DownloadTaskData data, String path,
			DownloadTaskListener listener) {
		super();
		mData = data;
		mListener = listener;
		mFile = DownloadManagerHelper.getFile(data.url);
		mTempFile = DownloadManagerHelper.getTempFile(data.url);
		mContext = context;
	}

	public File getFile() {
		return mFile;
	}

	public String getUrl() {
		return mData.url;
	}

	public DownloadTaskData getData() {
		return mData;
	}

	public DownloadStatus getStatus() {
		return mData.status;
	}

	public String getTitle() {
		return mData.getParam("title");
	}

	public boolean isInterrupt() {
		return mCanceled;
	}

	public long getDownloadPercent() {

		return mDownloadPercent;
	}

	public long getDownloadSize() {

		return mDownloadSize + mPreviousFileSize;
	}

	public long getTotalSize() {
		return mData.totalSize;
	}

	public long getDownloadSpeed() {

		return mNetworkSpeed;
	}

	public long getTotalTime() {

		return mTotalTime;
	}

	public DownloadTaskListener getListener() {

		return mListener;
	}

	// @Override
	// protected void onPreExecute() {
	//
	// if (DownloadManagerConfig.DEBUG) {
	// Log.e(TAG, "--" + getTitle() + "----onPreExecute-----");
	// }
	// mPreviousTime = System.currentTimeMillis();
	// mData.status = DownloadStatus.PREPARE;
	// if (mListener != null)
	// mListener.onPre(this);
	// }

	// @Override
	// protected Long doInBackground(Void... params) {
	//
	// if (DownloadManagerConfig.DEBUG) {
	// Log.e(TAG, "--" + getTitle() + "----doInBackground-----");
	// }
	//
	// long result = -1;
	// try {
	// result = download();
	// } catch (NetworkErrorException e) {
	// mError = e;
	// } catch (FileAlreadyExistException e) {
	// mError = e;
	// } catch (NoMemoryException e) {
	// mError = e;
	// } catch (IOException e) {
	// mError = e;
	// } finally {
	// if (mClient != null) {
	// mClient.close();
	// }
	// }
	//
	// return result;
	// }

	public void publishProgress(Integer... progress) {
		onProgressUpdate(progress);
	}

	public void cancel() {
		mCanceled = true;
	}

	// @Override
	protected void onProgressUpdate(Integer... progress) {

		if(isInterrupt())
			return;
		
		if (progress.length > 1) {
			mData.totalSize = progress[1];
			if (mData.totalSize == -1) {
				if (mListener != null)
					mListener.onError(this, mError);
			} else {

			}
		} else {
			mTotalTime = System.currentTimeMillis() - mPreviousTime;
			mDownloadSize = progress[0];
			mDownloadPercent = (mDownloadSize + mPreviousFileSize) * 100
					/ mData.totalSize;
			mNetworkSpeed = (mDownloadSize * 1000 / mTotalTime);
			if (mListener != null)
				mListener.onUpdate(this);
		}
	}

	private AndroidHttpClient mClient;
	private HttpGet mHttpGet;
	private HttpResponse mResponse;

	private long download() throws NetworkErrorException, IOException,
			FileAlreadyExistException, NoMemoryException {

		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----begin download-----totalSize: "
					+ mData.totalSize);
		}

		if (!DownloadManagerHelper.isNetworkAvailable(mContext)) {
			throw new NetworkErrorException("Network blocked.");
		}

		mClient = AndroidHttpClient.newInstance(TAG);
		mHttpGet = new HttpGet(mData.url);
		mResponse = mClient.execute(mHttpGet);
		mData.totalSize = mResponse.getEntity().getContentLength();

		if (mFile.exists() && mData.totalSize == mFile.length()) {
			if (DownloadManagerConfig.DEBUG) {
				Log.e(TAG, "--" + getTitle()
						+ "---Output file already exists. Skipping download.");
			}

			throw new FileAlreadyExistException(
					"Output file already exists. Skipping download.");
		} else if (mTempFile.exists()) {
			mHttpGet.addHeader("Range", "bytes=" + mTempFile.length() + "-");
			mPreviousFileSize = mTempFile.length();

			mClient.close();
			mClient = AndroidHttpClient.newInstance(TAG);
			mResponse = mClient.execute(mHttpGet);

			if (DownloadManagerConfig.DEBUG) {
				Log.e(TAG, "--" + getTitle()
						+ "---File is not complete, download now.");
				Log.e(TAG,
						"--" + getTitle() + "---File length:"
								+ mTempFile.length() + " totalSize:"
								+ mData.totalSize);
			}
		}

		long storage = DownloadManagerHelper.getAvailableStorage();
		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "---storage:" + storage
					+ " totalSize:" + mData.totalSize);
		}

		if (mData.totalSize - mTempFile.length() > storage) {
			throw new NoMemoryException("SD card no memory.");
		}

		mOutputStream = new ProgressReportingRandomAccessFile(mTempFile, "rw");

		publishProgress(0, (int) mData.totalSize);

		InputStream input = mResponse.getEntity().getContent();
		int bytesCopied = copy(input, mOutputStream);

		if ((mPreviousFileSize + bytesCopied) != mData.totalSize
				&& mData.totalSize != -1 && !mCanceled) {
			throw new IOException("Download incomplete: " + bytesCopied
					+ " != " + mData.totalSize);
		}

		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "---stop download---");
		}

		return bytesCopied;

	}

	public int copy(InputStream input, RandomAccessFile out)
			throws IOException, NetworkErrorException {

		if (input == null || out == null) {
			return -1;
		}

		byte[] buffer = new byte[DownloadManagerConfig.DOWNOAD_BUFFER_SIZE];

		BufferedInputStream in = new BufferedInputStream(input, buffer.length);
		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG,
					"--" + getTitle() + "---begin copy----length"
							+ out.length());
		}

		int count = 0, n = 0;
		long errorBlockTimePreviousTime = -1, expireTime = 0;

		try {

			out.seek(out.length());

			while (!mCanceled) {
				n = in.read(buffer, 0, buffer.length);
				if (n == -1) {
					break;
				}
				out.write(buffer, 0, n);
				count += n;

				if (!DownloadManagerHelper.isNetworkAvailable(mContext)) {
					throw new NetworkErrorException("Network blocked.");
				}

				if (mNetworkSpeed == 0) {
					if (errorBlockTimePreviousTime > 0) {
						expireTime = System.currentTimeMillis()
								- errorBlockTimePreviousTime;
						if (expireTime > DownloadManagerConfig.DOWNLOAD_TIME_OUT) {
							throw new ConnectTimeoutException(
									"connection time out.");
						}
					} else {
						errorBlockTimePreviousTime = System.currentTimeMillis();
					}
				} else {
					expireTime = 0;
					errorBlockTimePreviousTime = -1;
				}
			}
		} finally {
			mClient.close(); // must close client first
			mClient = null;
			out.close();
			in.close();
			input.close();
		}
		return count;

	}

	@Override
	public void run() {

		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----begin prepare-----");
		}
		mPreviousTime = System.currentTimeMillis();
		mData.status = DownloadStatus.PREPARE;
		if (mListener != null)
			mListener.onPre(this);

		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----begin download-----");
		}

		long result = -1;
		try {
			result = download();
		} catch (NetworkErrorException e) {
			mError = e;
		} catch (FileAlreadyExistException e) {
			mError = e;
		} catch (NoMemoryException e) {
			mError = e;
		} catch (IOException e) {
			mError = e;
		} finally {
			if (mClient != null) {
				mClient.close();
			}
		}

		if (DownloadManagerConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----download over--get byte="
					+ result);
		}

		this.mContext = null;
		if (result == -1 || mCanceled || mError != null) {
			if (DownloadManagerConfig.DEBUG && mError != null) {
				Log.e(TAG, "Download failed." + mError.getMessage());
			}
			mData.status = DownloadStatus.FAILED;
			if (mListener != null)
				mListener.onError(this, mError);
		} else {
			// finish download
			mData.status = DownloadStatus.DONE;
			mTempFile.renameTo(mFile);
			if (mListener != null)
				mListener.onFinished(this);
		}
	}

	public interface DownloadTaskListener {

		public void onUpdate(DownloadTask task);

		public void onFinished(DownloadTask task);

		// 准备开始下载
		public void onPre(DownloadTask task);

		// 成功获取到总大小
		// public void onBegin(DownloadTask task);

		public void onError(DownloadTask task, Throwable error);
	}

}
