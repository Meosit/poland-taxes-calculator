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
        .filterNot { DayOfWeek.SATURDAY == it.dayOfWeek() || it.dayOfWeek() == DayOfWeek.SUNDAY }
        .filterNot { it in holidays }
        .filterNot { it.dayOfWeek() == DayOfWeek.FRIDAY && it.plusDays(1) in holidays }
        .filterNot { it.dayOfWeek() == DayOfWeek.FRIDAY && it.dayOfMonth().toInt() <= 7 && cal.atDay(1).dayOfWeek() == DayOfWeek.SATURDAY && cal.atDay(1) in holidays }
        .toList()

    val workDaysCount = workDays.size
    fun workDaysAfter(date: LocalDate): Int = workDays.count { it.compareTo(date).toInt() >= 0 }

    fun inDayRange(day: LocalDate) = cal.atDay(1).compareTo(day).toInt() <= 0 && day.compareTo(cal.atEndOfMonth()).toInt() <= 0

    companion object {
        val publicHolidays = setOf(
            // New Year's Day
            MonthDay.of(1, 1),
            // Epiphany
            MonthDay.of(1, 6),
            // Easter Monday
            MonthDay.of(4, 18),
            // Labour Day
            MonthDay.of(5, 1),
            // Constitution Day
            MonthDay.of(5, 3),
            // Corpus Christi
            MonthDay.of(6, 16),
            // Assumption Day
            MonthDay.of(8, 15),
            // All Saints' Day
            MonthDay.of(11, 1),
            // Independence Day
            MonthDay.of(11, 11),
            // Christmas Day
            MonthDay.of(12, 25),
            // 2nd Day of Christmas
            MonthDay.of(12, 26),
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
