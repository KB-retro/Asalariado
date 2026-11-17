package com.asalariado.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.asalariado.core.DayEntry
import com.asalariado.core.MedicalVisit
import com.asalariado.core.Payslip
import com.asalariado.core.ShiftType
import com.asalariado.core.Source
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
    val shift: String,
    val startMin: Int?,
    val endMin: Int?,
    val holidayWorked: Boolean,
    val note: String,
    val source: String,
)

@Entity(tableName = "medical")
data class MedicalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMin: Int,
    val endMin: Int,
    val reason: String,
    val justified: Boolean,
)

@Entity(tableName = "payslips", primaryKeys = ["year", "month"])
data class PayslipEntity(
    val year: Int,
    val month: Int,
    val gross: Double?,
    val net: Double?,
    val note: String,
)

private fun LocalTime.toMin() = hour * 60 + minute
private fun Int.toTime() = LocalTime.of((this / 60) % 24, this % 60)

fun DayEntity.toModel() = DayEntry(
    date = LocalDate.ofEpochDay(epochDay),
    shift = ShiftType.fromName(shift),
    start = startMin?.toTime(),
    end = endMin?.toTime(),
    holidayWorked = holidayWorked,
    note = note,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.MANUAL),
)

fun DayEntry.toEntity() = DayEntity(
    epochDay = date.toEpochDay(),
    shift = shift.name,
    startMin = start?.toMin(),
    endMin = end?.toMin(),
    holidayWorked = holidayWorked,
    note = note,
    source = source.name,
)

fun MedicalEntity.toModel() = MedicalVisit(id, LocalDate.ofEpochDay(epochDay), startMin.toTime(), endMin.toTime(), reason, justified)
fun MedicalVisit.toEntity() = MedicalEntity(id, date.toEpochDay(), start.toMin(), end.toMin(), reason, justified)
fun PayslipEntity.toModel() = Payslip(year, month, gross, net, note)
fun Payslip.toEntity() = PayslipEntity(year, month, gross, net, note)

@Dao
interface AppDao {
    @Query("SELECT * FROM days")
    fun allDays(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days WHERE epochDay BETWEEN :from AND :to")
    suspend fun daysBetween(from: Long, to: Long): List<DayEntity>

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    @Query("DELETE FROM days WHERE epochDay = :epochDay")
    suspend fun deleteDay(epochDay: Long)

    @Query("SELECT * FROM medical ORDER BY epochDay DESC, startMin DESC")
    fun allMedical(): Flow<List<MedicalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedical(m: MedicalEntity)

    @Query("DELETE FROM medical WHERE id = :id")
    suspend fun deleteMedical(id: Long)

    @Query("SELECT * FROM payslips")
    fun allPayslips(): Flow<List<PayslipEntity>>

    @Upsert
    suspend fun savePayslip(p: PayslipEntity)

    @Query("DELETE FROM payslips WHERE year = :year AND month = :month")
    suspend fun deletePayslip(year: Int, month: Int)
}

@Database(entities = [DayEntity::class, MedicalEntity::class, PayslipEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "asalariado.db")
                .build().also { instance = it }
        }
    }
}
