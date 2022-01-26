import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.internal.JSJoda.LocalDate

data class Input(

    // your actual gross salary as in contract
    val salaryMonthlyGross: BigDecimal = "10000".bdc,

    // date when your contract has started
    val startDate: LocalDate = LocalDate.of(2021, 1, 12),

    // If the yearly tax declaration is shared with wife/husband - this will disable the second tax threshold.
    // This is related only to month-by-month taxes, not the yearly tax declaration itself
    val sharedTaxDeclaration: Boolean = false,

    // If true, it means that all taxes for food vouchers will be added to your gross salary,
    // this is some kind of hack to make this tax invisible and make your actual nett income the same
    val foodVouchersFullyCompensated: Boolean = true,

    // You can optionally (but preferred) correct the actual sum received in food vouchers
    // Under the column "Dodatki niepieniężne", lines "Bony PIT" + "Bony PIT ZUS"
    val actualFoodVouchersByMonth: Map<Month, BigDecimal> = mapOf(),

    // Amount of money which is received in food vouchers at each working day
    val dailyFoodVouchersIncome: BigDecimal = "43".bdc,

    // A list of deductions per month which will be deducted from your NETT salary
    // In payslip this is under column "Potrącenie"
    val occasionalNettDeductionsByMonth: Map<Month, BigDecimal> = mapOf(),

    val permanentMonthlyNettDeductions: BigDecimal = "124".bdc,

    // To calculate zero PIT for people under 26, set to somewhere in past if not applicable
    val birthday26years: LocalDate = LocalDate.of(2022, 11, 21),

    // whether to calculate the PPK tax, usually 2%, and after the 90 days of employment
    val participateInPPK: Boolean = true,

    // for month when person reaches 26
    val salaryDayOfMonth: Int = 10,

    // small tax reduce which covers transport to the office, 99% is false
    val liveOutsideOfCity: Boolean = false,

    // you can optionally provide a list of occasional compensations in GROSS (take value from payslip)
    // which will be added to your salary, for example it could be a Polish courses compensation
    // to check the NETT value and compare with the requested compensation - run two times "with" and "without" this, then divide
    val occasionalBonusesGross: Map<Month, BigDecimal> = mapOf(),

    // Also include target bonus calculation (2 extra salaries)
    val withTargetBonus: Boolean = true,

    // you can optionally correct the percentage of the quarter/annual bonus you already received
    // but keep in mind that actual percent in the email report is rounded,
    // so it's better to divide actual sum by target and get more precise value
    val actualQuarterBonusByMonth: Map<Month, BigDecimal> = mapOf(),
    val actualAnnualBonusByMonth: Map<Month, BigDecimal> = mapOf(),

    // actual bonus calculation, should not be changed in most cases
    val quarterTargetBonusRatio: BigDecimal = "0.2".bdc,
    val annualTargetBonusRatio: BigDecimal = "1.2".bdc,

    // you can configure a month since when you have the new gross salary,
    // every new entry applied since specified month and till more late entry
    val contractGrossSalaryChange: Map<Month, BigDecimal> = mapOf(),

    // optionally, enable new tax calculation changes (which are still unconfirmed) in 2022 and later
    // The two changes is increased normal tax cap (from 85k to 120k), increased a tax-free sum (from 525.12 to 5100)
    // and health tax cannot be reduced by the PIT (7.75% of health tax cannot be deducted from PIT)
    val useNewRulesAfter2022: Boolean = true,

    // only to convert your nett to USD, nothing else
    val usdRate: BigDecimal = "4.0507".bdc,
    val sickLeaves: List<Pair<LocalDate, LocalDate>> = listOf(),

    val creativeWorkStart: Month = Month("2022-01"),
    val creativeWorkPercent: BigDecimal = zero,
)