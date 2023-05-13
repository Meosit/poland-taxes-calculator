import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.internal.JSJoda.LocalDate
import kotlinx.datetime.internal.JSJoda.YearMonth

class UopCalcualtor(input: Input) {

    // your actual gross salary as in contract
    private val salaryMonthlyGross: BigDecimal = input.salaryMonthlyGross

    // date when your contract has started
    private val startDate: LocalDate = input.startDate
    // date when your contract has ended
    private val endDate: LocalDate = input.endDate

    // If the yearly tax declaration is shared with wife/husband - this will disable the second tax threshold.
    // This is related only to month-by-month taxes, not the yearly tax declaration itself
    private val sharedTaxDeclaration: Boolean = input.sharedTaxDeclaration

    // If true, it means that all taxes for food vouchers will be added to your gross salary,
    // this is some kind of hack to make this tax invisible and make your actual nett income the same
    private val foodVouchersFullyCompensated: Boolean = input.foodVouchersFullyCompensated

    // You can optionally (but preferred) correct the actual sum received in food vouchers
    // Under the column "Dodatki niepieniężne", lines "Bony PIT" + "Bony PIT ZUS"
    private val actualFoodVouchersByMonth: Map<Month, BigDecimal> = input.actualFoodVouchersByMonth

    // Amount of money which is received in food vouchers at each working day
    private val dailyFoodVouchersIncome: BigDecimal = input.dailyFoodVouchersIncome

    // A list of deductions per month which will be deducted from your NETT salary
    // In payslip this is under column "Potrącenie"
    private val occasionalNettDeductionsByMonth: Map<Month, BigDecimal> = input.occasionalNettDeductionsByMonth

    private val permanentMonthlyNettDeductions: BigDecimal = input.permanentMonthlyNettDeductions

    // To calculate zero PIT for people under 26, set to somewhere in past if not applicable
    private val birthday26years: LocalDate = input.birthday26years

    // whether to calculate the PPK tax, usually 2%, and after the 90 days of employment
    private val participateInPPK: Boolean = input.participateInPPK

    // for month when person reaches 26
    private val salaryDayOfMonth: Int = input.salaryDayOfMonth

    // small tax reduce which covers transport to the office, 99% is false
    private val liveOutsideOfCity: Boolean = input.liveOutsideOfCity

    // you can optionally provide a list of occasional compensations in GROSS (take value from payslip)
    // which will be added to your salary, for example it could be a Polish courses compensation
    // to check the NETT value and compare with the requested compensation - run two times "with" and "without" this, then divide
    private val occasionalBonusesGross: Map<Month, BigDecimal> = input.occasionalBonusesGross

    // Also include target bonus calculation (2 extra salaries)
    private val withTargetBonus: Boolean = input.withTargetBonus

    // you can optionally correct the percentage of the quarter/annual bonus you already received
    // but keep in mind that actual percent in the email report is rounded,
    // so it's better to divide actual sum by target and get more precise value
    private val actualQuarterBonusByMonth: Map<Month, BigDecimal> = input.actualQuarterBonusByMonth
    private val actualAnnualBonusByMonth: Map<Month, BigDecimal> = input.actualAnnualBonusByMonth

    // actual bonus calculation, should not be changed in most cases
    private val quarterTargetBonusRatio: BigDecimal = input.quarterTargetBonusRatio
    private val annualTargetBonusRatio: BigDecimal = input.annualTargetBonusRatio

    // you can configure a month since when you have the new gross salary,
    // every new entry applied since specified month and till more late entry
    private val contractGrossSalaryChange: Map<Month, BigDecimal> = input.contractGrossSalaryChange

    // optionally, enable new tax calculation changes in 2022 and later
    // The two changes is increased normal tax cap (from 85k to 120k), increased a tax-free sum (from 525.12 to 5100)
    // and health tax cannot be reduced by the PIT (7.75% of health tax cannot be deducted from PIT)
    private val useNewRulesAfter2022: Boolean = input.useNewRulesAfter2022

    // optionally, enable new tax calculation changes in July 2022 and later
    // The main change is a new tax rate of 12% instead of 17% for new
    private val useNewRulesAfterJuly2022: Boolean = input.useNewRulesAfterJuly2022

    // list of sick leave ranges which will be paid as 80% of contract salary
    private val sickLeaves: List<Pair<LocalDate, LocalDate>> = input.sickLeaves

    // Start date of creative work become applicable
    private val creativeWorkStart: Month = input.creativeWorkStart

    // Percent of PIT base reduce for authors of copyrighted works
    private val creativeWorkPercent: BigDecimal = input.creativeWorkPercent

    // FIXED VALUES
    // FIXED VALUES
    // FIXED VALUES
    private val retirementAndDisabilityCapPerYear = mapOf(
        1970 to "156810".bdc,
        2020 to "156810".bdc,
        2021 to "157770".bdc,
        2022 to "177660".bdc,
    )
    private val normalIncomeTaxCap = "85528".bdc
    private val normalIncomeTaxCapSince2022 = "120000".bdc

    private val retirementInsuranceTaxRate = "0.0976".bdc
    private val disabilityInsuranceTaxRate = "0.015".bdc
    private val sicknessInsuranceTaxRate = "0.0245".bdc
    private val healthInsuranceTaxRate = "0.09".bdc
    private val ppkTaxRate = "0.02".bdc

    private val normalTaxFreeQuota = "525.12".bdc
    private val normalTaxFreeQuotaSince2022 = "5100".bdc

    private val normalIncomeTaxRate = "0.17".bdc
    private val normalIncomeTaxRateSinceJuly2022 = "0.12".bdc
    private val increasedIncomeTaxRate = "0.32".bdc
    private val healthIncomeTaxReduceRate = "0.0775".bdc
    private val foodVoucherTaxFreeQuota = "190".bdc

    private data class YearlyState(
        val year: Int,
        var workingDays: BigDecimal = zero,
        var sickDays: BigDecimal = zero,
        var grossIncome: BigDecimal = zero,
        var contractGrossIncome: BigDecimal = zero,
        var foodVouchersBonuses: BigDecimal = zero,
        var targetBonuses: BigDecimal = zero,
        var occasionalBonuses: BigDecimal = zero,
        var zusBase: BigDecimal = zero,
        var pitBase: BigDecimal = zero,
        var pitBaseForTeens: BigDecimal = zero,
        var normalPit: BigDecimal = zero,
        var increasedPit: BigDecimal = zero,
        var creativeWorkTaxFree: BigDecimal = zero,
    )

    private data class SicknessCompensationState(
        var monthGrosses: List<BigDecimal> = emptyList(),
        var currentTotalWorkDays: BigDecimal = zero
    )


    fun calculate(): List<TaxMonthInfo> {
        val fixedLocalityTaxBaseFreeQuota = (if (liveOutsideOfCity) "300" else "250").bdc
        val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            .let { months -> listOf(
                months.map { Month(startDate.year().toInt(), it) }.filterNot { it.cal.atEndOfMonth().compareTo(startDate).toInt() < 0 },
                months.map { Month(startDate.year().toInt() + 1, it) },
                months.map { Month(startDate.year().toInt() + 2, it) }
            )}.flatten().filter { it.cal.atDay(1).compareTo(endDate).toInt() <= 0 }

        var yearlyState = YearlyState(months[0].year)
        val sicknessCompensationState = SicknessCompensationState()
        val years = mutableMapOf(yearlyState.year to yearlyState)
        val taxMonths = months.mapIndexed { i, month ->
            if (month.name == "January" && i != 0) {
                month.log("This is a first income of the calendar year, so starting calculations from scratch!")
                yearlyState = YearlyState(month.year)
                years += month.year to yearlyState
            }
            val polskiLad = month.cal.year().toInt() >= 2022 && useNewRulesAfter2022
            val polskiLad2 = month.cal.compareTo(YearMonth.Companion.of(2022, 7)).toInt() >= 0 && useNewRulesAfterJuly2022
            val actualNormalIncomeTaxCap: BigDecimal
            val actualNormalTaxFreeQuota: BigDecimal
            val actualNormalIncomeTaxRate: BigDecimal
            if (polskiLad) {
                actualNormalIncomeTaxRate = if (polskiLad2) {
                    month.log("Using a new 2022 July (Polski Ład 2.0) rules for tax calculation, tax rate for normal cap would be ${normalIncomeTaxRateSinceJuly2022.percentString()} instead of ${normalIncomeTaxRate.percentString()}")
                    normalIncomeTaxRateSinceJuly2022
                } else normalIncomeTaxRate
                month.log("Using a new 2022 (Polski Ład) rules for tax calculation, PIT of ${actualNormalIncomeTaxRate.percentString()} is till ${normalIncomeTaxCapSince2022.str()} ")
                actualNormalIncomeTaxCap = normalIncomeTaxCapSince2022
                actualNormalTaxFreeQuota = normalTaxFreeQuotaSince2022
            } else {
                actualNormalIncomeTaxRate = normalIncomeTaxRate
                actualNormalIncomeTaxCap = normalIncomeTaxCap
                actualNormalTaxFreeQuota = normalTaxFreeQuota
            }
            val workingDays: BigDecimal = when (i) {
                0 -> month.workDaysAfter(startDate).bdc
                months.size - 1 -> month.workDaysBefore(endDate).bdc
                else -> month.workDaysCount.bdc
            }
            val changedContractGross = contractGrossSalaryChange.filterKeys { it <= month }.maxByOrNull { it.key }?.value ?: salaryMonthlyGross
            if (workingDays == zero) {
                month.log("There are no working days found for this month")
                return@mapIndexed TaxMonthInfo(month, workingDays.intValue(), zero.intValue(), changedContractGross, zero, zero, zero, zero, zero, zero, zero, yearlyState.zusBase, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, month.getLog())
            }
            month.log("In this month there are ${workingDays.str()} working days")

            yearlyState.workingDays += workingDays

            var gross = grossWithFirstMonthRespect(i, months.size, month, changedContractGross, workingDays)
            var bonuses = zero
            yearlyState.contractGrossIncome += gross

            val (sickSalary, sickDays) = sickLeavesGross(month, sicknessCompensationState)
            val grossSalaryWithinSickness = (gross div workingDays) * sickDays
            gross -= grossSalaryWithinSickness
            gross += sickSalary
            month.logNonZero(sickDays, "Deducting gross income with due to sickness by ${grossSalaryWithinSickness.str()}, but also adding a sickness compensation of ${sickSalary.str()}, the actual gross now is ${gross.str()}")
            yearlyState.sickDays += sickDays

            val targetBonus = targetBonus(month, changedContractGross)
            gross += targetBonus
            bonuses += targetBonus
            yearlyState.targetBonuses += targetBonus
            sicknessCompensationState.monthGrosses = (sicknessCompensationState.monthGrosses + gross).takeLast(12)
            sicknessCompensationState.currentTotalWorkDays += workingDays

            occasionalBonusesGross[month]?.let {
                gross += it
                bonuses += it
                yearlyState.occasionalBonuses += it
                month.log("Adding an occasional bonus of ${it.str()} in this month to your gross, the new value is ${gross.str()}")
            }
            val foodVouchersTotal = actualFoodVouchersByMonth[month] ?: (workingDays * dailyFoodVouchersIncome)
            if (actualFoodVouchersByMonth.containsKey(month)) {
                month.logNonZero(foodVouchersTotal, "In this month you have received income in food vouchers equal to ${foodVouchersTotal.str()} (specified in config)")
            } else {
                month.logNonZero(foodVouchersTotal, "In this month you have received income in food vouchers equal to: ${workingDays.str()} * ${dailyFoodVouchersIncome.str()} = ${foodVouchersTotal.str()}")
            }
            val foodVouchersTaxBase = if (foodVouchersTotal > foodVoucherTaxFreeQuota) {
                val deltaForZUS = foodVouchersTotal - foodVoucherTaxFreeQuota
                month.log("You have food vouchers which are exceeded the limit of tax-free quota of ${foodVoucherTaxFreeQuota.str()}, therefore you should pay ZUS delta: ${foodVouchersTotal.str()} - 190 = ${deltaForZUS.str()}")
                deltaForZUS
            } else zero

            var zusBase = gross + foodVouchersTaxBase
            yearlyState.zusBase += zusBase
            var retirementAndDisabilityTaxBase = retirementAndDisabilityTaxBase(yearlyState.zusBase, zusBase, month)
            var retirementTax: BigDecimal = retirementTax(retirementAndDisabilityTaxBase)
            var disabilityTax: BigDecimal = disabilityTax(retirementAndDisabilityTaxBase)
            if (retirementAndDisabilityTaxBase == zero) {
                month.log("You have already reached the retirement and disability tax cap, those payments are skipped till the end of the year!")
            }
            var sicknessTax: BigDecimal = sicknessTax(zusBase)
            var socialSecurityTax = retirementTax + disabilityTax + sicknessTax
            var healthTaxByRate = healthTax(gross, socialSecurityTax)

            val foodVouchersTaxCompensation = foodVouchersCompensation(yearlyState.zusBase, foodVouchersTaxBase, month)
            var zusLogSuffix = ""
            if (foodVouchersTaxCompensation != zero) {
                yearlyState.zusBase += foodVouchersTaxCompensation
                yearlyState.foodVouchersBonuses += foodVouchersTaxCompensation
                gross += foodVouchersTaxCompensation
                month.log("Only tax compensation (basically a bonus) is added to your gross salary because the food vouchers are not the subject of PIT. The real gross income would be: ${(gross - foodVouchersTaxCompensation).str()} + ${foodVouchersTaxCompensation.str()} = ${gross.str()}")
                zusBase += foodVouchersTaxCompensation
                retirementAndDisabilityTaxBase = retirementAndDisabilityTaxBase(yearlyState.zusBase, zusBase, month, true)
                month.logNonZero(foodVouchersTaxCompensation, "Modifying the ZUS tax with respect of food vouchers compensation bonus")
                retirementTax = retirementTax(retirementAndDisabilityTaxBase)
                disabilityTax = disabilityTax(retirementAndDisabilityTaxBase)
                sicknessTax = sicknessTax(zusBase)
                socialSecurityTax = retirementTax + disabilityTax + sicknessTax
                healthTaxByRate = healthTax(zusBase, socialSecurityTax)
                zusLogSuffix = " including food tax compensation"
            }

            month.log("Retirement tax$zusLogSuffix: ${retirementInsuranceTaxRate.percentString()} of ${retirementAndDisabilityTaxBase.str()} = ${retirementTax.str()}")
            month.log("Disability tax$zusLogSuffix: ${disabilityInsuranceTaxRate.percentString()} of ${retirementAndDisabilityTaxBase.str()} = ${disabilityTax.str()}")
            month.log("Health Insurance Tax$zusLogSuffix: ${healthInsuranceTaxRate.percentString()} of (${gross.str()} - ${socialSecurityTax.str()}) = ${healthTaxByRate.str()}")
            month.log("Sickness tax$zusLogSuffix (% is never changes across the year): ${sicknessInsuranceTaxRate.percentString()} of ${gross.str()} = ${sicknessTax.str()}")

            yearlyState.grossIncome += gross
            val zus = socialSecurityTax + healthTaxByRate
            month.log("Therefore, ZUS tax in this month is: ${retirementTax.str()} + ${disabilityTax.str()} + ${sicknessTax.str()} + ${healthTaxByRate.str()} = ${zus.str()}")


            val baseIncome = gross - socialSecurityTax
            month.log("Base Income for PIT calculation is: ${gross.str()} - ${socialSecurityTax.str()} = ${baseIncome.str()}")

            val creativeWorkTaxFree = creativeWorkTaxFree(month, baseIncome, actualNormalIncomeTaxCap, yearlyState, sickSalary)
            val creativeBaseIncome = baseIncome - creativeWorkTaxFree
            val pitBase = positive(creativeBaseIncome - fixedLocalityTaxBaseFreeQuota).sc()
            yearlyState.creativeWorkTaxFree += creativeWorkTaxFree
            yearlyState.pitBase += pitBase
            month.log("For PIT, there is a fixed locality tax free amount of ${fixedLocalityTaxBaseFreeQuota.str()} (since you're living ${if (liveOutsideOfCity) "NOT " else ""}in the city where you're working).")
            month.log("So, the base for the PIT is: ${baseIncome.str()} - ${creativeWorkTaxFree.str()} - ${fixedLocalityTaxBaseFreeQuota.str()} = ${pitBase.str()}")

            val zeroTaxApplies = isTeenZeroTaxApply(month)

            val pitBaseForTeens = min(positive(yearlyState.pitBase - actualNormalIncomeTaxCap), pitBase)
            yearlyState.pitBaseForTeens += pitBaseForTeens

            val actualYearlyPitBase = if(zeroTaxApplies) yearlyState.pitBaseForTeens else yearlyState.pitBase
            val actualPitBase = when {
                zeroTaxApplies && yearlyState.pitBaseForTeens <= actualNormalIncomeTaxCap + pitBase -> {
                    if (yearlyState.pitBaseForTeens != zero) {
                        month.log("As a person under 26 years, you have reached your PIT-free cap of ${actualNormalIncomeTaxCap.str()} (currently ${yearlyState.pitBase.str()}), in this month you will pax income tax based on ${yearlyState.pitBaseForTeens.str()}, after that a normal rules apply")
                    } else {
                        month.log("You're younger than 26 and PIT-free cap of ${actualNormalIncomeTaxCap.str()} is not reached (now is ${yearlyState.pitBase.str()}), so no PIT at all!")
                    }
                    pitBaseForTeens
                }
                else -> pitBase
            }

            val pitNormalTax: BigDecimal
            val pitIncreasedTax: BigDecimal
            val pitTaxFree = monthlyTaxFreeQuota(actualNormalTaxFreeQuota, actualNormalIncomeTaxCap, actualYearlyPitBase, actualPitBase)
            val pitByRate = when {
                actualPitBase == zero -> {
                    pitNormalTax = zero
                    pitIncreasedTax = zero
                    zero
                }
                sharedTaxDeclaration -> {
                    month.log("Since you're sharing a tax declaration with your spouse, so normal PIT thresholds do not apply (due to unknown income of your spouse) - you will need to pay the difference with tax declaration, for now the difference is ${yearlyState.increasedPit.str()} (this diff do not take into account spouse's income)")
                    pitNormalTax = (actualPitBase * actualNormalIncomeTaxRate).sc()
                    month.log("The initial PIT is ${actualNormalIncomeTaxRate.percentString()} of ${actualPitBase.str()} = ${pitNormalTax.str()}")
                    pitIncreasedTax = zero
                    pitNormalTax
                }
                actualYearlyPitBase <= actualNormalIncomeTaxCap -> {
                    month.log("Base for PIT in this year (${actualYearlyPitBase.str()}) is less than a normal cap of ${actualNormalIncomeTaxCap.str()}")
                    pitNormalTax = (actualPitBase * actualNormalIncomeTaxRate).sc()
                    month.log("The initial PIT is ${actualNormalIncomeTaxRate.percentString()} of ${actualPitBase.str()} = ${pitNormalTax.str()}")
                    pitIncreasedTax = zero
                    pitNormalTax
                }
                actualYearlyPitBase - actualNormalIncomeTaxCap in zero..actualPitBase -> {
                    val normalCapOverflow = actualYearlyPitBase - actualNormalIncomeTaxCap
                    val normalCapThreshold = actualPitBase - normalCapOverflow
                    month.log("In this month the yearly base for PIT (${actualYearlyPitBase.str()}) reaches the existing cap of ${actualNormalIncomeTaxCap.str()}")
                    month.log("Only amount of ${normalCapThreshold.str()} can be taxed by ${actualNormalIncomeTaxRate.percentString()}, the rest ${normalCapOverflow.str()} will be taxed by increased rate of ${increasedIncomeTaxRate.percentString()}")
                    pitNormalTax = (normalCapThreshold * actualNormalIncomeTaxRate).sc()
                    pitIncreasedTax = (normalCapOverflow * increasedIncomeTaxRate).sc()
                    month.log("The initial PIT is: ${actualNormalIncomeTaxRate.percentString()} of $normalCapThreshold + ${increasedIncomeTaxRate.percentString()} of ${normalCapOverflow.str()} = ${pitNormalTax.str()} + ${pitIncreasedTax.str()} = ${(pitNormalTax + pitIncreasedTax).str()}")
                    pitNormalTax + pitIncreasedTax
                }
                else -> {
                    month.log("Base for PIT in this year (${actualYearlyPitBase.str()}) has reached the ${actualNormalIncomeTaxCap.str()} cap, so increasing percent applied")
                    pitNormalTax = zero
                    pitIncreasedTax = (actualPitBase * increasedIncomeTaxRate).sc()
                    month.log("The initial PIT is ${increasedIncomeTaxRate.percentString()} of ${actualPitBase.str()} = ${pitNormalTax.str()}")
                    pitIncreasedTax
                }
            }
            yearlyState.normalPit += pitNormalTax
            yearlyState.increasedPit += pitIncreasedTax
            val pit = positive(pitByRate - pitTaxFree)
            if (pitTaxFree != zero) {
                month.logNonZero(pitByRate, "Reducing initial PIT by predefined amount of ${pitTaxFree.str()} (${actualNormalTaxFreeQuota.str()} / 12): ${pitByRate.str()} - ${pitTaxFree.str()} = ${pit.str()}")
            } else {
                month.logNonZero(pitByRate, "Tax Free Quota not applicable in this month as the cap of ${actualNormalIncomeTaxCap.str()} was reached")
            }

            // this is to avoid 0 for people under 26, can be calculated just by 17% because on increased tax in this case is unreachable
            val virtualIncomeTax = (pitBase * actualNormalIncomeTaxRate - pitTaxFree).sc(0)
            val healthTax = when {
                virtualIncomeTax <= healthTaxByRate -> {
                    month.log("The amount of PIT (virtual for people under 26) ${virtualIncomeTax.str()} is less than Health Insurance Tax of ${healthTaxByRate.str()}, so setting the Health Insurance Tax to ${pit.str()} (just following the rules)")
                    pit
                }
                else -> healthTaxByRate
            }

            val incomeTaxHealthReduce = if (polskiLad) zero else (creativeBaseIncome * healthIncomeTaxReduceRate).sc()
            val reducedIncomeTax = positive(pit - incomeTaxHealthReduce).sc(0)
            if (pit != zero && incomeTaxHealthReduce != zero) {
                month.log("Calculating Health Tax part which can be taken from PIT: ${healthIncomeTaxReduceRate.percentString()} of ${baseIncome.str()} = ${incomeTaxHealthReduce.str()}")
                month.log("Reducing the income tax by allowed Health Insurance reduce (with rounding to integer): ${pit.str()} - ${incomeTaxHealthReduce.str()} = ${reducedIncomeTax.str()}")
            }

            val ppkTax = ppkTax(gross, month)

            var nettIncome = baseIncome - healthTax - reducedIncomeTax - ppkTax
            month.log("Calculating Nett Income: (Base income) - (Health Tax) - (PIT with all reductions, rounded to integer) = ${baseIncome.str()} - ${healthTax.str()} - ${reducedIncomeTax.str()} = ${nettIncome.str()}")
            val deductions = (occasionalNettDeductionsByMonth[month] ?: zero) + (if (i != 0) permanentMonthlyNettDeductions else zero)
            month.logNonZero(deductions, "In this month there is a nett deductions of ${deductions.str()}, amending the nett income: ${nettIncome.str()} - ${deductions.str()} = ${(nettIncome - deductions).str()}")
            nettIncome -= deductions

            TaxMonthInfo(month, workingDays.intValue(), sickDays.intValue(), changedContractGross, bonuses, gross, ppkTax, deductions, foodVouchersTotal, foodVouchersTaxBase, foodVouchersTaxCompensation, yearlyState.zusBase, zusBase, retirementTax, disabilityTax, sicknessTax, healthTax, zus, actualYearlyPitBase, actualPitBase, pitNormalTax, pitIncreasedTax, pitTaxFree, incomeTaxHealthReduce, reducedIncomeTax, nettIncome, month.getLog())
        }
        return taxMonths
    }

    private fun creativeWorkTaxFree(
        month: Month,
        baseIncome: BigDecimal,
        actualNormalIncomeTaxCap: BigDecimal,
        yearlyState: YearlyState,
        sickSalary: BigDecimal
    ) = if (month >= creativeWorkStart) {
        val creativeWorkEligible = baseIncome - sickSalary
        month.logNonZero(sickSalary, "You had a sick leave this month, so some part of your income cannot be reduced by creative work quota, available only: ${baseIncome.str()} - ${sickSalary.str()} = ${creativeWorkEligible.str()}")
        val creativeWorkTaxFree = min(
            baseIncome * creativeWorkPercent div 100.bdc,
            positive(actualNormalIncomeTaxCap - yearlyState.creativeWorkTaxFree)
        )
        month.logNonZero(
            creativeWorkPercent,
            "You have a PIT base reduce due to creative work quota by $creativeWorkPercent%: ${baseIncome.str()} * ${creativeWorkPercent.str()} / 100 = ${creativeWorkTaxFree.str()} (with maximum of ${actualNormalIncomeTaxCap.str()}, currently is ${yearlyState.creativeWorkTaxFree.str()})"
        )
        if (creativeWorkPercent != zero && creativeWorkTaxFree == zero)
            month.log("You have reached a maximum ($actualNormalIncomeTaxCap) of a PIT base reduce due to creative work - so no benefits")
        creativeWorkTaxFree
    } else zero

    private fun monthlyTaxFreeQuota(normalTaxFreeQuota: BigDecimal, normalIncomeTaxCap: BigDecimal, yearlyPitBase: BigDecimal, monthPitBase: BigDecimal) = when {
        sharedTaxDeclaration -> normalTaxFreeQuota
        yearlyPitBase in zero..normalIncomeTaxCap.minusPenny() -> normalTaxFreeQuota
        yearlyPitBase - normalIncomeTaxCap <= monthPitBase -> normalTaxFreeQuota
        else -> zero
    } div "12".bdc

    private fun sickLeavesGross(month: Month, sicknessCompensationState: SicknessCompensationState): Pair<BigDecimal, BigDecimal> {
        val sickDays = month.workDays.count { day -> sickLeaves.any { (start, end) -> (day.isAfter(start) && day.isBefore(end)) || day.isEqual(start) || day.isEqual(end) } }.bdc
        if (sickDays != zero && sicknessCompensationState.currentTotalWorkDays == zero) {
            month.log("You had $sickDays but you haven't yet paid for your ensurance for 30 days, so you are not eligible for sick pay")
            return zero to sickDays
        }
        if (sickDays == zero) {
            return zero to zero
        }
        val dailyGrossSalary = sicknessCompensationState.monthGrosses.takeLast(12).sum() div sicknessCompensationState.currentTotalWorkDays
        month.logNonZero(sickDays,"In this month you had $sickDays sick days which are paid as 80% of your contract gross daily salary for last 12 months = ${dailyGrossSalary.str()}")
        val grossSalaryWithinSickLeave = dailyGrossSalary * sickDays * "0.8".bdc
        month.logNonZero(sickDays, "So, gross salary within sick leave is: Daily Gross Salary * Sick Days * 80% = ${dailyGrossSalary.sc().str()} * $sickDays * 80% = ${grossSalaryWithinSickLeave.sc().str()}")
        return grossSalaryWithinSickLeave.sc() to sickDays
    }

    private fun grossWithFirstMonthRespect(
        i: Int,
        totalMonths: Int,
        month: Month,
        changedContractGross: BigDecimal,
        actualWorkingDays: BigDecimal
    ) = when (i) {
        0 -> {
            month.log("Hey, this is the first working month in this year! (at least I do not take into account your previous income)")
            val reducedMonthlyGross = (changedContractGross * (actualWorkingDays.sc(6) div month.workDaysCount.bdc.sc(6))).sc()
            if (reducedMonthlyGross <= changedContractGross) {
                month.log("You have partial working month due to start of employment, gross salary is ${reducedMonthlyGross.str()} ($actualWorkingDays out of ${month.workDaysCount} working days)")
            }
            reducedMonthlyGross
        }
        totalMonths - 1 -> {
            month.log("Hey, this is the last working month of this calculation! (at least I do not take into account your further income)")
            val reducedMonthlyGross = (changedContractGross * (actualWorkingDays.sc(6) div month.workDaysCount.bdc.sc(6))).sc()
            if (reducedMonthlyGross <= changedContractGross) {
                month.log("You have partial working month due to end of employment, gross salary is ${reducedMonthlyGross.str()} ($actualWorkingDays out of ${month.workDaysCount} working days)")
            }
            reducedMonthlyGross
        }
        else -> {
            month.log("In this month your standard gross salary is ${changedContractGross.str()}")
            changedContractGross
        }
    }

    private fun targetBonus(month: Month, gross: BigDecimal) =
        if (withTargetBonus && month.name in setOf("April", "July", "October", "February")) {
            val quarterTargetBonus = gross * quarterTargetBonusRatio
            val actualQuarterBonus = actualQuarterBonusByMonth[month] ?: quarterTargetBonus
            month.log("This month you'll get additional quarter bonus: ${quarterTargetBonusRatio.percentString()} of salary = target: $quarterTargetBonus, but actual is $actualQuarterBonus")

            val actualAnnualBonus = if (month.name == "February") {
                val annualTargetBonus = gross * annualTargetBonusRatio
                val actualAnnualBonus = actualAnnualBonusByMonth[month] ?: annualTargetBonus
                month.log("Also this month you'll get an annual bonus: ${annualTargetBonusRatio.percentString()} of salary = target: $annualTargetBonus, but actual is $actualAnnualBonus")
                actualAnnualBonus
            } else zero

            actualQuarterBonus + actualAnnualBonus
        } else zero

    private fun retirementAndDisabilityTaxBase(
        currentYearlyZusBase: BigDecimal,
        grossSalaryForZUS: BigDecimal,
        month: Month,
        withLogs: Boolean = true
    ): BigDecimal {
        val retirementAndDisabilityCap: BigDecimal = retirementAndDisabilityCapPerYear.filterKeys { it <= month.year }.maxByOrNull { it.key }?.value ?: retirementAndDisabilityCapPerYear.getValue(2021)
        return if (currentYearlyZusBase <= retirementAndDisabilityCap) {
            if (withLogs) month.log("Your current annual base for ZUS = ${currentYearlyZusBase.str()}, which is less than existing retirement and disability tax cap of ${retirementAndDisabilityCap.str()} (set by government, 30x of average Poland salary)")
            grossSalaryForZUS
        } else {
            val taxBase = positive(grossSalaryForZUS + retirementAndDisabilityCap - currentYearlyZusBase)
            if (withLogs) month.log("This is the last month in this calendar year when you'll have to pay a retirement and disability tax for sum of ${taxBase.str()} (then the cap of ${retirementAndDisabilityCap.str()} will be reached)")
            taxBase
        }
    }

    private fun healthTax(gross: BigDecimal, socialSecurityTax: BigDecimal): BigDecimal {
        return ((gross - socialSecurityTax) * healthInsuranceTaxRate).sc()
    }

    private fun foodVouchersCompensation(
        currentYearlyZusBase: BigDecimal, // <-- this already includes a food vouchers tax base
        foodVouchersTaxBase: BigDecimal,
        month: Month
    ) = if (foodVouchersFullyCompensated && foodVouchersTaxBase > zero) {
        val retirementAndDisabilityCap: BigDecimal = retirementAndDisabilityCapPerYear.filterKeys { it <= month.year }.maxByOrNull { it.key }?.value ?: retirementAndDisabilityCapPerYear.getValue(2021)
        fun whenCapReached(base: BigDecimal) = ((base * "0.112295".bdc) div "0.887705".bdc).sc()
        fun whenCapNotReached(base: BigDecimal) = ((base * "0.214761".bdc) div "0.785239".bdc).sc()
        month.log("Your company will fully compensate your food vouchers taxable income (${foodVouchersTaxBase.str()}) and make your nett unchanged! To do so, we need to add bonus which will cover ZUS tax and it's own tax")
        when {
            currentYearlyZusBase - foodVouchersTaxBase >= retirementAndDisabilityCap -> {
                month.log("You have reached a retirement and disability cap, so formula for calculating such ZUS compensation goes from the following equation: (${foodVouchersTaxBase.str()} + X) [Target Gross] - (${foodVouchersTaxBase.str()} + X)*0.0245 [ZUS] - ((${foodVouchersTaxBase.str()} + X) - (${foodVouchersTaxBase.str()} + X)*0.0245)*0.09 [Health] = ${foodVouchersTaxBase.str()} [Need to be compensated]")
                val taxCompensation = whenCapReached(foodVouchersTaxBase)
                month.log("Simplifying it, the food vouchers compensation bonus would be: X = ${foodVouchersTaxBase.str()} * 0.112295 / 0.887705 = ${taxCompensation.str()}")
                taxCompensation
            }
            currentYearlyZusBase in zero..retirementAndDisabilityCap -> {
                month.log("The retirement and disability cap is not reached yet, so formula for calculating such ZUS compensation goes from the following equation: (${foodVouchersTaxBase.str()} + X) [Target Gross] - (${foodVouchersTaxBase.str()} + X)*0.1371 [ZUS] - ((${foodVouchersTaxBase.str()} + X) - (${foodVouchersTaxBase.str()} + X)*0.1371)*0.09 [Health] = ${foodVouchersTaxBase.str()} [Need to be compensated]")
                val taxCompensation = whenCapNotReached(foodVouchersTaxBase)
                month.log("Simplifying it would be: X = ${foodVouchersTaxBase.str()} * 0.214761 / 0.785239 = ${taxCompensation.str()}")
                taxCompensation
            }
            currentYearlyZusBase - foodVouchersTaxBase <= retirementAndDisabilityCap && currentYearlyZusBase >= retirementAndDisabilityCap -> {
                month.log("The retirement and disability cap was reached by food vouchers taxable income, so applying mixed formula (too long to print)")
                val afterCap = currentYearlyZusBase - retirementAndDisabilityCap
                val taxCompensationAfterCap = whenCapReached(afterCap)
                val taxCompensationBeforeCap = whenCapNotReached(foodVouchersTaxBase - afterCap)
                val taxCompensation = taxCompensationBeforeCap + taxCompensationAfterCap
                month.log("Simplifying it would be: X = (${(foodVouchersTaxBase - afterCap).str()} * 0.214761 / 0.785239) + (${afterCap.str()} 0.112295 / 0.887705) = ${taxCompensationBeforeCap.str()} + ${taxCompensationAfterCap.str()} = ${taxCompensation.str()}")
                taxCompensation
            }
            else -> {
                for (i in 1..10) month.log("!!!!!!!!!!!!!!!!!! Seems like it's a bug in food vouchers tax compensation !!!!!!!!!!!!!!!!!!")
                zero
            }
        }
    } else zero

    private fun sicknessTax(grossSalary: BigDecimal): BigDecimal = (grossSalary * sicknessInsuranceTaxRate).sc()

    private fun retirementTax(base: BigDecimal) = if (base != zero) {
        (base * retirementInsuranceTaxRate).sc()
    } else zero

    private fun disabilityTax(base: BigDecimal) = if (base != zero) {
        (base * disabilityInsuranceTaxRate).sc()
    } else zero

    private fun ppkTax(grossSalary: BigDecimal, month: Month) = if (participateInPPK && (month.cal.atDay(1).toEpochDay().toInt() - startDate.toEpochDay().toInt()) >= 90) {
        val result = (grossSalary * ppkTaxRate).sc()
        month.log("You're participating in PPK and more than 90 days passed since your employment, so need to pay a contribution: ${ppkTaxRate.percentString()} of ${grossSalary.str()} = ${result.str()}")
        result
    } else zero

    private fun isTeenZeroTaxApply(month: Month) = if (month.cal.atDay(1).compareTo(birthday26years).toInt() >= 0) {
        if (month.inDayRange(birthday26years)) {
            if (birthday26years.dayOfMonth().toInt() > salaryDayOfMonth) {
                month.log("Unfortunately in this month of your 26 B-day you're not applicable for PIT-free privilege because the salary day ($salaryDayOfMonth) is after your birth day ${birthday26years.dayOfMonth()}")
                false
            } else {
                month.log("Good news! In this month you will have zero PIT for the last time! You will have this privilege because the salary day ($salaryDayOfMonth) is earlier than your birth day ${birthday26years.dayOfMonth()}")
                true
            }
        } else false
    } else true
}