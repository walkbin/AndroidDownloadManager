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
import android.util.Log;

import com.walkbin.common.dlmgr.data.DownloadTaskData;
import com.walkbin.common.dlmgr.data.DownloadStatus;
import com.walkbin.common.dlmgr.error.FileAlreadyExistException;
import com.walkbin.common.dlmgr.error.InvalidContentException;
import com.walkbin.common.dlmgr.error.NoMemoryException;
import com.walkbin.common.dlmgr.error.SDCardCannotWriteException;
import com.walkbin.common.dlmgr.error.SDCardNotFoundException;
import com.walkbin.common.dlmgr.error.TaskAlreadyExistException;
import com.walkbin.common.dlmgr.http.AndroidHttpClient;

/**
 * @see ConnectTimeoutException
 * @see NetworkErrorException
 * @see FileAlreadyExistException
 * @see InvalidContentException
 * @see NoMemoryException
 * @see SDCardCannotWriteException
 * @see SDCardNotFoundException
 * @see TaskAlreadyExistException
 * */
public class DownloadTask implements Runnable {

	private static final String TAG = "DownloadTask";

	private File mFile;
	private File mTempFile;
	private RandomAccessFile mOutputStream;
	private DownloadTaskListener mListener;
	private DownloadManager mMgr;

	private DownloadTaskData mData;

	private long mDownloadSize;
	private long mPreviousFileSize;
	private long mDownloadPercent;
	private long mLastDownloadPercentWhenNotify;
	private long mNetworkSpeed;// byte per second
	private long mPreviousTime;
	private long mTotalTime;
	private Throwable mError = null;
	private boolean mCanceled = false;
	private boolean mPaused = false;

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

	public DownloadTask(DownloadManager mgr, DownloadTaskData data, String path) {
		this(mgr, data, path, null);
	}

	public DownloadTask(DownloadManager mgr, DownloadTaskData data, String path,
			DownloadTaskListener listener) {
		super();
		mData = data;
		mListener = listener;
		mFile = DownloadManagerHelper.getFile(data.url);
		mTempFile = DownloadManagerHelper.getTempFile(data.url);
		mMgr = mgr;
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

	public void syncContinueInfo() {
		if (mTempFile != null && mTempFile.exists()) {
			mPreviousFileSize = mTempFile.length();
			if (mData.totalSize > 0)
				mDownloadPercent = mPreviousFileSize * 100 / mData.totalSize;
		}
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
	
	public void pause(){
		mPaused = true;
	}

	// @Override
	protected void onProgressUpdate(Integer... progress) {

		if (mCanceled || mPaused)
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
			mNetworkSpeed = (mDownloadSize * 1000 / mTotalTime); // take bps as
																	// unit
			if (mListener != null)
				if (mDownloadPercent > mLastDownloadPercentWhenNotify) {
					mLastDownloadPercentWhenNotify = mDownloadPercent;
					mListener.onUpdate(this);
				}
		}
	}

	private AndroidHttpClient mClient;
	private HttpGet mHttpGet;
	private HttpResponse mResponse;

	private long download() throws NetworkErrorException, IOException,
			FileAlreadyExistException, NoMemoryException,
			InvalidContentException {

		if (DownloadManagerDefaultConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----begin download-----totalSize: "
					+ mData.totalSize);
		}

		if (!DownloadManagerHelper.isNetworkAvailable(mMgr.getContext())) {
			throw new NetworkErrorException("Network blocked.");
		}

		mClient = AndroidHttpClient.newInstance(TAG);
		mHttpGet = new HttpGet(mData.url);
		mResponse = mClient.execute(mHttpGet);
		mData.totalSize = mResponse.getEntity().getContentLength();

		if (mData.totalSize <= 0) {
			throw new InvalidContentException("content length is invalid");
		}

		if (mFile.exists() && mData.totalSize == mFile.length()) {
			if (DownloadManagerDefaultConfig.DEBUG) {
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

			if (DownloadManagerDefaultConfig.DEBUG) {
				Log.e(TAG, "--" + getTitle()
						+ "---File is not complete, download now.");
				Log.e(TAG,
						"--" + getTitle() + "---File length:"
								+ mTempFile.length() + " totalSize:"
								+ mData.totalSize);
			}
		}

		if (mMgr.hasEnoughLeftStorage(mData.totalSize - mTempFile.length())){
			throw new NoMemoryException("no enough storage.");
		}

		mOutputStream = new ProgressReportingRandomAccessFile(mTempFile, "rw");
		mData.status = DownloadStatus.DOWNLOADING;
		publishProgress(0, (int) mData.totalSize);

		InputStream input = mResponse.getEntity().getContent();
		int bytesCopied = copy(input, mOutputStream);

		if ((mPreviousFileSize + bytesCopied) != mData.totalSize
				&& mData.totalSize != -1 && !mCanceled && !mPaused) {
			throw new IOException("Download incomplete: " + bytesCopied
					+ " != " + mData.totalSize);
		}

		if (DownloadManagerDefaultConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "---stop download---");
		}

		return bytesCopied;

	}

	public int copy(InputStream input, RandomAccessFile out)
			throws IOException, NetworkErrorException {

		if (input == null || out == null) {
			return -1;
		}

		byte[] buffer = new byte[DownloadManagerDefaultConfig.DOWNOAD_BUFFER_SIZE];

		BufferedInputStream in = new BufferedInputStream(input, buffer.length);
		if (DownloadManagerDefaultConfig.DEBUG) {
			Log.e(TAG,
					"--" + getTitle() + "---begin copy----length"
							+ out.length());
		}

		int count = 0, n = 0;
		long errorBlockTimePreviousTime = -1, expireTime = 0;

		try {

			out.seek(out.length());

			// we can fill the buffer totally then write into file
			while (!mCanceled && !mPaused) {
				n = 0;
				while (n < buffer.length) {
					int len = in.read(buffer, n, buffer.length - n);
					if (len == -1)
						break;
					n += len;
				}

				if (n == 0)
					break;

				out.write(buffer, 0, n);
				count += n;

				if (!DownloadManagerHelper.isNetworkAvailable(mMgr.getContext())) {
					throw new NetworkErrorException("Network blocked.");
				}

				if (mNetworkSpeed == 0) {
					if (errorBlockTimePreviousTime > 0) {
						expireTime = System.currentTimeMillis()
								- errorBlockTimePreviousTime;
						if (expireTime > DownloadManagerDefaultConfig.DOWNLOAD_TIME_OUT) {
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

			if (n > 0) {
				out.write(buffer, 0, n);
				count += n;
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

		if (DownloadManagerDefaultConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----begin prepare-----");
		}
		mPreviousTime = System.currentTimeMillis();
		mData.status = DownloadStatus.PREPARE;
		if (mListener != null)
			mListener.onPre(this);

		if (DownloadManagerDefaultConfig.DEBUG) {
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
		} catch (InvalidContentException e) {
			mError = e;
		} finally {
			if (mClient != null) {
				mClient.close();
			}
		}

		if (DownloadManagerDefaultConfig.DEBUG) {
			Log.e(TAG, "--" + getTitle() + "----download over--get byte="
					+ result);
		}

		mMgr = null;
		if (result == -1 || mError != null) {
			if (DownloadManagerDefaultConfig.DEBUG && mError != null) {
				Log.e(TAG, "Download failed." + mError.getMessage());
			}
			mData.status = DownloadStatus.FAILED;
			if (mListener != null)
				mListener.onError(this, mError);
		} else if(mCanceled){
			if (mListener != null)
				mListener.onCanceled(this);
		} else if(mPaused){
			if (mListener != null)
				mListener.onPaused(this);
		}else {
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
		
		public void onCanceled(DownloadTask task);
		
		public void onPaused(DownloadTask task);
	}

}
