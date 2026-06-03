package com.xiaojilu.app.model

data class Record(
    val id: String,
    val date: String, // 格式: yyyy-MM-dd
    val content: String,
    val createdAt: String, // ISO 8601格式
    val updatedAt: String // ISO 8601格式
)

// 导出专用数据模型
data class ExportRecord(
    val id: String,
    val date: String, // 格式: yyyy-MM-dd
    val content: String,
    val createdAt: String, // 格式: yyyy-MM-dd HH:mm:ss
    val updatedAt: String // 格式: yyyy-MM-dd HH:mm:ss
)

// 导出文件数据模型
data class ExportData(
    val records: List<ExportRecord>
)