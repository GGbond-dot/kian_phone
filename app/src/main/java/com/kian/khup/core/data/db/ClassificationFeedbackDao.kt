package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import com.kian.khup.core.data.db.entities.ClassificationFeedback

@Dao
interface ClassificationFeedbackDao {

    @Insert
    suspend fun insert(feedback: ClassificationFeedback)
}
