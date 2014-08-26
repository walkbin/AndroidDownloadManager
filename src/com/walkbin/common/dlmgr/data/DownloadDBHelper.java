package com.walkbin.common.dlmgr.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.walkbin.common.dlmgr.DownloadManagerConfig;
import com.walkbin.common.dlmgr.data.DownloadTaskData.DownloadStatus;

public class DownloadDBHelper extends SQLiteOpenHelper {

	private static final String TABLE_DOWNLOAD_TASK = "download_task";
	private static final int DATABASE_VERSION = 1;

	public DownloadDBHelper(Context context) {
		super(context, DownloadManagerConfig.DB_FILE, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + TABLE_DOWNLOAD_TASK + "("
				+ "url varchar(256) primary key," + "param varchar, "
				+ "status int, "+ "totalSize int, " + "createTime bigint" + ")");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	public int getMaxCount() {
		return DownloadManagerConfig.MAX_RECORD_COUNT;
	}

	protected String getTableName() {
		return TABLE_DOWNLOAD_TASK;
	}

	protected String getprimaryKey() {
		return "url";
	}

	protected String getOrderColumnName() {
		return "createTime";
	}

	protected void insertRecord(SQLiteDatabase db, DownloadTaskData record) {
		String sql = "insert into " + getTableName()
				+ "(url,param,status,totalSize,createTime) values(?,?,?,?,?)";
		Object[] os = new Object[] { record.url, record.params, record.status,record.totalSize,
				System.currentTimeMillis() };
		db.execSQL(sql, os);
	}

	/**
	 * 删除一条最老记录
	 * 
	 * @param db
	 */
	private void deleteOldRecord(SQLiteDatabase db) {
		String sql = null;
		if (!TextUtils.isEmpty(getOrderColumnName())) {
			sql = String
					.format("delete from %s where %s in (select %s from %s order by %s asc limit 1)",
							getTableName(), getprimaryKey(), getprimaryKey(),
							getTableName(), getOrderColumnName());
		} else {
			sql = String.format(
					"delete from %s where %s in (select %s from %s limit 1)",
					getTableName(), getprimaryKey(), getprimaryKey(),
					getTableName());
		}
		db.execSQL(sql);
	}

	private void insertRecord(DownloadTaskData record) {
		SQLiteDatabase db = null;
		try {
			db = getWritableDatabase();
			if (getMaxCount() > 0) {
				long curCount = DatabaseUtils.queryNumEntries(db,
						getTableName());
				if (curCount >= getMaxCount()) {
					deleteOldRecord(db);
				}
			}
			insertRecord(db, record);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	public void deletedTaskData(String url) {
		SQLiteDatabase db = null;
		try {
			db = getWritableDatabase();
			String sql = String.format("delete from %s where url =?",
					getTableName());
			db.execSQL(sql, new String[] { url });
		} catch (Exception e) {
			Log.e("DownloadLocalFactory",
					"deleteDownloadInfo    Exception e :        " + e);
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	private DownloadTaskData findRecord(String url) {
		SQLiteDatabase db = null;
		Cursor cursor = null;
		try {
			db = getReadableDatabase();
			String sql = String.format("select * from %s where url=?", getTableName());
			cursor = db.rawQuery(sql, new String[] {url });
			DownloadTaskData result = null;
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					result = createTaskData(cursor);
				}
			}
			return result;
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (db != null) {
				db.close();
			}
		}
		return null;
	}
	
	public ArrayList<DownloadTaskData> findAllTaskData() {
		SQLiteDatabase db = null;
		Cursor cursor = null;
		try {
			db = getReadableDatabase();
			String sql = null;
			if (!TextUtils.isEmpty(getOrderColumnName())) {
				sql = String.format("select * from %s order by %s desc", getTableName(), getOrderColumnName());
			} else {
				sql = String.format("select * from %s", getTableName());
			}
			cursor = db.rawQuery(sql, null);
			if (cursor != null) {
				ArrayList<DownloadTaskData> result = new ArrayList<DownloadTaskData>();
				while (cursor.moveToNext()) {
					result.add(createTaskData(cursor));
				}
				return result;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
			if (db != null) {
				db.close();
			}
		}
		return null;
	}
	
	public void updateTaskData(DownloadTaskData info) {
		DownloadTaskData findInfo = findRecord(info.url);
		if (findInfo == null) {
			insertRecord(info);
		} else {
			SQLiteDatabase db = null;
			try {
				db = getReadableDatabase();
				ContentValues values = new ContentValues();
				values.put("param", info.params.tranToString());
				values.put("totalSize", info.totalSize);
				values.put("status", info.status.ordinal());
				String whereClause = "url=?";
				String[] whereArgs = { findInfo.url };
				db.update(getTableName(), values, whereClause, whereArgs);
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
	}

	protected DownloadTaskData createTaskData(Cursor cursor) {
		DownloadTaskData info = new DownloadTaskData();
		info.url = cursor.getString(cursor.getColumnIndex("url"));
		String paramStr = cursor.getString(cursor.getColumnIndex("param"));
		info.params.restoreFromString(paramStr);
		info.status = DownloadStatus.values()[cursor.getInt(cursor.getColumnIndex("status"))];
		info.createTime = cursor.getLong(cursor.getColumnIndex("createTime"));
		return info;
	}
}
