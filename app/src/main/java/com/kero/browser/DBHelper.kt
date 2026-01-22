package com.kero.browser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 数据模型
data class HistoryItem(val id: Int, val title: String, val url: String, val time: Long)

class DBHelper(context: Context) : SQLiteOpenHelper(context, "KeroBrowser.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // 创建历史记录表
        // id: 序号, title: 标题, url: 网址, time: 访问时间
        val createTable = "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT, time INTEGER)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    // 添加历史记录
    fun addHistory(title: String, url: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("time", System.currentTimeMillis())
        }
        db.insert("history", null, values)
        db.close()
    }

    // 获取所有历史记录 (按时间倒序，最新的在前面)
    fun getAllHistory(): List<HistoryItem> {
        val list = ArrayList<HistoryItem>()
        val db = this.readableDatabase
        // 限制只查最近 50 条，防止列表太长卡顿
        val cursor = db.rawQuery("SELECT * FROM history ORDER BY time DESC LIMIT 50", null)
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val url = cursor.getString(cursor.getColumnIndexOrThrow("url"))
                val time = cursor.getLong(cursor.getColumnIndexOrThrow("time"))
                list.add(HistoryItem(id, title, url, time))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
    
    // 清空历史
    fun clearHistory() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM history")
        db.close()
    }
}
