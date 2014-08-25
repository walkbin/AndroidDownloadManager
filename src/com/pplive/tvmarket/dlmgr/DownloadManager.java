package com.pplive.tvmarket.dlmgr;

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

import com.pplive.tvmarket.dlmgr.data.DownloadOpenHelper;
import com.pplive.tvmarket.dlmgr.data.DownloadTaskData;
import com.pplive.tvmarket.dlmgr.error.SDCardCannotWriteException;
import com.pplive.tvmarket.dlmgr.error.SDCardNotFoundException;
import com.pplive.tvmarket.dlmgr.error.TaskAlreadyExistException;
import com.pplive.tvmarket.dlmgr.error.TaskFullException;

public class DownloadManager extends Thread {

	private Context mContext;

	private TaskQueue mTaskQueue;
	private List<DownloadTask> mDownloadingTasks;
	private List<DownloadTask> mPausingTasks;
	private List<DownloadTaskData> mDownloadedTasks;
	private DownloadOpenHelper mDBHelper;
	private List<DownloadManagerListener> mListeners;
	
	static final String TAG = "DownloadManager";

	enum NotifyAction {
		NA_ADD, NA_BEGIN, NA_UPDATE, NA_DONE, NA_DELETE,NA_ERROR
	}

	/** 专用于更新db数据的工作线程 */
	private static final HandlerThread sWorkerThread = new HandlerThread(
			"db work");
	static {
		sWorkerThread.start();
	}
	private static final Handler sDBHdlr = new Handler(
			sWorkerThread.getLooper());

	private final Handler mNotifyHdlr;// 主线程的通知

	private Boolean isRunning = false;

	public DownloadManager(Context context) {
		
		mContext = context;
		mTaskQueue = new TaskQueue();
		mDownloadingTasks = new ArrayList<DownloadTask>();
		mPausingTasks = new ArrayList<DownloadTask>();
		mDownloadedTasks = new ArrayList<DownloadTaskData>();
		mDBHelper = new DownloadOpenHelper(mContext);
		mNotifyHdlr = new Handler(mContext.getMainLooper());
		mListeners = new ArrayList<DownloadManager.DownloadManagerListener>();
	}

	public void startManage() {
		isRunning = true;
		DownloadManagerHelper.mkdir();
		start();
		checkUncompleteTasks();
	}

	public void close() {
		isRunning = false;
		// pauseAllTask();
	}

	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public void run() {

		super.run();
		while (isRunning) {
			DownloadTask task = mTaskQueue.poll();
			mDownloadingTasks.add(task);
			task.execute();
		}
	}

	public void addTask(DownloadTaskData data)
			throws SDCardNotFoundException, SDCardCannotWriteException,
			TaskFullException, MalformedURLException, TaskAlreadyExistException {

		if(hasTask(data.url)){
			throw new TaskAlreadyExistException("task already exist");
		}
		
		if (!DownloadManagerHelper.isSDCardPresent()) {
			throw new SDCardNotFoundException("sd card not found");
		}

		if (!DownloadManagerHelper.isSdCardWrittenable()) {
			throw new SDCardCannotWriteException("sd card cannot be written");
		}

		if (getTotalTaskCount() >= DownloadManagerConfig.MAX_TASK_COUNT) {
			throw new TaskFullException("download task is full");
		}

		DownloadTask task = newDownloadTask(data);
		if (task.getFile() == null) {
			throw new MalformedURLException();
		}
		
		addTask(task);
	}

	private void addTaskSafely(DownloadTaskData data) {
		try {
			addTask(data);
		} catch (Exception e) {
			if (DownloadManagerConfig.DEBUG) {
				e.printStackTrace();
			}
		}
	}

	private void addTask(DownloadTask task) {

		task.getData().status = DownloadTaskData.PREPARE;
		notifyTask(NotifyAction.NA_ADD, task);
		mTaskQueue.offer(task);
		syncDBUpdate(task.getData());

		if (!this.isAlive()) {
			this.startManage();
		}
	}

	// public void reBroadcastAddAllTask() {
	//
	// DownloadTask task;
	// for (int i = 0; i < mDownloadingTasks.size(); i++) {
	// task = mDownloadingTasks.get(i);
	// broadcastAddTask(task.getUrl(), task.isInterrupt());
	// }
	// for (int i = 0; i < mTaskQueue.size(); i++) {
	// task = mTaskQueue.get(i);
	// broadcastAddTask(task.getUrl());
	// }
	// for (int i = 0; i < mPausingTasks.size(); i++) {
	// task = mPausingTasks.get(i);
	// broadcastAddTask(task.getUrl());
	// }
	// }

	public boolean hasTask(String url) {

		DownloadTask task;
		for (int i = 0; i < mDownloadingTasks.size(); i++) {
			task = mDownloadingTasks.get(i);
			if (task.getUrl().equals(url)) {
				return true;
			}
		}

		for (int i = 0; i < mTaskQueue.size(); i++) {
			task = mTaskQueue.get(i);
			if (task.getUrl().equals(url)) {
				return true;
			}
		}

		for (int i = 0; i < mPausingTasks.size(); i++) {
			task = mPausingTasks.get(i);
			if (task.getUrl().equals(url)) {
				return true;
			}
		}

		return false;
	}

	public DownloadTask getTask(int position) {

		int downloadSize = getDownloadingTaskCount();

		if (position >= downloadSize) {
			return mTaskQueue.get(position - downloadSize);
		} else {
			return mDownloadingTasks.get(position);
		}
	}

	public DownloadTask getTask(String url) {
		for (DownloadTask task : mDownloadingTasks) {
			if (task.getUrl().equals(url))
				return task;
		}

		for (DownloadTask task : mPausingTasks) {
			if (task.getUrl().equals(url))
				return task;
		}

		for (int i = 0; i < mTaskQueue.size(); i++) {
			if (mTaskQueue.get(i).getUrl().equals(url))
				return mTaskQueue.get(i);
		}

		return null;
	}

	public int getQueueTaskCount() {
		return mTaskQueue.size();
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
	public void checkUncompleteTasks() {

		ArrayList<DownloadTaskData> taskDatas = mDBHelper.findAllTaskData();

		// 优先插入downloading的task
		for (DownloadTaskData data : taskDatas) {
			if (data.status == DownloadTaskData.DOWNLOADING) {
				addTaskSafely(data);
			}
		}

		for (DownloadTaskData data : taskDatas) {

			switch (data.status) {

			case DownloadTaskData.PREPARE:
				addTaskSafely(data);
				break;

			case DownloadTaskData.PAUSED:
				DownloadTask task = newDownloadTask(data);
				mPausingTasks.add(task);

				break;

			case DownloadTaskData.DOWNLOADED:
				// 判断文件是否存在，不存在则忽略
				File f = DownloadManagerHelper.getFile(data.url);
				if (f != null && f.exists()) {
					mDownloadedTasks.add(data);
				}
				break;
			}

		}
	}

	public void addListener(DownloadManagerListener l) {
		mListeners.add(l);
	}

	public void deleteListener(DownloadManagerListener l) {
		mListeners.remove(l);
	}

	public synchronized void deleteTask(String url) {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				task.onCancelled();
				mDownloadingTasks.remove(task);
				notifyTask(NotifyAction.NA_DELETE, task);
				itTask.remove();
			}
		}

		itTask = mTaskQueue.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				mTaskQueue.remove(task);
				notifyTask(NotifyAction.NA_DELETE, task);
				itTask.remove();
			}
		}

		itTask = mPausingTasks.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			if (task != null && task.getUrl().equals(url)) {
				mPausingTasks.remove(task);
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
	}

	/** 清除当前所有的下载任务 */
	public synchronized void deleteAllTask() {
		DownloadTask task;

		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			task.onCancelled();
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

		itTask = mTaskQueue.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			notifyTask(NotifyAction.NA_DELETE, task);
			syncDBDelete(task.getUrl());
			itTask.remove();
		}

		itTask = mTaskQueue.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			notifyTask(NotifyAction.NA_DELETE, task);
			String url = task.getUrl();

			File file = DownloadManagerHelper.getTempFile(url);
			if (file.exists())
				file.delete();

			syncDBDelete(url);
			itTask.remove();
		}
	}

	public synchronized void pauseTask(String url) {

		Iterator<DownloadTask> itTask = mDownloadingTasks.iterator();
		DownloadTask task;
		while (itTask.hasNext()) {
			task = itTask.next();
			if (task.getUrl().equals(url)) {
				task.onCancelled();

				DownloadTaskData data = task.getData();
				data.status = DownloadTaskData.PAUSED;
				task = newDownloadTask(data.copy());
				mPausingTasks.add(task);
				syncDBUpdate(data);
				itTask.remove();
			}
		}
	}

	public synchronized void pauseAllTask() {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mTaskQueue.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			mPausingTasks.add(task);
			itTask.remove();
		}

		itTask = mDownloadingTasks.iterator();
		while (itTask.hasNext()) {
			task = itTask.next();
			task.onCancelled();
			DownloadTaskData data = task.getData();
			data.status = DownloadTaskData.PAUSED;
			task = newDownloadTask(data.copy());
			mPausingTasks.add(task);
			syncDBUpdate(data);
			itTask.remove();
		}
	}

	public synchronized void continueTask(String url) {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mPausingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			if (task.getUrl().equals(url)) {
				task.getData().status = DownloadTaskData.PREPARE;
				mTaskQueue.offer(task);
				syncDBUpdate(task.getData());
				itTask.remove();
			}
		}
	}

	public synchronized void continueAllTask() {

		DownloadTask task;
		Iterator<DownloadTask> itTask = mPausingTasks.iterator();

		while (itTask.hasNext()) {
			task = itTask.next();
			task.getData().status = DownloadTaskData.PREPARE;
			mTaskQueue.offer(task);
			syncDBUpdate(task.getData());
			itTask.remove();
		}
	}

	public synchronized void completeTask(DownloadTask task) {

		if (mDownloadingTasks.contains(task)) {
			// 清除下载任务
			mDownloadingTasks.remove(task);
			task.getData().status = DownloadTaskData.DOWNLOADED;
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
				task.getData().status = DownloadTaskData.DOWNLOADING;
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
	private class TaskQueue extends LinkedList<DownloadTask> {

		private static final long serialVersionUID = 4491199220015175982L;

		public DownloadTask poll() {

			DownloadTask task = null;
			
			Log.e(TAG, "---poll---cursize=" + mDownloadingTasks.size() + "----pollsize=" + size());
			
			
			while (mDownloadingTasks.size() >= DownloadManagerConfig.MAX_CONCURRENT_COUNT
					|| (task = super.poll()) == null) {
				try {
					Thread.sleep(1000); // sleep
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			
			return task;
		}

		public DownloadTask get(int position) {

			if (position >= size()) {
				return null;
			}

			return super.get(position);
		}
	}

	private void notifyTask(NotifyAction action, final DownloadTask task,final Object param) {
		mNotifyHdlr.post(new NotifyRunnable(action, task,param));
	}
	
	private void notifyTask(NotifyAction action, final DownloadTask task) {
		notifyTask(action,task,null);
	}

	class NotifyRunnable implements Runnable {

		private NotifyAction mAction;
		private DownloadTask mTask;
		private Object mParam;

		NotifyRunnable(NotifyAction action, final DownloadTask task,final Object param) {
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

	public interface DownloadManagerListener {
		public void onTaskAdded(DownloadTask task);

		public void onTaskBegin(DownloadTask task);

		public void onTaskUpdate(DownloadTask task);

		public void onTaskDone(DownloadTask task);

		public void onTaskDeleted(DownloadTask task);
		
		public void onTaskError(DownloadTask task,Throwable error);
	}

}
