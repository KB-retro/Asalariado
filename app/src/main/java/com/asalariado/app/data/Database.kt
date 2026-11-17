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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asalariado.core.DayEntry
import com.asalariado.core.MedicalKind
import com.asalariado.core.MedicalVisit
import com.asalariado.core.Payslip
import com.asalariado.core.ShiftType
import com.asalariado.core.Source
import com.asalariado.core.Stoppage
import com.asalariado.core.StoppageKind
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
    @androidx.room.ColumnInfo(defaultValue = "COMMON") val kind: String = "COMMON",
)

@Entity(tableName = "payslips", primaryKeys = ["year", "month"])
data class PayslipEntity(
    val year: Int,
    val month: Int,
    val gross: Double?,
    val net: Double?,
    val note: String,
)

/** Recibo de una paga extra (julio / diciembre), que se cobra aparte de la nómina del mes. */
@Entity(tableName = "extra_payslips", primaryKeys = ["year", "month"])
data class ExtraPayslipEntity(
    val year: Int,
    val month: Int,
    val gross: Double?,
    val net: Double?,
    val note: String,
)

fun ExtraPayslipEntity.toModel() = Payslip(year, month, gross, net, note)
fun Payslip.toExtraEntity() = ExtraPayslipEntity(year, month, gross, net, note)

/** Horas de paro (huelga por horas) o de asamblea de un día. */
@Entity(tableName = "stoppages", primaryKeys = ["epochDay", "kind"])
data class StoppageEntity(
    val epochDay: Long,
    val kind: String,
    val hours: Double,
)

fun StoppageEntity.toModel() = runCatching { Stoppage(LocalDate.ofEpochDay(epochDay), StoppageKind.valueOf(kind), hours) }.getOrNull()

/** Tipo de día del calendario laboral anual. */
@Entity(tableName = "calendar_days")
data class CalendarDayEntity(
    @PrimaryKey val epochDay: Long,
    val type: String,
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

fun MedicalEntity.toModel() =
    MedicalVisit(id, LocalDate.ofEpochDay(epochDay), startMin.toTime(), endMin.toTime(), reason, justified, MedicalKind.fromName(kind))
fun MedicalVisit.toEntity() = MedicalEntity(id, date.toEpochDay(), start.toMin(), end.toMin(), reason, justified, kind.name)
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

    @Query("SELECT * FROM calendar_days")
    fun allCalendarDays(): Flow<List<CalendarDayEntity>>

    @Upsert
    suspend fun upsertCalendarDays(days: List<CalendarDayEntity>)

    @Query("DELETE FROM calendar_days WHERE epochDay = :epochDay")
    suspend fun deleteCalendarDay(epochDay: Long)

    @Query("SELECT * FROM payslips")
    fun allPayslips(): Flow<List<PayslipEntity>>

    @Upsert
    suspend fun savePayslip(p: PayslipEntity)

    @Query("DELETE FROM payslips WHERE year = :year AND month = :month")
    suspend fun deletePayslip(year: Int, month: Int)

    @Query("SELECT * FROM extra_payslips")
    fun allExtraPayslips(): Flow<List<ExtraPayslipEntity>>

    @Upsert
    suspend fun saveExtraPayslip(p: ExtraPayslipEntity)

    @Query("DELETE FROM extra_payslips WHERE year = :year AND month = :month")
    suspend fun deleteExtraPayslip(year: Int, month: Int)

    @Query("SELECT * FROM stoppages")
    fun allStoppages(): Flow<List<StoppageEntity>>

    @Upsert
    suspend fun saveStoppage(s: StoppageEntity)

    @Query("DELETE FROM stoppages WHERE epochDay = :epochDay AND kind = :kind")
    suspend fun deleteStoppage(epochDay: Long, kind: String)

    // ---- Copia de seguridad ----
    @Query("SELECT * FROM days") suspend fun daysOnce(): List<DayEntity>
    @Query("SELECT * FROM medical") suspend fun medicalOnce(): List<MedicalEntity>
    @Query("SELECT * FROM payslips") suspend fun payslipsOnce(): List<PayslipEntity>
    @Query("SELECT * FROM calendar_days") suspend fun calendarOnce(): List<CalendarDayEntity>
    @Query("DELETE FROM days") suspend fun clearDays()
    @Query("DELETE FROM medical") suspend fun clearMedical()
    @Query("DELETE FROM payslips") suspend fun clearPayslips()
    @Query("DELETE FROM calendar_days") suspend fun clearCalendar()
    @Query("SELECT * FROM extra_payslips") suspend fun extraPayslipsOnce(): List<ExtraPayslipEntity>
    @Query("DELETE FROM extra_payslips") suspend fun clearExtraPayslips()
    @Query("SELECT * FROM stoppages") suspend fun stoppagesOnce(): List<StoppageEntity>
    @Query("DELETE FROM stoppages") suspend fun clearStoppages()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStoppages(list: List<StoppageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExtraPayslips(list: List<ExtraPayslipEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMedical(list: List<MedicalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPayslips(list: List<PayslipEntity>)
}

@Database(
    entities = [DayEntity::class, MedicalEntity::class, PayslipEntity::class, CalendarDayEntity::class, ExtraPayslipEntity::class, StoppageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_days` (`epochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`epochDay`))",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `medical` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'COMMON'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `extra_payslips` (`year` INTEGER NOT NULL, `month` INTEGER NOT NULL, " +
                        "`gross` REAL, `net` REAL, `note` TEXT NOT NULL, PRIMARY KEY(`year`, `month`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stoppages` (`epochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`hours` REAL NOT NULL, PRIMARY KEY(`epochDay`, `kind`))",
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "asalariado.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
