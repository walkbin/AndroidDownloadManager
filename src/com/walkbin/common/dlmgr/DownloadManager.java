package com.walkbin.common.dlmgr;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.walkbin.common.dlmgr.data.DownloadDBHelper;
import com.walkbin.common.dlmgr.data.DownloadTaskData;
import com.walkbin.common.dlmgr.data.DownloadTaskData.DownloadStatus;
import com.walkbin.common.dlmgr.error.SDCardCannotWriteException;
import com.walkbin.common.dlmgr.error.SDCardNotFoundException;
import com.walkbin.common.dlmgr.error.TaskAlreadyExistException;

public class DownloadManager {

	private Context mContext;

	private List<DownloadTask> mDownloadingTasks;
	private List<DownloadTask> mPausingTasks;
	private List<DownloadTaskData> mDownloadedTasks;
	private DownloadDBHelper mDBHelper;
	private List<DownloadManagerListener> mListeners;
	private ExecutorService mTaskPool;
	private TaskBufferQueue mTaskBufferQueue;

	static final String TAG = "DownloadManager";

	public static enum NotifyAction {
		NA_ADD, NA_BEGIN, NA_UPDATE, NA_SUSPEND, NA_DONE, NA_DELETE, NA_ERROR
	}

	/** 专用于更新db数据的工作线程 */
	private static final HandlerThread sDBThread = new HandlerThread("db work");
	private static final HandlerThread sLogicThread = new HandlerThread(
			"work logic");
	static {
		sDBThread.start();
		sLogicThread.start();
	}
	private static final Handler sDBHdlr = new Handler(sDBThread.getLooper());

	private static final Handler sLogicHdlr = new Handler(
			sLogicThread.getLooper());

	private final Handler mNotifyHdlr;// 主线程的通知

	private Boolean isRunning = false;

	public DownloadManager(Context context) {

		mContext = context;
		mDownloadingTasks = new ArrayList<DownloadTask>();
		mPausingTasks = new ArrayList<DownloadTask>();
		mDownloadedTasks = new ArrayList<DownloadTaskData>();
		mDBHelper = new DownloadDBHelper(mContext);
		mNotifyHdlr = new Handler(mContext.getMainLooper());
		mListeners = new ArrayList<DownloadManager.DownloadManagerListener>();
		mTaskPool = Executors
				.newFixedThreadPool(DownloadManagerConfig.MAX_CONCURRENT_COUNT);
		mTaskBufferQueue = new TaskBufferQueue();
	}

	public void startManage() {
		isRunning = true;
		DownloadManagerHelper.mkdir();
		checkUncompleteTasks();
	}

	public void close() {
		isRunning = false;
		// pauseAllTask();
		sLogicHdlr.removeCallbacksAndMessages(null);
	}

	public boolean isRunning() {
		return isRunning;
	}

	public DownloadTask addTaskSafely(DownloadTaskData data) {
		try {
			return addTask(data);
		} catch (Exception e) {
			if (DownloadManagerConfig.DEBUG) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public DownloadTask addTask(DownloadTaskData data)
			throws SDCardNotFoundException, SDCardCannotWriteException,
			MalformedURLException, TaskAlreadyExistException {

		if (hasTask(data.url)) {
			throw new TaskAlreadyExistException("task already exist");
		}

		if (!DownloadManagerHelper.isSDCardPresent()) {
			throw new SDCardNotFoundException("sd card not found");
		}

		if (!DownloadManagerHelper.isSdCardWrittenable()) {
			throw new SDCardCannotWriteException("sd card cannot be written");
		}

		DownloadTask task = newDownloadTask(data);
		if (task.getFile() == null) {
			throw new MalformedURLException();
		}

		addTask(task);
		return task;
	}

	private synchronized void addTask(DownloadTask task) {
		task.getData().status = DownloadStatus.WAITING;
		mTaskBufferQueue.offer(task);
		notifyTask(NotifyAction.NA_ADD, task);
		syncDBUpdate(task.getData());

		tryPollTask();
	}

	public boolean hasTask(String url) {
		return getTask(url) != null;
	}

	public DownloadTask getTask(String url) {

		DownloadTask task = null;

		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();
		while (itTask.hasNext()) {
			task = (DownloadTask) itTask.next();
			if (task.getUrl().equals(url))
				return task;
		}

		itTask = mTaskBufferQueue.iterator();
		while (itTask.hasNext()) {
			task = (DownloadTask) itTask.next();
			if (task.getUrl().equals(url)) {
				return task;
			}
		}

		itTask = mPausingTasks.iterator();
		while (itTask.hasNext()) {
			task = (DownloadTask) itTask.next();
			if (task.getUrl().equals(url))
				return task;
		}

		return null;
	}

	public int getQueueTaskCount() {
		return mTaskBufferQueue.size();
	}

	public int getDownloadingTaskCount() {
		return mDownloadingTasks.size();
	}

	public int getPausingTaskCount() {
		return mPausingTasks.size();
	}

	public int getTotalTaskCount() {
		return getQueueTaskCount() + getDownloadingTaskCount()
				+ getPausingTaskCount();
	}

	/** 恢复任务 */
	public synchronized void checkUncompleteTasks() {

		ArrayList<DownloadTaskData> taskDatas = mDBHelper.findAllTaskData();

		// 优先插入downloading的task
		for (DownloadTaskData data : taskDatas) {
			if (data.status == DownloadStatus.DOWNLOADING) {
				addTaskSafely(data);
			}
		}

		for (DownloadTaskData data : taskDatas) {

			switch (data.status) {

			case PREPARE:
				addTaskSafely(data);
				break;

			case PAUSED:
				DownloadTask task = newDownloadTask(data);
				mPausingTasks.add(task);

				break;

			case DONE:
				// 判断文件是否存在，不存在则忽略
				File f = DownloadManagerHelper.getFile(data.url);
				if (f != null && f.exists()) {
					mDownloadedTasks.add(data);
				}
				break;
			case DOWNLOADING:
				break;
			case FAILED:
				break;
			case SUSPEND:
				break;
			case WAITING:
				break;
			default:
				break;
			}

		}
	}

	public synchronized void addListener(DownloadManagerListener l) {
		mListeners.add(l);
	}

	public synchronized void deleteListener(DownloadManagerListener l) {
		mListeners.remove(l);
	}

	public synchronized void deleteTask(String url) {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				task.cancel();
				notifyTask(NotifyAction.NA_DELETE, task);
				itTask.remove();
			}
		}

		itTask = mTaskBufferQueue.iterator();
		while (itTask.hasNext()) {
			task = (DownloadTask) itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				notifyTask(NotifyAction.NA_DELETE, task);
				itTask.remove();
			}
		}

		itTask = mPausingTasks.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				notifyTask(NotifyAction.NA_DELETE, task);
				itTask.remove();
			}
		}

		File file = DownloadManagerHelper.getFile(url);
		if (file.exists())
			file.delete();

		file = DownloadManagerHelper.getTempFile(url);
		if (file.exists())
			file.delete();

		syncDBDelete(url);

		tryPollTask();
	}

	/** 清除当前所有的下载任务 */
	public synchronized void deleteAllTask() {
		DownloadTask task;

		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			task.cancel();
			notifyTask(NotifyAction.NA_DELETE, task);

			String url = task.getUrl();
			File file = DownloadManagerHelper.getFile(url);
			if (file.exists())
				file.delete();

			file = DownloadManagerHelper.getTempFile(url);
			if (file.exists())
				file.delete();

			syncDBDelete(url);
			itTask.remove();
		}

		itTask = mPausingTasks.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			notifyTask(NotifyAction.NA_DELETE, task);

			String url = task.getUrl();
			File file = DownloadManagerHelper.getFile(url);
			if (file.exists())
				file.delete();

			file = DownloadManagerHelper.getTempFile(url);
			if (file.exists())
				file.delete();

			syncDBDelete(task.getUrl());
			itTask.remove();
		}

		itTask = mTaskBufferQueue.iterator();
		while (itTask.hasNext()) {
			task = (DownloadTask) itTask.next();
			notifyTask(NotifyAction.NA_DELETE, task);
			String url = task.getUrl();

			File file = DownloadManagerHelper.getFile(url);
			if (file.exists())
				file.delete();

			file = DownloadManagerHelper.getTempFile(url);
			if (file.exists())
				file.delete();

			syncDBDelete(url);
			itTask.remove();
		}
	}

	public synchronized void pauseTask(String url) {

		DownloadTask task = getTask(url);

		if (task == null)
			return;

		DownloadTaskData data = task.getData();
		boolean needDeal = false;

		switch (task.getStatus()) {
		case DOWNLOADING:
		case PREPARE:
			task.cancel();
			mDownloadingTasks.remove(task);
			data.status = DownloadStatus.PAUSED;
			task = newDownloadTask(data.copy());
			needDeal = true;
			break;
		case WAITING:
			mTaskBufferQueue.remove(task);
			data.status = DownloadStatus.PAUSED;
			needDeal = true;
			break;
		default:
			break;

		}

		if (needDeal) {
			mPausingTasks.add(task);
			syncDBUpdate(data);
		}

		tryPollTask();
	}

	public synchronized void pauseAllTask() {

		sLogicHdlr.post(new Runnable() {

			@Override
			public void run() {

				DownloadTask task;

				Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();
				while (itTask.hasNext()) {
					task = itTask.next();
					task.cancel();
					DownloadTaskData data = task.getData();
					data.status = DownloadStatus.PAUSED;
					task = newDownloadTask(data.copy());
					mPausingTasks.add(task);
					syncDBUpdate(data);
				}
				mDownloadingTasks.clear();

				itTask = mTaskBufferQueue.iterator();
				while (itTask.hasNext()) {
					task = itTask.next();
					task.getData().status = DownloadStatus.PAUSED;
					mPausingTasks.add(task);
					syncDBUpdate(task.getData());
				}
				mTaskBufferQueue.clear();
			}
		});
	}

	public synchronized void continueTask(String url) {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mPausingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			if (task.getUrl().equals(url)) {
				addTask(task);
				itTask.remove();
				break;
			}
		}
	}

	public synchronized void continueAllTask() {

		sLogicHdlr.post(new Runnable() {

			@Override
			public void run() {
				DownloadTask task;
				Iterator<DownloadTask> itTask = mPausingTasks.iterator();

				while (itTask.hasNext()) {
					task = itTask.next();
					addTask(task);
				}

				mPausingTasks.clear();
			}
		});

	}

	private void tryPollTask() {
		mTaskBufferQueue.poll();
	}

	private void completeTask(DownloadTask task) {

		if (mDownloadingTasks.contains(task)) {
			// 清除下载任务
			mDownloadingTasks.remove(task);
			task.getData().status = DownloadStatus.DONE;
			DownloadTaskData data = task.getData().copy();
			mDownloadedTasks.add(data);
			notifyTask(NotifyAction.NA_DONE, task);
			syncDBUpdate(data);
		}
	}

	private void syncDBUpdate(final DownloadTaskData data) {
		sDBHdlr.post(new Runnable() {

			@Override
			public void run() {
				mDBHelper.updateTaskData(data);
			}
		});
	}

	private void syncDBDelete(final String url) {
		sDBHdlr.post(new Runnable() {

			@Override
			public void run() {
				mDBHelper.deletedTaskData(url);
			}
		});
	}

	/**
	 * Create a new download task with default config
	 * 
	 * @param url
	 * @return
	 * @throws MalformedURLException
	 */
	private DownloadTask newDownloadTask(DownloadTaskData data) {

		DownloadTask.DownloadTaskListener taskListener = new DownloadTask.DownloadTaskListener() {

			@Override
			public void onUpdate(DownloadTask task) {
				syncDBUpdate(task.getData());
				notifyTask(NotifyAction.NA_UPDATE, task);
			}

			@Override
			public void onPre(DownloadTask task) {
				syncDBUpdate(task.getData());
				notifyTask(NotifyAction.NA_BEGIN, task);
			}

			@Override
			public void onFinished(DownloadTask task) {
				completeTask(task);
			}

			@Override
			public void onError(DownloadTask task, Throwable error) {

				if (error != null) {
					Toast.makeText(mContext, "Error: " + error.getMessage(),
							Toast.LENGTH_LONG).show();
				}

				// Intent errorIntent = new
				// Intent("com.yyxu.download.activities.DownloadListActivity");
				// errorIntent.putExtra(MyIntents.TYPE, MyIntents.Types.ERROR);
				// errorIntent.putExtra(MyIntents.ERROR_CODE, error);
				// errorIntent.putExtra(MyIntents.ERROR_INFO,
				// DownloadTask.getErrorInfo(error));
				// errorIntent.putExtra(MyIntents.URL, task.getUrl());
				// mContext.sendBroadcast(errorIntent);
				//
				// if (error != DownloadTask.ERROR_UNKOWN_HOST
				// && error != DownloadTask.ERROR_BLOCK_INTERNET
				// && error != DownloadTask.ERROR_TIME_OUT) {
				// completeTask(task);
				// }
			}
		};
		return new DownloadTask(mContext, data,
				DownloadManagerConfig.FILE_ROOT, taskListener);
	}

	/**
	 * A obstructed task queue
	 */
	// private class TaskQueue extends LinkedBlockingQueue<DownloadTask> {
	//
	// private static final long serialVersionUID = 4491199220015175982L;
	//
	// public DownloadTask poll() {
	//
	// DownloadTask task = null;
	//
	// // Log.e(TAG, "---poll---cursize=" + mDownloadingTasks.size() +
	// // "----pollsize=" + size());
	//
	// do {
	//
	// if (mDownloadingTasks.size() >=
	// DownloadManagerConfig.MAX_CONCURRENT_COUNT)
	// break;
	//
	// task = super.poll();
	//
	// } while (false);
	//
	// return task;
	// }
	// }

	private void notifyTask(NotifyAction action, final DownloadTask task,
			final Object param) {
		mNotifyHdlr.post(new NotifyRunnable(action, task, param));
	}

	private void notifyTask(NotifyAction action, final DownloadTask task) {
		notifyTask(action, task, null);
	}

	class NotifyRunnable implements Runnable {

		private NotifyAction mAction;
		private DownloadTask mTask;
		private Object mParam;

		NotifyRunnable(NotifyAction action, final DownloadTask task,
				final Object param) {
			mAction = action;
			mTask = task;
			mParam = param;
		}

		@Override
		public void run() {
			for (DownloadManagerListener l : mListeners) {

				switch (mAction) {
				case NA_ADD:
					Log.w(TAG, "---NA_ADD--" + mTask.getTitle());
					l.onTaskAdded(mTask);
					break;
				case NA_BEGIN:
					Log.w(TAG, "---NA_BEGIN--" + mTask.getTitle());
					l.onTaskBegin(mTask);
					break;
				case NA_DONE:
					Log.w(TAG, "---NA_DONE--" + mTask.getTitle());
					l.onTaskDone(mTask);
					break;
				case NA_DELETE:
					Log.w(TAG, "---NA_DELETE--" + mTask.getTitle());
					l.onTaskDeleted(mTask);
					break;
				case NA_UPDATE:
					Log.w(TAG, "---NA_UPDATE--" + mTask.getTitle());
					l.onTaskUpdate(mTask);
					break;
				case NA_ERROR:
					l.onTaskError(mTask, (Throwable) mParam);
					break;
				default:
					break;
				}
			}
		}

	}

	/**
	 * A obstructed task queue
	 */
	private class TaskBufferQueue extends LinkedList<DownloadTask> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 7876293110595425660L;

		public DownloadTask poll() {

			DownloadTask task = null;

			do {
				if (mDownloadingTasks.size() >= DownloadManagerConfig.MAX_CONCURRENT_COUNT)
					break;

				task = super.poll();
				if (task != null) {
					mTaskPool.submit(task);
					mDownloadingTasks.add(task);
				}

			} while (false);

			return task;
		}

		public DownloadTask get(int position) {

			if (position >= size()) {
				return null;
			}
			return get(position);
		}
	}

	public interface DownloadManagerListener {
		public void onTaskAdded(DownloadTask task);

		public void onTaskBegin(DownloadTask task);

		public void onTaskUpdate(DownloadTask task);

		public void onTaskDone(DownloadTask task);

		public void onTaskDeleted(DownloadTask task);

		public void onTaskError(DownloadTask task, Throwable error);
	}

}
