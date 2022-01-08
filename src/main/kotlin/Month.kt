import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.internal.JSJoda.DayOfWeek
import kotlinx.datetime.internal.JSJoda.LocalDate
import kotlinx.datetime.internal.JSJoda.MonthDay
import kotlinx.datetime.internal.JSJoda.YearMonth

data class Month(
    val year: Int,
    val name: String
): Comparable<Month> {

    constructor(month: YearMonth): this(month.year().toInt(), month.month().name().lowercase().replaceFirstChar(Char::uppercaseChar))

    constructor(str: String): this(YearMonth.parse(str))

    private val log: MutableList<String> = mutableListOf()

    val cal: YearMonth = YearMonth.of(year, kotlinx.datetime.internal.JSJoda.Month.valueOf(name.uppercase()))
    private val holidays = publicHolidays
        .filter { cal.monthValue() == it.monthValue() && it.atYear(year).dayOfWeek() != DayOfWeek.SUNDAY }
        .map { it.atYear(year) }
    val workDays = generateSequence(cal.atDay(1)) { it.plusDays(1) }
        .takeWhile { it.compareTo(cal.atEndOfMonth()).toInt() <= 0 }
        .filterNot { it in actualHolidays }
        .filterNot { it.dayOfWeek() == DayOfWeek.FRIDAY && it.plusDays(1) in holidays }
        .filterNot { it.dayOfWeek() == DayOfWeek.FRIDAY && it.dayOfMonth().toInt() <= 7 && cal.atDay(1).dayOfWeek() == DayOfWeek.SATURDAY && cal.atDay(1) in holidays }
        .filterNot { DayOfWeek.SATURDAY == it.dayOfWeek() || it.dayOfWeek() == DayOfWeek.SUNDAY }
        .toList()

    val workDaysCount = workDays.size
    fun workDaysAfter(date: LocalDate): Int = workDays.count { it.compareTo(date).toInt() >= 0 }

    fun inDayRange(day: LocalDate) = cal.atDay(1).compareTo(day).toInt() <= 0 && day.compareTo(cal.atEndOfMonth()).toInt() <= 0

    companion object {
        val publicHolidays = setOf(
            MonthDay.of(1, 1),
            MonthDay.of(1, 6),
            MonthDay.of(4, 4),
            MonthDay.of(4, 5),
            MonthDay.of(5, 1),
            MonthDay.of(5, 3),
            MonthDay.of(5, 23),
            MonthDay.of(6, 3),
            MonthDay.of(8, 15),
            MonthDay.of(11, 1),
            MonthDay.of(11, 11),
            MonthDay.of(12, 25),
            MonthDay.of(12, 26),
        )
        // actual holidays which should be taken into account as a priority
        val actualHolidays: Set<LocalDate> = setOf(
            LocalDate.of(2021, 11, 1),
            LocalDate.of(2022, 11, 1)
        )
    }

    override fun compareTo(other: Month): Int = this.cal.compareTo(other.cal).toInt()


    fun log(message: String) {
        log.add("> $message")
    }

    fun logNonZero(a: BigDecimal, message: String) {
        if (a != zero) log(message)
    }

    fun getLog() = log as List<String>
}
