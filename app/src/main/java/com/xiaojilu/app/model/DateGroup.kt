package com.xiaojilu.app.model

data class DateGroup(
    val date: String,
    val records: List<Record>,
    var isExpanded: Boolean = true
) {
    val recordCount: Int
        get() = records.size
    
    val formattedDate: String
        get() {
            val parts = date.split("-")
            return "${parts[1]}月${parts[2]}日"
        }
}